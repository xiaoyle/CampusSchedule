package cn.campus.schedule

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.math.sin

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val personalization=PersonalizationStore.read(this)
        if(!personalization.launch.enabled) { openApp(); return }
        enableEdgeToEdge()
        setContent { CampusTheme(personalization.themeMode) { ChargeGate(personalization) { openApp() } } }
    }
    private fun openApp() {
        startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out)
        finish()
    }
}

@Composable private fun ChargeGate(settings: Personalization, onComplete:()->Unit) {
    val progress=remember { Animatable(0f) }
    var opening by remember { mutableStateOf(false) }
    val haptic=LocalHapticFeedback.current
    val animations=ValueAnimator.areAnimatorsEnabled()
    Box(Modifier.fillMaxSize()) {
        LaunchScene(settings.launch, animations)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=settings.launch.shade.coerceIn(0f,.75f))))
        Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp)) {
            Box(Modifier.size(174.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val glow=18.dp.toPx()*(.35f+progress.value)
                    drawCircle(Color(0xFF8FF4D0).copy(alpha=.12f+.2f*progress.value),radius=size.minDimension*.42f+glow)
                    drawArc(Color(0xFFB7F2D5),-90f,360f*progress.value,false,style=Stroke(7.dp.toPx(),cap=StrokeCap.Round))
                    if(progress.value>0f) drawArc(Color.White.copy(alpha=.75f),-90f+360f*progress.value,34f,false,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                }
                Surface(
                    color=Color(0xFF102F2A).copy(alpha=.92f), shape=CircleShape,
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF8FF4D0).copy(alpha=.75f)),
                    shadowElevation=(12+progress.value*18).dp,
                    modifier=Modifier.size(126.dp).pointerInput(Unit) {
                        detectTapGestures(onPress={
                            if(opening) return@detectTapGestures
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope {
                                var finished=false
                                val charge=launch {
                                    progress.animateTo(1f,tween(if(animations)1500 else 1100,easing=LinearEasing))
                                    finished=true; opening=true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onComplete()
                                }
                                tryAwaitRelease()
                                if(!finished) { charge.cancel(); progress.animateTo(0f,tween(if(animations)260 else 1)) }
                            }
                        })
                    }
                ) {
                    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text("SYSU",color=Color(0xFFB7F2D5),fontWeight=FontWeight.Black,fontSize=18.sp,letterSpacing=2.sp)
                        Text("COURSE CORE",color=Color.White.copy(alpha=.78f),fontSize=10.sp,letterSpacing=1.sp)
                    }
                }
            }
            Text(if(progress.value>.02f) "充能中 ${(progress.value*100).toInt()}%" else "长按芯片进入",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text("中大课表助手 · xiaoyle 制作",color=Color.White.copy(alpha=.72f),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun LaunchScene(style: LaunchStyle, animations: Boolean) {
    val transition=rememberInfiniteTransition(label="launch-scene")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(if(animations)7000 else 1,easing=LinearEasing),RepeatMode.Restart),label="phase")
    val context=LocalContext.current
    val custom=remember(style.image) { DiyStore.imageFile(context,style.image)?.takeIf{it.exists()}?.let{BitmapFactory.decodeFile(it.path)?.asImageBitmap()} }
    if(style.scene=="custom" && custom!=null) {
        Image(custom,null,Modifier.fillMaxSize().blur((style.blur*20).dp).graphicsLayer {val motion=if(animations)style.motion else 0f;scaleX=style.zoom+motion*.035f*sin(phase*6.28f);scaleY=scaleX;translationX=(style.x-.5f)*120;translationY=(style.y-.5f)*160},contentScale=ContentScale.Crop)
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        val t=if(animations) phase else .35f
        when(style.scene) {
            "sky" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0C2545),Color(0xFF347A9A),Color(0xFFEAB978))))
                val night=ZonedDateTime.now(cn.campus.core.SCHOOL_ZONE).hour !in 6..17
                drawCircle(if(night)Color(0xFFE9F1FF) else Color(0xFFFFD27A),size.minDimension*.12f,Offset(size.width*.72f,size.height*.22f))
                repeat(5) {i-> val x=((i*.27f+t*.14f)%1.25f-.12f)*size.width; drawOval(Color.White.copy(alpha=.1f),Offset(x,size.height*(.22f+i*.11f)),androidx.compose.ui.geometry.Size(size.width*.32f,size.height*.045f)) }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color(0xFF0A2E32))),topLeft=Offset(0f,size.height*.66f))
            }
            "window" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFF5C99B),Color(0xFF6D98A4),Color(0xFF183F43))))
                val sway=sin(t*6.28f)*size.width*.015f*style.motion
                drawRect(Color(0xFF173630),Offset(size.width*.12f,0f),androidx.compose.ui.geometry.Size(size.width*.035f,size.height))
                drawRect(Color(0xFF173630),Offset(size.width*.63f,0f),androidx.compose.ui.geometry.Size(size.width*.035f,size.height*.72f))
                drawRect(Color(0xFF173630),Offset(0f,size.height*.34f),androidx.compose.ui.geometry.Size(size.width*.72f,size.height*.025f))
                drawCircle(Color(0xFFE7B89D),size.minDimension*.075f,Offset(size.width*.72f+sway,size.height*.48f))
                drawOval(Color(0xFF17252D),Offset(size.width*.62f+sway,size.height*.43f),androidx.compose.ui.geometry.Size(size.width*.25f,size.height*.32f))
                drawOval(Color(0xFFEEE2D4),Offset(size.width*.55f,size.height*.56f),androidx.compose.ui.geometry.Size(size.width*.36f,size.height*.44f))
                drawPath(Path().apply{moveTo(size.width*.72f,size.height*.46f);quadraticBezierTo(size.width*(.86f+t*.02f),size.height*.48f,size.width*.94f,size.height*.55f)},Color(0xFF202B31),style=Stroke(size.width*.035f,cap=StrokeCap.Round))
            }
            else -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF86BFD1),Color(0xFFF4D4A3),Color(0xFF17433B))))
                drawCircle(Color(0xFFFFE3A6).copy(alpha=.8f),size.minDimension*.1f,Offset(size.width*.76f,size.height*.17f))
                repeat(18){i-> val x=((i*.173f+t*.035f)%1f)*size.width; val y=((i*.297f+t*.08f)%1f)*size.height*.7f; drawCircle(Color.White.copy(alpha=.16f),2.dp.toPx()+(i%3),Offset(x,y))}
                drawRect(Color(0xFF2C4B44),Offset(size.width*.15f,size.height*.73f),androidx.compose.ui.geometry.Size(size.width*.7f,size.height*.2f))
                drawOval(Color(0xFF263A36),Offset(size.width*.37f,size.height*.33f),androidx.compose.ui.geometry.Size(size.width*.26f,size.height*.45f))
                drawCircle(Color(0xFF334E47),size.minDimension*.09f,Offset(size.width*.5f,size.height*.3f))
                drawPath(Path().apply{moveTo(size.width*.5f,size.height*.34f);lineTo(size.width*.34f,size.height*.72f);lineTo(size.width*.67f,size.height*.72f);close()},Color(0xFF314841))
            }
        }
    }
}
