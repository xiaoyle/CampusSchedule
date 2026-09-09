package cn.campus.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.campus.core.*
import java.time.*

@Composable fun ScheduleHub(
    data:AppData,lessons:List<Occurrence>,conflicts:Set<String>,today:LocalDate,requestedDate:LocalDate,
    onOpenLesson:(Occurrence)->Unit,onOpenTask:(TaskOccurrence)->Unit,onToggleTask:(TaskOccurrence,Boolean)->Unit,
    onToggleSubtask:(TaskOccurrence,String,Boolean)->Unit,onAddLesson:(LocalDate)->Unit,onAddTask:(LocalDate)->Unit
){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("schedule_view",Context.MODE_PRIVATE)}
    var calendarMode by rememberSaveable{mutableStateOf(prefs.getBoolean("calendar",false))}
    fun selectMode(calendar:Boolean){calendarMode=calendar;prefs.edit().putBoolean("calendar",calendar).apply()}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Text(if(calendarMode)"学习月历" else "每周课表",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Row{FilterChip(selected=!calendarMode,onClick={selectMode(false)},label={Text("周课表")});Spacer(Modifier.width(6.dp));FilterChip(selected=calendarMode,onClick={selectMode(true)},label={Text("月历")})}
        }
        if(calendarMode) LearningMonthView(data,lessons,today,requestedDate,onOpenLesson,onOpenTask,onToggleTask,onToggleSubtask,onAddLesson,onAddTask)
        else WeeklyScheduleView(data,lessons,conflicts,today,onOpenLesson,onAddLesson)
    }
}

@Composable private fun WeeklyScheduleView(data:AppData,lessons:List<Occurrence>,conflicts:Set<String>,today:LocalDate,onOpenLesson:(Occurrence)->Unit,onAddLesson:(LocalDate)->Unit){
    var week by rememberSaveable(data.schedule?.firstMonday){mutableIntStateOf(data.schedule?.let{ScheduleEngine.week(today,it.firstMonday).coerceIn(1,it.lastWeek.coerceAtLeast(1))}?:0)}
    val schedule=data.schedule
    val start=schedule?.let{LocalDate.parse(it.firstMonday).plusWeeks((week-1).toLong())}?:today.minusDays((today.dayOfWeek.value-1).toLong()).plusWeeks(week.toLong())
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End){FilledTonalButton(onClick={onAddLesson(if(!today.isBefore(start)&&!today.isAfter(start.plusDays(6)))today else start)}){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(5.dp));Text("添加课程")}}}
        if(schedule==null&&data.manualLessons.isEmpty())item{Text("还没有学校课表，也可以先添加一项单次课程。",style=MaterialTheme.typography.bodyMedium)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={week--},enabled=if(schedule==null)week>-52 else week>1){Icon(Icons.Outlined.ChevronLeft,"上一周")};Text(if(schedule==null)when{week==0->"本周";week>0->"$week 周后";else->"${-week} 周前"}else"第 $week 周",style=MaterialTheme.typography.titleLarge);IconButton(onClick={week++},enabled=if(schedule==null)week<52 else week<schedule.lastWeek){Icon(Icons.Outlined.ChevronRight,"下一周")}}}
        (0..6).forEach{offset->val date=start.plusDays(offset.toLong());item{Text("${weekdayName(date.dayOfWeek.value)}  ${date.monthValue}/${date.dayOfMonth}",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))};val dayItems=lessons.filter{it.date==date};if(dayItems.isEmpty())item{Text("无课",style=MaterialTheme.typography.bodySmall)};items(dayItems,key={"week-${it.key}"}){lesson->LessonCard(lesson,lesson.key in conflicts){onOpenLesson(lesson)}}}
    }
}

