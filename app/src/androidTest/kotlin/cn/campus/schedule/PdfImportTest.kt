package cn.campus.schedule

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.*

class PdfImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun bytes(): ByteArray {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        Assume.assumeTrue(assets.list("")!!.contains("schedule.pdf"))
        return assets.open("schedule.pdf").use { it.readBytes() }
    }
    @Test fun pdfBytesParseLocallyWithDatesAndCandidates() {
        val result=ScheduleFileImporter.parse(context,bytes(),"2026-09-07")
        val s=result.schedule
        assertEquals(11,s.periods.size)
        assertEquals(17,s.lastWeek)
        val all=ScheduleEngine.occurrences(AppData(s))
        val math=all.single { it.title=="高等数学一（I）" && it.date==LocalDate.parse("2026-09-07") }
        assertEquals(LocalTime.of(8,0),math.start)
        assertEquals(LocalTime.of(9,40),math.end)
        assertEquals(LocalTime.of(7,50),math.startInstant.minusSeconds(600).atZone(SCHOOL_ZONE).toLocalTime())
        assertEquals(3,all.single { it.title=="线性代数" && it.date==LocalDate.parse("2026-09-29") }.candidates.size)
        assertEquals(24,all.single { it.title.startsWith("新生研讨课") }.candidates.size)
        assertTrue(all.any { it.date.dayOfWeek==DayOfWeek.SUNDAY })
        assertTrue(all.any { it.date.dayOfWeek==DayOfWeek.SATURDAY })
        assertTrue(all.any { it.candidates.any { c -> c.location.isBlank() } })
    }
    @Test fun previewConfirmationReimportAndInvalidFilePreserveState() {
        val file=File(context.cacheDir,"课表.pdf").apply { writeBytes(bytes()) }
        runBlocking { context.scheduleApp.store.update { AppData() } }
        lateinit var vm: MainViewModel
        compose.runOnIdle { vm=ViewModelProvider(compose.activity)[MainViewModel::class.java]; vm.importUri(Uri.fromFile(file),"2026-09-07") }
        compose.waitUntil(30000) { !vm.busy.value }
        assertNull(vm.message.value)
        compose.onNodeWithText("确认导入课表").assertExists()
        assertNull(runBlocking { context.scheduleApp.store.read().schedule })
        Thread.sleep(500) // Let the native dialog entrance animation finish before visual capture.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bmp ->
            File(context.getExternalFilesDir(null),"pdf-preview.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
        compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(30000) { !vm.busy.value && vm.preview.value==null }
        val s=runBlocking { context.scheduleApp.store.read().schedule!! }
        val event=ScheduleEngine.occurrences(AppData(s)).first()
        val edit=LessonEdit(event.ruleId,event.originalDate,location="PDF 导入保留测试")
        runBlocking { context.scheduleApp.store.update { it.copy(edits=listOf(edit)) } }
        compose.runOnIdle { vm.importUri(Uri.fromFile(file),"2026-09-07") }
        compose.waitUntil(30000) { !vm.busy.value }
        assertEquals(ImportDiff(0,0,0,emptyList()),ScheduleEngine.diff(runBlocking { context.scheduleApp.store.read() },vm.preview.value!!.schedule))
        compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(30000) { !vm.busy.value && vm.preview.value==null }
        val before=runBlocking { context.scheduleApp.store.read() }
        assertEquals(listOf(edit),before.edits)
        file.writeText("%PDF-1.7 broken")
        compose.runOnIdle { vm.importUri(Uri.fromFile(file),"2026-09-07") }
        compose.waitUntil(30000) { !vm.busy.value }
        assertNotNull(vm.message.value)
        assertEquals(before,runBlocking { context.scheduleApp.store.read() })
        assertNull(vm.preview.value)
        file.delete()
    }
}
