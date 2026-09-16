package cn.campus.schedule

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.campus.core.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

private val taskTimeFormat=DateTimeFormatter.ofPattern("MM月dd日 HH:mm")
private val gapTimeFormat=DateTimeFormatter.ofPattern("HH:mm")

fun StudyTaskType.label()=when(this){StudyTaskType.HOMEWORK->"作业";StudyTaskType.EXAM->"考试";StudyTaskType.REVIEW->"复习";StudyTaskType.OTHER->"其他"}
fun StudyTaskPriority.label()=when(this){StudyTaskPriority.NORMAL->"普通";StudyTaskPriority.IMPORTANT->"重要";StudyTaskPriority.URGENT->"紧急"}
fun TaskRepeatKind.label()=when(this){TaskRepeatKind.DAILY->"每天";TaskRepeatKind.WEEKLY->"每周";TaskRepeatKind.CUSTOM_WEEKDAYS->"自选星期"}

fun taskAccent(task:TaskOccurrence,data:AppData):Long {
    val courseColor=task.courseRuleId?.let{id->data.courseColors[id]?:data.manualLessons.firstOrNull{it.id==id}?.color}
    return courseColor?:when(task.priority){StudyTaskPriority.URGENT->0xFFC43B43;StudyTaskPriority.IMPORTANT->0xFFCD7A18;StudyTaskPriority.NORMAL->0xFF176B52}
}

fun taskDueText(task:TaskOccurrence,now:ZonedDateTime):String {
    val due=task.dueAt?:return "无截止日期"
    val days=Duration.between(now.toLocalDate().atStartOfDay(SCHOOL_ZONE),due.toLocalDate().atStartOfDay(SCHOOL_ZONE)).toDays()
    return when{task.completedAt!=null->"已完成";due.isBefore(now)->"已逾期 · "+due.format(taskTimeFormat);task.type==StudyTaskType.EXAM&&days>0->"考试倒计时 $days 天 · "+due.format(taskTimeFormat);due.toLocalDate()==now.toLocalDate()->"今天 "+due.toLocalTime();else->due.format(taskTimeFormat)}
}

@Composable fun TodayAssistantHeader(snapshot:TodayDashboardSnapshot,profile:ProfileData,context:android.content.Context,onOpenTasks:()->Unit,onOpenDate:(LocalDate)->Unit){
    var now by remember { mutableStateOf(ZonedDateTime.now(SCHOOL_ZONE)) }
    LaunchedEffect(Unit){while(true){delay(60_000);now=ZonedDateTime.now(SCHOOL_ZONE)}}
    val today=snapshot.today
    val urgent=snapshot.priorityTask(now)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val avatar=remember(profile){AvatarRenderer.render(context,profile,112).asImageBitmap()};Image(avatar,"个人头像",Modifier.size(56.dp),contentScale=ContentScale.Fit)
            Column(Modifier.weight(1f)){Text(if(profile.nickname.isBlank())"你好，同学" else "你好，"+profile.nickname,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text(profile.greeting.ifBlank{"今天，去上课"},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("${today.monthValue} 月 ${today.dayOfMonth} 日 · "+weekdayName(today.dayOfWeek.value))}
        }
        val displayedWeek=snapshot.week
        if(displayedWeek!=null)Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(when{displayedWeek<1->"尚未开学";displayedWeek>snapshot.lastWeek->"学期课程已结束";else->"第 $displayedWeek 周 / ${snapshot.lastWeek} 周"},style=MaterialTheme.typography.labelMedium);Text((snapshot.progress*100).toInt().toString()+"%",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)};LinearProgressIndicator(progress={snapshot.progress},modifier=Modifier.fillMaxWidth())}
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){OverviewMetric(snapshot.todayLessons.size.toString(),"今日课程");OverviewMetric(snapshot.pendingTasks.size.toString(),"未完成");OverviewMetric("${snapshot.completedThisWeek}/${snapshot.weeklyTarget}","周目标")}}
        urgent?.let{task->OutlinedCard(onClick=onOpenTasks,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("下一步 · "+when{task.dueAt?.isBefore(now)==true->"先处理逾期";task.dueAt?.toLocalDate()==today->"今天截止";task.priority==StudyTaskPriority.URGENT->"紧急任务";else->task.type.label()},color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(task.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(taskDueText(task,now),style=MaterialTheme.typography.bodySmall,color=if(task.dueAt?.isBefore(now)==true)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}}}
        Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("未来 7 天",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){snapshot.futureLoads.forEachIndexed{index,load->Surface(color=if(index==0)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f).clickable{onOpenDate(load.date)}){Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(if(index==0)"今" else weekdayName(load.date.dayOfWeek.value).takeLast(1),style=MaterialTheme.typography.labelSmall);Text((load.taskCount+load.lessonCount).toString(),fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)}}}}}
    }
}

