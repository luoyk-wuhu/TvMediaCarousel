package com.tvcarousel.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tvcarousel.settings.Config

object MediaFolders {
    fun selected(c:Context)=c.contentResolver.persistedUriPermissions.filter {it.isReadPermission}.map {it.uri}
    fun add(c:Context,uri:Uri,flags:Int) {
        require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {"未取得读取权限"}
        c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // An explicit folder selection should not also import the rest of the USB drive.
        Config.set("autoUsb",false)
        MediaLibrary.rescan()
    }
    fun remove(c:Context,uri:Uri) {
        val permission=c.contentResolver.persistedUriPermissions.firstOrNull {it.uri==uri} ?: return
        var flags=0
        if(permission.isReadPermission)flags=flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if(permission.isWritePermission)flags=flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        c.contentResolver.releasePersistableUriPermission(uri,flags)
        MediaLibrary.rescan()
    }
    fun name(c:Context,uri:Uri)=runCatching {DocumentFile.fromTreeUri(c,uri)?.name}.getOrNull() ?: uri.lastPathSegment ?: "媒体文件夹"
}
