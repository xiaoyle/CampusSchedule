package cn.campus.schedule

import android.animation.ValueAnimator
import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import cn.campus.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

private val badgeIcons=listOf("star","book","flower","clock","moon","waves","sun","pen","check","timer","bolt","leaf","mountain","lamp","heart","flag","music","coffee","code","science","school","flight","diamond","trophy")
private fun iconGlyph(icon:String)=mapOf("star" to "★","book" to "书","flower" to "✿","clock" to "时","moon" to "☾","waves" to "浪","sun" to "☀","pen" to "笔","check" to "✓","timer" to "计","bolt" to "ϟ","leaf" to "叶","mountain" to "山","lamp" to "灯","heart" to "♥","flag" to "旗","music" to "♪","coffee" to "杯","code" to "码","science" to "研","school" to "学","flight" to "行","diamond" to "◆","trophy" to "冠")[icon]?:"★"
private val hexBadge=GenericShape{size,_->moveTo(size.width*.25f,0f);lineTo(size.width*.75f,0f);lineTo(size.width,size.height*.5f);lineTo(size.width*.75f,size.height);lineTo(size.width*.25f,size.height);lineTo(0f,size.height*.5f);close()}
private val shieldBadge=GenericShape{size,_->moveTo(size.width*.12f,0f);lineTo(size.width*.88f,0f);lineTo(size.width*.92f,size.height*.55f);quadraticBezierTo(size.width*.78f,size.height*.88f,size.width*.5f,size.height);quadraticBezierTo(size.width*.22f,size.height*.88f,size.width*.08f,size.height*.55f);close()}

