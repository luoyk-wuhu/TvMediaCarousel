package com.tvcarousel.settings

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.tvcarousel.boot.CarouselService
import com.tvcarousel.logs.Logs
import com.tvcarousel.media.*
import com.tvcarousel.orientation.RotatingActivity
import com.tvcarousel.ui.TouchUi as Ui
import com.tvcarousel.upload.UploadStatus

class SettingsActivity : RotatingActivity() {
    private val handler=Handler(Looper.getMainLooper())
    private var tab="媒体库"
    private val tabs=listOf("媒体库","文件夹","播放设置","设备与上传")
    private var page=0
    private var statusText:TextView?=null
    private var stats:TextView?=null
    private var qr:ImageView?=null
    private var qrAddress=""
    private val previews=mutableListOf<ImageView>()
    private var grid:LinearLayout?=null
    private var gridSignature=""
    private var homeVisible=false
    private var homeFocusKey:Any?=null
    private var focusHint:TextView?=null
    private val changed:()->Unit={updateStatus();if(tab=="媒体库")renderMedia()}
    private val update=object:Runnable {override fun run() {updateStatus();handler.postDelayed(this,3000)}}
    override fun onCreate(b:Bundle?) {
        super.onCreate(b);tab=b?.getString("tab") ?: intent.getStringExtra("tab") ?: "媒体库";homeFocusKey=b?.getString("homeFocus")
        stage.viewTreeObserver.addOnGlobalFocusChangeListener {_,focused->
            val title=(focused as? TextView)?.text
            focusHint?.text=if(title.isNullOrBlank())"方向键移动  ·  确认键操作  ·  返回键返回" else "当前焦点：$title"
            if(focused!=null)Ui.reveal(focused)
        }
        showHome()
    }
    override fun onStart() {super.onStart();MediaLibrary.listen(changed);handler.post(update)}
    override fun onStop() {handler.removeCallbacksAndMessages(null);MediaLibrary.unlisten(changed);super.onStop()}
    override fun onDestroy() {clearPreviews();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle) {out.putString("tab",tab);out.putString("homeFocus",homeFocusKey as? String);super.onSaveInstanceState(out)}
    private fun dp(n:Int)=Ui.dp(this,n)
    private fun label(value:String,size:Float=16f,muted:Boolean=false)=Ui.text(this,value,size,if(muted)Ui.muted else Color.WHITE)
    private fun button(parent:LinearLayout,title:String,primary:Boolean=false,action:()->Unit):Button {
        lateinit var b:Button
        b=Ui.button(this,title,primary) {if(homeVisible)homeFocusKey=b.tag;action()}
        parent.addView(b);return b
    }
    private fun clearPreviews() {previews.forEach {Glide.with(this).clear(it)};previews.clear()}
    private fun panel(title:String):LinearLayout {
        homeVisible=false;clearPreviews();statusText=null;stats=null;qr=null;qrAddress="";grid=null;gridSignature=""
        stage.removeAllViews();stage.setBackgroundColor(Ui.background)
        val scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false;isFocusable=false;setPadding(0,0,0,dp(48))}
        val p=Ui.column(this).apply {setPadding(dp(24),dp(16),dp(24),dp(28))}
        scroll.addView(p);stage.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        focusHint=label("方向键移动  ·  确认键操作  ·  返回键返回",14f).apply {
            setTextColor(Ui.focus);setPadding(dp(24),dp(8),dp(24),dp(8))
            background=Ui.shape(this@SettingsActivity,Ui.surface,0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        }
        stage.addView(focusHint,FrameLayout.LayoutParams(-1,dp(44),Gravity.BOTTOM))
        p.addView(label("TV MEDIA  /  电视媒体轮播",13f,true));p.addView(label(title,30f))
        p.post {if(!homeVisible&&p.isAttachedToWindow&&currentFocus==null)Ui.focusableViews(p).firstOrNull()?.requestFocus()}
        return p
    }
    private fun card(parent:LinearLayout,title:String,description:String=""):LinearLayout {
        val c=Ui.card(this);parent.addView(c);c.addView(label(title,21f))
        if(description.isNotEmpty())c.addView(label(description,14f,true));return c
    }
    private fun showHome() {
        val p=panel("让每一块屏幕，都有好内容")
        val nav=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(8),0,dp(18))}
        p.addView(nav)
        val tabButtons=mutableListOf<Button>()
        tabs.forEach {name->val b=Ui.button(this,name,tab==name) {selectTab(name)}.apply {
            tag="tab:$name"
            setOnFocusChangeListener {view,hasFocus->
                if(hasFocus&&homeVisible&&tab!=name)view.post {
                    if(view.isAttachedToWindow&&view.hasFocus()&&homeVisible&&tab!=name)selectTab(name)
                }
            }
        }
            nav.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(3),0,dp(3),0)})
            tabButtons.add(b)
        }
        when(tab) {"文件夹"->folders(p);"播放设置"->playback(p);"设备与上传"->device(p);else->library(p)}
        button(p,"▶  返回全屏播放",true) {finish()}
        homeVisible=true
        tabButtons.forEachIndexed {i,b->b.nextFocusLeftId=tabButtons[(i-1).coerceAtLeast(0)].id;b.nextFocusRightId=tabButtons[(i+1).coerceAtMost(tabButtons.lastIndex)].id;b.nextFocusUpId=b.id}
        Ui.focusableViews(p).firstOrNull {!(it.tag as? String).orEmpty().startsWith("tab:")}?.let {first->
            tabButtons.forEach {it.nextFocusDownId=first.id}
            first.nextFocusUpId=tabButtons[tabs.indexOf(tab).coerceAtLeast(0)].id
        }
        val key=homeFocusKey ?: "tab:$tab"
        p.post {if(p.isAttachedToWindow) {
            val choices=Ui.focusableViews(p)
            (choices.firstOrNull {it.tag==key} ?: choices.firstOrNull {it.tag=="tab:$tab"} ?: choices.firstOrNull())?.requestFocus()
        }}
        updateStatus()
    }
    private fun selectTab(name:String) {
        if(tab==name)return
        tab=name;page=0;homeFocusKey="tab:$name";showHome()
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        val focusedTab=(currentFocus?.tag as? String)?.takeIf {it.startsWith("tab:")}
        if(homeVisible&&focusedTab!=null&&(event.keyCode==KeyEvent.KEYCODE_DPAD_LEFT||event.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if(event.action==KeyEvent.ACTION_DOWN) {
                val index=tabs.indexOf(focusedTab.removePrefix("tab:")).coerceAtLeast(0)
                val step=if(event.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)1 else -1
                selectTab(tabs[(index+step).coerceIn(0,tabs.lastIndex)])
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun library(p:LinearLayout) {
        val summary=card(p,"你的媒体空间","点选封面查看并播放；触屏与遥控器均可操作。")
        stats=label("",18f);summary.addView(stats)
        statusText=label("",14f,true);summary.addView(statusText)
        button(summary,"＋  选择媒体文件夹",true) {chooseFolder()}
        button(summary,"↻  刷新媒体库") {MediaLibrary.rescan()}
        grid=Ui.column(this);p.addView(grid);renderMedia()
    }
    private fun renderMedia() {
        val container=grid ?: return
        val items=MediaLibrary.ordered()
        page=page.coerceIn(0,((items.size-1).coerceAtLeast(0))/24)
        val signature=items.joinToString {"${it.id}:${it.modified}:${it.name}"}+":$page"
        if(signature==gridSignature)return
        val focused=currentFocus?.takeIf {container.hasFocus()}
        val focusKey=focused?.tag
        val focusIndex=Ui.focusableViews(container).indexOf(focused).coerceAtLeast(0)
        gridSignature=signature;clearPreviews();container.removeAllViews()
        if(items.isEmpty()) {
            card(container,"从一个文件夹开始","添加本机、U 盘或 SD 卡里的图片和视频。也可前往「设备与上传」，用手机扫码上传。内置目录为空时不会显示占位媒体。")
            if(focused!=null)stage.post {Ui.focusableViews(stage).firstOrNull()?.requestFocus()}
            return
        }
        container.addView(label("内容预览  ·  ${items.size} 项",22f))
        val logicalWidth=if(Config.int("rotation",0)%180==0)resources.displayMetrics.widthPixels else resources.displayMetrics.heightPixels
        val columns=if(logicalWidth/resources.displayMetrics.density>=840)4 else 2
        items.drop(page*24).take(24).chunked(columns).forEach {group->
            val row=LinearLayout(this);container.addView(row)
            group.forEach {item->
                val tile=Ui.card(this).apply {setPadding(dp(10),dp(10),dp(10),dp(10))}
                row.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),dp(4),dp(4),dp(4))})
                val cover=ImageView(this).apply {scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=item.name;background=Ui.shape(this@SettingsActivity,Ui.background,10)}
                tile.addView(cover,LinearLayout.LayoutParams(-1,dp(120)));previews.add(cover)
                cover.isFocusable=false
                cover.setOnClickListener {homeFocusKey="media:${item.id}";showItem(item)}
                Glide.with(this).load(Uri.parse(item.uri)).override(dp(300),dp(120)).centerCrop().into(cover)
                tile.addView(label(if(item.type=="video")"视频  ·  ${size(item.size)}" else "图片  ·  ${size(item.size)}",12f,true))
                button(tile,item.name) {showItem(item)}.apply {tag="media:${item.id}";maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}
            }
            repeat(columns-group.size) {row.addView(Space(this),LinearLayout.LayoutParams(0,1,1f))}
        }
        if(items.size>24) {
            val pager=card(container,"第 ${page+1} / ${(items.size+23)/24} 页")
            if(page>0)button(pager,"上一页") {page--;gridSignature="";renderMedia()}
            if((page+1)*24<items.size)button(pager,"下一页") {page++;gridSignature="";renderMedia()}
        }
        if(focused!=null)container.post {
            val choices=Ui.focusableViews(container)
            (choices.firstOrNull {it.tag==focusKey} ?: choices.getOrNull(focusIndex.coerceAtMost(choices.lastIndex)) ?: Ui.focusableViews(stage).firstOrNull())?.requestFocus()
        }
    }
    private fun showItem(item:MediaItem) {
        val p=panel(item.name);p.addView(label("${if(item.type=="video")"视频" else "图片"} · ${size(item.size)} · ${if(item.source=="local")"本地副本" else "文件夹直接播放"}",16f,true))
        button(p,"▶  播放此媒体",true) {Config.set("startItemId",item.id);finish()}
        if(item.source=="local")button(p,"删除本地副本") {confirm("删除「${item.name}」？","仅删除 App 内的副本，源文件保留。") {MediaLibrary.worker.execute {val ok=MediaLibrary.delete(item.id);runOnUiThread {if(ok)showHome() else notice("删除失败，请刷新后重试。")}}}}
        button(p,"返回媒体库") {showHome()}
    }
    private fun folders(p:LinearLayout) {
        val c=card(p,"播放来源","支持本机、U 盘和 SD 卡；可添加多个文件夹，包含子文件夹。授权会在重启后保留。")
        button(c,"＋  手动选择文件夹",true) {chooseFolder()}
        c.addView(label("选择文件夹后自动关闭整盘发现，只扫描所选目录。移除授权不会删除源文件；已复制到本地的媒体仍会保留。",14f,true))
        val selected=MediaFolders.selected(this)
        if(selected.isEmpty())c.addView(label("尚未选择文件夹",16f,true))
        selected.forEach {uri->
            val folder=card(p,MediaFolders.name(this,uri),Uri.decode(uri.toString()))
            button(folder,"移除此文件夹") {confirm("移除文件夹？","直接播放的条目将在重扫后移出；本地副本可在媒体库中单独删除。") {runCatching {MediaFolders.remove(this,uri)}.onSuccess {showHome()}.onFailure {notice("移除失败：${it.message}")}}}
        }
        val mode=card(p,"扫描与存储")
        toggle(mode,"autoUsb","自动发现整盘 U 盘",true) {MediaLibrary.rescan()}
        toggle(mode,"usbCopy","复制到本地（关闭则直接播放）",true) {MediaLibrary.rescan()}
        mode.addView(label("复制模式可在拔盘后播放，会占用本地空间；直接播放不会复制，需保持存储设备连接。切换后原有副本仍保留。",14f,true))
        button(mode,"立即重新扫描") {MediaLibrary.rescan();Toast.makeText(this,"已开始后台扫描",Toast.LENGTH_SHORT).show()}
        button(mode,"申请媒体读取权限") {requestMediaPermission()}
    }
    private fun toggle(p:LinearLayout,key:String,title:String,default:Boolean,after:()->Unit={}) {
        val row=Switch(this).apply {
            id=View.generateViewId();tag="toggle:$key";isFocusable=true
            text=title;textSize=16f;setTextColor(Color.WHITE);minHeight=dp(60)
            background=Ui.focusBackground(this@SettingsActivity,Ui.surface);setTextColor(Ui.focusText(Color.WHITE))
            setPadding(dp(8),dp(8),dp(8),dp(8));isChecked=Config.bool(key,default)
            setOnCheckedChangeListener {_,checked->Config.set(key,checked);after()}
        };p.addView(row,LinearLayout.LayoutParams(-1,-2))
    }
    private fun choice(p:LinearLayout,key:String,title:String,values:List<String>,names:List<String>,default:String) {
        val selected=values.indexOf(Config.string(key,default)).coerceAtLeast(0)
        button(p,"$title：${names[selected]}") {
            val v=panel(title)
            names.forEachIndexed {i,name->button(v,(if(i==selected)"✓  " else "")+name,i==selected) {Config.set(key,values[i]);showHome()}}
            button(v,"返回") {showHome()}
        }.tag="choice:$key"
    }
    private fun number(p:LinearLayout,key:String,title:String,default:Int,min:Int,max:Int,after:()->Unit={}) {
        button(p,"$title：${Config.int(key,default)}") {
            val v=panel(title);v.addView(label("输入 $min–$max 范围的数字",16f,true))
            val field=EditText(this).apply {inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText(Config.int(key,default).toString());textSize=24f;setTextColor(Ui.focusText(Color.WHITE));background=Ui.focusBackground(this@SettingsActivity,Ui.surface);minHeight=dp(60);selectAll()}
            v.addView(field)
            // TVs without a usable system keyboard can still enter every number with the remote.
            listOf("123","456","789","0").forEach {digits->
                val row=LinearLayout(this);v.addView(row)
                digits.forEach {digit->row.addView(Ui.button(this,digit.toString()) {
                    if(field.hasSelection())field.text.replace(field.selectionStart.coerceAtLeast(0),field.selectionEnd.coerceAtLeast(0),digit.toString())
                    else if(field.length()<5)field.append(digit.toString())
                },LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(3),dp(3),dp(3),0)})}
            }
            button(v,"退格") {field.setText(field.text.toString().dropLast(1));field.setSelection(field.length())}
            button(v,"保存",true) {val n=field.text.toString().toIntOrNull();if(n==null||n !in min..max)field.error="请输入 $min–$max" else {Config.set(key,n);after();showHome()}}
            button(v,"返回") {showHome()}
        }.tag="number:$key"
    }
    private fun playback(p:LinearLayout) {
        val display=card(p,"画面与声音")
        button(display,"显示方向：${Config.int("rotation",0)}°") {Config.set("rotation",(Config.int("rotation",0)+90)%360);rotate();showHome()}.tag="rotation"
        toggle(display,"crop","填满屏幕（居中裁剪）",false);toggle(display,"fade","淡入淡出",true);toggle(display,"muted","静音播放",true)
        val order=card(p,"轮播节奏")
        number(order,"imageSeconds","图片停留秒数",5,3,60)
        choice(order,"sort","内容排序",listOf("name","modified","added"),listOf("自然文件名","修改时间","加入顺序"),"name")
        toggle(order,"random","随机播放",false);toggle(order,"loop","整库循环",true);toggle(order,"repeatVideo","单个视频循环",false)
        order.addView(label("单个视频循环开启后，会持续播放当前视频。",14f,true))
    }
    private fun device(p:LinearLayout) {
        val upload=card(p,"手机上传","连接同一局域网，扫码或在浏览器打开下方地址即可上传，无需密码。")
        statusText=label("",17f);upload.addView(statusText)
        qr=ImageView(this).apply {contentDescription="上传地址二维码";setBackgroundColor(Color.WHITE);setPadding(dp(8),dp(8),dp(8),dp(8))};upload.addView(qr,LinearLayout.LayoutParams(dp(180),dp(180)))
        upload.addView(label("局域网内可访问此地址的设备均可上传和删除本地媒体。",14f,true))
        toggle(upload,"uploadEnabled","开启局域网上传",true) {CarouselService.start(this)}
        number(upload,"port","上传端口",8080,1024,65535) {CarouselService.start(this)}
        val boot=card(p,"开机与设备")
        toggle(boot,"autoBoot","开机自动启动",true);number(boot,"bootDelay","启动等待秒数",5,0,120)
        button(boot,"设为默认桌面") {packageManager.setComponentEnabledSetting(ComponentName(this,"com.tvcarousel.HomeActivity"),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);openSystem(Intent(Settings.ACTION_HOME_SETTINGS))}
        button(boot,"关闭默认桌面入口") {packageManager.setComponentEnabledSetting(ComponentName(this,"com.tvcarousel.HomeActivity"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);notice("已关闭，请按 Home 选择系统桌面。")}
        button(boot,"打开应用系统设置") {openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}
        button(boot,"电池优化设置") {openSystem(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}
        boot.addView(label("无人值守建议设为默认桌面，并在电视系统中允许自启动和后台运行。",14f,true))
        val maintenance=card(p,"维护")
        button(maintenance,"查看运行日志") {val v=panel("运行日志");button(v,"返回") {showHome()};v.addView(label(Logs.read(),13f))}
        button(maintenance,"导出日志") {runCatching {startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"tv-carousel.log"),22)}.onFailure {notice("设备不支持系统文件选择器。")}}
        button(maintenance,"清空本地媒体") {confirm("清空全部本地媒体？","此操作无法撤销。源文件夹内的原文件不会删除；复制模式下，下次扫描可能重新导入。") {MediaLibrary.clear();showHome()}}
    }
    private fun requestMediaPermission() {
        val perms=if(Build.VERSION.SDK_INT>=34)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        requestPermissions(perms,21)
    }
    private fun chooseFolder() {
        runCatching {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),20)}
            .onFailure {notice("此设备未提供系统文件夹选择器。可开启自动发现 U 盘，或使用手机上传。")}
    }
    private fun updateStatus() {
        stats?.text="${MediaLibrary.items.count {it.type=="image"}} 张图片   ·   ${MediaLibrary.items.count {it.type=="video"}} 个视频   ·   可用 ${size(MediaLibrary.local.usableSpace)}"
        statusText?.text="${UploadStatus.address}\n${UploadStatus.detail}  ·  ${MediaLibrary.status}"
        val address=UploadStatus.address
        if(qr!=null&&address!=qrAddress) {qrAddress=address;qr?.visibility=if(address.startsWith("http://"))View.VISIBLE else View.GONE;qr?.setImageBitmap(if(address.startsWith("http://"))makeQr(address) else null)}
    }
    private fun size(n:Long)=if(n>=1073741824)String.format(java.util.Locale.ROOT,"%.1f GB",n/1073741824.0) else String.format(java.util.Locale.ROOT,"%.1f MB",n/1048576.0)
    private fun confirm(title:String,detail:String,action:()->Unit) {val p=panel(title);p.addView(label(detail));button(p,"取消",true) {showHome()}.requestFocus();button(p,"确认",false,action)}
    private fun notice(message:String) {val p=panel("提示");p.addView(label(message));button(p,"返回") {showHome()}}
    private fun openSystem(i:Intent) {runCatching {startActivity(i)}.onFailure {notice("此设备不提供该设置入口，请从系统设置操作。")}}
    private fun makeQr(value:String):Bitmap?=runCatching {
        val m=MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,320,320)
        Bitmap.createBitmap(320,320,Bitmap.Config.RGB_565).apply {setPixels(IntArray(320*320) {if(m[it%320,it/320])Color.BLACK else Color.WHITE},0,320,0,0,320,320)}
    }.getOrNull()
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data);if(result!=Activity.RESULT_OK)return
        val uri=data?.data ?: return
        if(request==20)runCatching {MediaFolders.add(this,uri,data.flags);tab="文件夹";showHome()}.onFailure {Logs.write("目录授权失败: $it");notice("无法保留目录授权，请重新选择：${it.message}")}
        if(request==22)MediaLibrary.worker.execute {val resultExport=runCatching {contentResolver.openOutputStream(uri,"wt")?.use {it.write(Logs.read().toByteArray())} ?: error("无法写入")};runOnUiThread {notice(if(resultExport.isSuccess)"日志已导出" else "日志导出失败，请检查空间与权限")}}
    }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) {super.onRequestPermissionsResult(r,p,g);MediaLibrary.rescan()}
    @Deprecated("Framework API") override fun onBackPressed() {if(homeVisible)finish() else showHome()}
}
