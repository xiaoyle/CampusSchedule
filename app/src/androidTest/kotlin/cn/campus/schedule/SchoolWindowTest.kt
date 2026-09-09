package cn.campus.schedule

import android.content.Intent
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

class SchoolWindowTest {
    private fun find(v:View):WebView?=if(v is WebView)v else (v as? ViewGroup)?.let {g -> (0 until g.childCount).firstNotNullOfOrNull {find(g.getChildAt(it))}}
    @Test fun asynchronousWindowHasFullSettingsAndClosesToParent() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<SchoolBrowserActivity>(Intent(context,SchoolBrowserActivity::class.java).putExtra(SchoolImportCache.MONDAY,"2026-09-07")).use {scenario ->
            scenario.onActivity {activity ->
                val parent=find(activity.window.decorView)!!
                parent.stopLoading()
                parent.loadDataWithBaseURL("https://cas.sysu.edu.cn/cas/login","<p>Mock authentication</p>","text/html","UTF-8","https://cas.sysu.edu.cn/cas/login")
            }
            Thread.sleep(500)
            scenario.onActivity {activity ->
                val parent=find(activity.window.decorView)!!
                val transport=parent.WebViewTransport()
                val message=Message.obtain(android.os.Handler(android.os.Looper.getMainLooper()))
                message.obj=transport
                assertTrue(parent.webChromeClient!!.onCreateWindow(parent,false,false,message))
                val child=find(activity.window.decorView)!!
                assertNotSame(parent,child);assertSame(child,transport.webView)
                assertTrue(child.settings.javaScriptEnabled);assertTrue(child.settings.domStorageEnabled)
                assertEquals(parent.settings.userAgentString,child.settings.userAgentString)
                assertFalse(child.settings.allowFileAccess)
                child.webChromeClient!!.onCloseWindow(child)
                assertSame(parent,find(activity.window.decorView))
            }
        }
    }
}
