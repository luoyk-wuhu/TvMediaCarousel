package com.tvcarousel.orientation

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.tvcarousel.settings.Config

open class RotatingActivity : Activity() {
    lateinit var stage:FrameLayout
    private lateinit var outer:FrameLayout
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        outer=FrameLayout(this);stage=FrameLayout(this)
        outer.addView(stage,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));setContentView(outer)
        outer.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->rotate()}
    }
    override fun onResume() {super.onResume();immersive();outer.post {rotate()}}
    override fun onWindowFocusChanged(hasFocus:Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    @Suppress("DEPRECATION") private fun immersive() {window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION}
    // D-pad directions follow the upright content's layout axes at every rotation.
    // Inverse-mapping them made LEFT/RIGHT navigate vertically on a rotated TV.
    fun rotate() {
        val degree=Config.int("rotation",0);val swap=degree==90||degree==270
        val w=if(swap)outer.height else outer.width;val h=if(swap)outer.width else outer.height
        if(w<=0||h<=0)return
        if(stage.layoutParams.width!=w||stage.layoutParams.height!=h)stage.layoutParams=FrameLayout.LayoutParams(w,h,Gravity.CENTER)
        stage.rotation=degree.toFloat()
    }
}
