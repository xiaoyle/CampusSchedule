package cn.campus.schedule

import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.campus.core.PhoneGuide
import cn.campus.core.SCHOOL_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable fun ReminderChecks(context: Context, minutes: Int, alarmEnabled: Boolean, onAlarm:(Boolean)->Unit, tick: Int, now: Instant, onMessage: (String)->Unit, requestNotification: ()->Unit) {
    var testTick by remember { mutableIntStateOf(0) }
    val state=remember(tick,minutes,alarmEnabled,testTick,now) { ReminderHealth.read(context,minutes,alarmEnabled) }
    val pending=remember(tick,testTick,now) { TestReminder.pendingAt(context) }
    fun open(intent: Intent) = SettingsNavigator.open(context,intent,onMessage)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("提醒检查",style=MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("课前持续闹铃",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold)
            Switch(checked=alarmEnabled,onCheckedChange=onAlarm)
        }
        Text("开启后按系统闹钟音量响铃，点击停止或 1 分钟后结束。请检查闹钟音量与勿扰模式；未授权准时提醒或后台受限时，可能仅收到普通通知。",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={AlarmPlaybackService.stop(context);onMessage("已停止闹铃")}) {Text("停止闹铃")}
        OutlinedButton(onClick={
            onMessage(if(!ReminderScheduler.notificationsAllowed(context)) "请先开启通知" else if(AlarmPlaybackService.start(context,"课前闹铃试听",test=true)) "已启动试听，最多 1 分钟，可点击停止闹铃" else "无法启动闹铃，请检查系统设置")
        }) {Text("试听闹铃")}
        state.lines().forEach { (title,value) ->
            Text(title,fontWeight=FontWeight.Bold)
            Text(value,style=MaterialTheme.typography.bodyMedium)
            when(title) {
                "应用通知" -> TextButton(onClick=requestNotification) { Text("开启通知 / 通知设置") }
                "课前提醒通知渠道" -> TextButton(onClick={open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,ReminderScheduler.CHANNEL))}) { Text("检查课前提醒渠道") }
                "准时提醒" -> if(state.exactRequired) TextButton(onClick={open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}) { Text("设置准时提醒权限") }
                "电池优化" -> TextButton(onClick={open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}) { Text("检查电池优化") }
            }
        }
        Text("厂商自启动与后台活动：请手动检查。已授权不代表锁屏待机提醒一定准时。",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={
            onMessage(if(ReminderScheduler.notify(context,7,"课前提醒测试","测试通知已提交，请核对声音和锁屏显示。")) "测试通知已提交，请检查手机是否收到" else "请先开启应用通知和课前提醒通知渠道")
        },modifier=Modifier.fillMaxWidth()) { Text("发送测试通知") }
        OutlinedButton(onClick={
            runCatching { TestReminder.schedule(context) }.onSuccess { testTick++; onMessage("已安排 1 分钟后测试，请锁屏检查；未授权准时提醒时可能延迟") }
                .onFailure { onMessage(it.message ?: "测试安排失败，请检查系统设置后重试") }
        },modifier=Modifier.fillMaxWidth()) { Text("1 分钟后测试提醒") }
        if(pending>0) {
            val label=Instant.ofEpochMilli(pending).atZone(SCHOOL_ZONE).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            Text("测试目标时间 $label（北京时间）。再次安排会替换本次测试；重启后不恢复。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={TestReminder.cancel(context);testTick++;onMessage("已取消测试提醒")}) {Text("取消测试提醒")}
        }
        TextButton(onClick={
            val version=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "未知"
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("课表助手排查信息",ReminderHealth.read(context,minutes,alarmEnabled).diagnostics(version)))
            onMessage("已复制机型和提醒状态，不含课表或个人信息")
        }) {Text("复制排查信息")}
    }
}

@Composable fun PhoneGuidance(context: Context,onMessage: (String)->Unit) {
    var selected by rememberSaveable { mutableStateOf(PhoneGuide.detect(Build.MANUFACTURER,Build.BRAND).name) }
    var expanded by remember { mutableStateOf(false) }
    val guide=PhoneGuide.valueOf(selected)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("手机后台设置",style=MaterialTheme.typography.titleLarge)
        Box {
            OutlinedButton(onClick={expanded=true}) {Text("当前指引：${guide.label} · 切换")}
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                PhoneGuide.entries.forEach { option -> DropdownMenuItem(text={Text(option.label)},onClick={selected=option.name;expanded=false}) }
            }
        }
        Text(guide.instruction)
        Text("各系统版本菜单名称可能不同。还需检查通知声音、锁屏通知和勿扰模式；强行停止应用后，请重新打开。",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={SettingsNavigator.open(context,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")),onMessage)}) {Text("打开应用系统设置")}
    }
}