@Composable private fun LearningMonthView(
    data:AppData,lessons:List<Occurrence>,today:LocalDate,requestedDate:LocalDate,
    onOpenLesson:(Occurrence)->Unit,onOpenTask:(TaskOccurrence)->Unit,onToggleTask:(TaskOccurrence,Boolean)->Unit,
    onToggleSubtask:(TaskOccurrence,String,Boolean)->Unit,onAddLesson:(LocalDate)->Unit,onAddTask:(LocalDate)->Unit
){
    var selected by rememberSaveable{mutableStateOf(requestedDate.toString())}
    var monthText by rememberSaveable{mutableStateOf(YearMonth.from(requestedDate).toString())}
    LaunchedEffect(requestedDate){selected=requestedDate.toString();monthText=YearMonth.from(requestedDate).toString()}
    val date=LocalDate.parse(selected);val month=YearMonth.parse(monthText)
    val monthTasks=remember(data,month){StudyTaskEngine.occurrences(data,month.atDay(1),month.atEndOfMonth())}
    val dayTasks=remember(data,date){StudyTaskEngine.occurrences(data,date,date)}
    val dayLessons=lessons.filter{it.date==date}
    val firstOffset=month.atDay(1).dayOfWeek.value-1
    val leadingCells:List<LocalDate?> = List(firstOffset){null}
    val monthCells:List<LocalDate?> = (1..month.lengthOfMonth()).map{month.atDay(it)}
    val partialCells=leadingCells+monthCells
    val cells=partialCells+List((7-partialCells.size%7)%7){null}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){IconButton(onClick={val target=month.minusMonths(1);monthText=target.toString();selected=target.atDay(minOf(date.dayOfMonth,target.lengthOfMonth())).toString()}){Icon(Icons.Outlined.ChevronLeft,"上个月")};Text("${month.year} 年 ${month.monthValue} 月",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);IconButton(onClick={val target=month.plusMonths(1);monthText=target.toString();selected=target.atDay(minOf(date.dayOfMonth,target.lengthOfMonth())).toString()}){Icon(Icons.Outlined.ChevronRight,"下个月")}};TextButton(onClick={selected=today.toString();monthText=YearMonth.from(today).toString()},modifier=Modifier.fillMaxWidth()){Text("回到今天")}}
        item{Row(Modifier.fillMaxWidth()){(1..7).forEach{day->Text(weekdayName(day).takeLast(1),style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}}
        items(cells.chunked(7),key={row->row.joinToString()}) { row->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell->
                    if(cell==null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else {
                        val courseCount=lessons.count{!it.cancelled&&it.date==cell}
                        val tasks=monthTasks.filter{it.dueAt?.toLocalDate()==cell}
                        val overdue=tasks.any{it.completedAt==null&&it.dueAt?.isBefore(ZonedDateTime.now(SCHOOL_ZONE))==true}
                        val done=tasks.isNotEmpty()&&tasks.all{it.completedAt!=null}
                        Surface(
                            color=when{cell.toString()==selected->MaterialTheme.colorScheme.primaryContainer;cell==today->MaterialTheme.colorScheme.secondaryContainer;else->Color.Transparent},
                            shape=RoundedCornerShape(14.dp),
                            modifier=Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clickable{selected=cell.toString()}
                        ) {
                            Column(Modifier.padding(5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(cell.dayOfMonth.toString(),fontWeight=if(cell==today||cell.toString()==selected)FontWeight.Bold else FontWeight.Normal)
                                Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                                    if(courseCount>0) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary,CircleShape))
                                    if(tasks.isNotEmpty()) Box(Modifier.size(6.dp).background(if(overdue)MaterialTheme.colorScheme.error else if(done)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
        item{HorizontalDivider();Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("${date.monthValue} 月 ${date.dayOfMonth} 日 · ${weekdayName(date.dayOfWeek.value)}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("${dayLessons.count{!it.cancelled}} 节课 · ${dayTasks.count{it.completedAt==null}} 项待办",style=MaterialTheme.typography.bodySmall)};Row{IconButton(onClick={onAddLesson(date)}){Icon(Icons.Outlined.AddBox,"添加课程")};IconButton(onClick={onAddTask(date)}){Icon(Icons.Outlined.AddTask,"添加待办")}}}}
        if(dayLessons.isEmpty()&&dayTasks.isEmpty())item{Text("这一天没有课程或截止任务。",modifier=Modifier.padding(vertical=12.dp))}
        val timeline=(dayLessons.map{CalendarItem(it.start,it,null)}+dayTasks.map{CalendarItem(it.dueAt?.toLocalTime()?:LocalTime.MAX,null,it)}).sortedBy{it.time}
        items(timeline,key={it.lesson?.key?:it.task!!.key}){item->item.lesson?.let{LessonCard(it,false){onOpenLesson(it)}}?:item.task?.let{StudyTaskCard(it,data,ZonedDateTime.now(SCHOOL_ZONE),onOpenTask,onToggleTask,onToggleSubtask)}}
    }
}

private data class CalendarItem(val time:LocalTime,val lesson:Occurrence?,val task:TaskOccurrence?)
