package com.tvcarousel.boot

import android.app.*
import android.content.*
import android.os.*
import com.tvcarousel.CarouselApp
import com.tvcarousel.R
import com.tvcarousel.logs.Logs
import com.tvcarousel.media.MediaLibrary
import com.tvcarousel.player.PlayerActivity
import com.tvcarousel.settings.Config
import com.tvcarousel.upload.*
import com.tvcarousel.usb.UsbMonitor

class CarouselService : Service() {
    private var server:LanServer?=null
    private var bound=""
    private lateinit var usb:UsbMonitor
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable {override fun run() {
        val ip=LanServer.lanIp(this@CarouselService)
        val desired=if(Config.bool("uploadEnabled",true)&&ip!=null)"$ip:${Config.int("port",8080)}" else ""
        if(desired!=bound||server==null&&desired.isNotEmpty()) {
            server?.close();server=null;bound=desired
            if(desired.isNotEmpty())try {server=LanServer(this@CarouselService,ip!!,Config.int("port",8080))}
            catch(e:Exception) {UploadStatus.address="上传服务不可用";UploadStatus.detail="上传服务启动失败，请检查端口";Logs.write("HTTP 启动失败: $e")}
            else {UploadStatus.address=if(ip==null)"无可用 WiFi / 有线局域网" else "上传服务已关闭";UploadStatus.detail=UploadStatus.address}
        }
        handler.postDelayed(this,10000)
    }}
    override fun onCreate() {
        super.onCreate()
        val nm=getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("signage","媒体轮播服务",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,PlayerActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,"signage") else Notification.Builder(this)
        startForeground(1,builder.setContentTitle("电视媒体轮播运行中").setContentText("点击返回播放；局域网上传可在设置中关闭").setSmallIcon(R.drawable.icon).setContentIntent(open).setOngoing(true).build())
        usb=UsbMonitor(this);usb.start();handler.post(tick)
        handler.postDelayed({MediaLibrary.rescan()},Config.int("bootDelay",5)*1000L)
        handler.postDelayed({MediaLibrary.rescan()},30000) // slow USB mount fallback
    }
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
        if(i?.getBooleanExtra("recover",false)==true||i?.getBooleanExtra("boot",false)==true) {
            handler.postDelayed({
                runCatching {startActivity(Intent(this,PlayerActivity::class.java).putExtra("bootDelayElapsed",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {Logs.write("系统限制后台打开播放页，请启用默认桌面: $it")}
            },Config.int("bootDelay",5)*1000L)
        }
        handler.removeCallbacks(tick);handler.post(tick)
        return START_STICKY
    }
    override fun onDestroy() {handler.removeCallbacksAndMessages(null);usb.stop();server?.close();super.onDestroy()}
    override fun onBind(i:Intent?)=null
    companion object {
        fun start(c:Context,boot:Boolean=false) {
            val i=Intent(c,CarouselService::class.java).putExtra("boot",boot)
            runCatching {if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i) else c.startService(i)}.onFailure {Logs.write("前台服务启动受限，请手动打开 App: $it")}
        }
    }
}
