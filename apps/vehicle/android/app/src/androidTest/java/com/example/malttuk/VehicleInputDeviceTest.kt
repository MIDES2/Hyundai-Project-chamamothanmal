package com.example.malttuk

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.malttuk.integration.FriendInboxActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device regression for keyboard/clipboard ANRs. Never submits credentials. */
@RunWith(AndroidJUnit4::class)
class VehicleInputDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(web: WebView, code: String): String {
        var result = "null"
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { web.evaluateJavascript(code) { result = it; done.countDown() } }
        assertTrue("WebView did not respond", done.await(8, TimeUnit.SECONDS))
        return result
    }
    @Test fun loginInputAndKeyboardRemainResponsive() {
        ActivityScenario.launch(FriendInboxActivity::class.java).use { scenario ->
            lateinit var web: WebView
            scenario.onActivity { activity ->
                web = findWeb(activity.window.decorView) ?: error("Missing vehicle WebView")
                assertFalse("Debug screen must avoid the stalled GPU renderer", activity.window.decorView.isHardwareAccelerated)
                val callback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = error("Clipboard menu must not run")
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
                    override fun onDestroyActionMode(mode: ActionMode) {}
                }
                assertNull(web.startActionMode(callback, ActionMode.TYPE_FLOATING))
            }
            val deadline = SystemClock.elapsedRealtime() + 30000
            var ready = false
            while (SystemClock.elapsedRealtime() < deadline) {
                ready = js(web, "Boolean(document.querySelector('.native-login input') && !document.querySelector('.native-login input').disabled)") == "true"
                if (ready) break
                Thread.sleep(250)
            }
            assertTrue("Login unavailable: check P gear, UX permission and HTML loading", ready)
            val point = JSONObject(js(web, "(()=>{const r=document.querySelector('.native-login input').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()"))
            scenario.onActivity {
                @Suppress("DEPRECATION")
                val scale = web.scale
                val x = (point.getDouble("x") * scale).toFloat()
                val y = (point.getDouble("y") * scale).toFloat()
                val now = SystemClock.uptimeMillis()
                web.requestFocus()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
                web.dispatchTouchEvent(down); web.dispatchTouchEvent(up)
                down.recycle(); up.recycle()
            }
            assertEquals("true", js(web, "document.activeElement===document.querySelector('.native-login input')"))
            // Leave the keyboard focused across the five-second ANR window.
            repeat(24) {
                Thread.sleep(500)
                val heartbeat = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post { heartbeat.countDown() }
                assertTrue("Input UI stopped responding", heartbeat.await(5, TimeUnit.SECONDS))
            }
            assertEquals("true", js(web, "(()=>{const e=document.querySelector('.native-login input');e.value='qa_input_probe';const ok=e.value==='qa_input_probe';e.value='';e.blur();return ok})()"))
        }
    }
}
