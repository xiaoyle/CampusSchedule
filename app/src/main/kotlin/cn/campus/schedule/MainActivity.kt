package cn.campus.schedule

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.campus.core.*
import kotlinx.coroutines.delay
import java.time.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CampusTheme { CampusScreen(this, intent.getStringExtra("lessonKey")) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CampusScreen(activity: MainActivity, openKey: String?, vm: MainViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monday by rememberSaveable { mutableStateOf("2026-09-07") }
    var selected by remember { mutableStateOf<Occurrence?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++; vm.refreshReminders() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.importUri(it, monday) } }
    val lessons = remember(data) { ScheduleEngine.occurrences(data, includeCancelled=true) }
    val today = now.atZone(SCHOOL_ZONE).toLocalDate()
    val active = lessons.filterNot { it.cancelled }
    val conflicts = remember(lessons) { ScheduleEngine.conflicts(lessons) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(30_000) } }
    LaunchedEffect(data.schedule?.firstMonday) { data.schedule?.let { monday = it.firstMonday } }
    LaunchedEffect(openKey, loading) { if (!loading && openKey != null) selected = lessons.firstOrNull { it.key == openKey } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { now = Instant.now(); permissionTick++; vm.refreshReminders() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Scaffold(snackbarHost={ SnackbarHost(snackbar) }, bottomBar={
        NavigationBar {
            listOf("今日" to Icons.Outlined.Today, "课表" to Icons.Outlined.CalendarMonth, "导入与设置" to Icons.Outlined.Tune).forEachIndexed { i, item ->
                NavigationBarItem(selected=tab == i, onClick={ tab=i }, icon={ Icon(item.second, contentDescription=null) }, label={ Text(item.first) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else when(tab) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item {
                        Text("中大课表助手", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
                        Text("今天，去上课", fontSize=30.sp, fontWeight=FontWeight.Bold)
                        Text("${today.monthValue} 月 ${today.dayOfMonth} 日 · ${dayName(today.dayOfWeek.value)}" + (data.schedule?.let { " · ${weekLabel(today, it)}" } ?: ""))
                    }
                    if (data.schedule == null) item { EmptyCard("把课表带到桌面", "导入学校课表后，在这里查看课程，并添加桌面组件。", "导入课表") { tab=2 } }
                    else {
                        val next = active.firstOrNull { it.endInstant > now }
                        item {
                            Surface(color=MaterialTheme.colorScheme.primaryContainer, shape=RoundedCornerShape(24.dp), modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(if (next != null && next.startInstant <= now) "正在上课" else "下一节课", fontWeight=FontWeight.Medium)
                                    if (next == null) Text("本学期课程已结束", fontSize=22.sp, fontWeight=FontWeight.Bold)
                                    else {
                                        Text("${next.start}–${next.end}", fontSize=32.sp, fontWeight=FontWeight.Bold)
                                        if (next.date != today) Text("${next.date} · ${dayName(next.date.dayOfWeek.value)}")
                                        Text(next.title, fontSize=22.sp, fontWeight=FontWeight.Bold)
                                        Text(next.locationText)
                                        TextButton(onClick={ selected=next }) { Text("查看课程") }
                                    }
                                }
                            }
                        }
                        val dayItems = lessons.filter { it.date == today }
                        item { Text("今日安排 · ${dayItems.count { !it.cancelled }} 次课", style=MaterialTheme.typography.titleMedium) }
                        if (dayItems.isEmpty()) item { Text("今天没有课程，留一点时间给自己。", modifier=Modifier.padding(vertical=12.dp)) }
                        items(dayItems, key={ it.key }) { lesson -> LessonCard(lesson, lesson.key in conflicts) { selected=lesson } }
                        item { Text("课表保存在本机 · 调课后请重新导入或修改本次课程", style=MaterialTheme.typography.bodySmall) }
                    }
                }
                1 -> {
                    var week by rememberSaveable { mutableIntStateOf(data.schedule?.let { ScheduleEngine.week(today,it.firstMonday).coerceIn(1,it.lastWeek.coerceAtLeast(1)) } ?: 1) }
                    val schedule = data.schedule
                    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        item { Text("每周课表", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Bold) }
                        if (schedule == null) item { EmptyCard("还没有课表", "先导入教务系统导出的 Word 或 PDF 文件。", "导入课表") { tab=2 } }
                        else {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                                    IconButton(onClick={week--}, enabled=week>1) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                                    Text("第 $week 周", style=MaterialTheme.typography.titleLarge)
                                    IconButton(onClick={week++}, enabled=week<schedule.lastWeek) { Icon(Icons.Outlined.ChevronRight, "下一周") }
                                }
                            }
                            val start = LocalDate.parse(schedule.firstMonday).plusWeeks((week-1).toLong())
                            (0..6).forEach { offset ->
                                val date=start.plusDays(offset.toLong())
                                item { Text("${dayName(date.dayOfWeek.value)}  ${date.monthValue}/${date.dayOfMonth}", color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=10.dp)) }
                                val dayItems=lessons.filter {it.date==date}
                                if(dayItems.isEmpty()) item { Text("无课", style=MaterialTheme.typography.bodySmall) }
                                items(dayItems, key={ "week-${it.key}" }) { lesson -> LessonCard(lesson,lesson.key in conflicts) { selected=lesson } }
                            }
                        }
                    }
                }
                2 -> LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item { Text("导入与设置", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Bold) }
                    item { Text("学校课表", style=MaterialTheme.typography.titleLarge); Text(data.schedule?.term ?: "尚未导入课表") }
                    item { OutlinedTextField(value=monday, onValueChange={monday=it}, label={Text("第 1 教学周的周一")}, supportingText={Text("格式：2026-09-07，导入新学期时可修改")}, singleLine=true, modifier=Modifier.fillMaxWidth()) }
                    item {
                        Button(onClick={filePicker.launch(arrayOf("*/*"))}, enabled=!busy, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile,null); Spacer(Modifier.width(8.dp)); Text("从文件导入课表") }
                        Text("电脑导出 Word / PDF → 发送到手机 → 保存到“下载”文件夹 → 在这里选择。只解析课表，不保存姓名和学号。", style=MaterialTheme.typography.bodySmall)
                    }
                    item {
                        FilledTonalButton(onClick={activity.startActivity(Intent(activity,DiyActivity::class.java))},modifier=Modifier.fillMaxWidth()) { Text("桌面 DIY · 换上自己的照片") }
                        Text("照片卡 / 毛玻璃 / 课程便签 · 两个组件分别定制",style=MaterialTheme.typography.bodySmall)
                    }
                    item { HorizontalDivider(); Text("桌面组件", style=MaterialTheme.typography.titleLarge, modifier=Modifier.padding(top=16.dp)) }
                    item {
                        OutlinedButton(onClick={pinWidget(activity,NextWidgetReceiver::class.java,vm)},modifier=Modifier.fillMaxWidth()) { Text("添加「下一节课」到桌面") }
                        OutlinedButton(onClick={pinWidget(activity,TodayWidgetReceiver::class.java,vm)},modifier=Modifier.fillMaxWidth()) { Text("添加「今日课程」到桌面") }
                        WidgetHelp(activity, permissionTick) {vm.message.value=it}
                    }
                    item { HorizontalDivider(); Text("课前提醒",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=16.dp)) }
                    item {
                        Column {
                            listOf(5,10,15,30,-1).forEach { minutes ->
                                Row(Modifier.fillMaxWidth().clickable(enabled=!busy){vm.reminder(minutes)}.padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                                    RadioButton(selected=data.reminderMinutes==minutes,onClick={vm.reminder(minutes)},enabled=!busy)
                                    Text(if(minutes<0) "关闭提醒" else "提前 $minutes 分钟")
                                }
                            }
                        }
                    }
                    item { ReminderChecks(activity, data.reminderMinutes, data.alarmEnabled, onAlarm={vm.alarm(it)}, tick=permissionTick, now=now, onMessage={vm.message.value=it}, requestNotification={
                        if(Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else SettingsNavigator.open(activity,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)) { vm.message.value=it }
                    }) }
                    item { PhoneGuidance(activity) {vm.message.value=it} }
                    item { Text("临时调课与隐私",fontWeight=FontWeight.Bold); Text("点击课程可修改或取消本次安排。节假日不自动停课。数据仅存本机。学校登录入口暂时关闭，请使用电脑导出的 Word 或 PDF。",style=MaterialTheme.typography.bodySmall); Text("xiaoyle 制作 · 非学校官方应用 · 0.3.0",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp)) }
                }
            }
        }
    }
    preview?.let { result ->
        val diff=ScheduleEngine.diff(data,result.schedule)
        AlertDialog(onDismissRequest={if(!busy) vm.preview.value=null},title={Text("确认导入课表")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(result.schedule.term)
                Text("第 1 周：${result.schedule.firstMonday}\n共 ${result.schedule.rules.size} 项课程安排，覆盖 ${result.schedule.lastWeek} 周")
                Text("新增 ${diff.added} · 移除 ${diff.removed} · 更新 ${diff.changed}")
                if(data.schedule!=null) Text("确认后替换当前课表，能匹配的个人修改会保留。")
                if(diff.unmatched.isNotEmpty()) {
                    Text("以下 ${diff.unmatched.size} 项个人修改无法匹配，确认后移除：",color=MaterialTheme.colorScheme.error)
                    diff.unmatched.forEach { edit -> Text("${edit.originalDate} ${data.schedule?.rules?.find {it.id==edit.ruleId}?.title.orEmpty()}",style=MaterialTheme.typography.bodySmall) }
                }
                result.warnings.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
                HorizontalDivider()
                result.schedule.rules.forEach { rule -> Text("${dayName(rule.weekday)} ${rule.startPeriod}–${rule.endPeriod}节 · ${rule.title}\n周次 ${rule.variants.flatMap {it.weeks}.sorted().joinToString(",")}",style=MaterialTheme.typography.bodySmall) }
            }
        },confirmButton={Button(onClick={vm.confirmImport()},enabled=!busy){Text("确认保存")}},dismissButton={TextButton(onClick={vm.preview.value=null},enabled=!busy){Text("取消")}})
    }
    selected?.let { lesson -> LessonDialog(lesson,busy,onDismiss={if(!busy)selected=null},onSave={vm.edit(it){selected=null}},onRestore={vm.restore(lesson){selected=null}}) }
}