@Composable fun AchievementBadge(def:AchievementDefinition,progress:AchievementProgress?,size:Dp=92.dp,showProgress:Boolean=true){
    val unlocked=progress?.unlockedAt!=null;val context=LocalContext.current;val design=def.badge
    val image=remember(design.image){DiyStore.imageFile(context,design.image)?.takeIf{it.exists()}?.let{BitmapFactory.decodeFile(it.path)?.asImageBitmap()}}
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
        Box(Modifier.size(size),contentAlignment=Alignment.Center){
            val shape=when(design.shape){BadgeShape.CIRCLE->CircleShape;BadgeShape.SHIELD->shieldBadge;BadgeShape.HEXAGON->hexBadge;BadgeShape.DIAMOND->RoundedCornerShape(18.dp)}
            val rotation=if(design.shape==BadgeShape.DIAMOND)45f else 0f
            Box(Modifier.fillMaxSize().graphicsLayer{rotationZ=rotation;alpha=if(unlocked)1f else .42f}.clip(shape).background(when(design.texture){BadgeTexture.RADIAL->Brush.radialGradient(listOf(Color(design.accent),Color(design.primary)));BadgeTexture.WAVES->Brush.linearGradient(listOf(Color(design.primary),Color(design.accent),Color(design.primary)));BadgeTexture.STARS->Brush.radialGradient(listOf(Color(design.primary).copy(.75f),Color(0xFF071A2D)));else->Brush.linearGradient(listOf(Color(design.primary),Color(design.primary).copy(.78f))) }).border(when(design.border){BadgeBorder.GOLD->4.dp;BadgeBorder.SILVER->4.dp;BadgeBorder.NONE->0.dp},when(design.border){BadgeBorder.GOLD->Color(0xFFFFD36B);BadgeBorder.SILVER->Color(0xFFDCE8F5);BadgeBorder.NONE->Color.Transparent},shape)){
                if(image!=null)Image(image,null,Modifier.fillMaxSize().graphicsLayer{scaleX=design.imageZoom;scaleY=design.imageZoom;translationX=(design.imageX-.5f)*size.toPx();translationY=(design.imageY-.5f)*size.toPx()},contentScale=ContentScale.Crop,colorFilter=if(unlocked)null else ColorFilter.tint(Color.Gray,BlendMode.Saturation))
            }
            Text(design.glyph.ifBlank{iconGlyph(design.icon)},color=Color.White,fontSize=(size.value*.34f).sp,fontWeight=FontWeight.Black,modifier=Modifier.graphicsLayer{rotationZ=-rotation},textAlign=TextAlign.Center)
            if(!unlocked)Icon(Icons.Outlined.Lock,"未解锁",tint=Color.White,modifier=Modifier.align(Alignment.BottomEnd).size(25.dp).background(Color(0xAA172D26),CircleShape).padding(4.dp))
        }
        if(showProgress){Text(def.name,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,maxLines=1);val current=progress?.current?:0;Text(if(unlocked)"已解锁" else if(def.id==AchievementEngine.MOUNTAIN_SEA)"${current}/300 分 · ${progress?.secondaryCurrent?:0}/5 项" else "$current/${def.target}",style=MaterialTheme.typography.labelSmall,color=if(unlocked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable fun AchievementGallery(data:AppData,onBack:()->Unit,onSave:(AchievementDefinition)->Unit,onDelete:(AchievementDefinition,AchievementProgress?,Boolean)->Unit,onManual:(String)->Unit,onReset:(String)->Unit,onFeature:(String)->Unit,onMoveFeatured:(String,Int)->Unit){
    var editing by remember{mutableStateOf<AchievementDefinition?>(null)};val definitions=AchievementEngine.definitions(data);val progress=data.achievementProgress.associateBy{it.achievementId}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"返回")};Column(Modifier.weight(1f)){Text("成就馆",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text("从一束初光，到山海同频",color=MaterialTheme.colorScheme.primary)};FilledTonalButton(onClick={editing=AchievementDefinition(UUID.randomUUID().toString(),"","",unlockMode=AchievementUnlockMode.AUTO,metric=AchievementMetric.TOTAL_FOCUS_MINUTES,target=60)}){Icon(Icons.Outlined.Add,null);Text("自定义")}}}
        item{Text("校园珐琅章",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("内置成就只依据真实任务与专注记录计算，解锁后永久保留。",style=MaterialTheme.typography.bodySmall)}
        if(data.featuredAchievementIds.isNotEmpty())item{Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("主页精选顺序",fontWeight=FontWeight.Bold);data.featuredAchievementIds.forEachIndexed{index,id->definitions.firstOrNull{it.id==id}?.let{def->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${index+1}. ${def.name}",Modifier.weight(1f));IconButton(onClick={onMoveFeatured(id,-1)},enabled=index>0){Icon(Icons.Outlined.KeyboardArrowUp,"上移")};IconButton(onClick={onMoveFeatured(id,1)},enabled=index<data.featuredAchievementIds.lastIndex){Icon(Icons.Outlined.KeyboardArrowDown,"下移")}}}}}}
        items(definitions,key={it.id}){def->val state=progress[def.id];OutlinedCard(onClick={if(!def.builtIn)editing=def},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(15.dp)){AchievementBadge(def,state,82.dp,false);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(def.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black);Text(def.description,style=MaterialTheme.typography.bodySmall);val value=if(def.id==AchievementEngine.MOUNTAIN_SEA)"${state?.current?:0}/300 分 · ${state?.secondaryCurrent?:0}/5 项" else "${state?.current?:0}/${def.target}";Text(if(state?.unlockedAt!=null)"已于 ${Instant.parse(state.unlockedAt).atZone(SCHOOL_ZONE).toLocalDate()} 解锁" else value,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);if(state?.unlockedAt!=null)FilterChip(selected=def.id in data.featuredAchievementIds,onClick={onFeature(def.id)},label={Text(if(def.id in data.featuredAchievementIds)"主页展示中" else "展示到主页")});if(!def.builtIn&&def.unlockMode==AchievementUnlockMode.MANUAL&&state?.unlockedAt==null)FilledTonalButton(onClick={onManual(def.id)}){Text("标记达成")}}}}}
        item{Text("自动成就会根据现有数据立即计算。最多创建50个；解锁后若要改条件，需要先重置。",style=MaterialTheme.typography.bodySmall)}
    }
    editing?.let{definition->AchievementEditor(definition,progress[definition.id],data,onDismiss={editing=null},onSave={onSave(it);editing=null},onDelete={onDelete(definition,progress[definition.id],definition.id in data.featuredAchievementIds);editing=null},onReset={onReset(definition.id)})}
}

@Composable private fun AchievementEditor(definition:AchievementDefinition,progress:AchievementProgress?,data:AppData,onDismiss:()->Unit,onSave:(AchievementDefinition)->Unit,onDelete:()->Unit,onReset:()->Unit){
    var name by remember(definition.id){mutableStateOf(definition.name)};var description by remember(definition.id){mutableStateOf(definition.description)};var mode by remember(definition.id){mutableStateOf(definition.unlockMode)};var metric by remember(definition.id){mutableStateOf(definition.metric?:AchievementMetric.TOTAL_FOCUS_MINUTES)};var target by remember(definition.id){mutableStateOf(definition.target.toString())};var courseId by remember(definition.id){mutableStateOf(definition.courseRuleId)};var design by remember(definition.id){mutableStateOf(definition.badge)};var deleteConfirm by remember{mutableStateOf(false)}
    val unlocked=progress?.unlockedAt!=null;val context=LocalContext.current;val scope=rememberCoroutineScope();var error by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{scope.launch(Dispatchers.IO){runCatching{DiyStore.importImage(context,it)}.onSuccess{name->withContext(Dispatchers.Main){design=design.copy(image=name)}}.onFailure{withContext(Dispatchers.Main){error=it.message}}}}}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(definition.name.isBlank())"设计新成就" else "编辑成就")},text={Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(11.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Column(horizontalAlignment=Alignment.CenterHorizontally){AchievementBadge(definition.copy(name=name.ifBlank{"新成就"},badge=design),AchievementProgress(definition.id,unlockedAt=Instant.now().toString()),92.dp,false);Text("点亮效果",style=MaterialTheme.typography.labelSmall)};Column(horizontalAlignment=Alignment.CenterHorizontally){AchievementBadge(definition.copy(name=name.ifBlank{"新成就"},badge=design),AchievementProgress(definition.id),92.dp,false);Text("锁定效果",style=MaterialTheme.typography.labelSmall)}}
        OutlinedTextField(name,{name=it.take(12)},label={Text("成就名称（1–12字）")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(description,{description=it.take(40)},label={Text("说明（最多40字）")},modifier=Modifier.fillMaxWidth())
        Text("解锁方式",fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Row{FilterChip(mode==AchievementUnlockMode.AUTO,{if(!unlocked)mode=AchievementUnlockMode.AUTO},{Text("自动规则")});Spacer(Modifier.width(8.dp));FilterChip(mode==AchievementUnlockMode.MANUAL,{if(!unlocked)mode=AchievementUnlockMode.MANUAL},{Text("手动确认")})}
        if(mode==AchievementUnlockMode.AUTO){var menu by remember{mutableStateOf(false)};Box(Modifier.fillMaxWidth()){OutlinedButton(onClick={if(!unlocked)menu=true},modifier=Modifier.fillMaxWidth()){Text(metricLabel(metric))};DropdownMenu(menu,{menu=false}){AchievementMetric.entries.forEach{value->DropdownMenuItem({Text(metricLabel(value))},{metric=value;menu=false})}}};OutlinedTextField(target,{if(!unlocked)target=it.filter(Char::isDigit).take(6)},label={Text("目标数值")},singleLine=true,enabled=!unlocked,modifier=Modifier.fillMaxWidth());if(metric==AchievementMetric.COURSE_FOCUS_MINUTES){var courseMenu by remember{mutableStateOf(false)};val courses=(data.schedule?.rules.orEmpty().map{it.id to it.title}+data.manualLessons.map{it.id to it.title}).distinctBy{it.first};Box(Modifier.fillMaxWidth()){OutlinedButton(onClick={if(!unlocked)courseMenu=true},modifier=Modifier.fillMaxWidth()){Text(courseId?.let{id->courses.firstOrNull{it.first==id}?.second}?:"选择课程")};DropdownMenu(courseMenu,{courseMenu=false}){courses.forEach{item->DropdownMenuItem({Text(item.second)},{courseId=item.first;courseMenu=false})}}}}}
        HorizontalDivider();Text("徽章外形",fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){BadgeShape.entries.forEach{value->FilterChip(design.shape==value,{design=design.copy(shape=value)},{Text(when(value){BadgeShape.CIRCLE->"圆";BadgeShape.SHIELD->"盾";BadgeShape.HEXAGON->"六边";BadgeShape.DIAMOND->"菱形"})})}}
        Text("配色",fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf(0xFF176B52L to 0xFFFFC75FL,0xFF126B91L to 0xFF9DDBF4L,0xFFC95454L to 0xFFFFD1A6L,0xFF6A4C93L to 0xFFE7C6FFL).forEach{pair->Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(pair.first),Color(pair.second)))).clickable{design=design.copy(primary=pair.first,accent=pair.second)})}}
        Text("纹理与边框",fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){BadgeTexture.entries.forEach{value->FilterChip(design.texture==value,{design=design.copy(texture=value)},{Text(when(value){BadgeTexture.SOLID->"纯色";BadgeTexture.RADIAL->"放射";BadgeTexture.STARS->"星轨";BadgeTexture.WAVES->"浪纹";BadgeTexture.GRID->"网格"})})}};Row{BadgeBorder.entries.forEach{value->RadioButton(design.border==value,{design=design.copy(border=value)})}}
        Text("图标（24种）",fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());badgeIcons.chunked(6).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){row.forEach{icon->FilterChip(design.icon==icon,{design=design.copy(icon=icon,glyph="")},{Text(iconGlyph(icon))})}}}
        OutlinedTextField(design.glyph,{design=design.copy(glyph=it.take(2))},label={Text("自定义短字：1个汉字或2个字母")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedButton(onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Image,null);Text("导入自己的徽章图片")}
        if(design.image.isNotBlank()){Text("图片缩放 ${(design.imageZoom*100).toInt()}%",style=MaterialTheme.typography.labelSmall);Slider(design.imageZoom,{design=design.copy(imageZoom=it)},valueRange=1f..3f);Text("横向位置",style=MaterialTheme.typography.labelSmall);Slider(design.imageX,{design=design.copy(imageX=it)});Text("纵向位置",style=MaterialTheme.typography.labelSmall);Slider(design.imageY,{design=design.copy(imageY=it)})}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(unlocked)Text("解锁条件已锁定。重置后才能修改，徽章与文字仍可编辑。",style=MaterialTheme.typography.bodySmall)
        if(unlocked)TextButton(onClick=onReset){Text("重置成就",color=MaterialTheme.colorScheme.error)};if(definition.name.isNotBlank())TextButton(onClick={deleteConfirm=true}){Text("删除自定义成就",color=MaterialTheme.colorScheme.error)}
    }},confirmButton={Button(onClick={val value=target.toLongOrNull()?:0;if(name.trim().isEmpty())error="请填写成就名称" else if(mode==AchievementUnlockMode.AUTO&&value<=0)error="目标数值须大于0" else if(mode==AchievementUnlockMode.AUTO&&metric==AchievementMetric.COURSE_FOCUS_MINUTES&&courseId==null)error="请选择课程" else onSave(definition.copy(name=name.trim(),description=description.trim(),unlockMode=mode,metric=if(mode==AchievementUnlockMode.AUTO)metric else null,target=if(mode==AchievementUnlockMode.AUTO)value else 1,courseRuleId=if(metric==AchievementMetric.COURSE_FOCUS_MINUTES)courseId else null,badge=design))}){Text("保存")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
    if(deleteConfirm)AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("删除这个成就？")},text={Text("成就进度和主页展示也会删除。")},confirmButton={Button(onClick=onDelete){Text("删除")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("取消")}})
}

