package cn.campus.schedule

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var launchPreview by remember { mutableStateOf(false) }
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::importAvatar) }
    val backgroundPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::importLaunchImage) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("个人中心",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Text("把课表助手调成你的样子",color=MaterialTheme.colorScheme.primary) }
        item { ProfileBanner(state,vm) }
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
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(
                        Triple("core","山海芯核",R.drawable.launch_mountain_sea_thumb),
                        Triple("orbit","星轨穹顶",R.drawable.launch_orbital_dome_thumb),
                        Triple("kapok","木棉矩阵",R.drawable.launch_kapok_matrix_thumb)
                    ).forEach { (key,label,image) ->
                        OutlinedCard(
                            onClick={vm.save(state.copy(launch=state.launch.copy(scene=key)))},
                            border=BorderStroke(if(normalizedLaunchScene(state.launch.scene)==key)2.dp else 1.dp,if(normalizedLaunchScene(state.launch.scene)==key)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier=Modifier.width(112.dp)
                        ) {Column(horizontalAlignment=Alignment.CenterHorizontally){Image(painterResource(image),null,Modifier.fillMaxWidth().height(148.dp),contentScale=ContentScale.Crop);Text(label,Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)}}
                    }
                }
                RadioChoice(normalizedLaunchScene(state.launch.scene)=="custom","我的照片") { if(state.launch.image.isBlank()) backgroundPicker.launch("image/*") else vm.save(state.copy(launch=state.launch.copy(scene="custom"))) }
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
                HorizontalDivider()
                Text("未来芯核外观",fontWeight=FontWeight.Medium)
                Text("选择预设，或输入自己的芯核与光环颜色。",style=MaterialTheme.typography.bodySmall)
                LaunchCoreEditor(state,vm)
                FilledTonalButton(onClick={launchPreview=true},modifier=Modifier.fillMaxWidth()){Text("预览启动效果")}
                Text("桌面组件显示对应静态海报；普通组件受安卓系统限制，不能稳定播放视频或实况照片。",style=MaterialTheme.typography.bodySmall)
            }
        }
        item { Text("NetID、学号、宿舍、头像和背景仅存放在本机，不会写入网页登录、课表文件或排查信息。",style=MaterialTheme.typography.bodySmall); Text("xiaoyle 制作 · "+appVersionName(activity),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold) }
    }
    if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text("清除个人资料？")},text={Text("将删除昵称、问候语、NetID、学号、宿舍和头像，不影响课表、提醒与主题。")},confirmButton={Button(onClick={clearConfirm=false;vm.clearProfile()}){Text("清除")}},dismissButton={TextButton(onClick={clearConfirm=false}){Text("取消")}})
    if(launchPreview) Dialog(onDismissRequest={launchPreview=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Box(Modifier.fillMaxSize()) {
            ChargeGate(state){launchPreview=false}
            FilledTonalButton(onClick={launchPreview=false},modifier=Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)){Text("关闭预览")}
        }
    }
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

@Composable private fun ProfileBanner(state:Personalization,vm:PersonalizationViewModel) {
    val now=ZonedDateTime.now(cn.campus.core.SCHOOL_ZONE)
    var menu by remember {mutableStateOf(false)}
    val styles=linkedMapOf(
        "anime" to ("动漫二次元" to listOf(R.drawable.banner_anime_1,R.drawable.banner_anime_2,R.drawable.banner_anime_3)),
        "apocalypse" to ("末世绝境" to listOf(R.drawable.banner_apocalypse_1,R.drawable.banner_apocalypse_2,R.drawable.banner_apocalypse_3)),
        "cyber" to ("科技赛博朋克" to listOf(R.drawable.banner_cyber_1,R.drawable.banner_cyber_2,R.drawable.banner_cyber_3)),
        "warm" to ("温馨风景" to listOf(R.drawable.banner_warm_1,R.drawable.banner_warm_2,R.drawable.banner_warm_3))
    )
    val selected=state.profileBannerStyle.takeIf{it in styles}?:"warm"
    Crossfade(targetState=selected,animationSpec=tween(180),label="profile-banner") { styleKey->
        val (label,images)=styles.getValue(styleKey)
        val pagerState=rememberPagerState(initialPage=(now.dayOfYear-1)%3,pageCount={3})
        Box(Modifier.fillMaxWidth().height(204.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.secondaryContainer)))) {
            HorizontalPager(state=pagerState,modifier=Modifier.fillMaxSize()) { page->
                Image(painterResource(images[page]),"$label，第${page+1}张背景",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            }
            Surface(color=Color.Black.copy(alpha=.42f),shape=RoundedCornerShape(999.dp),modifier=Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                IconButton(onClick={menu=true},modifier=Modifier.size(48.dp).semantics{contentDescription="更换轮播图风格"}) {Icon(Icons.Outlined.Palette,null,tint=Color.White)}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    styles.forEach{(key,pair)->DropdownMenuItem(text={Text(pair.first)},leadingIcon={if(key==selected)Text("✓",color=MaterialTheme.colorScheme.primary)},onClick={menu=false;vm.save(state.copy(profileBannerStyle=key),"轮播风格已切换")})}
                }
            }
            Surface(color=Color.Black.copy(alpha=.34f),shape=RoundedCornerShape(999.dp),modifier=Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    images.indices.forEach { index->Box(Modifier.size(if(index==pagerState.currentPage)15.dp else 6.dp,6.dp).background(Color.White.copy(alpha=if(index==pagerState.currentPage)1f else .52f),RoundedCornerShape(999.dp)))}
                }
            }
        }
    }
}

