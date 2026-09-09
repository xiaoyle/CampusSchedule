package cn.campus.schedule

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import cn.campus.core.*
import kotlinx.coroutines.delay
import java.time.*
import kotlin.math.*

fun AmbientSound.label()=when(this){AmbientSound.NONE->"关闭";AmbientSound.RAIN->"檐下雨声";AmbientSound.WAVES->"珠海海浪";AmbientSound.LIBRARY->"静谧图书馆"}

@Composable fun FocusQuickStartCard(data:AppData,onStart:(String,String?,String?,String?)->Unit,onOpen:()->Unit){
    val active=data.activeFocus
    val today=LocalDate.now(SCHOOL_ZONE)
    val nextTask=remember(data,today){StudyTaskEngine.occurrences(data,today.minusDays(30),today.plusDays(60)).filter{it.completedAt==null}.minByOrNull{it.dueAt?.toInstant()?:Instant.MAX}}
    ElevatedCard(onClick={if(active!=null)onOpen() else onStart(nextTask?.title?:"自由学习",nextTask?.courseRuleId,nextTask?.taskId,nextTask?.key)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp)){
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Surface(color=MaterialTheme.colorScheme.primary,shape=CircleShape,modifier=Modifier.size(58.dp)){Box(contentAlignment=Alignment.Center){Icon(if(active==null)Icons.Outlined.PlayArrow else Icons.Outlined.Timer,"专注",tint=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.size(31.dp))}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(if(active==null)"开始一段专注" else "专注进行中",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text(active?.title?:nextTask?.let{"建议先做 · ${it.title}"}?:"选择时长、环境音和学习目标",style=MaterialTheme.typography.bodyMedium,maxLines=2)}
            Icon(Icons.Outlined.ChevronRight,null)
        }
    }
}

