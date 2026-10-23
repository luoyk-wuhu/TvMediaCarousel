package com.tvcarousel.boot
import android.content.*
import com.tvcarousel.settings.Config
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        if(i.action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED)&&Config.bool("autoBoot",true))CarouselService.start(c,true)
    }
}
