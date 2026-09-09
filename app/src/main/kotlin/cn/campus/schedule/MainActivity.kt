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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.campus.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val personalizationVm: PersonalizationViewModel = viewModel()
            val personalization by personalizationVm.state.collectAsStateWithLifecycle()
            CampusTheme(personalization.themeMode) {
                CampusScreen(this,intent.getStringExtra("lessonKey"),intent.getStringExtra("taskId"),intent.getStringExtra("taskOccurrenceKey"),intent.getBooleanExtra("openFocus",false),intent.getStringExtra("focusCompletedId"),personalization,personalizationVm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CampusScreen(activity: MainActivity, openKey: String?, openTaskId:String?, openTaskOccurrenceKey:String?, openFocus:Boolean, focusCompletedId:String?, personalization: Personalization, personalizationVm: PersonalizationViewModel, vm: MainViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val personalMessage by personalizationVm.message.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(if(openTaskId==null)0 else 1) }
    var planTasks by rememberSaveable { mutableStateOf(openTaskId!=null) }
    var monday by rememberSaveable { mutableStateOf("2026-09-07") }
    var selected by remember { mutableStateOf<Occurrence?>(null) }
    var manualDialog by remember { mutableStateOf(false) }
    var editingManual by remember { mutableStateOf<ManualLesson?>(null) }
    var manualDate by remember { mutableStateOf(LocalDate.now(SCHOOL_ZONE)) }
    var taskDialog by remember { mutableStateOf(false) }
    var taskDraft by remember { mutableStateOf<StudyTask?>(null) }
    var taskOccurrence by remember { mutableStateOf<TaskOccurrence?>(null) }
    var taskExisting by remember { mutableStateOf(false) }
    var calendarDate by rememberSaveable { mutableStateOf(LocalDate.now(SCHOOL_ZONE).toString()) }
    var clearCompletedConfirm by remember { mutableStateOf(false) }
    var focusOpenState by rememberSaveable { mutableStateOf(openFocus) }
    var focusDialog by remember { mutableStateOf(false) }
    var focusTitle by remember { mutableStateOf("自由学习") }
    var focusCourse by remember { mutableStateOf<String?>(null) }
    var focusTask by remember { mutableStateOf<String?>(null) }
    var focusOccurrenceKey by remember { mutableStateOf<String?>(null) }
    var completedSession by remember { mutableStateOf<FocusSession?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++; vm.refreshReminders() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.importUri(it, monday) } }
    val schoolBrowser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringExtra(SchoolImportCache.TOKEN)?.let { vm.importSchool(it) }
    }
    val lessons = remember(data) { ScheduleEngine.occurrences(data, includeCancelled=true) }
    val today = now.atZone(SCHOOL_ZONE).toLocalDate()
    val active = lessons.filterNot { it.cancelled }
    val conflicts = remember(lessons) { ScheduleEngine.conflicts(lessons) }
    fun openLesson(lesson:Occurrence) {
        if(lesson.manual) {editingManual=data.manualLessons.firstOrNull{it.id==lesson.ruleId};manualDate=lesson.date;manualDialog=true}
        else selected=lesson
    }
    fun newTask(courseId:String?=null,courseTitle:String="",date:LocalDate?=null) {
        taskDraft=StudyTask(id=java.util.UUID.randomUUID().toString(),title="",courseRuleId=courseId,courseTitle=courseTitle,dueAt=date?.atTime(20,0)?.toString())
        taskOccurrence=null
        taskExisting=false
        taskDialog=true
    }
    fun openTask(item:TaskOccurrence) {taskDraft=data.studyTasks.firstOrNull{it.id==item.taskId}?:return;taskOccurrence=item;taskExisting=true;taskDialog=true}
    fun requestFocus(title:String,courseId:String?=null,taskId:String?=null,occurrenceKey:String?=null){focusTitle=title;focusCourse=courseId;focusTask=taskId;focusOccurrenceKey=occurrenceKey;focusDialog=true}
    fun toggleTaskSubtask(item:TaskOccurrence,id:String,complete:Boolean){
        vm.toggleSubtask(item,id,complete)
        if(complete&&item.subtasks.all{it.id==id||it.id in item.completedSubtaskIds})scope.launch{if(snackbar.showSnackbar("子任务已全部完成","完成主任务")==SnackbarResult.ActionPerformed)vm.completeTaskOccurrence(item,true)}
    }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(30_000) } }
    LaunchedEffect(data.schedule?.firstMonday) { data.schedule?.let { monday = it.firstMonday } }
    LaunchedEffect(openKey, loading) { if (!loading && openKey != null) lessons.firstOrNull {it.key==openKey}?.let(::openLesson) }
    LaunchedEffect(openTaskId,openTaskOccurrenceKey,loading) {if(!loading&&openTaskId!=null){tab=1;planTasks=true;val source=data.studyTasks.firstOrNull{it.id==openTaskId};source?.let{task->val item=openTaskOccurrenceKey?.let{StudyTaskEngine.occurrence(task,it)}?:StudyTaskEngine.listOccurrences(data,today).firstOrNull{it.taskId==openTaskId};item?.let(::openTask)}}}
    LaunchedEffect(data.focusSessions,focusCompletedId){val prefs=activity.getSharedPreferences("focus",0);val id=focusCompletedId?:prefs.getString("pendingCompletion",null);if(id!=null){data.focusSessions.firstOrNull{it.id==id}?.let{completedSession=it;prefs.edit().remove("pendingCompletion").apply()}}}
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(personalMessage) {
        personalMessage?.let {snackbar.showSnackbar(it);personalizationVm.message.value=null}
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { now = Instant.now(); permissionTick++; vm.refreshReminders();activity.scheduleApp.scope.launch{runCatching{FocusRuntime.refresh(activity)}} } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if(focusOpenState&&data.activeFocus!=null){val focusing=data.activeFocus!!;FocusTimerScreen(focusing,onBack={focusOpenState=false},onPause=vm::pauseFocus,onResume=vm::resumeFocus,onStop={vm.finishFocus(FocusStatus.STOPPED){if(focusing.mode!=FocusMode.BREAK)completedSession=it;focusOpenState=false}},onNaturalComplete={vm.finishFocus(FocusStatus.COMPLETED){if(focusing.mode!=FocusMode.BREAK)completedSession=it;focusOpenState=false}});return}
    Scaffold(snackbarHost={ SnackbarHost(snackbar) }, bottomBar={
        NavigationBar {
            listOf("今日" to Icons.Outlined.Today, "计划" to Icons.Outlined.CalendarMonth, "我的" to Icons.Outlined.Person).forEachIndexed { i, item ->
                NavigationBarItem(selected=if(i==2)tab>=2 else tab==i, onClick={ tab=i }, icon={ Icon(item.second, contentDescription=null) }, label={ Text(item.first) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else when(tab) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item {
                        TodayAssistantHeader(data,personalization.profile,activity,now.atZone(SCHOOL_ZONE),onOpenTasks={tab=1;planTasks=true},onOpenDate={activity.getSharedPreferences("schedule_view",Context.MODE_PRIVATE).edit().putBoolean("calendar",true).apply();calendarDate=it.toString();tab=1;planTasks=false})
                    }
                    item { FocusQuickStartCard(data,::requestFocus){focusOpenState=true} }
                    if (data.schedule == null && data.manualLessons.isEmpty()) item { EmptyCard("把课表带到桌面", "导入学校课表或添加一项单次课程后，就能在这里查看安排。", "导入课表") { tab=3 } }
                    else {
                        val next = active.firstOrNull { it.endInstant > now }
                        item {
                            Surface(color=MaterialTheme.colorScheme.primaryContainer, shape=RoundedCornerShape(24.dp), modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(if (next != null && next.startInstant <= now) "正在上课" else if(next!=null) "下一节课 · "+countdownText(next.startInstant,now) else "下一节课", fontWeight=FontWeight.Medium)
                                    if (next == null) Text(if(data.schedule==null) "暂无后续课程" else "本学期课程已结束", fontSize=22.sp, fontWeight=FontWeight.Bold)
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
                        items(dayItems, key={ it.key }) { lesson -> LessonCard(lesson, lesson.key in conflicts) { openLesson(lesson) } }
                        item { Text("课表保存在本机 · 调课后请重新导入或修改本次课程", style=MaterialTheme.typography.bodySmall) }
                    }
                }
                1 -> Column(Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp)) {
                        SegmentedButton(!planTasks,{planTasks=false},shape=SegmentedButtonDefaults.itemShape(0,2)){Text("日历")}
                        SegmentedButton(planTasks,{planTasks=true},shape=SegmentedButtonDefaults.itemShape(1,2)){Text("待办")}
                    }
                    Box(Modifier.weight(1f)) {
                        if(!planTasks) ScheduleHub(data,lessons,conflicts,today,LocalDate.parse(calendarDate),::openLesson,::openTask,{item,complete->vm.completeTaskOccurrence(item,complete)},::toggleTaskSubtask,onAddLesson={date->manualDate=date;editingManual=null;manualDialog=true},onAddTask={date->newTask(date=date)})
                        else StudyTaskScreen(data,now.atZone(SCHOOL_ZONE),onAdd={newTask(date=today)},onEdit=::openTask,onToggle={task,complete->vm.completeTaskOccurrence(task,complete)},onToggleSubtask=::toggleTaskSubtask,onClearCompleted={clearCompletedConfirm=true})
                    }
                }
                2 -> MyHub(activity,data,personalization.profile,onReview={tab=5},onAchievements={tab=6},onSettings={tab=3},onProfile={tab=4},onMessage={vm.message.value=it})
                3 -> LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item { Text("导入与设置", style=MaterialTheme.typography.headlineLarge, fontWeight=FontWeight.Bold) }
                    item { Text("学校课表", style=MaterialTheme.typography.titleLarge); Text(data.schedule?.term ?: "尚未导入课表") }
                    item { OutlinedTextField(value=monday, onValueChange={monday=it}, label={Text("第 1 教学周的周一")}, supportingText={Text("格式：2026-09-07，导入新学期时可修改")}, singleLine=true, modifier=Modifier.fillMaxWidth()) }
                    item {
                        Button(onClick={
                            if(runCatching {LocalDate.parse(monday).dayOfWeek == DayOfWeek.MONDAY}.getOrDefault(false)) {
                                schoolBrowser.launch(Intent(activity,SchoolBrowserActivity::class.java).putExtra(SchoolImportCache.MONDAY,monday))
                            } else vm.message.value="请填写第 1 教学周的周一，格式为 YYYY-MM-DD"
                        }, enabled=!busy, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Language,null); Spacer(Modifier.width(8.dp)); Text("从学校网页导入") }
                        Text("自行登录本科教务 → 课表查询 → 选择全部 → 点击底部导入。登录与二次验证兼容性待真机验证。", style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={filePicker.launch(arrayOf("*/*"))}, enabled=!busy, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile,null); Spacer(Modifier.width(8.dp)); Text("从文件导入课表") }
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
                        OutlinedButton(onClick={pinWidget(activity,StudyWidgetReceiver::class.java,vm)},modifier=Modifier.fillMaxWidth()) { Text("添加「学习看板」到桌面") }
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
                    item { Text("临时调课与隐私",fontWeight=FontWeight.Bold); Text("点击课程可修改或删除当天安排，也可以在课表页添加单次课程。节假日不自动停课。课程、待办与图片仅存本机。网页登录由学校处理，应用不读取账号、密码和验证码。",style=MaterialTheme.typography.bodySmall); Text("xiaoyle 制作 · 非学校官方应用 · "+appVersionName(activity),style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp)) }
                }
                4 -> ProfileScreen(activity,data,personalization,personalizationVm,vm::saveLearningGoal)
                5 -> FocusReviewScreen(data,onBack={tab=2},onGoal=vm::saveFocusGoal,onDelete=vm::deleteFocusSession)
                6 -> AchievementGallery(data,onBack={tab=2},onSave={vm.saveCustomAchievement(it)},onDelete={def,progress,featured->vm.deleteAchievement(def.id){scope.launch{if(snackbar.showSnackbar("已删除自定义成就","撤销")==SnackbarResult.ActionPerformed)vm.restoreAchievement(def,progress,featured)}}},onManual=vm::manualUnlockAchievement,onReset=vm::resetAchievement,onFeature={id->vm.featureAchievement(id,id !in data.featuredAchievementIds)},onMoveFeatured=vm::moveFeaturedAchievement)
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
    if(focusDialog) FocusStartDialog(data,focusTitle,focusCourse,focusTask,focusOccurrenceKey,onDismiss={focusDialog=false},onStart={title,mode,minutes,sound,volume,courseId,taskId,key->vm.startFocus(title,mode,minutes,sound,volume,courseId,taskId,key){focusDialog=false;focusOpenState=true}})
    completedSession?.let {session->FocusCompletionDialog(session,session.taskId!=null,onDismiss={completedSession=null},onCompleteTask={
        session.taskId?.let{id->data.studyTasks.firstOrNull{it.id==id}?.let{task->session.taskOccurrenceKey?.let{StudyTaskEngine.occurrence(task,it)}?:StudyTaskEngine.listOccurrences(data,today).firstOrNull{it.taskId==id}}?.let{vm.completeTaskOccurrence(it,true)}};completedSession=null
    },onContinue={completedSession=null;requestFocus(session.title,session.courseRuleId,session.taskId,session.taskOccurrenceKey)},onBreak={minutes->completedSession=null;vm.startFocus("休息",FocusMode.BREAK,minutes,AmbientSound.NONE,0f){focusOpenState=true}})}
    val celebration=data.achievementProgress.firstOrNull{it.unlockedAt!=null&&!it.celebrationSeen}?.achievementId?.let{id->AchievementEngine.definitions(data).firstOrNull{it.id==id}}
    celebration?.let{def->AchievementCelebration(def){vm.markCelebrationSeen(def.id)}}
    selected?.let { lesson -> LessonDialog(lesson,busy,onDismiss={if(!busy)selected=null},onSave={edit,color->vm.editWithColor(edit,color){selected=null}},onAddTask={selected=null;newTask(lesson.ruleId,lesson.title)},onDelete={
        vm.cancel(lesson) {selected=null;scope.launch {if(snackbar.showSnackbar("已删除 ${lesson.date} 的课程","撤销")==SnackbarResult.ActionPerformed) vm.restore(lesson)}}
    },onStartFocus={selected=null;requestFocus(lesson.title,lesson.ruleId)},onRestore={vm.restore(lesson){selected=null}}) }
    if(manualDialog) ManualLessonDialog(editingManual,manualDate,data.schedule?.periods.orEmpty(),lessons,onDismiss={if(!busy)manualDialog=false},onSave={lesson->vm.saveManual(lesson){manualDialog=false;editingManual=null}},onDelete={lesson->
        vm.deleteManual(lesson) {manualDialog=false;editingManual=null;scope.launch {if(snackbar.showSnackbar("已删除自建课程","撤销")==SnackbarResult.ActionPerformed) vm.saveManual(lesson)}}
    })
    if(taskDialog) taskDraft?.let { task->
        StudyTaskDialog(
            task=task,occurrence=taskOccurrence,existing=taskExisting,data=data,busy=busy,
            onDismiss={if(!busy){taskDialog=false;taskDraft=null;taskOccurrence=null}},
            onSave={updated,entire->if(taskExisting&&taskOccurrence!=null)vm.saveStudyTaskOccurrence(updated,taskOccurrence!!,entire){taskDialog=false;taskDraft=null;taskOccurrence=null}else vm.saveStudyTask(updated){taskDialog=false;taskDraft=null;taskOccurrence=null}},
            onDelete={entire->val item=taskOccurrence;if(item==null)vm.deleteStudyTask(task){taskDialog=false;taskDraft=null}else vm.deleteTaskOccurrence(item,entire){taskDialog=false;taskDraft=null;taskOccurrence=null;scope.launch{if(snackbar.showSnackbar(if(entire)"已删除任务系列" else "已删除本次任务","撤销")==SnackbarResult.ActionPerformed)vm.saveStudyTask(task)}}},
            onDuplicate={
                val item=taskOccurrence
                taskDraft=task.copy(id=java.util.UUID.randomUUID().toString(),title=item?.title?:task.title,type=item?.type?:task.type,courseRuleId=item?.courseRuleId?:task.courseRuleId,courseTitle=item?.courseTitle?:task.courseTitle,dueAt=item?.dueAt?.toLocalDateTime()?.toString()?:task.dueAt,priority=item?.priority?:task.priority,note=item?.note?:task.note,subtasks=item?.subtasks?:task.subtasks,completedAt=null,remindBeforeMinutes=null,repeatRule=null,instanceStates=emptyList(),createdAt=Instant.now().toString())
                taskOccurrence=null
                taskExisting=false
            },
            onStartFocus={val item=taskOccurrence;taskDialog=false;requestFocus(item?.title?:task.title,item?.courseRuleId?:task.courseRuleId,item?.taskId?:task.id,item?.key)}
        )
    }
    if(clearCompletedConfirm) AlertDialog(
        onDismissRequest={clearCompletedConfirm=false},
        title={Text("清除全部完成记录？")},
        text={Text("未完成任务不会受到影响。")},
        confirmButton={Button(onClick={vm.clearCompletedTasks{clearCompletedConfirm=false}}){Text("清除")}},
        dismissButton={TextButton(onClick={clearCompletedConfirm=false}){Text("取消")}}
    )
}

private fun countdownText(start:Instant,now:Instant):String {
    val minutes=Duration.between(now,start).toMinutes().coerceAtLeast(0)
    return when {
        minutes<1->"即将开始"
        minutes<60->"还有 "+minutes+" 分钟"
        minutes<24*60->"还有 "+(minutes/60)+" 小时 "+(minutes%60)+" 分钟"
        else->"还有 "+(minutes/(24*60))+" 天"
    }
}
fun appVersionName(context:Context):String=runCatching {context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull().orEmpty()
private fun dayName(day:Int) = listOf("星期一","星期二","星期三","星期四","星期五","星期六","星期日")[day-1]
private fun weekLabel(date:LocalDate,s:Schedule):String {val week=ScheduleEngine.week(date,s.firstMonday);return when {week<1->"尚未开学";week>s.lastWeek->"学期课程结束";else->"第 $week 周"}}
private fun openSettings(context:Context,intent:Intent) { runCatching{context.startActivity(intent)}.onFailure{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))} }
private fun pinWidget(context:Context,receiver:Class<*>,vm:MainViewModel) {
    vm.message.value=WidgetSupport.request(context,receiver)
}
@Composable private fun EmptyCard(title:String,body:String,action:String,onClick:()->Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,style=MaterialTheme.typography.titleLarge);Text(body);Button(onClick=onClick){Text(action)}}}
}
@Composable fun LessonCard(lesson:Occurrence,conflict:Boolean,onClick:()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(6.dp).height(108.dp).background(Color(lesson.color ?: 0xFF176B52),RoundedCornerShape(topStart=18.dp,bottomStart=18.dp)))
        Column(Modifier.padding(16.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("${lesson.start}–${lesson.end}" + if(lesson.cancelled) " · 已取消" else "",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=20.sp)
            Text(lesson.title,style=MaterialTheme.typography.titleMedium)
            Text(lesson.locationText,style=MaterialTheme.typography.bodyMedium)
            if(conflict) Text("与其他课程时间冲突",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)
            if(lesson.manual) Text("自建单次课程",style=MaterialTheme.typography.labelSmall)
            else if(lesson.modified) Text("已修改本次安排",style=MaterialTheme.typography.labelSmall)
        }
        }
    }
}
@Composable private fun LessonDialog(lesson:Occurrence,busy:Boolean,onDismiss:()->Unit,onSave:(LessonEdit,Long)->Unit,onAddTask:()->Unit,onDelete:()->Unit,onStartFocus:()->Unit,onRestore:()->Unit) {
    var date by remember(lesson.key) {mutableStateOf(lesson.date.toString())}
    var start by remember(lesson.key) {mutableStateOf(lesson.start.toString())}
    var end by remember(lesson.key) {mutableStateOf(lesson.end.toString())}
    var location by remember(lesson.key) {mutableStateOf(if(lesson.candidates.size==1) lesson.candidates[0].location else "")}
    var teacher by remember(lesson.key) {mutableStateOf(if(lesson.candidates.size==1) lesson.candidates[0].teacher else "")}
    var color by remember(lesson.key) {mutableLongStateOf(lesson.color ?: CourseColorOptions.first())}
    var error by remember {mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(lesson.title)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("仅修改本次课程 · 原日期 ${lesson.originalDate}",style=MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick=onAddTask,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("为这门课添加待办") }
            OutlinedButton(onClick=onStartFocus,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Timer,null);Spacer(Modifier.width(8.dp));Text("为这门课开始专注") }
            if(lesson.candidates.size>1) { Text("学校列出多个安排，请选择你的安排：")
                lesson.candidates.forEach {c-> OutlinedButton(onClick={location=c.location;teacher=c.teacher},modifier=Modifier.fillMaxWidth()){Text("${c.teacher}\n${c.location.ifBlank{"地点待确认"}}")}}
            }
            DatePickerField("上课日期",date,{date=it})
            TimePickerField("开始时间",start,{start=it})
            TimePickerField("结束时间",end,{end=it})
            OutlinedTextField(location,{location=it},label={Text("上课地点")})
            OutlinedTextField(teacher,{teacher=it},label={Text("任课教师")})
            Text("课程颜色",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {CourseColorOptions.forEach {value->Box(Modifier.size(34.dp).background(Color(value),CircleShape).clickable{color=value}.padding(5.dp),contentAlignment=Alignment.Center){if(color==value)Text("✓",color=Color.White)}}}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(!lesson.cancelled) TextButton(onClick=onDelete,enabled=!busy){Text("删除当天课程",color=MaterialTheme.colorScheme.error)}
            if(lesson.modified) TextButton(onClick=onRestore,enabled=!busy){Text("恢复学校原始安排")}
        }
    },confirmButton={Button(onClick={
        runCatching {
            val edit=LessonEdit(lesson.ruleId,lesson.originalDate,date=date.trim(),start=start.trim(),end=end.trim(),location=location.trim().takeIf{it.isNotEmpty()},teacher=teacher.trim().takeIf{it.isNotEmpty()})
            ScheduleEngine.validateEdit(edit); onSave(edit,color)
        }.onFailure {error="请检查日期和时间，结束时间须晚于开始时间"}
    },enabled=!busy){Text("保存本次修改")}},dismissButton={TextButton(onClick=onDismiss){Text("返回")}})
}
