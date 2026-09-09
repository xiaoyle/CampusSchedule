package cn.campus.schedule

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SchoolFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun findWeb(view:View):WebView? = if(view is WebView)view else (view as? ViewGroup)?.let {g -> (0 until g.childCount).firstNotNullOfOrNull {findWeb(g.getChildAt(it))}}
    private fun openSchool():SchoolBrowserActivity {
        val monitor=instrumentation.addMonitor(SchoolBrowserActivity::class.java.name,null,false)
        compose.onNodeWithText("导入与设置",useUnmergedTree=true).performClick()
        compose.onNodeWithText("从学校网页导入").performClick()
        val activity=monitor.waitForActivityWithTimeout(10_000) as? SchoolBrowserActivity
        instrumentation.removeMonitor(monitor)
        return requireNotNull(activity)
    }
    @Test fun buttonToPreviewToSaveAndRepeatedClick() {
        val before=runBlocking {compose.activity.scheduleApp.store.read()}
        try {
            val school=openSchool()
            val html="""<h1>2026学年度第一学期</h1><div class='ant-tabs-tab-active'>全部</div><div id='table-bot'><table><tr><th>节次名称</th>${listOf("一","二","三","四","五","六","日").joinToString(""){"<th>星期$it</th>"}}</tr><tr><td>第1节08:00~08:45</td><td>1-17每周/示例课程/老师/教室</td>${"<td></td>".repeat(6)}</tr></table></div>"""
            instrumentation.runOnMainSync {findWeb(school.window.decorView)!!.apply {stopLoading();loadDataWithBaseURL("https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint",html,"text/html","UTF-8",null)}}
            compose.waitUntil(15_000) {runCatching {compose.onNodeWithText("导入当前课表").assertIsEnabled();true}.getOrDefault(false)}
            compose.onNodeWithText("导入当前课表").performClick()
            compose.onNodeWithText("导入当前课表").assertIsNotEnabled()
            compose.waitUntil(15_000) {compose.onAllNodesWithText("确认导入课表").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(before,runBlocking {compose.activity.scheduleApp.store.read()})
            compose.onNodeWithText("确认保存").performClick()
            compose.waitUntil(10_000) {runBlocking {compose.activity.scheduleApp.store.read().schedule?.rules?.singleOrNull()?.title=="示例课程"}}
            assertEquals(17,runBlocking {compose.activity.scheduleApp.store.read().schedule!!.lastWeek})
        } finally {runBlocking {compose.activity.scheduleApp.store.update {before}}}
    }
    @Test fun clearSessionPreservesCoursesAndRecreationIsSafe() {
        val before=runBlocking {compose.activity.scheduleApp.store.read()}
        val school=openSchool()
        instrumentation.runOnMainSync {CookieManager.getInstance().setCookie(SchoolUrlPolicy.LOGIN,"campus_test_marker=present; Path=/; Secure")}
        compose.waitUntil(5000) {CookieManager.getInstance().getCookie(SchoolUrlPolicy.LOGIN).orEmpty().contains("campus_test_marker")}
        compose.onNodeWithText("清除登录状态").performClick()
        compose.onNodeWithText("清除学校登录状态？").assertExists()
        compose.onAllNodesWithText("清除登录状态").onLast().performClick()
        compose.waitUntil(5000) {!CookieManager.getInstance().getCookie(SchoolUrlPolicy.LOGIN).orEmpty().contains("campus_test_marker")}
        assertEquals(before,runBlocking {compose.activity.scheduleApp.store.read()})
        instrumentation.runOnMainSync {school.recreate()}
        compose.waitUntil(10_000) {compose.onAllNodesWithText("学校网页导入").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(before,runBlocking {compose.activity.scheduleApp.store.read()})
    }
}
