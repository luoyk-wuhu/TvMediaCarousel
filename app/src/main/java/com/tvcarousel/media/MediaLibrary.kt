package com.tvcarousel.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.tvcarousel.CarouselApp
import com.tvcarousel.logs.Logs
import com.tvcarousel.settings.Config
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object MediaLibrary {
    lateinit var context:android.app.Application
        private set
    lateinit var local:File
    private lateinit var database:File
    val worker=Executors.newSingleThreadExecutor()
    private val scanning=AtomicBoolean(false)
    private val again=AtomicBoolean(false)
    private val listeners=CopyOnWriteArraySet<()->Unit>()
    @Volatile var items:List<MediaItem> = emptyList(); private set
    @Volatile var status="等待扫描"; private set
    fun init(c:android.app.Application) {
        context=c; local=File(c.filesDir,"media").apply {mkdirs()};database=File(c.filesDir,"library.json")
        items=runCatching {val a=JSONArray(database.readText());(0 until a.length()).map {MediaItem.from(a.getJSONObject(it))}}.getOrDefault(emptyList())
        local.listFiles()?.filter {it.name.endsWith(".part")}?.forEach {it.delete()}
    }
    fun listen(f:()->Unit) {listeners.add(f)}
    fun unlisten(f:()->Unit) {listeners.remove(f)}
    private fun notifyChange() {CarouselApp.main.post {listeners.forEach {it()} }}
    @Synchronized private fun publish(list:List<MediaItem>) {
        items=list
        runCatching {val tmp=File(database.parentFile,"library.tmp");tmp.writeText(JSONArray().apply {list.forEach {put(it.json())}}.toString());check(tmp.renameTo(database))}.onFailure {Logs.write("保存媒体库失败: $it")}
        notifyChange()
    }
    fun ordered():List<MediaItem> {
        val current=items
        return when(Config.string("sort","name")) {"modified"->current.sortedBy {it.modified};"added"->current.sortedBy {it.addedAt};else->current.sortedWith {a,b->NaturalOrder.compare(a.name,b.name)}}
    }
    fun rescan() {
        if(!scanning.compareAndSet(false,true)) {again.set(true);return}
        status="正在后台扫描…";notifyChange()
        worker.execute {
            try {
                val direct=mutableListOf<MediaItem>(); val imports=HashMap<String,MediaItem>(); val visited=HashSet<String>();var count=0
                val old=items.associateBy {it.uri}
                fun accept(uri:Uri,name:String,size:Long,modified:Long) {
                    val type=MediaItem.type(name) ?: return
                    if(++count>20000)return
                    if(!visited.add(uri.toString()))return
                    if(Config.bool("usbCopy",true)) {
                        val key=Config.hash("$uri:$size:$modified")
                        val destination=File(local,"usb_$key.${name.substringAfterLast('.').lowercase()}")
                        if(!destination.exists()) runCatching {
                            context.contentResolver.openInputStream(uri)?.use {input-> copy(input,destination,size)} ?: error("无法读取 $name")
                        }.onFailure {Logs.write("导入 $name 失败: $it")}
                        if(destination.exists()) {
                            val localUri=Uri.fromFile(destination).toString()
                            imports[localUri]=MediaItem(Config.hash(localUri),localUri,type,name,destination.length(),modified,old[localUri]?.addedAt ?: System.currentTimeMillis(),"local")
                        }
                    } else {
                        val previous=old[uri.toString()]
                        direct+=MediaItem(Config.hash(uri.toString()),uri.toString(),type,name,size,modified,previous?.addedAt ?: System.currentTimeMillis(),"usb")
                    }
                }
                fun walkDoc(d:DocumentFile,depth:Int) {
                    if(depth>12||count>20000)return
                    d.listFiles().forEach {f-> val n=f.name ?: return@forEach
                        if(n.startsWith('.')||n.equals("Android",true)||n.equals("System Volume Information",true))return@forEach
                        runCatching {if(f.isDirectory)walkDoc(f,depth+1) else if(f.isFile)accept(f.uri,n,f.length(),f.lastModified())}.onFailure {Logs.write("SAF 扫描失败: $it")}
                    }
                }
                val authorizedVolumes=context.contentResolver.persistedUriPermissions.mapNotNull {runCatching {android.provider.DocumentsContract.getTreeDocumentId(it.uri).substringBefore(':').lowercase()}.getOrNull()}.toSet()
                context.contentResolver.persistedUriPermissions.filter {it.isReadPermission}.forEach {p->runCatching {DocumentFile.fromTreeUri(context,p.uri)?.let {walkDoc(it,0)}}.onFailure {Logs.write("授权目录不可用: $it")}}
                fun walkFile(f:File,depth:Int) {
                    if(depth>12||count>20000)return
                    f.listFiles()?.forEach {v->
                        if(v.name.startsWith('.')||v.name.equals("Android",true)||v.name.equals("System Volume Information",true))return@forEach
                        runCatching {if(v.isDirectory) {if(v.canonicalPath.startsWith(f.canonicalPath+"/"))walkFile(v,depth+1)} else accept(Uri.fromFile(v),v.name,v.length(),v.lastModified())}
                    }
                }
                val roots=mutableSetOf<File>()
                if(android.os.Build.VERSION.SDK_INT>=30) {
                    val manager=context.getSystemService(android.os.storage.StorageManager::class.java)
                    manager.storageVolumes.filter {it.isRemovable&&it.state==Environment.MEDIA_MOUNTED}.mapNotNullTo(roots) {it.directory}
                }
                File("/storage").listFiles()?.filter {it.name.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))&&it.canRead()}?.let {roots.addAll(it)}
                roots.filter {it.name.lowercase() !in authorizedVolumes}.forEach {runCatching {walkFile(it,0)}.onFailure {Logs.write("挂载目录扫描失败: $it")}}
                // MediaStore fallback for removable volumes visible to the current media permission grant.
                if(android.os.Build.VERSION.SDK_INT>=29) {
                    runCatching {
                        android.provider.MediaStore.getExternalVolumeNames(context).filter {it!="external_primary" && it.lowercase() !in authorizedVolumes && roots.none {f->f.name.equals(it,true)}}.forEach {volume->
                            val collection=android.provider.MediaStore.Files.getContentUri(volume)
                            context.contentResolver.query(collection,arrayOf("_id","_display_name","_size","date_modified"),"media_type IN (1,3)",null,null)?.use {c->
                                while(c.moveToNext()&&count<20000)accept(android.content.ContentUris.withAppendedId(collection,c.getLong(0)),c.getString(1) ?: "",c.getLong(2),c.getLong(3)*1000)
                            }
                        }
                    }.onFailure {Logs.write("MediaStore 扫描: $it")}
                }
                synchronized(this) {
                    val previous=items.associateBy {it.uri}
                    val locals=local.listFiles().orEmpty().filter {it.isFile&&MediaItem.type(it.name)!=null}.map {f->
                        val uri=Uri.fromFile(f).toString(); val prior=imports[uri] ?: previous[uri]
                        MediaItem(Config.hash(uri),uri,MediaItem.type(f.name)!!,prior?.name ?: f.name,f.length(),prior?.modified ?: f.lastModified(),prior?.addedAt ?: f.lastModified(),"local")
                    }
                    status="扫描完成：${locals.size+direct.size} 项"; publish(locals+direct.distinctBy {it.uri})
                }
            } catch(e:Exception) {status="扫描失败，请检查权限或重新授权 U 盘";Logs.write("扫描异常: $e");notifyChange()}
            finally {scanning.set(false);if(again.getAndSet(false))rescan()}
        }
    }
    private fun copy(input:InputStream,dest:File,expected:Long) {
        check(local.usableSpace>expected+64L*1024*1024) {"存储空间不足"}
        val tmp=File(local,dest.name+".part")
        try {tmp.outputStream().use {out->val b=ByteArray(128*1024);var total=0L;while(true) {val n=input.read(b);if(n<0)break;total+=n;check(local.usableSpace>64L*1024*1024);out.write(b,0,n)};if(expected>0)check(total==expected)};check(tmp.renameTo(dest))}
        finally {tmp.delete()}
    }
    @Synchronized fun commitUpload(temp:File,name:String):MediaItem {
        val ext=name.substringAfterLast('.').lowercase();require(MediaItem.type(name)!=null)
        val f=File(local,"${UUID.randomUUID()}.$ext"); check(temp.renameTo(f))
        val now=System.currentTimeMillis();val uri=Uri.fromFile(f).toString()
        val item=MediaItem(Config.hash(uri),uri,MediaItem.type(name)!!,name,f.length(),now,now,"local")
        publish(items+item);return item
    }
    @Synchronized fun delete(id:String):Boolean {
        val item=items.find {it.id==id&&it.source=="local"} ?: return false
        val f=File(Uri.parse(item.uri).path ?: return false)
        if(f.parentFile?.canonicalPath!=local.canonicalPath||!f.delete())return false
        publish(items.filter {it.id!=id});return true
    }
    fun clear() {worker.execute {synchronized(this) {items.filter {it.source=="local"}.forEach {runCatching {File(Uri.parse(it.uri).path!!).delete()}};publish(items.filter {it.source!="local"})}}}
}
