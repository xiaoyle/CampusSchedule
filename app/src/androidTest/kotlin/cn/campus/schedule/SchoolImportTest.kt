package cn.campus.schedule

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real WebView engine with synthetic course markup; no school credentials or network fixture. */
class SchoolImportTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val route="https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint"
    private val html="""<html><body><h1>2026学年度第一学期示例同学课程表</h1><input name='password' type='password' value='private-sentinel'><div>学号：private-id</div><div class='ant-tabs-tab-active' role='tab' aria-selected='true'>全部</div><div id='table-bot'><table><thead><tr><th>节次名称</th>${listOf("一","二","三","四","五","六","日").joinToString(""){"<th>星期$it</th>"}}</tr></thead><tbody><tr><td>第1节08:00~08:45</td><td rowspan='2'><span>1-17每周/</span><span>本(专必)示例课程/</span><span style='display:none'>示例老师/</span><span>A101/</span><span>20人</span></td>${"<td></td>".repeat(6)}</tr><tr><td>第2节08:55~09:40</td>${"<td></td>".repeat(6)}</tr></tbody></table></div></body></html>"""
    private fun eval(view:WebView,script:String):JsonElement {
        val latch=CountDownLatch(1);var result="null"
        instrumentation.runOnMainSync {view.evaluateJavascript(script){result=it;latch.countDown()}}
        assertTrue("WebView callback timed out",latch.await(10,TimeUnit.SECONDS))
        return Json.parseToJsonElement(result)
    }
    private fun withPage(url:String=route, action:(WebView)->Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            lateinit var view:WebView;val loaded=CountDownLatch(1)
            scenario.onActivity {activity ->
                view=WebView(activity);view.settings.javaScriptEnabled=true
                view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView,u:String?){loaded.countDown()}}
                view.loadDataWithBaseURL(url,html,"text/html","UTF-8",null)
            }
            try {assertTrue(loaded.await(15,TimeUnit.SECONDS));action(view)} finally {instrumentation.runOnMainSync {view.destroy()}}
        }
    }
    private fun script(name:String)=context.assets.open(name).bufferedReader().use {it.readText()}
    @Test fun extractsOnlyCourseDataAndRejectsPartialWeeks() = withPage {view ->
        val raw=eval(view,script("school-table.js"))
        assertFalse(raw.toString().contains("private-sentinel"));assertFalse(raw.toString().contains("private-id"));assertFalse(raw.toString().contains("示例同学"))
        val parsed=WebScheduleParser().parse(dataJson.decodeFromJsonElement<WebTableSnapshot>(raw),"2026-09-07")
        assertEquals(17,parsed.schedule.lastWeek);assertEquals(2,parsed.schedule.rules.single().endPeriod)
        assertEquals("示例老师",parsed.schedule.rules.single().variants.single().candidates.single().teacher)
        eval(view,"document.querySelector('[role=tab]').textContent='第1周'")
        assertTrue(eval(view,script("school-table.js")).jsonObject.containsKey("error"))
    }
    @Test fun refusesLoginAndForeignOrigins() {
        for(url in listOf(SchoolUrlPolicy.LOGIN,"https://example.org/#/studentTimeTabPrint")) withPage(url) {view ->
            assertTrue(eval(view,script("school-table.js")).jsonObject.containsKey("error"))
            assertFalse(eval(view,script("school-export.js")).jsonPrimitive.boolean)
            assertEquals(JsonNull,eval(view,"window.__campusCapture || null"))
        }
    }
    @Test fun capturesBinaryExportAndRestoresHooks() = withPage {view ->
        eval(view,"window.beforeCreate=URL.createObjectURL;window.beforeSubmit=HTMLFormElement.prototype.submit;var b=document.createElement('button');b.textContent='导出课表';b.onclick=()=>URL.createObjectURL(new Blob([new Uint8Array([0,255,128,42])]));document.body.appendChild(b)")
        assertTrue(eval(view,script("school-export.js")).jsonPrimitive.boolean)
        var data:JsonElement=JsonNull
        for(i in 0..40) {data=eval(view,"window.__campusCapture.data");if(data!=JsonNull)break;Thread.sleep(50)}
        assertEquals("AP+AKg==",data.jsonPrimitive.content)
        assertTrue(eval(view,"window.__campusCapture.stop(); URL.createObjectURL===window.beforeCreate && HTMLFormElement.prototype.submit===window.beforeSubmit").jsonPrimitive.boolean)
    }
    @Test fun oneTimeCacheAndFailedImportPreserveData() {
        val before=runBlocking {context.scheduleApp.store.read()}
        val parsed=ImportResult(Schedule("2026学年度第一学期","2026-09-07",listOf(Period(1,"08:00","08:45")),listOf(LessonRule("sample","示例课程",1,1,1,listOf(Variant(listOf(1),listOf(Candidate("老师","教室"))))))),emptyList())
        val token=SchoolImportCache.write(context,parsed)
        assertEquals(parsed,SchoolImportCache.consume(context,token))
        assertTrue(runCatching {SchoolImportCache.consume(context,token)}.isFailure)
        assertTrue(runCatching {SchoolImportCache.consume(context,"../schedule.db")}.isFailure)
        val expired=SchoolImportCache.write(context,parsed)
        File(context.cacheDir,"school-imports/$expired.json").setLastModified(System.currentTimeMillis()-90_000_000)
        SchoolImportCache.cleanup(context)
        assertTrue(runCatching {SchoolImportCache.consume(context,expired)}.isFailure)
        assertEquals(before,runBlocking {context.scheduleApp.store.read()})
    }
}
