package com.tvcarousel.boot
import android.app.*
import android.content.*
import android.os.Build
import com.tvcarousel.settings.Config
object Recovery {
    fun schedule(c:Context) {runCatching {
        val now=System.currentTimeMillis();val start=Config.prefs.getLong("crashWindow",0)
        val count=if(now-start<600000)Config.int("crashCount",0)+1 else 1
        if(count==1)Config.set("crashWindow",now)
        Config.prefs.edit().putInt("crashCount",count).commit()
        if(count>3)return
        val intent=Intent(c,CarouselService::class.java).putExtra("recover",true)
        val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val p=if(Build.VERSION.SDK_INT>=26)PendingIntent.getForegroundService(c,4,intent,flags) else PendingIntent.getService(c,4,intent,flags)
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,now+15000,p)
    }}
}
