package cn.campus.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.campus.core.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

internal val CourseColorOptions=listOf(0xFF176B52,0xFF126B91,0xFF7254A3,0xFFB35B43,0xFF986120,0xFFB53E73)

@Composable fun ManualLessonDialog(
    initial:ManualLesson?,occurrence:Occurrence?,prefill:ManualLesson?,defaultDate:LocalDate,periods:List<Period>,existing:List<Occurrence>,
    templates:List<ManualLesson>,suggestedRepeatCount:Int,
    onDismiss:()->Unit,onSave:(ManualLesson,ManualLessonEditScope,Int)->Unit,
    onDelete:(ManualLesson,ManualLessonEditScope,Int)->Unit,
    onCopy:(ManualLesson)->Unit
) {
    val index=occurrence?.manualIndex?:0
    var scope by remember(initial?.id,index){mutableStateOf(ManualLessonEditScope.INSTANCE)}
    var title by remember(initial?.id,index,prefill?.id){mutableStateOf(occurrence?.title?:initial?.title?:prefill?.title.orEmpty())}
    var date by remember(initial?.id,index){mutableStateOf(occurrence?.date?.toString()?:initial?.date?:defaultDate.toString())}
    var teacher by remember(initial?.id,index,prefill?.id){mutableStateOf(occurrence?.candidates?.firstOrNull()?.teacher?:initial?.teacher?:prefill?.teacher.orEmpty())}
    var location by remember(initial?.id,index,prefill?.id){mutableStateOf(occurrence?.candidates?.firstOrNull()?.location?:initial?.location?:prefill?.location.orEmpty())}
    var custom by remember(initial?.id,index,prefill?.id){mutableStateOf(initial!=null||prefill!=null||periods.isEmpty())}
    var startPeriod by remember(initial?.id,index){mutableStateOf(periods.firstOrNull{it.start==(occurrence?.start?:initial?.start)?.toString()}?.number?.toString()?:periods.firstOrNull()?.number?.toString()?:"1")}
    var endPeriod by remember(initial?.id,index){mutableStateOf(periods.firstOrNull{it.end==(occurrence?.end?:initial?.end)?.toString()}?.number?.toString()?:periods.getOrNull(1)?.number?.toString()?:startPeriod)}
    var start by remember(initial?.id,index,prefill?.id){mutableStateOf(occurrence?.start?.toString()?:initial?.start?:prefill?.start?:periods.firstOrNull()?.start.orEmpty())}
    var end by remember(initial?.id,index,prefill?.id){mutableStateOf(occurrence?.end?.toString()?:initial?.end?:prefill?.end?:periods.getOrNull(1)?.end?:periods.firstOrNull()?.end.orEmpty())}
    var color by remember(initial?.id,index,prefill?.id){mutableLongStateOf(occurrence?.color?:initial?.color?:prefill?.color?:CourseColorOptions.first())}
    var repeating by remember(initial?.id){mutableStateOf((initial?.repeatCount?:1)>1)}
    var repeatCount by remember(initial?.id){mutableIntStateOf(initial?.repeatCount?:suggestedRepeatCount.coerceIn(2,30))}
    var error by remember{mutableStateOf<String?>(null)}

    fun loadBase(){val source=initial?:return;title=source.title;date=source.date;teacher=source.teacher;location=source.location;start=source.start;end=source.end;color=source.color;custom=true;repeatCount=source.repeatCount;repeating=source.repeatCount>1}
    fun loadOccurrence(){val item=occurrence?:return;title=item.title;date=item.date.toString();teacher=item.candidates.firstOrNull()?.teacher.orEmpty();location=item.candidates.firstOrNull()?.location.orEmpty();start=item.start.toString();end=item.end.toString();color=item.color?:CourseColorOptions.first();custom=true}
    fun chooseScope(value:ManualLessonEditScope){scope=value;if(value==ManualLessonEditScope.SERIES)loadBase()else loadOccurrence()}
    fun draft():ManualLesson{
        val actualStart:String;val actualEnd:String
        if(custom){actualStart=start.trim();actualEnd=end.trim()}else{
            val first=periods.firstOrNull{it.number==startPeriod.toIntOrNull()}?:error("请选择有效的开始节次")
            val last=periods.firstOrNull{it.number==endPeriod.toIntOrNull()}?:error("请选择有效的结束节次")
            require(first.number<=last.number){"结束节次不能早于开始节次"};actualStart=first.start;actualEnd=last.end
        }
        val total=if(initial==null){if(repeating)repeatCount else 1}else if(scope==ManualLessonEditScope.SERIES){if(repeating)repeatCount else 1}else initial.repeatCount
        return ManualLesson(initial?.id?:UUID.randomUUID().toString(),date.trim(),title.trim(),actualStart,actualEnd,teacher.trim(),location.trim(),color,total).also(ScheduleEngine::validateManualLesson)
    }
    val proposal=runCatching{val d=draft();if(initial==null)d else ScheduleEngine.updateManualLesson(initial,d,scope,index)}.getOrNull()
    val candidateOccurrences=proposal?.let{ScheduleEngine.occurrences(AppData(manualLessons=listOf(it)))}.orEmpty()
    val affected=when{initial==null||scope==ManualLessonEditScope.SERIES->candidateOccurrences;scope==ManualLessonEditScope.FUTURE->candidateOccurrences.filter{(it.manualIndex?:0)>=index};else->candidateOccurrences.filter{it.manualIndex==index}}
    val conflicts=affected.filter{candidate->existing.any{other->!other.cancelled&&other.key!=candidate.key&&candidate.date==other.date&&candidate.start<other.end&&other.start<candidate.end}}
    val first=candidateOccurrences.firstOrNull();val last=candidateOccurrences.lastOrNull()

    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"添加课程" else if(initial.repeatCount>1)"编辑每周课程" else "编辑自建课程")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){
            if(initial!=null&&initial.repeatCount>1)Text("自建课程 · 每周 · 第 ${index+1}/${initial.repeatCount} 次",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
            if(initial!=null&&initial.repeatCount>1){Text("应用范围",fontWeight=FontWeight.Bold);SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){
                listOf(ManualLessonEditScope.INSTANCE to "仅本次",ManualLessonEditScope.FUTURE to "本次及以后",ManualLessonEditScope.SERIES to "整个系列").forEachIndexed{i,(value,label)->SegmentedButton(scope==value,{chooseScope(value)},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(label)}}
            }}
            if(initial==null&&templates.isNotEmpty()){Text("最近课程",fontWeight=FontWeight.Bold);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){templates.take(5).forEach{template->AssistChip(onClick={title=template.title;teacher=template.teacher;location=template.location;start=template.start;end=template.end;color=template.color;custom=true},label={Text(template.title,maxLines=1)})}}}
            OutlinedTextField(title,{title=it.take(60)},label={Text("课程名称 *")},modifier=Modifier.fillMaxWidth())
            DatePickerField(if(initial!=null&&scope==ManualLessonEditScope.SERIES)"首次日期 *" else "日期 *",date,{date=it})
            if(periods.isNotEmpty())Row(verticalAlignment=Alignment.CenterVertically){RadioButton(!custom,{custom=false});Text("按节次");Spacer(Modifier.width(14.dp));RadioButton(custom,{custom=true});Text("自定义时间")}
            if(!custom)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(startPeriod,{startPeriod=it.filter(Char::isDigit).take(2)},label={Text("开始节次")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(endPeriod,{endPeriod=it.filter(Char::isDigit).take(2)},label={Text("结束节次")},singleLine=true,modifier=Modifier.weight(1f))}else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){TimePickerField("开始时间 *",start,{start=it});TimePickerField("结束时间 *",end,{end=it})}
            OutlinedTextField(location,{location=it.take(80)},label={Text("地点（可选）")},modifier=Modifier.fillMaxWidth());OutlinedTextField(teacher,{teacher=it.take(50)},label={Text("教师（可选）")},modifier=Modifier.fillMaxWidth())
            Text("课程颜色",style=MaterialTheme.typography.labelLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){CourseColorOptions.forEach{value->Box(Modifier.size(34.dp).background(Color(value),CircleShape).clickable{color=value}.padding(5.dp),contentAlignment=Alignment.Center){if(color==value)Text("✓",color=Color.White)}}}
            if(initial==null||scope==ManualLessonEditScope.SERIES){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("每周重复",fontWeight=FontWeight.Bold);Text(if(repeating)"每隔7天出现一次" else "只在这一天出现",style=MaterialTheme.typography.bodySmall)};Switch(repeating,{repeating=it})};if(repeating){Text("总上课次数（含本次） · $repeatCount 次",fontWeight=FontWeight.Medium);Slider(repeatCount.toFloat(),{repeatCount=it.toInt().coerceIn(2,30)},valueRange=2f..30f,steps=27)}}
            if(first!=null&&last!=null&&candidateOccurrences.size>1)Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium){Text("每周${first.date.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.CHINA)} ${first.start}–${first.end}，共 ${candidateOccurrences.size} 次\n${first.date} 至 ${last.date}",modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}
            if(conflicts.isNotEmpty())Text("有 ${conflicts.size} 次安排与现有课程冲突：${conflicts.take(3).joinToString("、"){it.date.toString()}}${if(conflicts.size>3)"等" else ""}。仍可保存。",color=MaterialTheme.colorScheme.error)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(initial!=null){
                OutlinedButton(onClick={runCatching(::draft).onSuccess{onCopy(it.copy(id=UUID.randomUUID().toString(),repeatCount=1,revisions=emptyList(),instanceEdits=emptyList()))}.onFailure{error=it.message}},modifier=Modifier.fillMaxWidth()){Text("复制为新课程")}
                TextButton(onClick={onDelete(initial,if(initial.repeatCount==1)ManualLessonEditScope.SERIES else scope,index)},modifier=Modifier.align(Alignment.End)){Text(when(scope){ManualLessonEditScope.INSTANCE->"删除本次课程";ManualLessonEditScope.FUTURE->"删除本次及以后";ManualLessonEditScope.SERIES->"删除整个系列"},color=MaterialTheme.colorScheme.error)}
            }
        }
    },confirmButton={Button(onClick={runCatching(::draft).onSuccess{onSave(it,if(initial?.repeatCount==1)ManualLessonEditScope.SERIES else scope,index)}.onFailure{error=it.message?:"请检查日期和时间"}}){Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("返回")}})
}
