package com.tvcarousel.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.tvcarousel.media.MediaLibrary

class UsbMonitor(private val context:Context) : BroadcastReceiver() {
    private val handler=Handler(Looper.getMainLooper())
    private val scan=Runnable {MediaLibrary.rescan()}
    fun start() {val f=IntentFilter().apply {
        addAction(Intent.ACTION_MEDIA_MOUNTED);addAction(Intent.ACTION_MEDIA_UNMOUNTED);addAction(Intent.ACTION_MEDIA_EJECT);addAction(Intent.ACTION_MEDIA_REMOVED);addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);addDataScheme("file")
    }; if(android.os.Build.VERSION.SDK_INT>=33)context.registerReceiver(this,f,Context.RECEIVER_EXPORTED) else context.registerReceiver(this,f)}
    override fun onReceive(c:Context,i:Intent) {handler.removeCallbacks(scan);handler.postDelayed(scan,if(i.action==Intent.ACTION_MEDIA_MOUNTED)1500 else 0)}
    fun stop() {handler.removeCallbacksAndMessages(null);runCatching {context.unregisterReceiver(this)}}
}
