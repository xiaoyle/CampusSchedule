package cn.campus.schedule

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.color.ColorProvider
import cn.campus.core.*
import java.time.*

private val ink = ColorProvider(Palette.Ink, Palette.LightInk)
private val green = ColorProvider(Palette.Green, Palette.Mint)
private val paper = ColorProvider(Palette.Paper, Palette.Night)
private val refreshedAt = longPreferencesKey("refreshedAt")
private val widgetTaskId = ActionParameters.Key<String>("widgetTaskId")
private val widgetOccurrenceKey = ActionParameters.Key<String>("widgetOccurrenceKey")

abstract class CourseWidget(private val compact: Boolean) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(androidx.compose.ui.unit.DpSize(180.dp,140.dp), androidx.compose.ui.unit.DpSize(220.dp,180.dp), androidx.compose.ui.unit.DpSize(250.dp,220.dp), androidx.compose.ui.unit.DpSize(300.dp,300.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.scheduleApp.store.read()
        provideContent {
            val current by context.scheduleApp.store.data.collectAsState(initial=data)
            val stamp = currentState<Preferences>()[refreshedAt]
            val diy = remember(stamp) { DiyStore.read(context, compact) }
            val personalization = remember(stamp) { PersonalizationStore.read(context) }
            val palette=remember(stamp,personalization.themeMode) {CampusPalettes.resolve(personalization.themeMode)}
            val now = stamp?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
            val today = now.atZone(SCHOOL_ZONE).toLocalDate()
            val lessons = ScheduleEngine.occurrences(current)
            val next = lessons.firstOrNull { it.endInstant > now }
            val todayLessons = lessons.filter { it.date == today }
            val small = LocalSize.current.height < 220.dp || LocalSize.current.width < 240.dp
            val baseInk=ColorProvider(palette.lightInk,palette.darkInk)
            val baseGreen=ColorProvider(palette.primary,palette.darkPrimary)
            val basePaper=ColorProvider(palette.lightBackground,palette.darkBackground)
            val ink = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.foreground)) else baseInk
            val green = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.highlight)) else baseGreen
            val widgetSize = LocalSize.current
            val backdrop = remember(diy,widgetSize) { if(diy.enabled) runCatching { DiyRenderer.render(context,diy,(widgetSize.width.value*1.5f).toInt(),(widgetSize.height.value*1.5f).toInt()) }.getOrNull() else null }
            val surface = if(backdrop != null) GlanceModifier.background(ImageProvider(backdrop)) else GlanceModifier.background(basePaper)
            Column(GlanceModifier.fillMaxSize().then(surface).cornerRadius(20.dp).padding(if(small) 6.dp else 14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text(diy.heading(compact), style=TextStyle(color=green, fontSize=if(small) 12.sp else 15.sp, fontWeight=FontWeight.Bold), modifier=GlanceModifier.defaultWeight(), maxLines=1)
                    Text("刷新", style=TextStyle(color=green, fontSize=12.sp), modifier=GlanceModifier.padding(horizontal=8.dp,vertical=if(small) 0.dp else 4.dp).clickable(actionRunCallback<RefreshWidget>()))
                }
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val schedule = current.schedule
                    if (schedule == null && current.manualLessons.isEmpty()) {
                        Text("点击导入你的课表", style=TextStyle(color=ink, fontSize=14.sp), maxLines=2, modifier=GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()))
                    } else if (compact) {
                        if (next == null) Text(if(schedule==null)"暂无后续课程" else "本学期课程已结束", style=TextStyle(color=ink, fontSize=14.sp),maxLines=2)
                        else WidgetLesson(next, next.startInstant <= now, context, true, diy,palette.primary)
                    } else if (small) {
                        if (todayLessons.isEmpty()) Text(if(schedule==null)"今天没有课程" else if(ScheduleEngine.week(today,schedule.firstMonday)>schedule.lastWeek) "本学期课程已结束" else if(ScheduleEngine.week(today,schedule.firstMonday)<1) "尚未开学" else "今天没有课程",style=TextStyle(color=ink,fontSize=14.sp),maxLines=2,modifier=GlanceModifier.clickable(actionStartActivity<MainActivity>()))
                        else {
                            val lesson=todayLessons.firstOrNull {it.endInstant>now} ?: todayLessons.last()
                            WidgetLesson(lesson,lesson.startInstant<=now && lesson.endInstant>now,context,false,diy,palette.primary)
                        }
                    } else {
                        val week = schedule?.let{ScheduleEngine.week(today,it.firstMonday)}
                        val label = if(schedule==null) "个人安排" else when { week!! < 1 -> "尚未开学"; week > schedule.lastWeek -> "学期课程结束"; else -> "第${week}周" }
                        Text("${today.monthValue}月${today.dayOfMonth}日 · $label", style=TextStyle(color=ink, fontSize=12.sp))
                        if (todayLessons.isEmpty()) {
                            Text("今天没有课程", style=TextStyle(color=ink, fontSize=18.sp), modifier=GlanceModifier.padding(vertical=8.dp).clickable(actionStartActivity<MainActivity>()))
                            next?.let { Text("下次 ${it.date.monthValue}/${it.date.dayOfMonth} ${it.start} ${it.title}", style=TextStyle(color=ink, fontSize=13.sp), maxLines=2) }
                        } else {
                            androidx.glance.appwidget.lazy.LazyColumn(GlanceModifier.fillMaxSize()) {
                                items(todayLessons.size) { index -> WidgetLesson(todayLessons[index], todayLessons[index].startInstant <= now && todayLessons[index].endInstant > now, context, false,diy,palette.primary) }
                            }
                        }
                    }
                }
                if(diy.enabled && diy.motto.isNotBlank() && !small) Text(diy.motto,style=TextStyle(color=ink,fontSize=11.sp),maxLines=1)
                Row(GlanceModifier.fillMaxWidth().padding(top=if(small) 0.dp else 2.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("xiaoyle 制作",style=TextStyle(color=ink,fontSize=11.sp),maxLines=1,modifier=GlanceModifier.defaultWeight())
                    if(personalization.profile.showAvatarOnWidgets) {
                        val avatar=remember(stamp,personalization.profile) {AvatarRenderer.render(context,personalization.profile,72)}
                        Image(ImageProvider(avatar),"个人头像",modifier=GlanceModifier.size(if(small)22.dp else 28.dp))
                    }
                }
            }
        }
    }
    suspend fun updateAllSafe(context: Context) {
        GlanceAppWidgetManager(context).getGlanceIds(javaClass).forEach { id ->
            updateAppWidgetState(context, id) { it[refreshedAt] = System.currentTimeMillis() }
            update(context, id)
        }
    }
}

