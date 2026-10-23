package com.tvcarousel.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tvcarousel.logs.Logs
import com.tvcarousel.media.MediaLibrary
import com.tvcarousel.settings.Config
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.UUID
import java.util.concurrent.*

object UploadStatus { @Volatile var address="无可用 WiFi / 有线局域网"; @Volatile var detail="服务未启动" }

class LanServer(private val context:Context,private val ip:String,requestedPort:Int) {
    private val server=ServerSocket()
    private val clients=java.util.Collections.newSetFromMap(ConcurrentHashMap<Socket,Boolean>())
    private val pool=ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,ArrayBlockingQueue(2))
    @Volatile private var running=true
    private var failures=0
    private var blockedUntil=0L
    val port:Int
    init {
        server.reuseAddress=true
        var bound=false
        for(p in requestedPort..minOf(65535,requestedPort+10)) {
            try {server.bind(InetSocketAddress(InetAddress.getByName(ip),p));bound=true;break} catch(e:BindException) {Logs.write("端口 $p 被占用")} catch(e:Exception) {server.close();pool.shutdownNow();throw e}
        }
        if(!bound) {server.close();pool.shutdownNow();error("上传端口均被占用")};port=server.localPort
        UploadStatus.address="http://$ip:$port";UploadStatus.detail=if(port==requestedPort)"上传服务运行中" else "端口冲突，已改用 $port"
        Thread({while(running) {try {
            val s=server.accept();s.soTimeout=60000;clients.add(s)
            try {pool.execute {s.use {handle(it)};clients.remove(s)}} catch(e:RejectedExecutionException) {clients.remove(s);s.close()}
        } catch(e:IOException) {if(running)Logs.write("HTTP 接入失败: $e")}}},"lan-accept").start()
    }
    fun close() {running=false;runCatching {server.close()};clients.forEach {runCatching {it.close()}};pool.shutdownNow()}
    private class HttpError(val code:Int,override val message:String):Exception(message)
    private fun line(input:InputStream,max:Int):String {
        val b=ByteArrayOutputStream()
        while(true) {val c=input.read();if(c<0)throw EOFException();if(c==10)break;if(b.size()>=max)throw HttpError(431,"请求头过长");b.write(c)}
        return b.toString("UTF-8").trimEnd('\r')
    }
    @Synchronized private fun auth(pin:String):Boolean {
        if(System.currentTimeMillis()<blockedUntil)return false
        if(Config.verify("uploadPin",pin)) {failures=0;return true}
        if(++failures>=5) {blockedUntil=System.currentTimeMillis()+30000;failures=0};return false
    }
    private fun handle(s:Socket) {
        try {
            val input=BufferedInputStream(s.getInputStream(),128*1024)
            val request=line(input,4096).split(' ');if(request.size!=3)throw HttpError(400,"无效请求")
            val method=request[0]; val target=URI(request[1]);val path=target.path
            val headers=HashMap<String,String>();var total=0
            while(true) {val l=line(input,8192);total+=l.length;if(total>16384)throw HttpError(431,"请求头过长");if(l.isEmpty())break
                val split=l.indexOf(':');if(split<=0)throw HttpError(400,"无效请求头")
                val key=l.substring(0,split).lowercase();if(headers.containsKey(key))throw HttpError(400,"重复请求头")
                headers[key]=l.substring(split+1).trim()
            }
            val host="$ip:$port"
            if(headers["host"]!=host)throw HttpError(403,"请使用电视显示的 IP 地址访问")
            if(headers["origin"]!=null&&headers["origin"]!="http://$host")throw HttpError(403,"跨站请求已拒绝")
            if(method=="GET"&&path=="/") {respond(s,200,context.assets.open("upload.html").use {it.readBytes()},"text/html; charset=utf-8");return}
            if(!auth(headers["x-pin"] ?: ""))throw HttpError(401,"PIN 错误，连续失败后请等待 30 秒")
            val params=target.rawQuery?.split('&')?.associate {val p=it.split('=',limit=2);URLDecoder.decode(p[0],"UTF-8") to URLDecoder.decode(p.getOrElse(1){""},"UTF-8")} ?: emptyMap()
            when {
                method=="GET"&&path=="/api/files" -> json(s,JSONObject().put("files",JSONArray().apply {MediaLibrary.items.filter {it.source=="local"}.forEach {put(it.json().apply {remove("uri")})}}).put("free",MediaLibrary.local.usableSpace).put("status",MediaLibrary.status))
                method=="POST"&&path=="/api/refresh" -> {MediaLibrary.rescan();json(s,JSONObject().put("ok",true))}
                method=="DELETE"&&path=="/api/file" -> {if(!MediaLibrary.delete(params["id"] ?: ""))throw HttpError(404,"文件不存在或无法删除");json(s,JSONObject().put("ok",true))}
                method=="POST"&&path=="/api/upload" -> {
                    if(headers.containsKey("transfer-encoding"))throw HttpError(411,"请使用已知文件大小的浏览器上传")
                    val length=headers["content-length"]?.toLongOrNull() ?: throw HttpError(411,"缺少文件大小")
                    if(length<=0||length>16L*1024*1024*1024)throw HttpError(413,"单文件须在 1 字节到 16 GiB 之间")
                    val name=params["name"] ?: throw HttpError(400,"缺少文件名")
                    if(name.length>240||name.any {it<' '||it=='/'||it=='\\'}||MediaLibrary.MediaTypeInvalid(name))throw HttpError(415,"不支持此文件类型")
                    if(MediaLibrary.local.usableSpace<length+64L*1024*1024)throw HttpError(507,"存储空间不足")
                    val tmp=File(MediaLibrary.local,"${UUID.randomUUID()}.part")
                    try {
                        FileOutputStream(tmp).use {out->
                            StreamTransfer.copyExact(input,out,length) {if(MediaLibrary.local.usableSpace<64L*1024*1024)throw HttpError(507,"存储空间不足")}
                            out.fd.sync()
                        }
                        val item=MediaLibrary.commitUpload(tmp,name);Logs.write("上传完成: ${item.name} (${item.size})");json(s,JSONObject().put("ok",true))
                    } finally {tmp.delete()}
                }
                else -> throw HttpError(404,"接口不存在")
            }
        } catch(e:HttpError) {runCatching {respond(s,e.code,JSONObject().put("error",e.message).toString().toByteArray(),"application/json; charset=utf-8")}}
        catch(e:Exception) {Logs.write("HTTP 请求失败: ${e.javaClass.simpleName}");runCatching {respond(s,500,"{\"error\":\"请求失败或连接中断\"}".toByteArray(),"application/json")}}
    }
    private fun json(s:Socket,j:JSONObject)=respond(s,200,j.toString().toByteArray(),"application/json; charset=utf-8")
    private fun respond(s:Socket,code:Int,data:ByteArray,type:String) {
        val out=s.getOutputStream()
        out.write(("HTTP/1.1 $code Response\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; frame-ancestors 'none'\r\n\r\n").toByteArray(Charsets.US_ASCII));out.write(data);out.flush()
    }
    companion object {
        fun lanIp(c:Context):String? {
            val cm=c.getSystemService(ConnectivityManager::class.java)
            return cm.allNetworks.firstNotNullOfOrNull {network->
                val cap=cm.getNetworkCapabilities(network)
                if(cap==null||(!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))null
                else cm.getLinkProperties(network)?.linkAddresses?.map {it.address}?.filterIsInstance<Inet4Address>()?.firstOrNull {it.isSiteLocalAddress&&!it.isLoopbackAddress}?.hostAddress
            }
        }
    }
}
private fun MediaLibrary.MediaTypeInvalid(name:String)=com.tvcarousel.media.MediaItem.type(name)==null
