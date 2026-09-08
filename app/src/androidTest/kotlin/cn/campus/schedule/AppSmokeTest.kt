package cn.campus.schedule

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.*

class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun seed(): Schedule = runBlocking {
        Assume.assumeTrue("Optional private fixture is not distributed", InstrumentationRegistry.getInstrumentation().context.assets.list("")!!.contains("schedule.doc"))
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("schedule.doc").use { it.readBytes() }
        val schedule = WordScheduleParser().parse(bytes, "2026-09-07").schedule
        context.scheduleApp.store.update { AppData(schedule) }
        schedule
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun a_emptyStateAndSettings() {
        runBlocking { context.scheduleApp.store.update { AppData() } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("把课表带到桌面").fetchSemanticsNodes().isNotEmpty() }
        screenshot("empty.png")
        compose.onNodeWithText("导入课表").performClick()
        compose.onNodeWithText("从文件导入课表").assertIsDisplayed()
        compose.onNodeWithText("第 1 教学周的周一").assertExists()
        screenshot("settings.png")
    }
    @Test fun b_realFileParsesOnAndroidAndDisplays() {
        val schedule = seed()
        assertEquals(11, schedule.periods.size)
        assertEquals(17, schedule.lastWeek)
        val math = ScheduleEngine.occurrences(AppData(schedule)).first { it.title == "高等数学一（I）" }
        assertEquals(LocalTime.of(9,40), math.end)
        compose.waitUntil(15000) { compose.onAllNodesWithText("查看课程").fetchSemanticsNodes().isNotEmpty() }
        screenshot("today.png")
        compose.onNodeWithText("课表", useUnmergedTree=true).performClick()
        compose.onNodeWithText("第 1 周").assertIsDisplayed()
        compose.onAllNodesWithText("高等数学一（I）").onFirst().performClick()
        compose.onNodeWithText("保存本次修改").assertIsDisplayed()
        screenshot("lesson.png")
        compose.onNodeWithText("返回").performClick()
        screenshot("week.png")
    }
    @Test fun c_storeEditsSurviveReimportAndScheduleWithoutPermissions() = runBlocking {
        val schedule = seed()
        val first = ScheduleEngine.occurrences(AppData(schedule)).first()
        val edit = LessonEdit(first.ruleId, first.originalDate, location="测试教室")
        context.scheduleApp.store.update { it.copy(edits=listOf(edit)) }
        context.scheduleApp.store.update { ScheduleEngine.merge(it,schedule) }
        assertEquals(listOf(edit), context.scheduleApp.store.read().edits)
        ReminderScheduler.refresh(context)
        assertNotNull(context.scheduleApp.store.read().schedule)
    }
    @Test fun d_importPreviewRequiresConfirmation() {
        Assume.assumeTrue("Optional private fixture is not distributed", InstrumentationRegistry.getInstrumentation().context.assets.list("")!!.contains("schedule.doc"))
        runBlocking { context.scheduleApp.store.update { AppData() } }
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("schedule.doc").use { it.readBytes() }
        val file = File(context.cacheDir, "test-input.doc").apply { writeBytes(bytes) }
        compose.runOnIdle {
            val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
            vm.importUri(android.net.Uri.fromFile(file), "2026-09-07")
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("确认导入课表").fetchSemanticsNodes().isNotEmpty() }
        assertNull(runBlocking { context.scheduleApp.store.read().schedule })
        screenshot("import-preview.png")
        compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(15000) { runBlocking { context.scheduleApp.store.read().schedule != null } }
        assertEquals("2026-09-07", runBlocking { context.scheduleApp.store.read().schedule!!.firstMonday })
        file.delete()
    }
    @Test fun e_widgetsRenderInNativeHost() {
        seed()
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val host = android.appwidget.AppWidgetHost(context, 901)
        val ids = mutableListOf<Int>()
        lateinit var layout: android.widget.LinearLayout
        compose.runOnIdle {
            layout = android.widget.LinearLayout(compose.activity).apply {
                orientation=android.widget.LinearLayout.VERTICAL
                setPadding(30,60,30,30)
                setBackgroundColor(android.graphics.Color.rgb(232,239,233))
            }
            host.startListening()
            listOf(NextWidgetReceiver::class.java, TodayWidgetReceiver::class.java).forEach { receiver ->
                val id=host.allocateAppWidgetId();ids+=id
                val options=android.os.Bundle().apply {
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,300)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,200)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,200)
                }
                assertTrue("Grant widget bind permission to the test host before running",manager.bindAppWidgetIdIfAllowed(id,android.content.ComponentName(context,receiver),options))
                val view=host.createView(compose.activity,id,manager.getAppWidgetInfo(id))
                layout.addView(view,android.widget.LinearLayout.LayoutParams(-1,(210*context.resources.displayMetrics.density).toInt()))
            }
            compose.activity.setContentView(layout)
        }
        try {
            runBlocking { NextWidget().updateAllSafe(context);TodayWidget().updateAllSafe(context) }
            fun texts(view: android.view.View): String = when(view) {
                is android.widget.TextView -> view.text.toString()
                is android.view.ViewGroup -> (0 until view.childCount).joinToString(" "){texts(view.getChildAt(it))}
                else -> ""
            }
            var content=""
            val deadline=System.currentTimeMillis()+30000
            while(System.currentTimeMillis()<deadline) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {content=texts(layout)}
                if(content.contains("高等数学"))break
                Thread.sleep(500)
            }
            assertTrue("Widget should display the imported lesson: $content",content.contains("高等数学"))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(1000) // Wait for the host's next rendered frame, not just RemoteViews inflation.
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null),"widgets.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }finally{ids.forEach{host.deleteAppWidgetId(it)};host.stopListening()}
    }
    @Test fun f_scheduledNotificationIsDelivered() = runBlocking {
        seed()
        val original=context.scheduleApp.store.read()
        try {
            assertTrue(ReminderScheduler.notificationsAllowed(context))
            assertTrue(ReminderScheduler.exactAllowed(context))
            val start=ZonedDateTime.now(SCHOOL_ZONE).plusMinutes(5).plusSeconds(12).withNano(0)
            val monday=start.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val rule=LessonRule("test-"+System.currentTimeMillis(),"通知投递测试",start.dayOfWeek.value,1,1,listOf(Variant(listOf(1),listOf(Candidate("测试教师","测试教室")))))
            val schedule=Schedule("模拟器测试",monday.toString(),listOf(cn.campus.core.Period(1,start.toLocalTime().toString(),start.plusMinutes(40).toLocalTime().toString())),listOf(rule))
            context.scheduleApp.store.update {AppData(schedule,reminderMinutes=5)}
            ReminderScheduler.refresh(context)
            val manager=context.getSystemService(android.app.NotificationManager::class.java)
            val deadline=System.currentTimeMillis()+40000
            var found=false
            while(System.currentTimeMillis()<deadline){
                found=manager.activeNotifications.any {it.notification.extras.getString(android.app.Notification.EXTRA_TITLE)=="通知投递测试"}
                if(found)break
                kotlinx.coroutines.delay(500)
            }
            assertTrue("AlarmManager must deliver a real scheduled notification",found)
        }finally {context.scheduleApp.store.update{original};context.getSystemService(android.app.NotificationManager::class.java).cancelAll();ReminderScheduler.refresh(context)}
    }
}
