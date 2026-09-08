package cn.campus.schedule

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class ReminderHealth(
    val enabled: Boolean,
    val applicationNotifications: Boolean?,
    val channelNotifications: Boolean?,
    val exact: Boolean?,
    val batteryExempt: Boolean?,
    val alarmEnabled: Boolean = true,
    val exactRequired: Boolean = Build.VERSION.SDK_INT >= 31
) {
    fun lines(): List<Pair<String,String>> = listOf(
        "课程提醒" to if(enabled) "已开启" else "已关闭，请在上方选择提前时间",
        "持续闹铃" to if(alarmEnabled) "已开启，最长 1 分钟" else "已关闭，仅发送通知",
        "应用通知" to status(applicationNotifications, "未开启，无法显示提醒"),
        "课前提醒通知渠道" to status(channelNotifications, "未开启，请检查通知分类"),
        "准时提醒" to if(!exactRequired) "此系统无需授权" else status(exact,"需要设置，提醒可能延迟"),
        "电池优化" to when(batteryExempt) { true -> "系统未优化此应用"; false -> "正在优化，请检查后台限制"; null -> "无法读取，请手动检查" }
    )
    private fun status(value: Boolean?, no: String) = when(value) { true -> "已开启"; false -> no; null -> "无法读取，请手动检查" }
    fun diagnostics(version: String): String = buildString {
        appendLine("中大课表助手 $version · xiaoyle 制作")
        appendLine("品牌：${Build.MANUFACTURER} / ${Build.BRAND}")
        appendLine("型号：${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        this@ReminderHealth.lines().forEach { (title,state) -> appendLine("$title：$state") }
        append("厂商自启动与后台活动：请手动检查；以上状态不代表锁屏提醒已实测通过。")
    }
    companion object {
        fun read(context: Context, reminderMinutes: Int, alarmEnabled: Boolean = true) = ReminderHealth(
            reminderMinutes >= 0,
            runCatching { (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrNull(),
            runCatching { context.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderScheduler.CHANNEL)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } ?: false }.getOrNull(),
            runCatching { ReminderScheduler.exactAllowed(context) }.getOrNull(),
            runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrNull(),
            alarmEnabled
        )
    }
}

object SettingsNavigator {
    fun tryOpen(intents: List<Intent>, launch: (Intent) -> Unit): Boolean = intents.any { runCatching { launch(it); true }.getOrDefault(false) }
    fun open(context: Context, preferred: Intent, onFailure: (String) -> Unit) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))
        if (!tryOpen(listOf(preferred,details)) { context.startActivity(it) }) onFailure("无法打开系统设置。请手动打开手机设置，搜索“中大课表助手”并检查应用权限。")
    }
}

/** A separate one-shot alarm. Persist only the token and boot count, never course data. */
object TestReminder {
    const val ACTION = "cn.campus.schedule.TEST_REMIND"
    const val ID = 8
    private fun prefs(context: Context) = context.getSharedPreferences("test_reminder",0)
    private fun boot(context: Context) = Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
    private fun intent(context: Context, at: Long = 0) = PendingIntent.getBroadcast(context,80,
        Intent(context,ReminderReceiver::class.java).setAction(ACTION).putExtra("at",at),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun pendingAt(context: Context): Long {
        val p=prefs(context)
        return if (p.getInt("boot",-1)==boot(context)) p.getLong("at",0) else 0
    }
    @Synchronized fun schedule(context: Context): Long {
        require(ReminderScheduler.notificationsAllowed(context)) { "请先开启应用通知和课前提醒通知渠道" }
        val at=System.currentTimeMillis()+60_000
        val alarm=context.getSystemService(AlarmManager::class.java)
        val p=intent(context,at)
        alarm.cancel(p)
        prefs(context).edit().remove("at").apply()
        ReminderScheduler.scheduleAlarm(alarm,context,java.time.Instant.ofEpochMilli(at),p)
        prefs(context).edit().putLong("at",at).putInt("boot",boot(context)).apply()
        return at
    }
    @Synchronized fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        prefs(context).edit().remove("at").apply()
        context.getSystemService(NotificationManager::class.java).cancel(ID)
        if(AlarmPlaybackService.isTest) AlarmPlaybackService.stop(context)
    }
    @Synchronized fun receive(context: Context, expected: Long): Boolean {
        if (expected<=0 || pendingAt(context)!=expected) return false
        prefs(context).edit().remove("at").apply()
        return ReminderScheduler.notify(context,ID,"锁屏提醒测试","1 分钟测试已触发。请核对是否收到通知、声音和锁屏显示；长期待机仍需实测。")
    }
}
