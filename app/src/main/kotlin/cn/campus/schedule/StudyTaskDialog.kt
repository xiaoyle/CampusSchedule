package cn.campus.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.campus.core.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

private val editorDateTimeFormat=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private fun reminderLabel(minutes:Int?)=when(minutes){null->"不提醒";0->"截止时";10->"提前10分钟";30->"提前30分钟";60->"提前1小时";1440->"提前1天";4320->"提前3天";else->"不提醒"}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StudyTaskDialog(task:StudyTask,occurrence:TaskOccurrence?,existing:Boolean,data:AppData,busy:Boolean,onDismiss:()->Unit,onSave:(StudyTask,Boolean)->Unit,onDelete:(Boolean)->Unit,onDuplicate:()->Unit,onStartFocus:()->Unit){
    val shown=occurrence
    var title by remember(task.id,shown?.key){mutableStateOf(shown?.title?:task.title)}
    var type by remember(task.id,shown?.key){mutableStateOf(shown?.type?:task.type)}
    var priority by remember(task.id,shown?.key){mutableStateOf(shown?.priority?:task.priority)}
    var courseId by remember(task.id,shown?.key){mutableStateOf(shown?.courseRuleId?:task.courseRuleId)}
    var courseTitle by remember(task.id,shown?.key){mutableStateOf(shown?.courseTitle?:task.courseTitle)}
    var dueText by remember(task.id,shown?.key){mutableStateOf((shown?.dueAt?.toLocalDateTime()?.toString()?:task.dueAt)?.let{runCatching{LocalDateTime.parse(it).format(editorDateTimeFormat)}.getOrDefault(it)}.orEmpty())}
    var note by remember(task.id,shown?.key){mutableStateOf(shown?.note?:task.note)}
    var reminder by remember(task.id,shown?.key){mutableStateOf(shown?.remindBeforeMinutes?:task.remindBeforeMinutes)}
    var repeatKind by remember(task.id){mutableStateOf(task.repeatRule?.kind)}
    var weekdays by remember(task.id){mutableStateOf(task.repeatRule?.weekdays.orEmpty().toSet())}
    var endsOn by remember(task.id){mutableStateOf(task.repeatRule?.endsOn.orEmpty())}
    var entireSeries by remember(task.id,shown?.key){mutableStateOf(false)}
    var deleteSeriesConfirm by remember(task.id,shown?.key){mutableStateOf(false)}
    val subtasks=remember(task.id,shown?.key){mutableStateListOf<StudySubtask>().apply{addAll(shown?.subtasks?:task.subtasks)}}
    var newSubtask by remember(task.id){mutableStateOf("")}
    var courseExpanded by remember{mutableStateOf(false)};var reminderExpanded by remember{mutableStateOf(false)};var error by remember(task.id,shown?.key){mutableStateOf<String?>(null)}
    val courseOptions=remember(data.schedule,data.manualLessons){(data.schedule?.rules.orEmpty().map{it.id to it.title}+data.manualLessons.map{it.id to it.title}).distinctBy{it.first}}
    val repeatedExisting=existing&&occurrence?.repeated==true
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing)"编辑待办" else "新增待办")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(repeatedExisting){Text("修改范围",style=MaterialTheme.typography.labelLarge);Row(Modifier.fillMaxWidth()){FilterChip(selected=!entireSeries,onClick={entireSeries=false},label={Text("仅本次")});Spacer(Modifier.width(8.dp));FilterChip(selected=entireSeries,onClick={entireSeries=true},label={Text("整个系列")})}}
            OutlinedTextField(title,{title=it.take(60)},label={Text("任务标题")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("任务类型",style=MaterialTheme.typography.labelLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){StudyTaskType.entries.forEach{value->FilterChip(selected=type==value,onClick={type=value},label={Text(value.label())})}}
            Text("优先级",style=MaterialTheme.typography.labelLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){StudyTaskPriority.entries.forEach{value->FilterChip(selected=priority==value,onClick={priority=value},label={Text(value.label())})}}
            ExposedDropdownMenuBox(expanded=courseExpanded,onExpandedChange={courseExpanded=it}){OutlinedTextField(value=courseTitle.ifBlank{"不关联课程"},onValueChange={},readOnly=true,label={Text("关联课程（可选）")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(courseExpanded)},modifier=Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable));ExposedDropdownMenu(expanded=courseExpanded,onDismissRequest={courseExpanded=false}){DropdownMenuItem(text={Text("不关联课程")},onClick={courseId=null;courseTitle="";courseExpanded=false});courseOptions.forEach{option->DropdownMenuItem(text={Text(option.second)},onClick={courseId=option.first;courseTitle=option.second;courseExpanded=false})}}}
            DateTimePickerField("截止时间（可选）",dueText,{dueText=it},allowEmpty=true)
            ExposedDropdownMenuBox(expanded=reminderExpanded,onExpandedChange={reminderExpanded=it}){OutlinedTextField(value=reminderLabel(reminder),onValueChange={},readOnly=true,label={Text("提醒")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(reminderExpanded)},modifier=Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable));ExposedDropdownMenu(expanded=reminderExpanded,onDismissRequest={reminderExpanded=false}){listOf<Int?>(null,0,10,30,60,1440,4320).forEach{value->DropdownMenuItem(text={Text(reminderLabel(value))},onClick={reminder=value;reminderExpanded=false})}}}
            if(!repeatedExisting||entireSeries){Text("重复",style=MaterialTheme.typography.labelLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(selected=repeatKind==null,onClick={repeatKind=null},label={Text("不重复")});FilterChip(selected=repeatKind==TaskRepeatKind.DAILY,onClick={repeatKind=TaskRepeatKind.DAILY},label={Text("每天")});FilterChip(selected=repeatKind==TaskRepeatKind.WEEKLY,onClick={repeatKind=TaskRepeatKind.WEEKLY},label={Text("每周")})};FilterChip(selected=repeatKind==TaskRepeatKind.CUSTOM_WEEKDAYS,onClick={repeatKind=TaskRepeatKind.CUSTOM_WEEKDAYS},label={Text("自选星期")});if(repeatKind==TaskRepeatKind.CUSTOM_WEEKDAYS){listOf((1..4).toList(),(5..7).toList()).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach{day->FilterChip(selected=day in weekdays,onClick={weekdays=if(day in weekdays)weekdays-day else weekdays+day},label={Text(weekdayName(day).takeLast(1))})}}}};if(repeatKind!=null)DatePickerField("结束日期（可选）",endsOn,{endsOn=it},allowEmpty=true)}
            OutlinedTextField(note,{note=it.take(500)},label={Text("备注（可选）")},minLines=3,maxLines=6,modifier=Modifier.fillMaxWidth())
            Text("检查清单",style=MaterialTheme.typography.labelLarge)
            subtasks.forEachIndexed{index,item->var drag by remember(item.id){mutableFloatStateOf(0f)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){Icon(Icons.Outlined.DragHandle,"长按拖动排序",Modifier.padding(top=14.dp).pointerInput(item.id){detectDragGesturesAfterLongPress(onDragEnd={drag=0f},onDragCancel={drag=0f}){change,amount->change.consume();drag+=amount.y;if(abs(drag)>45f){val target=(index+if(drag>0)1 else -1).coerceIn(0,subtasks.lastIndex);if(target!=index){val moved=subtasks.removeAt(index);subtasks.add(target,moved)};drag=0f}}});OutlinedTextField(item.title,{value->subtasks[index]=item.copy(title=value.take(60))},singleLine=true,modifier=Modifier.weight(1f));IconButton(onClick={subtasks.remove(item)}){Icon(Icons.Outlined.Delete,"删除子任务")}}}
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){OutlinedTextField(newSubtask,{newSubtask=it.take(60)},label={Text("添加子任务")},singleLine=true,modifier=Modifier.weight(1f));IconButton(onClick={if(newSubtask.isNotBlank()&&subtasks.size<30){subtasks+=StudySubtask(UUID.randomUUID().toString(),newSubtask.trim(),subtasks.size);newSubtask=""}}){Icon(Icons.Outlined.Add,"添加子任务")}}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(existing){FilledTonalButton(onClick=onStartFocus,enabled=!busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Timer,null);Spacer(Modifier.width(8.dp));Text("开始专注")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=onDuplicate,enabled=!busy){Text("复制")};TextButton(onClick={if(repeatedExisting&&entireSeries)deleteSeriesConfirm=true else onDelete(entireSeries||!repeatedExisting)},enabled=!busy){Text(if(repeatedExisting&&!entireSeries)"删除本次" else if(repeatedExisting)"删除整个系列" else "删除",color=MaterialTheme.colorScheme.error)}}}
        }
    },confirmButton={Button(onClick={runCatching{val due=dueText.trim().takeIf{it.isNotEmpty()}?.let{LocalDateTime.parse(it,editorDateTimeFormat).toString()};val repeat=repeatKind?.let{TaskRepeatRule(it,if(it==TaskRepeatKind.CUSTOM_WEEKDAYS)weekdays.sorted()else emptyList(),endsOn.trim().takeIf{date->date.isNotEmpty()}?.let(LocalDate::parse)?.toString())};val updated=task.copy(title=title.trim(),type=type,priority=priority,courseRuleId=courseId,courseTitle=courseTitle.trim(),dueAt=due,note=note.trim(),remindBeforeMinutes=if(due==null)null else reminder,subtasks=subtasks.mapIndexed{i,item->item.copy(order=i)},repeatRule=if(repeatedExisting&&!entireSeries)task.repeatRule else repeat);StudyTaskEngine.validate(updated);onSave(updated,entireSeries||!repeatedExisting)}.onFailure{error=it.message?:"请检查任务内容和截止时间"}},enabled=!busy){Text("保存")}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("取消")}})
    if(deleteSeriesConfirm)AlertDialog(onDismissRequest={deleteSeriesConfirm=false},title={Text("删除整个重复系列？")},text={Text("全部未来任务和过去完成记录都会删除。")},confirmButton={Button(onClick={deleteSeriesConfirm=false;onDelete(true)}){Text("删除整个系列")}},dismissButton={TextButton(onClick={deleteSeriesConfirm=false}){Text("取消")}})
}

