package com.tvcarousel.logs

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    private lateinit var dir:File
    fun init(c:Context) {dir=File(c.filesDir,"logs").apply {mkdirs()}}
    @Synchronized fun write(s:String) {runCatching {
        val f=File(dir,"current.log")
        if(f.length()>1024*1024) {val old=File(dir,"previous.log");old.delete();f.renameTo(old)}
        f.appendText(SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())+" "+s.take(12000)+"\n")
    }}
    @Synchronized fun read()=runCatching {File(dir,"current.log").readText().takeLast(60000)}.getOrDefault("")
}