@Composable private fun OverviewMetric(value:String,label:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary);Text(label,style=MaterialTheme.typography.labelMedium)}}

@Composable fun GapRadarCard(snapshot:TodayDashboardSnapshot,activeFocus:ActiveFocusState?,onOpenTasks:()->Unit,onAddTask:()->Unit,onContinue:()->Unit,onStart:(TaskOccurrence,Int)->Unit) {
    var now by remember { mutableStateOf(ZonedDateTime.now(SCHOOL_ZONE)) }
    LaunchedEffect(Unit){while(true){delay(60_000);now=ZonedDateTime.now(SCHOOL_ZONE)}}
    val recommendation=remember(snapshot,now.toLocalTime().hour,now.toLocalTime().minute){GapRadarEngine.recommend(now,snapshot.todayLessons,snapshot.taskWindow)}
    var confirm by remember{mutableStateOf(false)}
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Radar,"空档雷达",tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text("空档雷达",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
            when {
                activeFocus!=null->{Text("已有专注正在进行",fontWeight=FontWeight.Bold);Text(activeFocus.title,style=MaterialTheme.typography.bodySmall);FilledTonalButton(onClick=onContinue,modifier=Modifier.fillMaxWidth()){Text("继续专注")}}
                recommendation==null->{Text("今天的可用时间已结束",fontWeight=FontWeight.Bold);Text("明天再来看看新的学习空档。",style=MaterialTheme.typography.bodySmall)}
                recommendation.task==null->{Text("${recommendation.gap.start.format(gapTimeFormat)}–${recommendation.gap.end.format(gapTimeFormat)} · 可用 ${recommendation.gap.minutes} 分钟",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text("暂时没有能放入这段空档的待办。",style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onOpenTasks){Text("查看待办")};Button(onClick=onAddTask){Text("添加待办")}}}
                else->{val task=requireNotNull(recommendation.task);Text("${recommendation.gap.start.format(gapTimeFormat)}–${recommendation.gap.end.format(gapTimeFormat)} · 可用 ${recommendation.gap.minutes} 分钟",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(task.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("建议专注 ${recommendation.suggestedMinutes} 分钟 · ${taskDueText(task,now)}",style=MaterialTheme.typography.bodySmall);Button(onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text("用这段空档开始专注")}}
            }
        }
    }
    if(confirm&&recommendation?.task!=null){val item=requireNotNull(recommendation.task);AlertDialog(onDismissRequest={confirm=false},title={Text("开始空档专注？")},text={Text("${item.title}\n${recommendation.gap.start.format(gapTimeFormat)}–${recommendation.gap.end.format(gapTimeFormat)}，计划专注 ${recommendation.suggestedMinutes} 分钟。\n\n专注结束后再由你决定是否完成任务。")},confirmButton={Button(onClick={confirm=false;onStart(item,recommendation.suggestedMinutes)}){Text("开始专注")}},dismissButton={TextButton(onClick={confirm=false}){Text("暂不开始")}})}
}

@Composable fun StudyTaskScreen(data:AppData,onAdd:()->Unit,onEdit:(TaskOccurrence)->Unit,onToggle:(TaskOccurrence,Boolean)->Unit,onToggleSubtask:(TaskOccurrence,String,Boolean)->Unit,onClearCompleted:()->Unit){
    var now by remember { mutableStateOf(ZonedDateTime.now(SCHOOL_ZONE)) }
    LaunchedEffect(Unit){while(true){delay(60_000);now=ZonedDateTime.now(SCHOOL_ZONE)}}
    var status by rememberSaveable{mutableStateOf("all")};var query by rememberSaveable{mutableStateOf("")};var typeFilter by rememberSaveable{mutableStateOf<StudyTaskType?>(null)};var priorityFilter by rememberSaveable{mutableStateOf<StudyTaskPriority?>(null)};var courseFilter by rememberSaveable{mutableStateOf<String?>(null)};var courseMenu by remember{mutableStateOf(false)};var filtersExpanded by rememberSaveable{mutableStateOf(false)};var completedExpanded by rememberSaveable{mutableStateOf(false)}
    val today=now.toLocalDate();val weekEnd=today.plusDays((7-today.dayOfWeek.value).toLong());val all=remember(data,today){StudyTaskEngine.occurrences(data,today.minusDays(30),today.plusDays(60))}
    val visible=all.filter{task->(query.isBlank()||listOf(task.title,task.courseTitle,task.note).any{it.contains(query,true)})&&(typeFilter==null||task.type==typeFilter)&&(priorityFilter==null||task.priority==priorityFilter)&&(courseFilter==null||task.courseRuleId==courseFilter)&&when(status){"today"->task.completedAt==null&&task.dueAt?.toLocalDate()==today;"week"->task.completedAt==null&&task.dueAt?.toLocalDate()?.let{!it.isBefore(today)&&!it.isAfter(weekEnd)}==true;"done"->task.completedAt!=null;else->true}}
    val incomplete=visible.filter{it.completedAt==null};val completed=visible.filter{it.completedAt!=null};val groups=listOf("逾期" to incomplete.filter{it.dueAt?.isBefore(now)==true},"今天" to incomplete.filter{it.dueAt?.toLocalDate()==today&&it.dueAt?.isBefore(now)!=true},"本周" to incomplete.filter{it.dueAt?.toLocalDate()?.let{d->d.isAfter(today)&&!d.isAfter(weekEnd)}==true},"以后" to incomplete.filter{it.dueAt?.toLocalDate()?.isAfter(weekEnd)==true},"无截止日期" to incomplete.filter{it.dueAt==null})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("课业待办",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text("把每一项任务变成清晰的一步",color=MaterialTheme.colorScheme.primary)};FilledTonalButton(onClick=onAdd){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(5.dp));Text("添加")}}}
        item{OutlinedTextField(query,{query=it},label={Text("搜索任务、课程或备注")},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotBlank())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"清空搜索")}},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("all" to "全部","today" to "今天","week" to "本周","done" to "已完成").forEach{(key,label)->FilterChip(selected=status==key,onClick={status=key},label={Text(label)})}}}
        item{TextButton(onClick={filtersExpanded=!filtersExpanded}){Icon(Icons.Outlined.FilterList,null);Spacer(Modifier.width(5.dp));Text(if(filtersExpanded)"收起筛选" else "更多筛选")};if(filtersExpanded)Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("类型",style=MaterialTheme.typography.labelMedium);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){StudyTaskType.entries.forEach{kind->FilterChip(selected=typeFilter==kind,onClick={typeFilter=if(typeFilter==kind)null else kind},label={Text(kind.label())})}};Text("优先级",style=MaterialTheme.typography.labelMedium);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){StudyTaskPriority.entries.forEach{level->FilterChip(selected=priorityFilter==level,onClick={priorityFilter=if(priorityFilter==level)null else level},label={Text(level.label())})}};Box{OutlinedButton(onClick={courseMenu=true}){Text(courseFilter?.let{id->all.firstOrNull{it.courseRuleId==id}?.courseTitle}?:"全部课程")};DropdownMenu(expanded=courseMenu,onDismissRequest={courseMenu=false}){DropdownMenuItem(text={Text("全部课程")},onClick={courseFilter=null;courseMenu=false});all.filter{it.courseRuleId!=null&&it.courseTitle.isNotBlank()}.distinctBy{it.courseRuleId}.forEach{item->DropdownMenuItem(text={Text(item.courseTitle)},onClick={courseFilter=item.courseRuleId;courseMenu=false})}}};TextButton(onClick={typeFilter=null;priorityFilter=null;courseFilter=null;query=""}){Text("恢复默认")}}}
        if(visible.isEmpty())item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(data.studyTasks.isEmpty())"还没有课业待办" else "没有符合条件的任务",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(if(data.studyTasks.isEmpty())"添加作业、考试或复习计划，它会出现在今日首页和学习看板。" else "调整搜索或筛选条件后再试。");if(data.studyTasks.isEmpty())Button(onClick=onAdd){Text("添加第一项待办")}}}}
        groups.filter{it.second.isNotEmpty()}.forEach{(title,tasks)->item{Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=if(title=="逾期")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground)};items(tasks,key={it.key}){task->StudyTaskCard(task,data,now,onEdit,onToggle,onToggleSubtask)}}
        if(completed.isNotEmpty()){item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={completedExpanded=!completedExpanded}){Text("已完成 ${completed.size} 项 "+if(completedExpanded)"收起" else "展开")};TextButton(onClick=onClearCompleted){Text("清除记录",color=MaterialTheme.colorScheme.error)}}};if(completedExpanded||status=="done")items(completed,key={it.key}){task->StudyTaskCard(task,data,now,onEdit,onToggle,onToggleSubtask)}}
    }
}