class StudyWidget : GlanceAppWidget() {
    override val sizeMode=SizeMode.Responsive(setOf(androidx.compose.ui.unit.DpSize(220.dp,180.dp),androidx.compose.ui.unit.DpSize(250.dp,220.dp),androidx.compose.ui.unit.DpSize(300.dp,300.dp)))
    override suspend fun provideGlance(context:Context,id:GlanceId) {
        val initial=context.scheduleApp.store.read()
        provideContent {
            val current by context.scheduleApp.store.data.collectAsState(initial=initial)
            val stamp=currentState<Preferences>()[refreshedAt]
            val personalization=remember(stamp){PersonalizationStore.read(context)}
            val palette=remember(stamp,personalization.themeMode){CampusPalettes.resolve(personalization.themeMode)}
            val ink=ColorProvider(palette.lightInk,palette.darkInk)
            val accent=ColorProvider(palette.primary,palette.darkPrimary)
            val surface=ColorProvider(palette.lightBackground,palette.darkBackground)
            val now=(stamp?.let{Instant.ofEpochMilli(it)}?:Instant.now()).atZone(SCHOOL_ZONE)
            val today=now.toLocalDate()
            val todayLessons=ScheduleEngine.occurrences(current).count{!it.cancelled&&it.date==today}
            val taskItems=StudyTaskEngine.occurrences(current,today.minusDays(30),today.plusDays(60))
            val pending=taskItems.filter{it.completedAt==null}
            val nearest=pending.minWithOrNull(compareBy<TaskOccurrence>{it.dueAt?.toInstant()?:Instant.MAX}.thenByDescending{it.priority})
            val weekStart=today.minusDays((today.dayOfWeek.value-1).toLong())
            val weekEnd=weekStart.plusDays(6)
            val completedThisWeek=taskItems.count{item->item.completedAt?.let{runCatching{Instant.parse(it).atZone(SCHOOL_ZONE).toLocalDate()}.getOrNull()}?.let{!it.isBefore(weekStart)&&!it.isAfter(weekEnd)}==true}
            val todayTasks=taskItems.count{it.completedAt==null&&it.dueAt?.toLocalDate()==today}
            val total=current.studyTasks.size
            val activeFocus=current.activeFocus
            val todayFocusMinutes=current.focusSessions.filter{FocusEngine.valid(it)&&Instant.parse(it.endedAt).atZone(SCHOOL_ZONE).toLocalDate()==today}.sumOf{it.focusedSeconds}/60
            val small=LocalSize.current.height<220.dp||LocalSize.current.width<240.dp
            Column(GlanceModifier.fillMaxSize().background(surface).cornerRadius(20.dp).padding(if(small)8.dp else 14.dp)) {
                Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("学习看板",style=TextStyle(color=accent,fontSize=if(small)13.sp else 16.sp,fontWeight=FontWeight.Bold),modifier=GlanceModifier.defaultWeight(),maxLines=1)
                    Text("刷新",style=TextStyle(color=accent,fontSize=12.sp),modifier=GlanceModifier.padding(horizontal=8.dp).clickable(actionRunCallback<RefreshWidget>()))
                }
                Row(GlanceModifier.fillMaxWidth().padding(vertical=if(small)4.dp else 8.dp)) {
                    Text("今日 "+todayLessons+" 节课",style=TextStyle(color=ink,fontSize=12.sp),modifier=GlanceModifier.defaultWeight(),maxLines=1)
                    Text("今日任务 "+todayTasks,style=TextStyle(color=ink,fontSize=12.sp),maxLines=1)
                }
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    if(activeFocus!=null) {
                        val elapsed=FocusEngine.elapsedSeconds(activeFocus,now.toInstant(),android.os.SystemClock.elapsedRealtime())
                        val remaining=activeFocus.plannedSeconds?.let{(it-elapsed).coerceAtLeast(0)}
                        Text(if(activeFocus.status==FocusStatus.PAUSED)"专注已暂停" else if(activeFocus.mode==FocusMode.BREAK)"正在休息" else "正在专注",style=TextStyle(color=accent,fontSize=12.sp,fontWeight=FontWeight.Bold),maxLines=1)
                        Text(activeFocus.title,style=TextStyle(color=ink,fontSize=if(small)16.sp else 20.sp,fontWeight=FontWeight.Bold),maxLines=2,modifier=GlanceModifier.padding(vertical=4.dp).clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("openFocus",true))))
                        Text(remaining?.let{"剩余 ${it/60} 分钟"}?:"已专注 ${elapsed/60} 分钟",style=TextStyle(color=ink,fontSize=12.sp),maxLines=1)
                    } else if(nearest==null) {
                        Text(if(total==0)"还没有待办" else "任务都完成了",style=TextStyle(color=ink,fontSize=if(small)16.sp else 19.sp,fontWeight=FontWeight.Bold),modifier=GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("taskId","new"))),maxLines=2)
                    } else {
                        val due=nearest.dueAt
                        val dueText=when {
                            due==null->"无截止日期"
                            due.isBefore(now)->"已逾期"
                            due.toLocalDate()==today->"今天 "+due.toLocalTime()
                            nearest.type==StudyTaskType.EXAM->"考试还有 "+Duration.between(today.atStartOfDay(SCHOOL_ZONE),due.toLocalDate().atStartOfDay(SCHOOL_ZONE)).toDays()+" 天"
                            else->due.monthValue.toString()+"月"+due.dayOfMonth+"日 "+due.toLocalTime()
                        }
                        Text(nearest.type.label()+" · "+dueText,style=TextStyle(color=accent,fontSize=12.sp,fontWeight=FontWeight.Bold),maxLines=1)
                        Text(nearest.title,style=TextStyle(color=ink,fontSize=if(small)16.sp else 20.sp,fontWeight=FontWeight.Bold),maxLines=2,modifier=GlanceModifier.padding(vertical=4.dp).clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("taskId",nearest.taskId).putExtra("taskOccurrenceKey",nearest.key))))
                        if(!small&&nearest.courseTitle.isNotBlank())Text(nearest.courseTitle,style=TextStyle(color=ink,fontSize=12.sp),maxLines=1)
                        if(!small)Text("完成本次",style=TextStyle(color=accent,fontSize=12.sp,fontWeight=FontWeight.Bold),modifier=GlanceModifier.padding(vertical=5.dp).clickable(actionRunCallback<CompleteWidgetTask>(actionParametersOf(widgetTaskId to nearest.taskId,widgetOccurrenceKey to nearest.key))))
                    }
                }
                Text("今日专注 ${todayFocusMinutes} 分 · 任务 $completedThisWeek/${current.learningGoal.weeklyTarget}",style=TextStyle(color=accent,fontSize=11.sp),maxLines=1)
                Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("xiaoyle 制作",style=TextStyle(color=ink,fontSize=11.sp),modifier=GlanceModifier.defaultWeight(),maxLines=1)
                    if(personalization.profile.showAvatarOnWidgets) {
                        val avatar=remember(stamp,personalization.profile){AvatarRenderer.render(context,personalization.profile,72)}
                        Image(ImageProvider(avatar),"个人头像",modifier=GlanceModifier.size(if(small)22.dp else 28.dp))
                    }
                }
            }
        }
    }
    suspend fun updateAllSafe(context:Context) {
        GlanceAppWidgetManager(context).getGlanceIds(javaClass).forEach { id->
            updateAppWidgetState(context,id){it[refreshedAt]=System.currentTimeMillis()}
            update(context,id)
        }
    }
}
@Composable private fun WidgetLesson(lesson: Occurrence, active: Boolean, context: Context, compact: Boolean, diy: DiyStyle,defaultAccent:Color) {
    val ink = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.foreground)) else ink
    val lessonColor=lesson.color
    val green = when {lessonColor!=null->androidx.glance.unit.ColorProvider(Color(lessonColor));diy.enabled->androidx.glance.unit.ColorProvider(Color(diy.highlight));else->androidx.glance.unit.ColorProvider(defaultAccent)}
    val scale = if(diy.enabled) diy.fontScale else 1f
    val small = LocalSize.current.height < 220.dp || LocalSize.current.width < 240.dp
    val singleLine = LocalSize.current.height < 280.dp || LocalSize.current.width < 260.dp
    val note = if(diy.enabled && diy.kind=="notes") GlanceModifier.background(Color(0xFFE6EEDC)).cornerRadius(12.dp).padding(horizontal=6.dp) else GlanceModifier
    Column(GlanceModifier.fillMaxWidth().then(note).padding(vertical=if(small) 0.dp else 6.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra("lessonKey", lesson.key)))) {
        Text((if (active) "正在上课 · " else "") + (if (compact) "${lesson.date.monthValue}/${lesson.date.dayOfMonth} " else "") + (if(small) "${lesson.start}" else "${lesson.start}–${lesson.end}"), style=TextStyle(color=green, fontSize=((if(small) 12 else 14)*scale).sp, fontWeight=FontWeight.Bold),maxLines=if(singleLine)1 else 2)
        Text(lesson.title, style=TextStyle(color=ink, fontSize=((if(small) 13 else if(compact) 18 else 15)*scale).sp, fontWeight=FontWeight.Bold), maxLines=if(singleLine)1 else 2)
        Text(lesson.locationText, style=TextStyle(color=ink, fontSize=((if(small) 11 else 12)*scale).sp), maxLines=if(singleLine)1 else 2)
    }
}
class TodayWidget : CourseWidget(false)
class NextWidget : CourseWidget(true)
class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodayWidget() }
class NextWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = NextWidget() }
class StudyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = StudyWidget() }
class RefreshWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) { ReminderScheduler.refresh(context) }
}
class CompleteWidgetTask : ActionCallback {
    override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters) {
        val id=parameters[widgetTaskId]?:return
        val key=parameters[widgetOccurrenceKey]?:return
        context.scheduleApp.store.update {state->state.copy(studyTasks=StudyTaskEngine.sorted(state.studyTasks.map{task->
            if(task.id!=id)task else {val occurrence=StudyTaskEngine.occurrence(task,key)?:return@map task;val old=task.instanceStates.firstOrNull{it.occurrenceKey==key};val changed=(old?:TaskInstanceState(key)).copy(completedAt=Instant.now().toString(),completedSubtaskIds=occurrence.subtasks.map{it.id});if(!occurrence.repeated)task.copy(completedAt=Instant.now().toString(),instanceStates=task.instanceStates.filterNot{it.occurrenceKey==key}+changed) else task.copy(instanceStates=task.instanceStates.filterNot{it.occurrenceKey==key}+changed)}
        }))}
        context.scheduleApp.store.update{AchievementEngine.evaluate(it).first}
        ReminderScheduler.refresh(context)
    }
}
