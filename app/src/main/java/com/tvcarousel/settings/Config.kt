package com.tvcarousel.settings

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

object Config {
    lateinit var prefs: SharedPreferences
    fun init(c: Context) { prefs=c.getSharedPreferences("settings",0)
        if (!prefs.contains("uploadPin")) { val p=(100000+SecureRandom().nextInt(900000)).toString(); set("initialPin",p); set("uploadPin",hash(p)); set("settingsPin",hash(p)) }
    }
    fun string(k:String,d:String="")=prefs.getString(k,d) ?: d
    fun int(k:String,d:Int)=prefs.getInt(k,d)
    fun bool(k:String,d:Boolean)=prefs.getBoolean(k,d)
    fun set(k:String,v:Any) { val e=prefs.edit(); when(v) { is String->e.putString(k,v); is Int->e.putInt(k,v); is Boolean->e.putBoolean(k,v); is Long->e.putLong(k,v) };e.apply() }
    fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    fun verify(key:String,p:String)=MessageDigest.isEqual(hash(p).toByteArray(),string(key).toByteArray())
}
