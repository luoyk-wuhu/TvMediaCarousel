package com.tvcarousel.settings

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.tvcarousel.boot.CarouselService
import com.tvcarousel.logs.Logs
import com.tvcarousel.media.MediaLibrary
import com.tvcarousel.orientation.RotatingActivity
import com.tvcarousel.upload.UploadStatus

class SettingsActivity : RotatingActivity() {
    private var unlocked=false
    private var homeScroll=0
    private var homeFocusIndex=-1
    private val handler=Handler(Looper.getMainLooper())
    private var deniedCount=0
    private var blockUntil=0L
    private var addressText:TextView?=null
    private var qr:ImageView?=null
    private var qrAddress=""
    private val update=object:Runnable {override fun run() {
        addressText?.text="${UploadStatus.address}\n${UploadStatus.detail}\n${MediaLibrary.status}"
        val address=UploadStatus.address
        if(address!=qrAddress) {qrAddress=address;qr?.setImageBitmap(if(address.startsWith("http"))makeQr(address) else null)}
        handler.postDelayed(this,5000)
    }}
    override fun onCreate(b:Bundle?) {super.onCreate(b);gate()}
    override fun onStart() {super.onStart();handler.post(update)}
    override fun onStop() {handler.removeCallbacksAndMessages(null);super.onStop()}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(text:String,size:Float=19f)=TextView(this).apply {this.text=text;textSize=size;setTextColor(Color.WHITE);setPadding(dp(8),dp(10),dp(8),dp(10))}
    private fun panel(title:String):LinearLayout {
        addressText=null;qr=null;qrAddress="";stage.removeAllViews();stage.setBackgroundColor(Color.rgb(16,24,32))
        val scroll=ScrollView(this);val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(28),dp(18),dp(28),dp(36))}
        scroll.addView(column);stage.addView(scroll,android.widget.FrameLayout.LayoutParams(-1,-1));column.addView(label(title,28f));return column
    }
    private fun button(parent:LinearLayout,text:String,click:()->Unit):Button {
        val b=Button(this).apply {this.text=text;textSize=18f;isAllCaps=false;setTextColor(Color.WHITE);minHeight=dp(54)
            background=StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),GradientDrawable().apply {setColor(Color.rgb(36,89,102));setStroke(dp(3),Color.rgb(82,211,188));cornerRadius=dp(6).toFloat()})
                addState(intArrayOf(),GradientDrawable().apply {setColor(Color.rgb(35,51,64));cornerRadius=dp(6).toFloat()})
            }
        }
        parent.addView(b,LinearLayout.LayoutParams(-1,-2).apply {setMargins(dp(4),dp(4),dp(4),dp(4))});b.setOnClickListener {click()};return b
    }
    private fun pad(title:String,secret:Boolean=true,initial:String="",cancel:()->Unit,done:(String,TextView)->Unit) {
        val p=panel(title);var value=initial
        val display=label(if(secret)"•".repeat(value.length) else value,26f);p.addView(display)
        val info=label("使用方向键和确认键输入数字");p.addView(info)
        fun render() {display.text=if(secret)"•".repeat(value.length) else value}
        var first:Button?=null
        listOf("123","456","789","0").forEach {row->val line=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL};p.addView(line)
            row.forEach {digit->val cell=LinearLayout(this);line.addView(cell,LinearLayout.LayoutParams(0,-2,1f));val b=button(cell,digit.toString()) {if(value.length<12){value+=digit;render()}};if(first==null)first=b}
        }
        button(p,"退格") {value=value.dropLast(1);render()};button(p,"清除") {value="";render()}
        button(p,"确认") {done(value,info)};button(p,"返回",cancel);first?.requestFocus()
    }
    private fun gate() {
        pad(if(Config.bool("settingsPinChanged",false))"输入设置 PIN" else "首次设置 PIN：${Config.string("initialPin")}（请登录后修改）",cancel={finish()}) {pin,info->
            if(System.currentTimeMillis()<blockUntil)info.text="尝试过多，请等待 30 秒"
            else if(Config.verify("settingsPin",pin)) {unlocked=true;showHome()}
            else {deniedCount++;if(deniedCount>=5){blockUntil=System.currentTimeMillis()+30000;deniedCount=0};info.text="PIN 错误"}
        }
    }
    private fun showHome() {
        val p=panel("电视媒体轮播 · 设置")
        val scroll=p.parent as ScrollView
        p.addView(label("菜单键 / 长按确认键进入；返回键回到播放。焦点带绿色边框。"))
        if(Build.VERSION.SDK_INT>=34)p.addView(label("Android 14 可仅授权选定媒体；权限变化后自动重扫。U 盘建议单独授权目录。"))
        addressText=label("");p.addView(addressText)
        qr=ImageView(this).apply {setBackgroundColor(Color.WHITE);setPadding(dp(6),dp(6),dp(6),dp(6))};p.addView(qr,LinearLayout.LayoutParams(dp(220),dp(220)))
        p.addView(label("上传 PIN：${Config.string("uploadDisplay",Config.string("initialPin"))}"))
        if(!Config.bool("settingsPinChanged",false))p.addView(label("首次设置 PIN：${Config.string("initialPin")}；请修改两个 PIN。"))
        fun toggle(key:String,title:String,default:Boolean) {
            lateinit var b:Button
            fun text()="$title：${if(Config.bool(key,default))"开" else "关"}"
            b=button(p,text()) {Config.set(key,!Config.bool(key,default));b.text=text();if(key=="uploadEnabled")CarouselService.start(this)}
        }
        fun choice(key:String,title:String,values:List<String>,labels:List<String>,default:String) {
            lateinit var b:Button
            fun text()="$title：${labels[values.indexOf(Config.string(key,default)).coerceAtLeast(0)]}"
            b=button(p,text()) {val n=(values.indexOf(Config.string(key,default))+1)%values.size;Config.set(key,values[n]);b.text=text()}
        }
        fun remember() {homeScroll=scroll.scrollY;homeFocusIndex=p.indexOfChild(currentFocus)}
        fun number(key:String,title:String,default:Int,min:Int,max:Int,after:()->Unit={}) {
            button(p,"$title：${Config.int(key,default)}") {remember();pad("$title（$min–$max）",false,Config.int(key,default).toString(),{showHome()}) {v,info->
                val n=v.toIntOrNull();if(n==null||n !in min..max)info.text="请输入 $min 到 $max 的数字" else {Config.set(key,n);after();showHome()}
            }}
        }
        lateinit var rotation:Button
        rotation=button(p,"显示方向：${Config.int("rotation",0)}°") {val n=(Config.int("rotation",0)+90)%360;Config.set("rotation",n);rotate();rotation.text="显示方向：$n°"}
        number("imageSeconds","图片停留秒数",5,3,60)
        toggle("random","随机播放",false)
        choice("sort","排序规则",listOf("name","modified","added"),listOf("自然文件名","修改时间（升序）","加入顺序"),"name")
        toggle("loop","整体循环",true);toggle("repeatVideo","单个视频循环（开启后停留在当前视频）",false)
        toggle("muted","静音",true);toggle("crop","居中裁剪（关闭为适应屏幕）",false);toggle("fade","淡入过渡",true)
        toggle("usbCopy","U 盘复制到本地（关闭为直接播放）",true)
        button(p,"授权 U 盘目录（Android 11+ 推荐）") {remember();runCatching {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),20)}.onFailure {notice("此设备未提供系统文件选择器，请尝试存储权限或其他盒子固件。")}}
        button(p,"申请 / 检查媒体读取权限") {
            val perms=if(Build.VERSION.SDK_INT>=34)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            val missing=perms.filter {checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
            if(missing.isNotEmpty())requestPermissions(missing.toTypedArray(),21) else notice("媒体权限已授予。U 盘仍可能需要单独授权目录。")
        }
        button(p,"重新扫描 U 盘 / 媒体库") {MediaLibrary.rescan()}
        button(p,"清空本地媒体") {remember();val v=panel("确定清空全部本地媒体？此操作不可撤销。");button(v,"取消") {showHome()}.requestFocus();button(v,"确认清空") {MediaLibrary.clear();showHome()}}
        toggle("uploadEnabled","局域网上传服务",true)
        number("port","上传端口",8080,1024,65535) {CarouselService.start(this)}
        button(p,"修改上传 PIN") {remember();pad("新上传 PIN（4–12 位）",cancel={showHome()}) {pin,info->if(pin.length !in 4..12)info.text="需要 4–12 位数字" else {Config.set("uploadPin",Config.hash(pin));Config.set("uploadDisplay",pin);showHome()}}}
        button(p,"修改设置 PIN") {remember();pad("新设置 PIN（4–12 位，请妥善保存）",cancel={showHome()}) {pin,info->if(pin.length !in 4..12)info.text="需要 4–12 位数字" else {Config.set("settingsPin",Config.hash(pin));Config.set("settingsPinChanged",true);showHome()}}}
        number("bootDelay","启动延迟秒数",5,0,120)
        toggle("autoBoot","接收开机自动启动",true)
        button(p,"启用默认桌面入口 / 打开桌面设置") {
            packageManager.setComponentEnabledSetting(ComponentName(this,"com.tvcarousel.HomeActivity"),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
            openSystem(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        button(p,"关闭默认桌面入口") {
            packageManager.setComponentEnabledSetting(ComponentName(this,"com.tvcarousel.HomeActivity"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);notice("默认桌面入口已关闭，请按 Home 选择系统桌面。")
        }
        button(p,"厂商自启动 / 后台白名单引导") {notice("在系统设置中找到：应用 → 电视媒体轮播 → 自启动 / 后台运行，允许自启动并取消后台限制。菜单因厂商而异。Android 10+ 建议启用默认桌面；Android Go 或部分固件可能完全禁止后台弹出界面。")}
        button(p,"打开应用系统设置") {openSystem(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}
        button(p,"电池优化设置") {openSystem(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}
        button(p,"查看日志") {remember();val v=panel("运行日志（最近 60 KB）");button(v,"返回") {showHome()}.requestFocus();v.addView(label(Logs.read(),14f))}
        button(p,"导出日志到 U 盘 / 文件") {runCatching {startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"tv-carousel-${System.currentTimeMillis()}.log"),22)}.onFailure {notice("设备不支持系统文件选择器。")}}
        button(p,"返回播放") {finish()}
        scroll.post {scroll.scrollTo(0,homeScroll)}
        (p.getChildAt(homeFocusIndex).takeIf {it is Button} ?: (0 until p.childCount).map {p.getChildAt(it)}.firstOrNull {it is Button})?.requestFocus()
        handler.removeCallbacks(update);handler.post(update)
    }
    private fun notice(text:String) {val v=panel("提示");v.addView(label(text));button(v,"返回设置") {showHome()}.requestFocus()}
    private fun openSystem(i:Intent) {runCatching {startActivity(i)}.onFailure {notice("此设备不提供该系统设置入口，请从电视系统设置手动操作。")}}
    private fun makeQr(s:String):Bitmap?=runCatching {
        val m=MultiFormatWriter().encode(s,BarcodeFormat.QR_CODE,400,400)
        Bitmap.createBitmap(400,400,Bitmap.Config.RGB_565).apply {val pixels=IntArray(160000) {i->if(m[i%400,i/400])Color.BLACK else Color.WHITE};setPixels(pixels,0,400,0,0,400,400)}
    }.getOrNull()
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(result!=Activity.RESULT_OK)return
        val uri=data?.data ?: return
        if(request==20) {
            runCatching {contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);MediaLibrary.rescan()}.onFailure {Logs.write("目录授权失败: $it");notice("无法保留该目录授权，请重新选择。")}
        }
        if(request==22) {MediaLibrary.worker.execute {
            val resultExport=runCatching {contentResolver.openOutputStream(uri,"wt")?.use {it.write(Logs.read().toByteArray())} ?: error("无法写入")}
            runOnUiThread {notice(if(resultExport.isSuccess)"日志已导出" else "日志导出失败，请检查 U 盘权限和空间")}
        }}
    }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray) {super.onRequestPermissionsResult(r,p,g);MediaLibrary.rescan()}
    @Deprecated("Framework API") override fun onBackPressed() {finish()}
}