@Composable fun FocusStartDialog(data:AppData,initialTitle:String,initialCourseId:String?,taskId:String?,occurrenceKey:String?,onDismiss:()->Unit,onStart:(String,FocusMode,Int?,AmbientSound,Float,String?,String?,String?)->Unit){
    var title by rememberSaveable{mutableStateOf(initialTitle)};var mode by rememberSaveable{mutableStateOf(FocusMode.COUNTDOWN)}
    var minutes by rememberSaveable{mutableIntStateOf(data.focusSettings.lastMinutes.coerceIn(1,180))};var custom by rememberSaveable{mutableStateOf("")}
    var sound by rememberSaveable{mutableStateOf(data.focusSettings.ambientSound)};var volume by rememberSaveable{mutableFloatStateOf(data.focusSettings.ambientVolume)}
    var course by rememberSaveable{mutableStateOf(initialCourseId)};var courseMenu by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val courses=(data.schedule?.rules.orEmpty().map{it.id to it.title}+data.manualLessons.map{it.id to it.title}).distinctBy{it.first}
    AlertDialog(onDismissRequest=onDismiss,title={Text("开始专注")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(title,{title=it.take(60)},label={Text("这次要完成什么")},singleLine=true,modifier=Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){
            SegmentedButton(mode==FocusMode.COUNTDOWN,{mode=FocusMode.COUNTDOWN},shape=SegmentedButtonDefaults.itemShape(0,2)){Text("倒计时")}
            SegmentedButton(mode==FocusMode.STOPWATCH,{mode=FocusMode.STOPWATCH},shape=SegmentedButtonDefaults.itemShape(1,2)){Text("正计时")}
        }
        if(mode==FocusMode.COUNTDOWN){Text("专注时长",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(25,45,60).forEach{value->FilterChip(minutes==value&&custom.isBlank(),{minutes=value;custom=""},{Text("$value 分")})}};OutlinedTextField(custom,{custom=it.filter(Char::isDigit).take(3)},label={Text("自定义 1–180 分钟")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        Text("环境音",fontWeight=FontWeight.Bold);AmbientSound.entries.forEach{value->Row(Modifier.fillMaxWidth().clickable{sound=value},verticalAlignment=Alignment.CenterVertically){RadioButton(sound==value,{sound=value});Text(value.label())}}
        if(sound!=AmbientSound.NONE){Text("音量 ${(volume*100).toInt()}%",style=MaterialTheme.typography.labelMedium);Slider(volume,{volume=it},valueRange=.05f..1f)}
        Box{OutlinedButton(onClick={courseMenu=true},modifier=Modifier.fillMaxWidth()){Text(course?.let{id->courses.firstOrNull{it.first==id}?.second}?:"不关联课程")};DropdownMenu(courseMenu,{courseMenu=false}){DropdownMenuItem({Text("不关联课程")},{course=null;courseMenu=false});courses.forEach{item->DropdownMenuItem({Text(item.second)},{course=item.first;courseMenu=false})}}}
        Text("锁屏通知可暂停、继续或结束；计时不会自动完成关联任务。",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={Button(onClick={val chosen=if(custom.isBlank())minutes else custom.toIntOrNull();if(title.isBlank())error="请填写专注目标" else if(mode==FocusMode.COUNTDOWN&&chosen !in 1..180)error="请输入1至180分钟" else onStart(title.trim(),mode,if(mode==FocusMode.COUNTDOWN)chosen else null,sound,volume,course,taskId,occurrenceKey)}){Text("开始")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable fun FocusTimerScreen(active:ActiveFocusState,onBack:()->Unit,onPause:()->Unit,onResume:()->Unit,onStop:()->Unit,onNaturalComplete:()->Unit){
    var now by remember{mutableStateOf(Instant.now())};var elapsedRealtime by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};var stopConfirm by remember{mutableStateOf(false)}
    LaunchedEffect(active.id,active.status){while(true){now=Instant.now();elapsedRealtime=SystemClock.elapsedRealtime();delay(1000)}}
    val elapsed=FocusEngine.elapsedSeconds(active,now,elapsedRealtime);val remaining=FocusEngine.remainingSeconds(active,now,elapsedRealtime)
    LaunchedEffect(remaining,elapsed,active.status){if(active.status==FocusStatus.RUNNING&&(remaining==0L||(remaining==null&&elapsed>=FocusEngine.MAX_STOPWATCH_SECONDS)))onNaturalComplete()}
    val display=remaining?:elapsed;val progress=active.plannedSeconds?.let{(elapsed.toFloat()/it).coerceIn(0f,1f)}?:((elapsed%3600)/3600f)
    val hour=now.atZone(SCHOOL_ZONE).hour;val colors=when(hour){in 6..10->listOf(Color(0xFFBFE3ED),Color(0xFFFFD59A),Color(0xFF376F6A));in 11..16->listOf(Color(0xFF88D6EB),Color(0xFFDFEFF3),Color(0xFF176B74));in 17..19->listOf(Color(0xFFFFB46D),Color(0xFF8A759C),Color(0xFF243D55));else->listOf(Color(0xFF071A2D),Color(0xFF173B69),Color(0xFF102B38))}
    val animations=ValueAnimator.areAnimatorsEnabled();val transition=rememberInfiniteTransition(label="orbit");val drift by transition.animateFloat(0f,1f,infiniteRepeatable(tween(if(animations)8000 else 1,easing=androidx.compose.animation.core.LinearEasing)),label="drift")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))){
        Canvas(Modifier.fillMaxSize()){
            val center=Offset(size.width/2,size.height*.42f);val radius=size.minDimension*.36f
            drawCircle(Color.White.copy(alpha=.12f),radius,center,style=Stroke(3.dp.toPx()))
            drawArc(Color.White.copy(alpha=.92f),-90f,360f*progress,false,topLeft=Offset(center.x-radius,center.y-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(8.dp.toPx(),cap=StrokeCap.Round))
            val angle=Math.toRadians((-90+360*progress).toDouble());val orb=Offset(center.x+cos(angle).toFloat()*radius,center.y+sin(angle).toFloat()*radius)
            drawCircle(if(hour in 6..18)Color(0xFFFFE39A)else Color(0xFFE4EEFF),12.dp.toPx()+(if(animations)sin((drift*6.28f).toDouble()).toFloat()*2.dp.toPx() else 0f),orb)
            if(hour !in 6..18)repeat(16){i->val x=((i*.271f+drift*.015f)%1f)*size.width;val y=((i*.397f)%1f)*size.height*.7f;drawCircle(Color.White.copy(alpha=.42f),1.5.dp.toPx(),Offset(x,y))}
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.KeyboardArrowDown,"收起",tint=Color.White)};Spacer(Modifier.weight(1f));Text(if(active.mode==FocusMode.BREAK)"休息计时" else "校园昼夜光轨",color=Color.White.copy(.85f),fontWeight=FontWeight.Bold)}
            Spacer(Modifier.weight(.6f));Text(active.title,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black,textAlign=TextAlign.Center,maxLines=2)
            Text(if(active.status==FocusStatus.PAUSED)"已暂停" else if(active.mode==FocusMode.STOPWATCH)"正计时" else "剩余时间",color=Color.White.copy(.72f),modifier=Modifier.padding(top=8.dp))
            Text(FocusRuntime.formatSeconds(display),color=Color.White,fontSize=58.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
            Text(active.ambientSound.label(),color=Color.White.copy(.72f));Spacer(Modifier.weight(1f))
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){
                FilledTonalButton(onClick={if(active.status==FocusStatus.RUNNING)onPause()else onResume()},modifier=Modifier.height(58.dp)){Icon(if(active.status==FocusStatus.RUNNING)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(active.status==FocusStatus.RUNNING)"暂停" else "继续")}
                OutlinedButton(onClick={if(elapsed<60)stopConfirm=true else onStop()},modifier=Modifier.height(58.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White),border=BorderStroke(1.dp,Color.White.copy(.65f))){Icon(Icons.Outlined.Stop,null);Spacer(Modifier.width(8.dp));Text("结束")}
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if(stopConfirm)AlertDialog(onDismissRequest={stopConfirm=false},title={Text("专注不足1分钟")},text={Text("仍要保存这次记录吗？不足1分钟不会计入成就和复盘。")},confirmButton={Button(onClick={stopConfirm=false;onStop()}){Text("保存并结束")}},dismissButton={TextButton(onClick={stopConfirm=false}){Text("继续专注")}})
}

@Composable fun FocusCompletionDialog(session:FocusSession,hasTask:Boolean,onDismiss:()->Unit,onCompleteTask:()->Unit,onContinue:()->Unit,onBreak:(Int)->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text("这一段完成了")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("${session.title}\n有效专注 ${session.focusedSeconds/60} 分钟");if(hasTask)FilledTonalButton(onClick=onCompleteTask,modifier=Modifier.fillMaxWidth()){Text("完成关联任务")};Button(onClick=onContinue,modifier=Modifier.fillMaxWidth()){Text("继续专注")};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={onBreak(5)},modifier=Modifier.weight(1f)){Text("休息5分钟")};OutlinedButton(onClick={onBreak(10)},modifier=Modifier.weight(1f)){Text("休息10分钟")}}}},confirmButton={TextButton(onClick=onDismiss){Text("稍后处理")}})
}

@Composable fun FocusReviewScreen(data:AppData,onBack:()->Unit,onGoal:(Int)->Unit,onDelete:(String)->Unit){
    var range by rememberSaveable{mutableIntStateOf(0)};val today=LocalDate.now(SCHOOL_ZONE);val monday=today.minusDays((today.dayOfWeek.value-1).toLong())
    val pair=when(range){1->monday.minusWeeks(1) to monday.minusDays(1);2->today.minusDays(27) to today;else->monday to monday.plusDays(6)};val review=remember(data,range,today){ReviewEngine.review(data,pair.first,pair.second)}
    var goal by remember(data.focusSettings.weeklyMinutesTarget){mutableFloatStateOf(data.focusSettings.weeklyMinutesTarget.toFloat())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"返回")};Column{Text("学习复盘",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text("把投入变成看得见的轨迹",color=MaterialTheme.colorScheme.primary)}}}
        item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("本周","上周","近四周").forEachIndexed{i,label->SegmentedButton(range==i,{range=i},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(label)}}}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricCard("${review.focusedMinutes}","专注分钟",Modifier.weight(1f));MetricCard("${review.completedTasks}","完成任务",Modifier.weight(1f));MetricCard("${review.onTimeTasks}","按时完成",Modifier.weight(1f))}}
        item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text("任务复盘",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);val rate=if(review.scheduledTasks==0)0 else review.finishedScheduledTasks*100/review.scheduledTasks;Row(Modifier.fillMaxWidth()){Text("本时段完成率",Modifier.weight(1f));Text(if(review.scheduledTasks==0)"暂无截止任务" else "$rate%（${review.finishedScheduledTasks}/${review.scheduledTasks}）",fontWeight=FontWeight.Bold)};Row(Modifier.fillMaxWidth()){Text("当前逾期项目",Modifier.weight(1f));Text("${review.overdueTasks} 项",fontWeight=FontWeight.Bold,color=if(review.overdueTasks>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)};Text("未来七天负荷",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(0L..6L).forEach{offset->val day=today.plusDays(offset);Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(offset==0L)"今" else day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,java.util.Locale.CHINA),style=MaterialTheme.typography.labelSmall);Text("${review.futureLoad[day]?:0}",fontWeight=FontWeight.Black)}}}}}}
        item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("每日投入",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);val max=(review.dailyMinutes.values.maxOrNull()?:1).coerceAtLeast(1);Row(Modifier.fillMaxWidth().height(130.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Bottom){generateSequence(pair.first){it.plusDays(1)}.takeWhile{!it.isAfter(pair.second)}.toList().takeLast(14).forEach{day->val value=review.dailyMinutes[day]?:0;Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom){Box(Modifier.fillMaxWidth().height((8+92*value/max.toFloat()).dp).clip(RoundedCornerShape(topStart=6.dp,topEnd=6.dp)).background(MaterialTheme.colorScheme.primary));Text(day.dayOfMonth.toString(),fontSize=10.sp)}}}}}}
        item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("课程投入",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);if(review.courseMinutes.isEmpty())Text("完成专注后，这里会显示课程分布。") else review.courseMinutes.entries.sortedByDescending{it.value}.take(6).forEach{(name,value)->Row(Modifier.fillMaxWidth()){Text(name,Modifier.weight(1f),maxLines=1);Text("$value 分钟",fontWeight=FontWeight.Bold)}}}}}
        item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp)){Text("每周专注目标 · ${goal.toInt()} 分钟",fontWeight=FontWeight.Bold);LinearProgressIndicator(progress={(review.focusedMinutes/goal).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp));Slider(goal,{goal=(it/30).roundToInt()*30f},onValueChangeFinished={onGoal(goal.toInt())},valueRange=30f..2100f,steps=68)}}}
        item{Text("专注历史",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        val sessions=data.focusSessions.filter{FocusEngine.valid(it)}.sortedByDescending{it.endedAt}.take(50)
        if(sessions.isEmpty())item{Text("还没有有效专注记录。")}
        items(sessions,key={it.id}){session->OutlinedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(session.title,fontWeight=FontWeight.Bold);Text("${Instant.parse(session.endedAt).atZone(SCHOOL_ZONE).toLocalDate()} · ${session.focusedSeconds/60} 分钟",style=MaterialTheme.typography.bodySmall)};IconButton(onClick={onDelete(session.id)}){Icon(Icons.Outlined.Delete,"删除记录")}}}}
    }
}

@Composable private fun MetricCard(value:String,label:String,modifier:Modifier){Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp),modifier=modifier){Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text(label,style=MaterialTheme.typography.labelSmall)}}}