private fun dayName(day:Int) = listOf("星期一","星期二","星期三","星期四","星期五","星期六","星期日")[day-1]
private fun weekLabel(date:LocalDate,s:Schedule):String {val week=ScheduleEngine.week(date,s.firstMonday);return when {week<1->"尚未开学";week>s.lastWeek->"学期课程结束";else->"第 $week 周"}}
private fun openSettings(context:Context,intent:Intent) { runCatching{context.startActivity(intent)}.onFailure{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))} }
private fun pinWidget(context:Context,receiver:Class<*>,vm:MainViewModel) {
    vm.message.value=WidgetSupport.request(context,receiver)
}
@Composable private fun EmptyCard(title:String,body:String,action:String,onClick:()->Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,style=MaterialTheme.typography.titleLarge);Text(body);Button(onClick=onClick){Text(action)}}}
}
@Composable private fun LessonCard(lesson:Occurrence,conflict:Boolean,onClick:()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("${lesson.start}–${lesson.end}" + if(lesson.cancelled) " · 已取消" else "",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=20.sp)
            Text(lesson.title,style=MaterialTheme.typography.titleMedium)
            Text(lesson.locationText,style=MaterialTheme.typography.bodyMedium)
            if(conflict) Text("与其他课程时间冲突",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)
            if(lesson.modified) Text("已修改本次安排",style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun LessonDialog(lesson:Occurrence,busy:Boolean,onDismiss:()->Unit,onSave:(LessonEdit)->Unit,onRestore:()->Unit) {
    var date by remember(lesson.key) {mutableStateOf(lesson.date.toString())}
    var start by remember(lesson.key) {mutableStateOf(lesson.start.toString())}
    var end by remember(lesson.key) {mutableStateOf(lesson.end.toString())}
    var location by remember(lesson.key) {mutableStateOf(if(lesson.candidates.size==1) lesson.candidates[0].location else "")}
    var teacher by remember(lesson.key) {mutableStateOf(if(lesson.candidates.size==1) lesson.candidates[0].teacher else "")}
    var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(lesson.title)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("仅修改本次课程 · 原日期 ${lesson.originalDate}",style=MaterialTheme.typography.bodySmall)
            if(lesson.candidates.size>1) { Text("学校列出多个安排，请选择你的安排：")
                lesson.candidates.forEach {c-> OutlinedButton(onClick={location=c.location;teacher=c.teacher},modifier=Modifier.fillMaxWidth()){Text("${c.teacher}\n${c.location.ifBlank{"地点待确认"}}")}}
            }
            OutlinedTextField(date,{date=it},label={Text("上课日期 YYYY-MM-DD")},singleLine=true)
            OutlinedTextField(start,{start=it},label={Text("开始时间 HH:mm")},singleLine=true)
            OutlinedTextField(end,{end=it},label={Text("结束时间 HH:mm")},singleLine=true)
            OutlinedTextField(location,{location=it},label={Text("上课地点")})
            OutlinedTextField(teacher,{teacher=it},label={Text("任课教师")})
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(!lesson.cancelled) TextButton(onClick={onSave(LessonEdit(lesson.ruleId,lesson.originalDate,cancelled=true,date=lesson.date.toString(),start=lesson.start.toString(),end=lesson.end.toString()))},enabled=!busy){Text("取消本次课程",color=MaterialTheme.colorScheme.error)}
            if(lesson.modified) TextButton(onClick=onRestore,enabled=!busy){Text("恢复学校原始安排")}
        }
    },confirmButton={Button(onClick={
        runCatching {
            val edit=LessonEdit(lesson.ruleId,lesson.originalDate,date=date.trim(),start=start.trim(),end=end.trim(),location=location.trim().takeIf{it.isNotEmpty()},teacher=teacher.trim().takeIf{it.isNotEmpty()})
            ScheduleEngine.validateEdit(edit); onSave(edit)
        }.onFailure {error="请检查日期和时间，结束时间须晚于开始时间"}
    },enabled=!busy){Text("保存本次修改")}},dismissButton={TextButton(onClick=onDismiss){Text("返回")}})
}
