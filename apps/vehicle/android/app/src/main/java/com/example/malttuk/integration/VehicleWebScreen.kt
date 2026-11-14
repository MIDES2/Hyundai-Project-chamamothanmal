package com.example.malttuk.integration

import android.annotation.SuppressLint
import android.content.Context
import android.view.ActionMode
import android.webkit.*
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** A local, network-isolated display. Backend tokens and audio never cross this interface. */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
class VehicleWebScreen(context:Context,private val command:(JSONObject)->Unit,private val ready:()->Unit) {
    val view=object : WebView(context) {
        // Pleos WebView can block the UI in ClipboardManager.hasPrimaryClip while
        // preparing its floating text menu. Keep typing, skip that native menu.
        override fun startActionMode(callback: ActionMode.Callback): ActionMode? = null
        override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = null
    }
    private var loaded=false
    private var destroyed=false
    private var lastState=""
    init {
        view.isSaveEnabled=false
        with(view.settings) {
            javaScriptEnabled=true;domStorageEnabled=false;databaseEnabled=false
            allowFileAccess=false;allowContentAccess=false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs=false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false)
            setGeolocationEnabled(false);mediaPlaybackRequiresUserGesture=true
            @Suppress("DEPRECATION")
            saveFormData=false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false)
        view.addJavascriptInterface(object {
            @androidx.annotation.Keep
            @JavascriptInterface fun send(body:String) {
                if(body.length>4096) return
                view.post {
                    if(destroyed || !loaded || view.url!=VehicleWebPolicy.HOME) return@post
                    runCatching { command(JSONObject(body)) }
                }
            }
        },"VehicleNative")
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
            override fun shouldInterceptRequest(web:WebView,request:WebResourceRequest):WebResourceResponse {
                val path=VehicleWebPolicy.assetPath(request.url.toString())
                if(path==null || request.method!="GET") return denied()
                return try {
                    val mime=when(path.substringAfterLast('.')) { "html"->"text/html";"js"->"application/javascript";"css"->"text/css";"svg"->"image/svg+xml";"woff2"->"font/woff2";else->"image/"+path.substringAfterLast('.') }
                    val headers=mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff",
                        "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; media-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
                    WebResourceResponse(mime,"UTF-8",200,"OK",headers,context.assets.open(path))
                } catch(e:Exception) { denied() }
            }
            override fun onPageFinished(web:WebView,url:String) { loaded=url==VehicleWebPolicy.HOME;if(loaded) { lastState="";ready() } }
        }
        view.loadUrl(VehicleWebPolicy.HOME)
    }
    private fun denied()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(byteArrayOf()))
    fun render(state:JSONObject) {
        if(!loaded || destroyed) return
        val encoded=state.toString()
        if(encoded==lastState) return
        lastState=encoded
        view.evaluateJavascript("window.onVehicleState && window.onVehicleState($encoded)",null)
    }
    fun resume() { if(!destroyed) view.onResume() }
    fun pause() { if(!destroyed) view.onPause() }
    fun destroy() { destroyed=true;view.removeJavascriptInterface("VehicleNative");view.stopLoading();view.destroy() }
}
