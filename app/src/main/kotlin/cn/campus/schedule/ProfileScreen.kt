package cn.campus.schedule

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.campus.core.ScheduleEngine
import java.time.*

@Composable fun ProfileScreen(activity: ComponentActivity, data:cn.campus.core.AppData, state: Personalization, vm: PersonalizationViewModel, onGoal:(Int)->Unit) {
    val profile=state.profile
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var greeting by remember(profile.greeting) { mutableStateOf(profile.greeting) }
    var netId by remember(profile.netId) { mutableStateOf(profile.netId) }
    var studentNumber by remember(profile.studentNumber) { mutableStateOf(profile.studentNumber) }
    var dorm by remember(profile.dorm) { mutableStateOf(profile.dorm) }
    var clearConfirm by remember { mutableStateOf(false) }
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::importAvatar) }
    val backgroundPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::importLaunchImage) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("个人中心",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Text("把课表助手调成你的样子",color=MaterialTheme.colorScheme.primary) }
        item { SeasonalBanner() }
        item { LearningOverview(data,onGoal) }
        item {
            ElevatedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("头像与资料",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        val bitmap=remember(profile) { AvatarRenderer.render(activity,profile,144).asImageBitmap() }
                        Image(bitmap,null,Modifier.size(84.dp),contentScale=ContentScale.Fit)
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick={avatarPicker.launch("image/*")}) { Text("选择头像") }
                            Text("图片会去除定位等元数据，仅保存在本机",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(profile.avatar.isNotBlank()) {
                        Text("头像缩放 ${(profile.avatarZoom*100).toInt()}%",style=MaterialTheme.typography.labelMedium)
                        Slider(profile.avatarZoom,{vm.save(state.copy(profile=profile.copy(avatarZoom=it)))},valueRange=1f..3f)
                        Text("横向位置",style=MaterialTheme.typography.labelMedium)
                        Slider(profile.avatarX,{vm.save(state.copy(profile=profile.copy(avatarX=it)))})
                        Text("纵向位置",style=MaterialTheme.typography.labelMedium)
                        Slider(profile.avatarY,{vm.save(state.copy(profile=profile.copy(avatarY=it)))})
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=profile.avatarShape=="circle",onClick={vm.save(state.copy(profile=profile.copy(avatarShape="circle")))},label={Text("圆形")})
                        FilterChip(selected=profile.avatarShape=="rounded",onClick={vm.save(state.copy(profile=profile.copy(avatarShape="rounded")))},label={Text("圆角方形")})
                    }
                    Text("头像边框",style=MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        listOf(0xFFB7F2D5,0xFF9DDBF4,0xFFFFCF8A,0xFFFFD1DF).forEach {value->Box(Modifier.size(30.dp).background(Color(value),RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).then(if(profile.avatarBorder==value)Modifier.padding(6.dp).background(Color.White,RoundedCornerShape(6.dp)) else Modifier).clickable{vm.save(state.copy(profile=profile.copy(avatarBorder=value)))})}
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("显示在桌面组件"); Text("头像位于右下角",style=MaterialTheme.typography.bodySmall) }
                        Switch(profile.showAvatarOnWidgets,{vm.save(state.copy(profile=profile.copy(showAvatarOnWidgets=it)),"组件头像设置已保存")})
                    }
                    HorizontalDivider()
                    OutlinedTextField(nickname,{nickname=it.take(20)},label={Text("昵称（可选）")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(greeting,{greeting=it.take(30)},label={Text("今日问候语")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(netId,{netId=it.take(40)},label={Text("NetID（可选）")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(studentNumber,{studentNumber=it.take(30)},label={Text("学号（可选）")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(dorm,{dorm=it.take(40)},label={Text("宿舍楼（可选）")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(onClick={vm.save(state.copy(profile=profile.copy(nickname=nickname.trim(),greeting=greeting.trim().ifBlank{"今天，去上课"},netId=netId.trim(),studentNumber=studentNumber.trim(),dorm=dorm.trim())),"个人资料已保存")},modifier=Modifier.fillMaxWidth()) { Text("保存个人资料") }
                    TextButton(onClick={clearConfirm=true},modifier=Modifier.align(Alignment.End)) { Text("清除个人资料",color=MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            SettingsCard("校园配色") {
                Text("应用、默认组件和启动芯片会使用所选配色。")
                ThemeChoice("kangle","康乐园绿",state,vm)
                ThemeChoice("zhuhai","珠海海蓝",state,vm)
                ThemeChoice("seasonal","随四季自动切换",state,vm)
            }
        }
        item {
            SettingsCard("启动充能") {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("长按芯片进入"); Text("组件和通知会直接进入课程",style=MaterialTheme.typography.bodySmall) }
                    Switch(state.launch.enabled,{vm.save(state.copy(launch=state.launch.copy(enabled=it)),"启动效果设置已保存")})
                }
                Text("启动场景",fontWeight=FontWeight.Medium)
                listOf("statue" to "仰望天空","sky" to "日月悬空","window" to "窗边微风").forEach { (key,label) ->
                    RadioChoice(state.launch.scene==key,label) { vm.save(state.copy(launch=state.launch.copy(scene=key))) }
                }
                RadioChoice(state.launch.scene=="custom","我的照片") { if(state.launch.image.isBlank()) backgroundPicker.launch("image/*") else vm.save(state.copy(launch=state.launch.copy(scene="custom"))) }
                OutlinedButton(onClick={backgroundPicker.launch("image/*")},modifier=Modifier.fillMaxWidth()) { Text("选择自己的启动背景") }
                if(state.launch.scene=="custom") {
                    Text("背景缩放 ${(state.launch.zoom*100).toInt()}%",style=MaterialTheme.typography.labelMedium)
                    Slider(state.launch.zoom,{vm.save(state.copy(launch=state.launch.copy(zoom=it)))},valueRange=1f..2f)
                    Text("横向位置",style=MaterialTheme.typography.labelMedium)
                    Slider(state.launch.x,{vm.save(state.copy(launch=state.launch.copy(x=it)))})
                    Text("纵向位置",style=MaterialTheme.typography.labelMedium)
                    Slider(state.launch.y,{vm.save(state.copy(launch=state.launch.copy(y=it)))})
                    Text("背景模糊",style=MaterialTheme.typography.labelMedium)
                    Slider(state.launch.blur,{vm.save(state.copy(launch=state.launch.copy(blur=it)))})
                }
                Text("背景明暗",style=MaterialTheme.typography.labelMedium)
                Slider(state.launch.shade,{vm.save(state.copy(launch=state.launch.copy(shade=it)))},valueRange=0f..0.7f)
                Text("动态强度",style=MaterialTheme.typography.labelMedium)
                Slider(state.launch.motion,{vm.save(state.copy(launch=state.launch.copy(motion=it)))},valueRange=0f..1f)
                Text("桌面组件显示对应静态海报；普通组件受安卓系统限制，不能稳定播放视频或实况照片。",style=MaterialTheme.typography.bodySmall)
            }
        }
        item { Text("NetID、学号、宿舍、头像和背景仅存放在本机，不会写入网页登录、课表文件或排查信息。",style=MaterialTheme.typography.bodySmall); Text("xiaoyle 制作 · "+appVersionName(activity),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold) }
    }
    if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text("清除个人资料？")},text={Text("将删除昵称、问候语、NetID、学号、宿舍和头像，不影响课表、提醒与主题。")},confirmButton={Button(onClick={clearConfirm=false;vm.clearProfile()}){Text("清除")}},dismissButton={TextButton(onClick={clearConfirm=false}){Text("取消")}})
}

@Composable private fun LearningOverview(data:cn.campus.core.AppData,onGoal:(Int)->Unit) {
    val now=ZonedDateTime.now(cn.campus.core.SCHOOL_ZONE)
    val today=now.toLocalDate()
    val monday=today.minusDays((today.dayOfWeek.value-1).toLong())
    val sunday=monday.plusDays(6)
    val weeklyCourses=remember(data,today) {ScheduleEngine.occurrences(data).count { !it.cancelled && !it.date.isBefore(monday) && !it.date.isAfter(sunday) }}
    val taskItems=remember(data,today){cn.campus.core.StudyTaskEngine.occurrences(data,today.minusDays(30),today.plusDays(60))}
    val pending=taskItems.count {it.completedAt==null}
    val completedDates=remember(data){data.studyTasks.flatMap{task->listOfNotNull(task.completedAt)+task.instanceStates.mapNotNull{it.completedAt}}.mapNotNull{runCatching{Instant.parse(it).atZone(cn.campus.core.SCHOOL_ZONE).toLocalDate()}.getOrNull()}.toSet()}
    val completed=taskItems.count{item->item.completedAt?.let{runCatching{Instant.parse(it).atZone(cn.campus.core.SCHOOL_ZONE).toLocalDate()}.getOrNull()}?.let{!it.isBefore(monday)&&!it.isAfter(sunday)}==true}
    val streak=remember(completedDates,today){var day=if(today in completedDates)today else today.minusDays(1);var value=0;while(day in completedDates){value++;day=day.minusDays(1)};value}
    var goal by remember(data.learningGoal.weeklyTarget){mutableFloatStateOf(data.learningGoal.weeklyTarget.toFloat())}
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("学习概览",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround) {
                listOf(weeklyCourses.toString() to "本周课程",pending.toString() to "未完成",streak.toString() to "连续进展").forEach { (value,label)->
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)
                        Text(label,style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text("本周目标 · $completed/${goal.toInt()} 项",style=MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress={(completed/goal).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            Slider(value=goal,onValueChange={goal=it},onValueChangeFinished={onGoal(goal.toInt())},valueRange=1f..30f,steps=28)
        }
    }
}

@Composable private fun SettingsCard(title:String,content:@Composable ColumnScope.()->Unit) {
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); content() } }
}
@Composable private fun ThemeChoice(key:String,label:String,state:Personalization,vm:PersonalizationViewModel) = RadioChoice(state.themeMode==key,label) {vm.save(state.copy(themeMode=key),"配色已切换")}
@Composable private fun RadioChoice(selected:Boolean,label:String,onClick:()->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { RadioButton(selected,onClick); Text(label) } }

@Composable private fun SeasonalBanner() {
    val now=ZonedDateTime.now(cn.campus.core.SCHOOL_ZONE)
    val month=now.monthValue
    val (title,images)=when(month) {
        1->"考试周" to listOf(R.drawable.season_exam_1,R.drawable.season_exam_2,R.drawable.season_exam_3)
        in 2..4->"春季开学" to listOf(R.drawable.season_spring_1,R.drawable.season_spring_2,R.drawable.season_spring_3)
        in 5..7->"毕业季" to listOf(R.drawable.season_graduation_1,R.drawable.season_graduation_2,R.drawable.season_graduation_3)
        in 8..10->"秋季开学" to listOf(R.drawable.season_autumn_1,R.drawable.season_autumn_2,R.drawable.season_autumn_3)
        else->"备考季" to listOf(R.drawable.season_prep_1,R.drawable.season_prep_2,R.drawable.season_prep_3)
    }
    val quote=DailyQuotes.forDate(now.toLocalDate())
    val pagerState=rememberPagerState(initialPage=(now.dayOfYear-1)%images.size,pageCount={images.size})
    Box(Modifier.fillMaxWidth().height(204.dp).clip(RoundedCornerShape(26.dp))) {
        HorizontalPager(state=pagerState,modifier=Modifier.fillMaxSize()) { page->
            Box(Modifier.fillMaxSize().semantics { contentDescription=title+"，第"+(page+1)+"张校园照片。每日一句："+quote }) {
                Image(painterResource(images[page]),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.16f),Color.Black.copy(alpha=.82f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(start=20.dp,end=20.dp,bottom=20.dp)) {
                    Text(title,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black)
                    Text(quote,color=Color.White.copy(alpha=.94f),style=MaterialTheme.typography.bodyLarge,maxLines=1)
                }
            }
        }
        Surface(color=Color.Black.copy(alpha=.38f),shape=RoundedCornerShape(999.dp),modifier=Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                images.indices.forEach { index->
                    Box(Modifier.size(if(index==pagerState.currentPage) 15.dp else 6.dp,6.dp).background(Color.White.copy(alpha=if(index==pagerState.currentPage) 1f else .52f),RoundedCornerShape(999.dp)))
                }
            }
        }
    }
}