@Composable fun StudyTaskCard(task:TaskOccurrence,data:AppData,now:ZonedDateTime,onEdit:(TaskOccurrence)->Unit,onToggle:(TaskOccurrence,Boolean)->Unit,onToggleSubtask:(TaskOccurrence,String,Boolean)->Unit){
    val orphan=task.courseRuleId!=null&&!StudyTaskEngine.courseExists(task,data)
    OutlinedCard(onClick={onEdit(task)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth()){Box(Modifier.width(6.dp).heightIn(min=112.dp).fillMaxHeight().background(Color(taskAccent(task,data)),RoundedCornerShape(topStart=18.dp,bottomStart=18.dp)));Column(Modifier.padding(14.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(task.type.label()+" · "+task.priority.label()+(if(task.repeated)" · 重复" else ""),style=MaterialTheme.typography.labelMedium,color=if(task.priority==StudyTaskPriority.URGENT)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f));Icon(if(task.completedAt==null)Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,if(task.completedAt==null)"标记完成" else "恢复任务",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp).clickable{onToggle(task,task.completedAt==null)})};Text(task.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=2);if(task.courseTitle.isNotBlank())Text(task.courseTitle+(if(orphan)" · 原课程已移除" else ""),style=MaterialTheme.typography.bodySmall,color=if(orphan)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1);Text(taskDueText(task,now),style=MaterialTheme.typography.bodySmall,color=if(task.completedAt==null&&task.dueAt?.isBefore(now)==true)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant);if(task.subtasks.isNotEmpty()){val done=task.subtasks.count{it.id in task.completedSubtaskIds};LinearProgressIndicator(progress={done.toFloat()/task.subtasks.size},modifier=Modifier.fillMaxWidth());Text("子任务 $done/${task.subtasks.size}",style=MaterialTheme.typography.labelSmall);task.subtasks.sortedBy{it.order}.take(3).forEach{sub->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(sub.id in task.completedSubtaskIds,onCheckedChange={onToggleSubtask(task,sub.id,it)});Text(sub.title,maxLines=1,style=MaterialTheme.typography.bodySmall)}}}}}}
}

fun weekdayName(day:Int)=listOf("星期一","星期二","星期三","星期四","星期五","星期六","星期日")[day-1]
