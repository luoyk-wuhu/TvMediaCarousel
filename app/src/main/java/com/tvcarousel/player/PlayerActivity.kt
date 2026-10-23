package com.tvcarousel.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.tvcarousel.R
import com.tvcarousel.boot.CarouselService
import com.tvcarousel.logs.Logs
import com.tvcarousel.media.*
import com.tvcarousel.orientation.RotatingActivity
import com.tvcarousel.settings.Config
import com.tvcarousel.settings.SettingsActivity
import com.tvcarousel.upload.UploadStatus
import com.tvcarousel.ui.TouchUi as Ui

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlayerActivity : RotatingActivity() {
    private lateinit var image:ImageView
    private lateinit var video:PlayerView
    private lateinit var message:TextView
    private lateinit var emptyPanel:LinearLayout
    private lateinit var controls:LinearLayout
    private lateinit var track:TextView
    private lateinit var pauseButton:Button
    private var paused=false
    private var finishedRound=false
    private val hideControls=Runnable {if(!paused&&queue.isNotEmpty()&&!controls.hasFocus())controls.visibility=View.GONE}
    private var player:ExoPlayer?=null
    private val handler=Handler(Looper.getMainLooper())
    private var queue=listOf<MediaItem>()
    private var index=-1
    private var generation=0
    private var active=false
    private var firstStart=true
    private var playAfter=0L
    private var transitioning=false
    private var failureCount=0
    private var lastPosition=-1L
    private var stalledSince=0L
    private val advance=Runnable {if(!paused)next()}
    private val unlock=Runnable {startActivity(Intent(this,SettingsActivity::class.java))}
    private val changed:()->Unit={
        if(active) {
            val current=queue.getOrNull(index)
            if(current==null||MediaLibrary.items.none {it.id==current.id}) {handler.removeCallbacks(advance);reload();next()}
            else {queue=playlist();index=queue.indexOfFirst {it.id==current.id}}
        }
    }
    private val heartbeat=object:Runnable {override fun run() {
        if(!active)return
        if(queue.isEmpty())message.text=emptyText()
        player?.let {p->if(video.visibility==View.VISIBLE&&!paused) {
            if(p.currentPosition!=lastPosition) {lastPosition=p.currentPosition;stalledSince=SystemClock.elapsedRealtime()}
            else if(SystemClock.elapsedRealtime()-stalledSince>90000) {Logs.write("视频无进度，跳过");next()}
        }}
        handler.postDelayed(this,5000)
    }}
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        stage.setBackgroundColor(android.graphics.Color.BLACK)
        image=ImageView(this);stage.addView(image,FrameLayout.LayoutParams(-1,-1))
        video=layoutInflater.inflate(R.layout.video,stage,false) as PlayerView;stage.addView(video)
        message=TextView(this).apply {gravity=Gravity.CENTER;textSize=23f;setTextColor(-1);setPadding(36,36,36,36)};stage.addView(message,FrameLayout.LayoutParams(-1,-1))
        buildTouchUi()
        CarouselService.start(this)
        if(!Config.bool("askedPermissions",false))requestMediaPermissions()
    }
    private fun buildTouchUi() {
        val d={n:Int->Ui.dp(this,n)}
        emptyPanel=Ui.column(this).apply {
            gravity=Gravity.CENTER;setPadding(d(24),d(24),d(24),d(24));background=Ui.shape(this@PlayerActivity,Ui.background,0)
        }
        emptyPanel.addView(Ui.text(this,"TV MEDIA  /  电视媒体轮播",14f,Ui.accent))
        emptyPanel.addView(Ui.text(this,"把喜欢的内容，放上大屏",30f))
        emptyPanel.addView(Ui.text(this,"选择一个媒体文件夹，或通过手机上传开始轮播",16f,Ui.muted))
        emptyPanel.addView(Ui.button(this,"＋  选择媒体文件夹",true) {openDashboard("文件夹")})
        emptyPanel.addView(Ui.button(this,"打开媒体库 / 手机上传") {openDashboard("媒体库")})
        stage.addView(emptyPanel,FrameLayout.LayoutParams(-1,-1))
        controls=Ui.column(this).apply {setPadding(d(16),d(12),d(16),d(16));background=Ui.shape(this@PlayerActivity,Ui.surface,18)}
        track=Ui.text(this,"",15f);controls.addView(track)
        val row=LinearLayout(this);controls.addView(row)
        fun action(title:String,click:()->Unit):Button {
            val b=Ui.button(this,title,false) {click();revealControls()}
            row.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(d(3),0,d(3),0)});return b
        }
        action("上一项") {previous()}
        pauseButton=action("暂停") {togglePause()}
        action("下一项") {if(finishedRound)reload();next()}
        action("媒体库 / 设置") {openDashboard("媒体库")}
        val buttons=Ui.focusableViews(row)
        buttons.forEachIndexed {i,b->
            b.nextFocusLeftId=buttons[(i-1).coerceAtLeast(0)].id
            b.nextFocusRightId=buttons[(i+1).coerceAtMost(buttons.lastIndex)].id
            b.nextFocusUpId=b.id;b.nextFocusDownId=b.id
        }
        stage.addView(controls,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply {setMargins(d(16),0,d(16),d(16))})
        controls.visibility=View.GONE
        image.isFocusable=false;video.isFocusable=false;video.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS;message.isFocusable=false
        image.setOnClickListener {revealControls()};video.setOnClickListener {revealControls()};message.setOnClickListener {if(finishedRound){reload();next()};revealControls()}
    }
    private fun openDashboard(tab:String) {startActivity(Intent(this,SettingsActivity::class.java).putExtra("tab",tab))}
    private fun revealControls() {
        controls.visibility=View.VISIBLE;handler.removeCallbacks(hideControls);handler.postDelayed(hideControls,6000)
    }
    private fun togglePause() {
        if(finishedRound){reload();next();return}
        paused=!paused;pauseButton.text=if(paused)"继续" else "暂停"
        player?.playWhenReady=!paused;handler.removeCallbacks(advance)
        stalledSince=SystemClock.elapsedRealtime()
        if(!paused&&image.visibility==View.VISIBLE)handler.postDelayed(advance,Config.int("imageSeconds",5)*1000L)
    }
    private fun previous() {
        if(queue.isEmpty()||transitioning)return
        index=if(index<=0)queue.size-2 else index-2;next()
    }
    private fun requestMediaPermissions() {
        Config.set("askedPermissions",true)
        val p=if(Build.VERSION.SDK_INT>=34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,Manifest.permission.POST_NOTIFICATIONS) else if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing=p.filter {checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
        if(missing.isNotEmpty())requestPermissions(missing.toTypedArray(),10)
    }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) {super.onRequestPermissionsResult(r,p,g);MediaLibrary.rescan()}
    override fun onStart() {
        super.onStart();active=true
        val delay=if(firstStart&&!intent.getBooleanExtra("bootDelayElapsed",false))Config.int("bootDelay",5)*1000L else 0L
        firstStart=false;playAfter=SystemClock.elapsedRealtime()+delay
        paused=false;pauseButton.text="暂停";controls.visibility=View.GONE
        emptyPanel.visibility=if(MediaLibrary.items.isEmpty())View.VISIBLE else View.GONE
        message.visibility=View.VISIBLE;message.text=if(MediaLibrary.items.isEmpty())emptyText() else "正在启动播放…"
        image.scaleType=if(Config.bool("crop",false))ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        player=ExoPlayer.Builder(this).build().also {p->
            video.player=p;video.resizeMode=if(Config.bool("crop",false))AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            p.volume=if(Config.bool("muted",true))0f else 1f
            p.addListener(object:Player.Listener {
                override fun onPlaybackStateChanged(state:Int) {if(state==Player.STATE_ENDED&&active) {
                    failureCount=0
                    if(Config.bool("repeatVideo",false)) {p.seekTo(0);p.play()} else handler.post(advance)
                }}
                override fun onPlayerError(error:PlaybackException) {Logs.write("视频播放失败: ${queue.getOrNull(index)?.name}: ${error.errorCodeName}");failed()}
            })
        }
        MediaLibrary.listen(changed);reload()
        val requested=Config.string("startItemId")
        if(requested.isNotEmpty()) {val target=queue.indexOfFirst {it.id==requested};if(target>=0) {index=target-1;playAfter=SystemClock.elapsedRealtime()};Config.set("startItemId","")}
        handler.postDelayed(advance,if(requested.isNotEmpty())0 else delay);handler.post(heartbeat)
        MediaLibrary.rescan()
    }
    override fun onStop() {
        active=false;generation++;image.animate().cancel();video.animate().cancel();transitioning=false;handler.removeCallbacksAndMessages(null);MediaLibrary.unlisten(changed)
        video.player=null;player?.release();player=null;Glide.with(this).clear(image);super.onStop()
    }
    private fun playlist()=MediaLibrary.ordered().let {if(Config.bool("random",false))it.shuffled() else it}
    private fun reload() {queue=playlist();index=-1;finishedRound=false}
    private fun emptyText():String {
        return "${UploadStatus.address}\n${MediaLibrary.status}\n轻触屏幕显示播放控制，菜单键进入媒体库"
    }
    private fun next() {
        if(!active||transitioning)return
        val wait=playAfter-SystemClock.elapsedRealtime()
        if(wait>0) {handler.removeCallbacks(advance);handler.postDelayed(advance,wait);return}
        val previous=if(video.visibility==View.VISIBLE)video else image
        if(previous.visibility==View.VISIBLE&&Config.bool("fade",true)) {
            transitioning=true
            previous.animate().cancel()
            previous.animate().alpha(0f).setDuration(250).withEndAction {transitioning=false;showNext()}.start()
        } else showNext()
    }
    private fun showNext() {
        if(!active)return
        handler.removeCallbacks(advance);generation++;val token=generation
        emptyPanel.visibility=if(queue.isEmpty())View.VISIBLE else View.GONE
        player?.stop();Glide.with(this).clear(image);image.visibility=View.GONE;video.visibility=View.GONE
        if(queue.isEmpty()) {message.visibility=View.VISIBLE;message.text=emptyText();return}
        index++
        if(index>=queue.size) {
            if(!Config.bool("loop",true)) {index=queue.lastIndex;finishedRound=true;message.visibility=View.VISIBLE;message.text="本轮播放已结束，轻触画面或按确认键重新播放";return}
            queue=playlist();index=0
            if(queue.isEmpty()) {message.visibility=View.VISIBLE;message.text=emptyText();return}
        }
        val item=queue[index];message.visibility=View.GONE
        track.text="${index+1} / ${queue.size}   ·   ${item.name}"
        if(item.type=="video") {
            video.visibility=View.VISIBLE;fade(video);lastPosition=-1;stalledSince=SystemClock.elapsedRealtime()
            player?.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri));player?.prepare();player?.playWhenReady=!paused
        } else {
            image.visibility=View.VISIBLE
            Glide.with(this).load(android.net.Uri.parse(item.uri)).override(stage.width.coerceAtLeast(1),stage.height.coerceAtLeast(1)).listener(object:RequestListener<Drawable> {
                override fun onLoadFailed(e:GlideException?,model:Any?,target:Target<Drawable>,first:Boolean):Boolean {
                    if(active&&generation==token) {Logs.write("图片解码失败: ${item.name}");handler.post {if(generation==token)failed()}};return false
                }
                override fun onResourceReady(resource:Drawable,model:Any,target:Target<Drawable>?,dataSource:DataSource,first:Boolean):Boolean {
                    if(active&&generation==token) {failureCount=0;fade(image);if(!paused)handler.postDelayed(advance,Config.int("imageSeconds",5)*1000L)};return false
                }
            }).into(image)
        }
    }
    private fun failed() {
        if(!active)return
        failureCount++;handler.removeCallbacks(advance)
        if(failureCount>=queue.size.coerceAtLeast(1)) {
            message.text="媒体暂时无法播放，30 秒后重试。菜单键可进入设置。";message.visibility=View.VISIBLE
            handler.postDelayed({if(active){failureCount=0;reload();next()}},30000)
        } else handler.postDelayed(advance,500)
    }
    private fun fade(v:View) {v.animate().cancel();v.alpha=if(Config.bool("fade",true))0f else 1f;if(v.alpha==0f)v.animate().alpha(1f).setDuration(350).start()}
    override fun onKeyDown(key:Int,e:KeyEvent):Boolean {
        if(key==KeyEvent.KEYCODE_BACK&&controls.visibility==View.VISIBLE) {controls.clearFocus();controls.visibility=View.GONE;return true}
        if((controls.hasFocus()||emptyPanel.hasFocus())&&key!=KeyEvent.KEYCODE_MENU&&key!=KeyEvent.KEYCODE_BACK)return super.onKeyDown(key,e)
        when(key) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {previous();revealControls();return true}
            KeyEvent.KEYCODE_DPAD_RIGHT -> {if(finishedRound)reload();next();revealControls();return true}
            KeyEvent.KEYCODE_DPAD_UP -> {revealControls();pauseButton.requestFocus();return true}
            KeyEvent.KEYCODE_MENU -> {if(e.repeatCount==0)handler.post(unlock);return true}
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER -> {if(e.repeatCount==0) {if(index>=queue.lastIndex&&!Config.bool("loop",true)) {reload();next()};handler.postDelayed(unlock,1500)};return true}
            KeyEvent.KEYCODE_BACK -> return true
        }
        return super.onKeyDown(key,e)
    }
    override fun onKeyUp(key:Int,e:KeyEvent):Boolean {if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) {handler.removeCallbacks(unlock);if(controls.hasFocus()||emptyPanel.hasFocus())return super.onKeyUp(key,e);return true};return super.onKeyUp(key,e)}
}