private fun colorHex(value:Long)="#"+(value and 0xFFFFFF).toString(16).uppercase().padStart(6,'0')
private fun parseColorHex(value:String):Long?=value.trim().takeIf{it.matches(Regex("#[0-9A-Fa-f]{6}"))}?.drop(1)?.toLongOrNull(16)?.or(0xFF000000)

@Composable private fun LaunchCoreEditor(state:Personalization,vm:PersonalizationViewModel) {
    var coreText by remember(state.launch.coreColor){mutableStateOf(colorHex(state.launch.coreColor))}
    var glowText by remember(state.launch.glowColor){mutableStateOf(colorHex(state.launch.glowColor))}
    var opacity by remember(state.launch.coreOpacity){mutableFloatStateOf(state.launch.coreOpacity)}
    var intensity by remember(state.launch.glowIntensity){mutableFloatStateOf(state.launch.glowIntensity)}
    val presets=listOf(
        "山海薄荷" to (0xFF071A1B to 0xFF8FF4D0),"珠海晴蓝" to (0xFF06263B to 0xFF74D8FF),
        "星轨银紫" to (0xFF171529 to 0xFFC9C4FF),"木棉暖金" to (0xFF35140D to 0xFFFFC46B),"霓虹玫红" to (0xFF2A0B29 to 0xFFFF69D7)
    )
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {presets.forEach{(name,colors)->FilterChip(selected=state.launch.coreColor==colors.first&&state.launch.glowColor==colors.second,onClick={coreText=colorHex(colors.first);glowText=colorHex(colors.second);vm.save(state.copy(launch=state.launch.copy(coreColor=colors.first,glowColor=colors.second)),"芯核配色已切换")},label={Text(name)})}}
        OutlinedTextField(coreText,{value->coreText=value.take(7);parseColorHex(coreText)?.let{vm.save(state.copy(launch=state.launch.copy(coreColor=it))) }},label={Text("芯核主色")},supportingText={if(parseColorHex(coreText)==null)Text("颜色格式不正确，请输入 #RRGGBB") else Text("例如 #071A1B")},isError=parseColorHex(coreText)==null,singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(glowText,{value->glowText=value.take(7);parseColorHex(glowText)?.let{vm.save(state.copy(launch=state.launch.copy(glowColor=it))) }},label={Text("光环颜色")},supportingText={if(parseColorHex(glowText)==null)Text("颜色格式不正确，请输入 #RRGGBB") else Text("例如 #8FF4D0")},isError=parseColorHex(glowText)==null,singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("芯核透明度 ${(opacity*100).toInt()}%",style=MaterialTheme.typography.labelMedium);Slider(opacity,{opacity=it},onValueChangeFinished={vm.save(state.copy(launch=state.launch.copy(coreOpacity=opacity)),"芯核透明度已保存")},valueRange=.25f..1f)
        Text("光效强度 ${(intensity*100).toInt()}%",style=MaterialTheme.typography.labelMedium);Slider(intensity,{intensity=it},onValueChangeFinished={vm.save(state.copy(launch=state.launch.copy(glowIntensity=intensity)),"光效强度已保存")},valueRange=0f..1.5f)
        TextButton(onClick={val value=LaunchStyle(enabled=state.launch.enabled,scene=state.launch.scene,image=state.launch.image,zoom=state.launch.zoom,x=state.launch.x,y=state.launch.y,blur=state.launch.blur,shade=state.launch.shade,motion=state.launch.motion);coreText=colorHex(value.coreColor);glowText=colorHex(value.glowColor);opacity=value.coreOpacity;intensity=value.glowIntensity;vm.save(state.copy(launch=value),"芯核外观已恢复默认")}){Text("恢复芯核默认外观")}
    }
}
