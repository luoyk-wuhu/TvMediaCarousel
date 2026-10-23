package com.tvcarousel.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Shared touch targets and visible remote-control focus, without another UI runtime. */
object TouchUi {
    val background=Color.rgb(12,18,30)
    val surface=Color.rgb(24,34,51)
    val muted=Color.rgb(162,178,199)
    val accent=Color.rgb(110,231,205)
    val focus=Color.rgb(255,211,105)
    fun focusBackground(c:Context,normal:Int)=StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused),shape(c,focus,12).apply {setStroke(dp(c,3),Color.WHITE)})
        addState(intArrayOf(android.R.attr.state_pressed),shape(c,Color.rgb(66,88,108),12))
        addState(intArrayOf(),shape(c,normal,12))
    }
    fun focusText(normal:Int)=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused),intArrayOf()),intArrayOf(background,normal))
    fun focusableViews(root:View):List<View> {
        if(root.visibility!=View.VISIBLE)return emptyList()
        if(root is ViewGroup)return (0 until root.childCount).flatMap {focusableViews(root.getChildAt(it))}
        return if(root.isFocusable&&root.isEnabled)listOf(root) else emptyList()
    }
    fun reveal(view:View) {view.post {if(view.isAttachedToWindow&&view.hasFocus())view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}}
    fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    fun shape(c:Context,color:Int,radius:Int=18)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(c,radius).toFloat()
    }
    fun text(c:Context,value:String,size:Float=16f,color:Int=Color.WHITE)=TextView(c).apply {
        text=value;textSize=size;setTextColor(color);setPadding(0,dp(c,6),0,dp(c,6))
        if(size>=24)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    fun column(c:Context)=LinearLayout(c).apply {orientation=LinearLayout.VERTICAL}
    fun card(c:Context)=column(c).apply {
        background=shape(c,surface);setPadding(dp(c,20),dp(c,14),dp(c,20),dp(c,18))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(c,14)}
    }
    fun button(c:Context,title:String,primary:Boolean=false,action:()->Unit)=Button(c).apply {
        text=title;textSize=16f;isAllCaps=false;gravity=Gravity.CENTER
        id=View.generateViewId();tag=title;isFocusable=true;isFocusableInTouchMode=false
        setTextColor(focusText(if(primary)TouchUi.background else Color.WHITE))
        minHeight=dp(c,56);minimumHeight=dp(c,56);setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10))
        background=focusBackground(c,if(primary)accent else Color.rgb(35,49,69))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(c,6);bottomMargin=dp(c,4)}
        setOnClickListener {action()}
    }
}
