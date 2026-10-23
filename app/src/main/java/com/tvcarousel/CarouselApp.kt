package com.tvcarousel

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.tvcarousel.logs.Logs
import com.tvcarousel.media.MediaLibrary
import com.tvcarousel.settings.Config

class CarouselApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Config.init(this); Logs.init(this); MediaLibrary.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logs.write("CRASH ${t.name}: ${e.stackTraceToString()}")
            // Alarm restart is best effort; stop crash loops after three failures in ten minutes.
            com.tvcarousel.boot.Recovery.schedule(this)
            previous?.uncaughtException(t,e)
        }
    }
    companion object { val main = Handler(Looper.getMainLooper()) }
}
