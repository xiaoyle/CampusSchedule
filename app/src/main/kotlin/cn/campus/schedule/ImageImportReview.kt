package cn.campus.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.campus.core.ImageImportDraft
import cn.campus.core.ImageImportKind
import cn.campus.core.ImageImportRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ImageImportReviewScreen(
    draft:ImageImportDraft,
    busy:Boolean,
    onBack:()->Unit,
    onReplace:(ImageImportRow)->Unit,
    onRemove:(String)->Unit,
    onContinue:()->Unit
){
    var editing by remember{mutableStateOf<ImageImportRow?>(null)}
    BackHandler(enabled=true){if(editing!=null)editing=null else onBack()}
    Scaffold(
        topBar={TopAppBar(title={Column{Text("校对图片课表");Text(if(draft.kind==ImageImportKind.WEEKLY_MOBILE)"手机单周截图" else "完整学期课表",style=MaterialTheme.typography.labelSmall)}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"返回")}},actions={TextButton(onClick=onContinue,enabled=!busy&&draft.rows.isNotEmpty()){Text("生成预览")}})},
        bottomBar={Surface(tonalElevation=4.dp){Button(onClick=onContinue,enabled=!busy&&draft.rows.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(16.dp).height(52.dp)){if(busy)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp) else Icon(Icons.Outlined.FactCheck,null);Spacer(Modifier.width(8.dp));Text("完成校对并预览")}}}
    ){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item(key="summary",contentType="summary"){
                ElevatedCard(shape=RoundedCornerShape(22.dp),colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(draft.term,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("识别到 ${draft.rows.size} 项课程"+(if(draft.capturedWeeks.isNotEmpty())" · 周次 ${draft.capturedWeeks.joinToString("、")}" else ""))
                    Text("图片识别可能出现错字。请重点核对课程名、周次、星期和节次。",style=MaterialTheme.typography.bodySmall)
                }}
            }
            draft.warnings.forEachIndexed{index,text->item(key="warning-$index",contentType="warning"){Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Outlined.Info,"提示",tint=MaterialTheme.colorScheme.primary);Text(text,style=MaterialTheme.typography.bodySmall)}}}
            items(draft.rows,key={it.id},contentType={"course"}){row->
                OutlinedCard(onClick={editing=row},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){
                    Row(Modifier.fillMaxWidth()){
                        Box(Modifier.width(6.dp).heightIn(min=132.dp).fillMaxHeight().background(if(row.issues.isEmpty())MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error))
                        Column(Modifier.padding(15.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${weekdayLabel(row.weekday)} · ${row.startPeriod}–${row.endPeriod}节 · ${row.weekExpression}",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick={onRemove(row.id)}){Icon(Icons.Outlined.Delete,"移除课程",tint=MaterialTheme.colorScheme.error)}}
                            Text(row.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            if(row.teacher.isNotBlank()||row.location.isNotBlank())Text(listOf(row.teacher,row.location).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                            row.issues.forEach{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)}
                            Text("点击卡片修改",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(draft.rows.isEmpty())item(key="empty"){Text("课程已全部移除，请返回重新选择图片。",color=MaterialTheme.colorScheme.error)}
        }
    }
    editing?.let{row->ImageRowEditor(row,onDismiss={editing=null},onSave={onReplace(it);editing=null})}
}

@Composable private fun ImageRowEditor(row:ImageImportRow,onDismiss:()->Unit,onSave:(ImageImportRow)->Unit){
    var weeks by remember(row.id){mutableStateOf(row.weekExpression)}
    var weekday by remember(row.id){mutableIntStateOf(row.weekday)}
    var start by remember(row.id){mutableStateOf(row.startPeriod.toString())}
    var end by remember(row.id){mutableStateOf(row.endPeriod.toString())}
    var title by remember(row.id){mutableStateOf(row.title)}
    var teacher by remember(row.id){mutableStateOf(row.teacher)}
    var location by remember(row.id){mutableStateOf(row.location)}
    var error by remember(row.id){mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("修改识别课程")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
            OutlinedTextField(weeks,{weeks=it},label={Text("周次，例如 1-17周每周")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("星期",style=MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){(1..4).forEach{day->FilterChip(selected=weekday==day,onClick={weekday=day},label={Text(day.toString())},modifier=Modifier.weight(1f))}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){(5..7).forEach{day->FilterChip(selected=weekday==day,onClick={weekday=day},label={Text(day.toString())},modifier=Modifier.weight(1f))}}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it.filter(Char::isDigit).take(2)},label={Text("开始节次")},singleLine=true,modifier=Modifier.weight(1f));OutlinedTextField(end,{end=it.filter(Char::isDigit).take(2)},label={Text("结束节次")},singleLine=true,modifier=Modifier.weight(1f))}
            OutlinedTextField(title,{title=it.take(100)},label={Text("课程名称")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(teacher,{teacher=it.take(80)},label={Text("教师（可空）")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(location,{location=it.take(120)},label={Text("地点（可空）")},modifier=Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={Button(onClick={
        val s=start.toIntOrNull();val e=end.toIntOrNull()
        when{weeks.isBlank()->error="请填写周次";title.isBlank()->error="请填写课程名称";s==null||e==null||s !in 1..10||e !in s..10->error="节次应为 1 至 10，且结束节次不能早于开始";else->onSave(row.copy(weekExpression=weeks.trim(),weekday=weekday,startPeriod=s,endPeriod=e,title=title.trim(),teacher=teacher.trim(),location=location.trim(),issues=emptyList()))}
    }){Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

private fun weekdayLabel(day:Int)=listOf("周一","周二","周三","周四","周五","周六","周日").getOrElse(day-1){"星期"}
