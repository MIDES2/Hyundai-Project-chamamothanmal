package com.example.malttuk.integration

import java.net.URI

/** Only packaged assets may run with the native interface. Never use a server-selected URL. */
object VehicleWebPolicy {
    const val HOME="https://appassets.androidplatform.net/assets/vehicle-ui/native-vehicle.html"
    fun assetPath(url:String):String? = runCatching {
        val uri=URI(url)
        if(uri.scheme!="https" || uri.host!="appassets.androidplatform.net" || uri.port!=-1 || uri.userInfo!=null || uri.rawQuery!=null || uri.rawFragment!=null) return null
        val path=uri.rawPath?:return null
        if(!path.startsWith("/assets/vehicle-ui/") || path.contains('%') || path.contains("..") || !path.matches(Regex("[A-Za-z0-9_./-]+"))) return null
        if(path.substringAfterLast('.') !in setOf("html","js","css","svg","png","webp","jpg","jpeg","woff2")) return null
        path.removePrefix("/assets/")
    }.getOrNull()
}