private fun metricLabel(value:AchievementMetric)=when(value){AchievementMetric.TOTAL_FOCUS_MINUTES->"累计专注分钟";AchievementMetric.SINGLE_FOCUS_MINUTES->"单次专注分钟";AchievementMetric.FOCUS_STREAK_DAYS->"连续专注天数";AchievementMetric.COMPLETED_TASKS->"完成任务数量";AchievementMetric.ON_TIME_TASKS->"按时完成任务";AchievementMetric.WEEKLY_FOCUS_MINUTES->"本周专注分钟";AchievementMetric.COURSE_FOCUS_MINUTES->"指定课程专注分钟"}

@Composable fun AchievementCelebration(def:AchievementDefinition,onDismiss:()->Unit){
    val animations=ValueAnimator.areAnimatorsEnabled();var entered by remember{mutableStateOf(!animations)};LaunchedEffect(Unit){entered=true};val scale by animateFloatAsState(if(entered)1f else .35f,tween(if(animations)1200 else 0,easing=FastOutSlowInEasing),label="badge-cast");val rotation by animateFloatAsState(if(entered)0f else -22f,tween(if(animations)1200 else 0,easing=FastOutSlowInEasing),label="badge-turn")
    AlertDialog(onDismissRequest=onDismiss,title={Text("徽章铸成",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.graphicsLayer{scaleX=scale;scaleY=scale;rotationZ=rotation}){AchievementBadge(def,AchievementProgress(def.id,def.target,unlockedAt=Instant.now().toString()),150.dp,false)};Text(def.name,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(def.description,textAlign=TextAlign.Center)}},confirmButton={Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth()){Text("收下徽章")}})
}

