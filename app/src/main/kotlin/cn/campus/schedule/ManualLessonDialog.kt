package cn.campus.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.campus.core.ManualLesson
import cn.campus.core.Occurrence
import cn.campus.core.Period
import cn.campus.core.ScheduleEngine
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal val CourseColorOptions=listOf(0xFF176B52,0xFF126B91,0xFF7254A3,0xFFB35B43,0xFF986120,0xFFB53E73)

@Composable fun ManualLessonDialog(
    initial: ManualLesson?, defaultDate: LocalDate, periods: List<Period>, existing: List<Occurrence>,
    onDismiss:()->Unit, onSave:(ManualLesson)->Unit, onDelete:(ManualLesson)->Unit
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var date by remember(initial?.id) { mutableStateOf(initial?.date ?: defaultDate.toString()) }
    var teacher by remember(initial?.id) { mutableStateOf(initial?.teacher.orEmpty()) }
    var location by remember(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
    var custom by remember(initial?.id) { mutableStateOf(initial!=null || periods.isEmpty()) }
    var startPeriod by remember { mutableStateOf(periods.firstOrNull()?.number?.toString() ?: "1") }
    var endPeriod by remember { mutableStateOf(periods.getOrNull(1)?.number?.toString() ?: startPeriod) }
    var start by remember(initial?.id) { mutableStateOf(initial?.start ?: periods.firstOrNull()?.start.orEmpty()) }
    var end by remember(initial?.id) { mutableStateOf(initial?.end ?: periods.getOrNull(1)?.end ?: periods.firstOrNull()?.end.orEmpty()) }
    var color by remember(initial?.id) { mutableLongStateOf(initial?.color ?: CourseColorOptions.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    fun draft(): ManualLesson {
        val actualStart: String; val actualEnd: String
        if(custom) { actualStart=start.trim(); actualEnd=end.trim() }
        else {
            val first=periods.firstOrNull{it.number==startPeriod.toIntOrNull()} ?: error("请选择有效的开始节次")
            val last=periods.firstOrNull{it.number==endPeriod.toIntOrNull()} ?: error("请选择有效的结束节次")
            require(first.number<=last.number) {"结束节次不能早于开始节次"}
            actualStart=first.start; actualEnd=last.end
        }
        return ManualLesson(initial?.id ?: UUID.randomUUID().toString(),date.trim(),title.trim(),actualStart,actualEnd,teacher.trim(),location.trim(),color).also(ScheduleEngine::validateManualLesson)
    }
    val conflict=runCatching {
        val item=draft(); val d=LocalDate.parse(item.date); val s=LocalTime.parse(item.start); val e=LocalTime.parse(item.end)
        existing.any { !it.cancelled && (!it.manual || it.ruleId!=item.id) && it.date==d && s<it.end && it.start<e }
    }.getOrDefault(false)
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"添加单次课程" else "编辑自建课程")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("这项安排只会出现在指定日期。",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(title,{title=it.take(60)},label={Text("课程名称 *")},modifier=Modifier.fillMaxWidth())
            DatePickerField("日期 *",date,{date=it})
            if(periods.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically) {
                RadioButton(!custom,{custom=false}); Text("按节次")
                Spacer(Modifier.width(14.dp)); RadioButton(custom,{custom=true}); Text("自定义时间")
            }
            if(!custom) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(startPeriod,{startPeriod=it.filter(Char::isDigit).take(2)},label={Text("开始节次")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(endPeriod,{endPeriod=it.filter(Char::isDigit).take(2)},label={Text("结束节次")},singleLine=true,modifier=Modifier.weight(1f))
            } else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TimePickerField("开始时间 *",start,{start=it})
                TimePickerField("结束时间 *",end,{end=it})
            }
            OutlinedTextField(location,{location=it.take(80)},label={Text("地点（可选）")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(teacher,{teacher=it.take(50)},label={Text("教师（可选）")},modifier=Modifier.fillMaxWidth())
            Text("课程颜色",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                CourseColorOptions.forEach { value -> Box(Modifier.size(34.dp).background(Color(value),CircleShape).clickable{color=value}.padding(5.dp),contentAlignment=Alignment.Center) {if(color==value) Text("✓",color=Color.White)} }
            }
            if(conflict) Text("这段时间与现有课程冲突，仍可保存。",color=MaterialTheme.colorScheme.error)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(initial!=null) TextButton(onClick={onDelete(initial)},modifier=Modifier.align(Alignment.End)) {Text("删除这项课程",color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={Button(onClick={runCatching(::draft).onSuccess(onSave).onFailure{error=it.message ?: "请检查日期和时间"}}) {Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("返回")}})
}
