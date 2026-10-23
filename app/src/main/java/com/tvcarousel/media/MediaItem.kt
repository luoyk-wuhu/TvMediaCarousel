package com.tvcarousel.media

import org.json.JSONObject
import java.util.Comparator

data class MediaItem(val id:String,val uri:String,val type:String,val name:String,val size:Long,
    val modified:Long,val addedAt:Long,val source:String) {
    fun json()=JSONObject().put("id",id).put("uri",uri).put("type",type).put("name",name).put("size",size).put("modified",modified).put("addedAt",addedAt).put("source",source)
    companion object {
        fun from(j:JSONObject)=MediaItem(j.getString("id"),j.getString("uri"),j.getString("type"),j.getString("name"),j.getLong("size"),j.getLong("modified"),j.getLong("addedAt"),j.getString("source"))
        fun type(name:String):String?=when(name.substringAfterLast('.',"").lowercase()) {"jpg","jpeg","png","webp"->"image";"mp4","m4v","mkv","webm"->"video";else->null}
    }
}
object NaturalOrder : Comparator<String> {
    private val parts=Regex("[0-9]+|[^0-9]+")
    override fun compare(a:String,b:String):Int {
        val x=parts.findAll(a.lowercase()).map {it.value}.toList(); val y=parts.findAll(b.lowercase()).map {it.value}.toList()
        for(i in 0 until minOf(x.size,y.size)) {
            val p=x[i];val q=y[i]
            val c=if(p[0].isDigit()&&q[0].isDigit()) {val m=p.trimStart('0').ifEmpty {"0"};val n=q.trimStart('0').ifEmpty {"0"}; m.length.compareTo(n.length).takeIf {it!=0} ?: m.compareTo(n)} else p.compareTo(q)
            if(c!=0)return c
        }
        return x.size.compareTo(y.size).takeIf {it!=0} ?: a.compareTo(b)
    }
}