@Composable fun MyHub(context:Context,data:AppData,profile:ProfileData,onReview:()->Unit,onAchievements:()->Unit,onSettings:()->Unit,onProfile:()->Unit,onMessage:(String)->Unit){
    val progress=data.achievementProgress.associateBy{it.achievementId};val defs=AchievementEngine.definitions(data).associateBy{it.id};val featured=data.featuredAchievementIds.mapNotNull(defs::get)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Image(AvatarRenderer.render(context,profile,128).asImageBitmap(),null,Modifier.size(72.dp));Column{Text(profile.nickname.ifBlank{"个人中心"},style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black);Text(profile.greeting,color=MaterialTheme.colorScheme.primary)}}}
        if(featured.isNotEmpty())item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(16.dp)){Text("我的精选徽章",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){featured.forEach{AchievementBadge(it,progress[it.id],74.dp,false)}}}}}
        item{HubEntry(Icons.Outlined.Insights,"学习复盘","专注时间、任务完成与课程投入",onReview);HubEntry(Icons.Outlined.WorkspacePremium,"成就馆","查看五枚校园徽章并设计自己的成就",onAchievements);HubEntry(Icons.Outlined.Tune,"课表与提醒","导入、桌面组件、闹铃与后台检查",onSettings);HubEntry(Icons.Outlined.Palette,"个人资料与个性化","头像、主题、启动动画和组件DIY",onProfile)}
        item{OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){Text("关于作者",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text("xiaoyle · 集成电路学院",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);AuthorLink("GitHub主页","https://github.com/xiaoyle",context,onMessage);AuthorLink("中大课表项目网站","https://xiaoyle.github.io/CampusSchedule/",context,onMessage);AuthorLink("GitHub Issues反馈","https://github.com/xiaoyle/CampusSchedule/issues",context,onMessage);ListItem(headlineContent={Text("个人博客")},supportingContent={Text("筹备中")},leadingContent={Icon(Icons.Outlined.Article,null)});ContactRow("微信号","abc135235435",false,context,onMessage);ContactRow("手机号","13726492826",true,context,onMessage)}}}
        item{Text("xiaoyle 制作 · 非学校官方应用 · ${appVersionName(context)}",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun HubEntry(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,body:String,onClick:()->Unit){ListItem(headlineContent={Text(title,fontWeight=FontWeight.Bold)},supportingContent={Text(body)},leadingContent={Icon(icon,null)},trailingContent={Icon(Icons.Outlined.ChevronRight,null)},modifier=Modifier.clickable(onClick=onClick))}
@Composable private fun AuthorLink(title:String,url:String,context:Context,onMessage:(String)->Unit){ListItem(headlineContent={Text(title)},supportingContent={Text(url,maxLines=1)},leadingContent={Icon(Icons.Outlined.OpenInNew,null)},modifier=Modifier.clickable{runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{onMessage("无法打开链接，请检查浏览器")}})}
@Composable private fun ContactRow(title:String,value:String,phone:Boolean,context:Context,onMessage:(String)->Unit){ListItem(headlineContent={Text(title)},supportingContent={Text(value)},leadingContent={Icon(if(phone)Icons.Outlined.Phone else Icons.Outlined.ContentCopy,null)},trailingContent={Row{IconButton(onClick={val clipboard=context.getSystemService(android.content.ClipboardManager::class.java);clipboard.setPrimaryClip(ClipData.newPlainText(title,value));onMessage("已复制$title")}){Icon(Icons.Outlined.ContentCopy,"复制")};if(phone)IconButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$value")))}.onFailure{onMessage("无法打开拨号盘")}}){Icon(Icons.Outlined.Phone,"拨号")}}})}
