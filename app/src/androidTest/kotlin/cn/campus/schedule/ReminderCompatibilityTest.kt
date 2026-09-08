package cn.campus.schedule

import android.app.*
import android.content.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ReminderCompatibilityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @After fun clean() { TestReminder.cancel(context);AlarmPlaybackService.stop(context) }
    @Test fun replacementCancellationAndScheduledRing() {
        Assume.assumeTrue(ReminderScheduler.notificationsAllowed(context) && ReminderScheduler.exactAllowed(context))
        runBlocking {context.scheduleApp.store.update {it.copy(alarmEnabled=true)}}
        val before=runBlocking {context.scheduleApp.store.read()}
        val a=TestReminder.schedule(context);Thread.sleep(5);val b=TestReminder.schedule(context)
        assertTrue(b>a);assertEquals(b,TestReminder.pendingAt(context))
        assertFalse(TestReminder.receive(context,a))
        TestReminder.cancel(context);assertEquals(0,TestReminder.pendingAt(context))
        assertFalse(TestReminder.receive(context,b))
        TestReminder.schedule(context)
        compose.waitUntil(90000) {AlarmPlaybackService.isPlaying}
        assertEquals(0,TestReminder.pendingAt(context))
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any {it.id==TestReminder.ID})
        assertEquals(before,runBlocking {context.scheduleApp.store.read()})
        compose.waitUntil(75000) {!AlarmPlaybackService.isPlaying}
    }
    @Test fun bootClearsTestAndReschedulesCourses() {
        Assume.assumeTrue(ReminderScheduler.notificationsAllowed(context))
        TestReminder.schedule(context)
        val before=runBlocking {context.scheduleApp.store.read()}
        RecoveryReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0,TestReminder.pendingAt(context))
        assertEquals(before,runBlocking {context.scheduleApp.store.read()})
    }
}
