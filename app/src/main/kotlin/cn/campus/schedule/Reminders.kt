package cn.campus.schedule

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import cn.campus.core.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    const val CHANNEL = "class_reminders"
    private val lock = Mutex()
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "课程开始前提醒上课时间与地点" })
    }
    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pending(context: Context, action: String, code: Int, at: Long = 0, keys: ArrayList<String> = arrayListOf()) =
        PendingIntent.getBroadcast(context, code, Intent(context, ReminderReceiver::class.java).setAction(action)
            .putExtra("at", at).putStringArrayListExtra("keys", keys), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun refresh(context: Context) = lock.withLock {
        val data = context.scheduleApp.store.read()
        val now = Instant.now()
        val items = ScheduleEngine.occurrences(data)
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context, "REMIND", 11))
        alarm.cancel(pending(context, "BOUNDARY", 12))
        // Remove stale notifications after imports/edits, without dismissing still-valid active lessons.
        val manager = context.getSystemService(NotificationManager::class.java)
        val valid = items.filter { it.endInstant > now }.map { it.key.hashCode() }.toSet()
        manager.activeNotifications.filter { it.id !in setOf(7,TestReminder.ID,AlarmPlaybackService.NOTIFICATION) && it.id !in valid }.forEach { manager.cancel(it.id) }
        if (data.reminderMinutes >= 0 && notificationsAllowed(context)) {
            val delivered = context.getSharedPreferences("alarms", 0).getStringSet("delivered", emptySet()).orEmpty()
            val upcoming = items.filter { it.key !in delivered && it.startInstant.minusSeconds(data.reminderMinutes * 60L) > now }
            val next = upcoming.minOfOrNull { it.startInstant.minusSeconds(data.reminderMinutes * 60L) }
            if (next != null) {
                val keys = ArrayList(upcoming.filter { it.startInstant.minusSeconds(data.reminderMinutes * 60L) == next }.map { it.key })
                scheduleAlarm(alarm, context, next, pending(context, "REMIND", 11, next.toEpochMilli(), keys))
            }
        }
        val midnight = now.atZone(SCHOOL_ZONE).toLocalDate().plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant()
        val boundary = items.flatMap { listOf(it.startInstant, it.endInstant) }.filter { it > now }.minOrNull()?.let { minOf(it, midnight) } ?: midnight
        scheduleAlarm(alarm, context, boundary, pending(context, "BOUNDARY", 12))
        TodayWidget().updateAllSafe(context)
        NextWidget().updateAllSafe(context)
    }
    internal fun scheduleAlarm(alarm: AlarmManager, context: Context, time: Instant, pending: PendingIntent) {
        try {
            if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toEpochMilli(), pending)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toEpochMilli(), pending)
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toEpochMilli(), pending) }
    }
    fun notify(context: Context, id: Int, title: String, message: String, key: String? = null): Boolean {
        if (!notificationsAllowed(context)) return false
        val click = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).putExtra("lessonKey", key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(click).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        return try { NotificationManagerCompat.from(context).notify(id, notification); true } catch (_: SecurityException) { false }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmPlaybackService.STOP) { AlarmPlaybackService.stop(context); return }
        val result = goAsync()
        context.scheduleApp.scope.launch {
            try {
                if (intent.action == TestReminder.ACTION) {
                    if(TestReminder.receive(context,intent.getLongExtra("at",0)) && context.scheduleApp.store.read().alarmEnabled)
                        AlarmPlaybackService.start(context,"课前闹铃测试",test=true)
                    return@launch
                }
                if (intent.action == "REMIND") {
                    val data = context.scheduleApp.store.read()
                    val keys = intent.getStringArrayListExtra("keys").orEmpty().toSet()
                    val expected = intent.getLongExtra("at", -1)
                    val now = Instant.now()
                    val prefs = context.getSharedPreferences("alarms", 0)
                    val delivered = prefs.getStringSet("delivered", emptySet()).orEmpty().toMutableSet()
                    val announced=mutableListOf<String>()
                    if (data.reminderMinutes >= 0) ScheduleEngine.occurrences(data).filter {
                        it.key in keys && it.key !in delivered && it.endInstant > now &&
                            it.startInstant.minusSeconds(data.reminderMinutes * 60L).toEpochMilli() == expected
                    }.forEach {
                        if (ReminderScheduler.notify(context, it.key.hashCode(), it.title, "${it.start}–${it.end} · ${it.locationText}", it.key)) { delivered += it.key; announced += it.title }
                    }
                    if(data.alarmEnabled && announced.isNotEmpty()) AlarmPlaybackService.start(context,announced.distinct().joinToString("、"))
                    prefs.edit().putStringSet("delivered", delivered.toList().takeLast(500).toSet()).apply()
                }
                ReminderScheduler.refresh(context)
            } finally { result?.finish() }
        }
    }
}
class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TestReminder.cancel(context)
        // Boot recovery must not depend on a deferred JobScheduler window.
        val pending = goAsync()
        context.scheduleApp.scope.launch {
            try { ReminderScheduler.refresh(context) }
            catch (_: Exception) { RecoveryWorker.once(context) }
            finally { pending?.finish() }
        }
    }
}
class RecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { ReminderScheduler.refresh(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
    companion object {
        fun install(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecoveryWorker>(30, TimeUnit.MINUTES).build()) }
        fun once(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecoveryWorker>().build()) }
    }
}
