package cn.campus.schedule

import android.os.Bundle
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.campus.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class DiyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CampusTheme { DiyEditor(this) } }
    }
}

private val styleSaver = Saver<DiyStyle,String>(save={Json.encodeToString(it)},restore={Json.decodeFromString<DiyStyle>(it)})

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DiyEditor(activity: DiyActivity) {
    val scope=rememberCoroutineScope()
    val snack=remember { SnackbarHostState() }
    // Two independent drafts; switching preview never throws away unsaved work.
    var next by rememberSaveable(stateSaver=styleSaver) { mutableStateOf(DiyStore.read(activity,true)) }
    var today by rememberSaveable(stateSaver=styleSaver) { mutableStateOf(DiyStore.read(activity,false)) }
    var baselineNext by rememberSaveable(stateSaver=styleSaver) { mutableStateOf(next) }
    var baselineToday by rememberSaveable(stateSaver=styleSaver) { mutableStateOf(today) }
    var compact by rememberSaveable { mutableStateOf(true) }
    var large by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var exit by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var themeName by rememberSaveable { mutableStateOf("") }
    var themes by remember { mutableStateOf(DiyStore.themes(activity)) }
    var courses by remember { mutableStateOf<List<Occurrence>>(emptyList()) }
    val style=if(compact) next else today
    val dirty=next!=baselineNext || today!=baselineToday
    fun change(value: DiyStyle) { if(compact) next=value.copy(enabled=true) else today=value.copy(enabled=true) }
    fun leave() { if(!busy) { if(dirty) exit=true else activity.finish() } }
    BackHandler { leave() }
    LaunchedEffect(Unit) { courses=withContext(Dispatchers.IO) { ScheduleEngine.occurrences(activity.scheduleApp.store.read()) } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) {
            val target=compact
            busy=true; error=null
            scope.launch {
                try {
                    val name=withContext(Dispatchers.IO) { DiyStore.importImage(activity,uri) }
                    if(target) next=next.copy(enabled=true,image=name,zoom=1f,x=.5f,y=.5f)
                    else today=today.copy(enabled=true,image=name,zoom=1f,x=.5f,y=.5f)
                } catch(e: Exception) { error=e.message ?: "图片读取失败，请重新选择" }
                finally { busy=false }
            }
        }
    }
    Scaffold(topBar={ TopAppBar(title={Text("我的桌面 · DIY")},navigationIcon={TextButton(onClick={leave()},enabled=!busy){Text("返回")}}) },
        snackbarHost={SnackbarHost(snack)},
        bottomBar={ Surface(shadowElevation=6.dp) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal=20.dp,vertical=8.dp)) {
                Button(onClick={
                    busy=true; error=null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { DiyStore.save(activity,true,next); DiyStore.save(activity,false,today) }
                            baselineNext=next; baselineToday=today
                            try {
                                withContext(Dispatchers.IO) { NextWidget().updateAllSafe(activity); TodayWidget().updateAllSafe(activity) }
                                snack.showSnackbar("已保存到桌面；尚未添加组件时，请返回设置页添加")
                            } catch(_:Exception) { error="搭配已保存，桌面刷新失败，请点击组件上的刷新" }
                        } catch(e:Exception) { error=e.message ?: "保存失败，请重试" }
                        finally {busy=false}
                    }
                },enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text(if(busy) "正在处理…" else "保存到桌面")}
            }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            // The preview stays visible while controls below scroll.
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal=20.dp,vertical=8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(compact,{if(!busy)compact=true},label={Text("下一节课")})
                    FilterChip(!compact,{if(!busy)compact=false},label={Text("今日课程")})
                    TextButton(onClick={large=!large}){Text(if(large) "缩小预览" else "放大预览")}
                }
                DiyPreview(activity, style, compact, large, courses, onMove={dx,dy ->
                    if(!busy && style.image.isNotEmpty()) change(style.copy(x=(style.x-dx).coerceIn(0f,1f),y=(style.y-dy).coerceIn(0f,1f)))
                })
                Text("布局预览 · 实际大小随桌面调整 · 图片可拖动定位",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=4.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                Text("把喜欢的画面，带进每一天",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("photo" to "照片卡","glass" to "毛玻璃","notes" to "课程便签").forEach { (key,label) ->
                        FilterChip(style.enabled && style.kind==key,{if(!busy) change(style.copy(kind=key))},label={Text(label)})
                    }
                }
                Button(onClick={picker.launch(arrayOf("image/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(style.image.isBlank()) "从相册选一张背景图" else "更换背景图片")}
                Text("图片仅存本机。支持 JPG / PNG / WebP 等系统可读取图片，最大 24 MB。",style=MaterialTheme.typography.bodySmall)
                if(style.image.isNotBlank()) {
                    Row {
                        TextButton(onClick={change(style.copy(image="",zoom=1f,x=.5f,y=.5f))},enabled=!busy){Text("移除图片")}
                        TextButton(onClick={change(style.copy(zoom=1f,x=.5f,y=.5f))},enabled=!busy){Text("居中裁剪")}
                    }
                    DiySlider("放大裁剪",style.zoom,1f..3f,busy){change(style.copy(zoom=it))}
                    DiySlider("图片位置 · 左右",style.x,0f..1f,busy){change(style.copy(x=it))}
                    DiySlider("图片位置 · 上下",style.y,0f..1f,busy){change(style.copy(y=it))}
                }
                DiySlider("背景模糊",style.blur,0f..1f,busy){change(style.copy(blur=it))}
                if(style.kind!="notes") DiySlider("压暗图片 · 让文字更清楚",style.shade,0f..0.85f,busy){change(style.copy(shade=it))}
                DiySlider("背景不透明度",style.opacity,.15f..1f,busy){change(style.copy(opacity=it))}
                Text("透明区域会透出你的桌面壁纸；预览底色仅作参考。",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("写上你的个性",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(style.title,{change(style.copy(title=it.take(18)))},enabled=!busy,label={Text("卡片标题（留空使用默认）")},placeholder={Text("例如：今日打怪任务")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(style.motto,{change(style.copy(motto=it.take(24)))},enabled=!busy,label={Text("一句话 · 最多 24 字")},placeholder={Text("例如：今天也要准时到达")},singleLine=true,modifier=Modifier.fillMaxWidth())
                DiySlider("课程字号",style.fontScale,.85f..1.3f,busy){change(style.copy(fontScale=it))}
                Text("点缀颜色（照片卡 / 毛玻璃）",style=MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf(0xFFB7F2D5 to "薄荷",0xFFBEE2FF to "晴蓝",0xFFFFD1DF to "莓粉",0xFFFFE2A8 to "暖杏").forEach { (color,label) ->
                        FilterChip(style.accent==color,{if(!busy)change(style.copy(accent=color))},label={Text(label)},leadingIcon={Box(Modifier.size(10.dp).background(Color(color),RoundedCornerShape(5.dp)))})
                    }
                }
                HorizontalDivider()
                Text("我的搭配",style=MaterialTheme.typography.titleMedium)
                Text("保存后可套用到任意一种组件，最多收藏 8 套。",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={themeName="";themeDialog=true},enabled=!busy && themes.size<8,modifier=Modifier.fillMaxWidth()){Text("收藏当前搭配")}
                themes.forEachIndexed { index, theme ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        OutlinedButton(onClick={change(theme.style)},enabled=!busy,modifier=Modifier.weight(1f)){Text(theme.name,maxLines=1,overflow=TextOverflow.Ellipsis)}
                        TextButton(onClick={
                            val old=themes
                            runCatching { DiyStore.saveThemes(activity,themes.filterIndexed { i,_->i!=index }) }.onSuccess {
                                themes=DiyStore.themes(activity)
                                scope.launch { if(snack.showSnackbar("已移除收藏",actionLabel="撤销")==SnackbarResult.ActionPerformed) {
                                    runCatching { DiyStore.saveThemes(activity,old); themes=old }.onFailure {error="恢复失败，请重试"}
                                } }
                            }.onFailure {error="删除失败，请重试"}
                        },enabled=!busy){Text("删除")}
                    }
                }
                TextButton(onClick={if(compact)next=DiyStyle() else today=DiyStyle()},enabled=!busy){Text("当前组件恢复默认外观")}
                Text("xiaoyle 制作 · 0.3.0\n相同类型的桌面组件共用一套搭配。修改外观不会改变课表和提醒。",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(exit) AlertDialog(onDismissRequest={exit=false},title={Text("还有未保存的搭配")},text={Text("返回会放弃本次外观修改，已收藏的搭配仍保留。")},confirmButton={TextButton(onClick={activity.finish()}){Text("放弃修改并返回")}},dismissButton={TextButton(onClick={exit=false}){Text("继续编辑")}})
    if(themeDialog) AlertDialog(onDismissRequest={themeDialog=false},title={Text("给搭配起个名字")},text={OutlinedTextField(themeName,{themeName=it.take(16)},label={Text("搭配名称")},singleLine=true)},confirmButton={TextButton(enabled=themeName.isNotBlank(),onClick={
        runCatching { DiyStore.saveThemes(activity,themes+SavedDiy(themeName.trim(),style.copy(enabled=true))) }.onSuccess { themes=DiyStore.themes(activity);themeDialog=false }.onFailure {error="收藏失败，请重试";themeDialog=false}
    }){Text("收藏")}},dismissButton={TextButton(onClick={themeDialog=false}){Text("取消")}})
}

@Composable private fun DiySlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,busy:Boolean,onChange:(Float)->Unit) {
    Column {
        Text(label + " · " + if(range.endInclusive>1f) "${(value*100).toInt()}%" else "${(value*100).toInt()}%",style=MaterialTheme.typography.labelLarge)
        Slider(value=value,onValueChange=onChange,valueRange=range,enabled=!busy,modifier=Modifier.semantics { contentDescription=label })
    }
}

@Composable private fun DiyPreview(context:android.content.Context,style:DiyStyle,compact:Boolean,large:Boolean,courses:List<Occurrence>,onMove:(Float,Float)->Unit) {
    val move by rememberUpdatedState(onMove)
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val height=if(large) 250 else 180
    LaunchedEffect(style,large) {
        delay(60)
        failed=false
        try { bitmap=withContext(Dispatchers.Default) { if(style.enabled) DiyRenderer.render(context,style,520,if(large)420 else 300) else null } }
        catch(_:Exception) { failed=true }
    }
    val ink=if(style.enabled) Color(style.foreground) else MaterialTheme.colorScheme.onSurface
    val accent=if(style.enabled) Color(style.highlight) else MaterialTheme.colorScheme.primary
    val now=Instant.now(); val date=now.atZone(SCHOOL_ZONE).toLocalDate()
    val chosen=if(compact) courses.filter { it.endInstant>now }.take(1) else courses.filter {it.date==date}.take(if(large)2 else 1)
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
        .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume();move(amount.x/size.width,amount.y/size.height) } }) {
        bitmap?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds) }
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(style.heading(compact),color=accent,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1,modifier=Modifier.weight(1f),overflow=TextOverflow.Ellipsis)
                Text("刷新",color=accent,fontSize=12.sp)
            }
            Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if(style.enabled && style.kind=="notes") Modifier.background(Color(0xFFE6EEDC)) else Modifier).padding(vertical=4.dp,horizontal=if(style.kind=="notes" && style.enabled)6.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                if(chosen.isEmpty()) {
                    Text("08:00–09:40 · 效果示例",color=accent,fontSize=(14*style.fontScale).sp)
                    Text("课程名称",color=ink,fontWeight=FontWeight.Bold,fontSize=(18*style.fontScale).sp)
                    Text("上课教室",color=ink,fontSize=(12*style.fontScale).sp)
                } else chosen.forEach { lesson ->
                    Text("${lesson.start}–${lesson.end}",color=accent,fontSize=(14*style.fontScale).sp,maxLines=1)
                    Text(lesson.title,color=ink,fontWeight=FontWeight.Bold,fontSize=((if(compact)18 else 15)*style.fontScale).sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(lesson.locationText,color=ink,fontSize=(12*style.fontScale).sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            if(style.enabled && style.motto.isNotBlank() && large) Text(style.motto,color=ink,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("xiaoyle 制作",color=ink,fontSize=11.sp)
        }
        if(failed) Text("图片预览失败，请重新选择",Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.errorContainer),color=MaterialTheme.colorScheme.onErrorContainer)
    }
}