@Composable fun DateTimePickerField(label:String,value:String,onValue:(String)->Unit,allowEmpty:Boolean){
    val context=LocalContext.current;val parsed=runCatching{LocalDateTime.parse(value,editorDateTimeFormat)}.getOrNull();val base=parsed?:LocalDateTime.now().withSecond(0).withNano(0)
    OutlinedTextField(value,{onValue(it.take(16))},label={Text(label)},supportingText={Text("格式：yyyy-MM-dd HH:mm")},singleLine=true,trailingIcon={IconButton(onClick={DatePickerDialog(context,{_,y,m,d->val date=LocalDate.of(y,m+1,d);TimePickerDialog(context,{_,h,min->onValue(date.atTime(h,min).format(editorDateTimeFormat))},base.hour,base.minute,true).show()},base.year,base.monthValue-1,base.dayOfMonth).show()}){Icon(Icons.Outlined.Event,"选择日期和时间")}},modifier=Modifier.fillMaxWidth())
    if(allowEmpty&&value.isNotBlank())TextButton(onClick={onValue("")}){Text("清除时间")}
}

@Composable fun TimePickerField(label:String,value:String,onValue:(String)->Unit){
    val context=LocalContext.current
    val base=runCatching{LocalTime.parse(value)}.getOrDefault(LocalTime.now().withSecond(0).withNano(0))
    OutlinedTextField(value,{onValue(it.take(5))},label={Text(label)},supportingText={Text("格式：HH:mm")},singleLine=true,modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={TimePickerDialog(context,{_,h,m->onValue(LocalTime.of(h,m).toString())},base.hour,base.minute,true).show()}){Icon(Icons.Outlined.Schedule,"选择时间")}})
}

@Composable fun DatePickerField(label:String,value:String,onValue:(String)->Unit,allowEmpty:Boolean=false){
    val context=LocalContext.current;val base=runCatching{LocalDate.parse(value)}.getOrNull()?:LocalDate.now()
    OutlinedTextField(value,{onValue(it.take(10))},label={Text(label)},supportingText={Text("格式：yyyy-MM-dd")},singleLine=true,trailingIcon={IconButton(onClick={DatePickerDialog(context,{_,y,m,d->onValue(LocalDate.of(y,m+1,d).toString())},base.year,base.monthValue-1,base.dayOfMonth).show()}){Icon(Icons.Outlined.Event,"选择日期")}},modifier=Modifier.fillMaxWidth())
    if(allowEmpty&&value.isNotBlank())TextButton(onClick={onValue("")}){Text("清除日期")}
}
