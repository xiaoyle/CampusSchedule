package cn.campus.schedule

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CompatibilityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name: String): ByteArray {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        Assume.assumeTrue(assets.list("")!!.contains(name))
        return assets.open(name).use {it.readBytes()}
    }
    private fun photo(name:String) {
        Thread.sleep(500)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun sourceFilesAndManifest() {
        for(name in listOf("schedule.doc","schedule.pdf")) {
            val result=ScheduleFileImporter.parse(context,fixture(name),"2026-09-07")
            assertEquals(29,result.schedule.rules.size);assertEquals(17,result.schedule.lastWeek)
            runBlocking {context.scheduleApp.store.update {AppData(result.schedule,alarmEnabled=false)}}
        }
        assertEquals(3,WidgetSupport.count(context))
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS)
        assertTrue(info.activities.orEmpty().any {it.name.endsWith("SchoolBrowserActivity") && !it.exported})
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertEquals("0.10.0",info.versionName)
    }
    @Test fun diagnosticsAndSettingsFallback() {
        val health=ReminderHealth.read(context,-1,false)
        assertFalse(health.enabled);assertFalse(health.alarmEnabled)
        assertEquals(Build.VERSION.SDK_INT>=31,health.exactRequired)
        val report=health.diagnostics("0.3.0")
        assertTrue(report.contains(Build.MODEL));assertTrue(report.contains("xiaoyle 制作"))
        assertFalse(report.contains("高等数学"));assertFalse(report.contains("学号："))
        val attempts=mutableListOf<String?>()
        assertTrue(SettingsNavigator.tryOpen(listOf(Intent("bad"),Intent("fallback"))) {attempts+=it.action;if(it.action=="bad") throw ActivityNotFoundException()})
        assertEquals(listOf("bad","fallback"),attempts)
        assertFalse(SettingsNavigator.tryOpen(listOf(Intent("bad"))) {throw SecurityException()})
        val providers=context.getSystemService(android.appwidget.AppWidgetManager::class.java).installedProviders.filter {it.provider.packageName==context.packageName}
        assertTrue(providers.all {it.initialLayout!=0 && it.previewImage!=0})
    }
    @Test fun settingsBrandSwitchAndSchoolLogin() {
        compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
        compose.onNodeWithText("从学校网页导入").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("提醒检查"))
        compose.onNodeWithText("提醒检查").assertIsDisplayed()
        photo("checks-api${Build.VERSION.SDK_INT}.png")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("手机后台设置"))
        compose.onNodeWithText("当前指引：",substring=true).performClick()
        compose.onNodeWithText("华为安卓").performClick()
        compose.onNodeWithText("当前指引：华为安卓 · 切换").assertExists()
        photo("guide-api${Build.VERSION.SDK_INT}.png")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("xiaoyle 制作 · 非学校官方应用 · 0.9.0"))
        compose.onNodeWithText("xiaoyle 制作 · 非学校官方应用 · 0.9.0").assertIsDisplayed()
    }
    @Test fun alarmAudioStartsAndStopsWithoutChangingSchedule() {
        Assume.assumeTrue(ReminderScheduler.notificationsAllowed(context))
        val before=runBlocking {context.scheduleApp.store.read()}
        compose.runOnIdle {assertTrue(AlarmPlaybackService.start(context,"闹铃设备验证",true))}
        compose.waitUntil(15000) {AlarmPlaybackService.isPlaying}
        val manager=context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.any {it.id==AlarmPlaybackService.NOTIFICATION})
        photo("alarm-api${Build.VERSION.SDK_INT}.png")
        compose.runOnIdle {ReminderReceiver().onReceive(context,Intent(AlarmPlaybackService.STOP))}
        compose.waitUntil(10000) {!AlarmPlaybackService.isPlaying}
        assertEquals(before,runBlocking {context.scheduleApp.store.read()})
    }
}
