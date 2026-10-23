package com.tvcarousel

import android.content.Intent
import android.content.ContentValues
import android.graphics.*
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.tvcarousel.media.MediaLibrary
import com.tvcarousel.media.MediaFolders
import com.tvcarousel.player.PlayerActivity
import com.tvcarousel.settings.Config
import com.tvcarousel.settings.SettingsActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File

/** Runs against the actual app in a disposable test device; screenshots use generated fixtures. */
class TouchFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    private val output=File(context.getExternalFilesDir(null),"test-screenshots").apply {mkdirs()}
    @Before fun prepare() {
        Config.set("askedPermissions",true);Config.set("bootDelay",0);Config.set("autoUsb",false)
        Config.set("fade",false);Config.set("rotation",0);Config.set("imageSeconds",3)
        Config.set("loop",true);Config.set("repeatVideo",false)
        instrumentation.startActivitySync(Intent(context,PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.waitForIdle()
    }
    @After fun cleanup() {
        device.takeScreenshot(File(output,"last-state.png"));device.dumpWindowHierarchy(File(output,"last-state.xml"))
        Config.set("rotation",0);Config.set("imageSeconds",5);Config.set("fade",true)
        MediaLibrary.worker.submit {MediaLibrary.items.filter {it.name.startsWith("__ui_test_")}.forEach {MediaLibrary.delete(it.id)}}.get()
    }
    private fun click(text:String) {
        if(!device.hasObject(By.text(text)))runCatching {UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text)}
        val node=device.wait(Until.findObject(By.text(text)),10000)
        assertNotNull("Missing touch target: $text",node);node.click();device.waitForIdle()
    }
    private fun screenshot(name:String) {device.waitForIdle();SystemClock.sleep(250);assertTrue(device.takeScreenshot(File(output,"$name.png")))}
    private fun dashboard(tab:String="媒体库") {
        instrumentation.startActivitySync(Intent(context,SettingsActivity::class.java).putExtra("tab",tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync();device.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.text("让每一块屏幕，都有好内容")),10000))
    }
    @Test fun dashboardAndSystemFolderPickerNeedNoPassword() {
        dashboard();assertTrue(device.hasObject(By.text("你的媒体空间")))
        assertFalse(device.hasObject(By.textContains("PIN")));screenshot("dashboard-empty")
        click("文件夹");click("＋  手动选择文件夹")
        assertTrue("System folder picker did not open",device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")),3000)||device.wait(Until.hasObject(By.pkg("com.android.documentsui")),3000))
        screenshot("system-folder-picker");device.dumpWindowHierarchy(File(output,"folder-picker.xml"))
        for(i in 0..5) {if(device.currentPackageName=="com.tvcarousel")break;device.pressBack();device.waitForIdle()}
        assertTrue(device.wait(Until.hasObject(By.text("播放来源")),5000))
    }
    @Test fun previewPlaybackPauseAndRotationWorkWithTouch() {
        MediaLibrary.worker.submit {
            for(i in 1..2) {
                val bitmap=Bitmap.createBitmap(1200,720,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(bitmap);canvas.drawColor(if(i==1)Color.rgb(31,76,89) else Color.rgb(62,48,89))
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(110,231,205);textSize=84f}
                canvas.drawText("TV MEDIA",80f,290f,paint);paint.color=Color.WHITE;paint.textSize=42f
                canvas.drawText("Touch preview / $i",84f,370f,paint)
                val temp=File(MediaLibrary.local,"ui-test-$i.part");temp.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                MediaLibrary.commitUpload(temp,"__ui_test_$i.png")
            }
        }.get()
        dashboard();UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("__ui_test_1.png")
        assertTrue(device.wait(Until.hasObject(By.text("__ui_test_1.png")),10000));screenshot("dashboard-media")
        click("__ui_test_1.png");click("▶  播放此媒体")
        assertTrue(device.wait(Until.gone(By.text("让每一块屏幕，都有好内容")),5000));device.waitForIdle()
        device.click(device.displayWidth/2,device.displayHeight/2);click("暂停")
        assertTrue(device.hasObject(By.text("继续")));screenshot("playback-paused")
        val selector=By.text(java.util.regex.Pattern.compile("[0-9]+ / [0-9]+   ·   __ui_test_.*"))
        val current=device.wait(Until.findObject(selector),5000)?.text
        assertNotNull("Current media indicator missing",current)
        SystemClock.sleep(3600)
        assertEquals("Paused image advanced",current,device.findObject(selector)?.text)
        click("下一项");assertTrue(device.wait(Until.hasObject(By.textContains("__ui_test_2.png")),5000))
        click("继续");click("媒体库 / 设置")
        click("播放设置");screenshot("playback-settings")
        click("显示方向：0°");assertTrue(device.wait(Until.hasObject(By.text("显示方向：90°")),5000));screenshot("rotated-settings")
        click("显示方向：90°");click("显示方向：180°");click("显示方向：270°")
        assertTrue(device.hasObject(By.text("显示方向：0°")))
        click("设备与上传");assertTrue(device.hasObject(By.text("手机上传")));screenshot("upload-settings")
        assertFalse(device.hasObject(By.textContains("PIN")))
    }
    @Test fun selectedFolderIsPersistedScannedAndRemoved() {
        if(android.os.Build.VERSION.SDK_INT<29)return
        val folder="TvCarouselUiTest"
        val filename="__ui_saf_test.png"
        val values=ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/$folder")
        }
        val source=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.CYAN)}
        context.contentResolver.openOutputStream(source)!!.use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val oldCopy=Config.bool("usbCopy",true);Config.set("usbCopy",false)
        try {
            dashboard("文件夹");click("＋  手动选择文件夹")
            if(!device.hasObject(By.text(folder)))click("Pictures")
            click(folder)
            click("使用此文件夹");click("允许")
            assertTrue(device.wait(Until.hasObject(By.text("播放来源")),5000))
            assertTrue(MediaFolders.selected(context).any {it.toString().contains(folder)})
            assertFalse("Explicit selection must disable whole-drive discovery",Config.bool("autoUsb",true))
            var found=false
            for(i in 0..50) {if(MediaLibrary.items.any {it.name==filename&&it.source=="usb"}){found=true;break};SystemClock.sleep(100)}
            assertTrue("Selected folder was not scanned in direct-play mode",found);screenshot("selected-folder")
            // Reopen the screen: the directory must be reconstructed from persisted read permissions.
            dashboard("文件夹");val title=device.wait(Until.findObject(By.text(folder)),5000)
            assertNotNull(title);title.parent.findObject(By.text("移除此文件夹")).click();click("确认")
            assertFalse(MediaFolders.selected(context).any {it.toString().contains(folder)})
            assertNotNull("Removing a folder must preserve its source file",context.contentResolver.openInputStream(source)?.use {it.read()})
        } finally {
            MediaFolders.selected(context).filter {it.toString().contains(folder)}.forEach {MediaFolders.remove(context,it)}
            context.contentResolver.delete(source,null,null);Config.set("usbCopy",oldCopy)
        }
    }
    @Test fun realLanServerAllowsPasswordlessUploadAndRejectsCrossSiteRequests() {
        Config.set("uploadEnabled",true);com.tvcarousel.boot.CarouselService.start(context)
        var address=""
        for(i in 0..100) {address=com.tvcarousel.upload.UploadStatus.address;if(address.startsWith("http://"))break;SystemClock.sleep(100)}
        assertTrue("LAN server did not become available",address.startsWith("http://"))
        fun request(path:String,method:String="GET",marker:Boolean=true,origin:String?=null,data:ByteArray?=null):Pair<Int,String> {
            val connection=java.net.URL(address+path).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout=5000;connection.readTimeout=5000;connection.requestMethod=method
                if(marker)connection.setRequestProperty("X-Requested-With","TVCarousel")
                if(origin!=null)connection.setRequestProperty("Origin",origin)
                if(data!=null){connection.doOutput=true;connection.setFixedLengthStreamingMode(data.size);connection.outputStream.use {it.write(data)}}
                val code=connection.responseCode
                return code to (if(code>=400)connection.errorStream else connection.inputStream).bufferedReader().use {it.readText()}
            }finally {connection.disconnect()}
        }
        assertEquals(200,request("/api/files").first)
        assertEquals(403,request("/api/files",marker=false).first)
        assertEquals(403,request("/api/refresh","POST",origin="https://example.invalid").first)
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
        val bytes=java.io.ByteArrayOutputStream().apply {bitmap.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();bitmap.recycle()
        assertEquals(200,request("/api/upload?name=__ui_test_http.png","POST",data=bytes).first)
        val payload=org.json.JSONObject(request("/api/files").second).getJSONArray("files")
        val item=(0 until payload.length()).map {payload.getJSONObject(it)}.first {it.getString("name")=="__ui_test_http.png"}
        assertEquals(200,request("/api/file?id="+item.getString("id"),"DELETE").first)
        assertFalse(MediaLibrary.items.any {it.id==item.getString("id")})
    }
}
