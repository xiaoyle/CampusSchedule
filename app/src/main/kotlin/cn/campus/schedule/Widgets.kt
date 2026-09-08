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

abstract class CourseWidget(private val compact: Boolean) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(androidx.compose.ui.unit.DpSize(180.dp,140.dp), androidx.compose.ui.unit.DpSize(220.dp,180.dp), androidx.compose.ui.unit.DpSize(250.dp,220.dp), androidx.compose.ui.unit.DpSize(300.dp,300.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.scheduleApp.store.read()
        provideContent {
            val current by context.scheduleApp.store.data.collectAsState(initial=data)
            val stamp = currentState<Preferences>()[refreshedAt]
            val diy = remember(stamp) { DiyStore.read(context, compact) }
            val now = stamp?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
            val today = now.atZone(SCHOOL_ZONE).toLocalDate()
            val lessons = ScheduleEngine.occurrences(current)
            val next = lessons.firstOrNull { it.endInstant > now }
            val todayLessons = lessons.filter { it.date == today }
            val small = LocalSize.current.height < 220.dp || LocalSize.current.width < 240.dp
            val ink = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.foreground)) else ink
            val green = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.highlight)) else green
            val widgetSize = LocalSize.current
            val backdrop = remember(diy,widgetSize) { if(diy.enabled) runCatching { DiyRenderer.render(context,diy,(widgetSize.width.value*1.5f).toInt(),(widgetSize.height.value*1.5f).toInt()) }.getOrNull() else null }
            val surface = if(backdrop != null) GlanceModifier.background(ImageProvider(backdrop)) else GlanceModifier.background(paper)
            Column(GlanceModifier.fillMaxSize().then(surface).cornerRadius(20.dp).padding(if(small) 6.dp else 14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text(diy.heading(compact), style=TextStyle(color=green, fontSize=if(small) 12.sp else 15.sp, fontWeight=FontWeight.Bold), modifier=GlanceModifier.defaultWeight(), maxLines=1)
                    Text("刷新", style=TextStyle(color=green, fontSize=12.sp), modifier=GlanceModifier.padding(horizontal=8.dp,vertical=if(small) 0.dp else 4.dp).clickable(actionRunCallback<RefreshWidget>()))
                }
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    val schedule = current.schedule
                    if (schedule == null) {
                        Text("点击导入你的课表", style=TextStyle(color=ink, fontSize=14.sp), maxLines=2, modifier=GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()))
                    } else if (compact) {
                        if (next == null) Text("本学期课程已结束", style=TextStyle(color=ink, fontSize=14.sp),maxLines=2)
                        else WidgetLesson(next, next.startInstant <= now, context, true, diy)
                    } else if (small) {
                        if (todayLessons.isEmpty()) Text(if(ScheduleEngine.week(today,schedule.firstMonday)>schedule.lastWeek) "本学期课程已结束" else if(ScheduleEngine.week(today,schedule.firstMonday)<1) "尚未开学" else "今天没有课程",style=TextStyle(color=ink,fontSize=14.sp),maxLines=2,modifier=GlanceModifier.clickable(actionStartActivity<MainActivity>()))
                        else {
                            val lesson=todayLessons.firstOrNull {it.endInstant>now} ?: todayLessons.last()
                            WidgetLesson(lesson,lesson.startInstant<=now && lesson.endInstant>now,context,false,diy)
                        }
                    } else {
                        val week = ScheduleEngine.week(today, schedule.firstMonday)
                        val label = when { week < 1 -> "尚未开学"; week > schedule.lastWeek -> "学期课程结束"; else -> "第${week}周" }
                        Text("${today.monthValue}月${today.dayOfMonth}日 · $label", style=TextStyle(color=ink, fontSize=12.sp))
                        if (todayLessons.isEmpty()) {
                            Text("今天没有课程", style=TextStyle(color=ink, fontSize=18.sp), modifier=GlanceModifier.padding(vertical=8.dp).clickable(actionStartActivity<MainActivity>()))
                            next?.let { Text("下次 ${it.date.monthValue}/${it.date.dayOfMonth} ${it.start} ${it.title}", style=TextStyle(color=ink, fontSize=13.sp), maxLines=2) }
                        } else {
                            androidx.glance.appwidget.lazy.LazyColumn(GlanceModifier.fillMaxSize()) {
                                items(todayLessons.size) { index -> WidgetLesson(todayLessons[index], todayLessons[index].startInstant <= now && todayLessons[index].endInstant > now, context, false,diy) }
                            }
                        }
                    }
                }
                if(diy.enabled && diy.motto.isNotBlank() && !small) Text(diy.motto,style=TextStyle(color=ink,fontSize=11.sp),maxLines=1)
                Text("xiaoyle 制作",style=TextStyle(color=ink,fontSize=11.sp),maxLines=1,modifier=GlanceModifier.fillMaxWidth().padding(top=if(small) 0.dp else 2.dp))
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
@Composable private fun WidgetLesson(lesson: Occurrence, active: Boolean, context: Context, compact: Boolean, diy: DiyStyle) {
    val ink = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.foreground)) else ink
    val green = if(diy.enabled) androidx.glance.unit.ColorProvider(Color(diy.highlight)) else green
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
class RefreshWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) { ReminderScheduler.refresh(context) }
}
