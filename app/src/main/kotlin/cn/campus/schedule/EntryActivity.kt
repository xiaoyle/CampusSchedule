package cn.campus.schedule

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
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

private val FutureHexagon=GenericShape {size,_->
    moveTo(size.width*.5f,0f);lineTo(size.width*.94f,size.height*.25f);lineTo(size.width*.94f,size.height*.75f)
    lineTo(size.width*.5f,size.height);lineTo(size.width*.06f,size.height*.75f);lineTo(size.width*.06f,size.height*.25f);close()
}

internal fun normalizedLaunchScene(scene:String)=when(scene) {
    "statue","core","mountain_sea"->"core"
    "sky","orbit","orbital_dome"->"orbit"
    "window","kapok","kapok_matrix"->"kapok"
    "custom"->"custom"
    else->"core"
}

@Composable internal fun ChargeGate(settings: Personalization, onComplete:()->Unit) {
    val progress=remember { Animatable(0f) }
    var opening by remember { mutableStateOf(false) }
    val haptic=LocalHapticFeedback.current
    val animations=ValueAnimator.areAnimatorsEnabled()
    val scene=normalizedLaunchScene(settings.launch.scene)
    val accent=Color(settings.launch.glowColor)
    val coreColor=Color(settings.launch.coreColor).copy(alpha=settings.launch.coreOpacity.coerceIn(.25f,1f))
    val glow=settings.launch.glowIntensity.coerceIn(0f,1.5f)
    val coreText=if(Color(settings.launch.coreColor).luminance()>.46f) Color(0xFF071A1B) else Color.White
    Box(Modifier.fillMaxSize()) {
        LaunchScene(settings.launch,animations)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.06f),Color.Black.copy(alpha=settings.launch.shade.coerceIn(.12f,.68f)),Color.Black.copy(alpha=.66f)))))
        Column(Modifier.align(Alignment.Center).padding(horizontal=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(190.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val p=progress.value
                    drawCircle(accent.copy(alpha=((.06f+.18f*p)*glow).coerceIn(0f,1f)),radius=size.minDimension*(.40f+.10f*p))
                    drawCircle(accent.copy(alpha=((.05f+.11f*p)*glow).coerceIn(0f,1f)),radius=size.minDimension*(.48f+.05f*p))
                    drawArc(accent.copy(alpha=((.5f+.5f*p)*glow).coerceIn(0f,1f)),-90f,360f*p,false,style=Stroke(7.dp.toPx()))
                    drawArc(Color.White.copy(alpha=.68f),90f,-360f*p,false,style=Stroke(2.dp.toPx()))
                    repeat(6){i->
                        val angle=Math.toRadians((i*60-90).toDouble());val inner=size.minDimension*.34f;val outer=size.minDimension*(.45f+.04f*p)
                        drawLine(accent.copy(alpha=((.14f+.44f*p)*glow).coerceIn(0f,1f)),center+Offset(cos(angle).toFloat()*inner,sin(angle).toFloat()*inner),center+Offset(cos(angle).toFloat()*outer,sin(angle).toFloat()*outer),1.5.dp.toPx())
                    }
                }
                Surface(
                    color=coreColor,shape=FutureHexagon,
                    border=BorderStroke(1.5.dp,accent.copy(alpha=.85f)),shadowElevation=(14+progress.value*22).dp,
                    modifier=Modifier.size(134.dp).pointerInput(Unit) {
                        detectTapGestures(onPress={
                            if(opening)return@detectTapGestures
                            coroutineScope {
                                var finished=false
                                val charge=launch {
                                    progress.animateTo(1f,tween(if(animations)1500 else 1100,easing=FastOutSlowInEasing))
                                    finished=true;opening=true;haptic.performHapticFeedback(HapticFeedbackType.LongPress);onComplete()
                                }
                                tryAwaitRelease()
                                if(!finished){charge.cancel();progress.animateTo(0f,tween(if(animations)300 else 1))}
                            }
                        })
                    }
                ) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                            val p=progress.value
                            drawLine(accent.copy(alpha=.35f+.55f*p),center-Offset(size.width*.32f,0f),center+Offset(size.width*.32f,0f),2.dp.toPx())
                            drawLine(accent.copy(alpha=.35f+.55f*p),center-Offset(0f,size.height*.32f),center+Offset(0f,size.height*.32f),2.dp.toPx())
                            drawCircle(accent.copy(alpha=.55f+.4f*p),7.dp.toPx()+p*4.dp.toPx(),center)
                        }
                        Column(horizontalAlignment=Alignment.CenterHorizontally){Text("SYSU",color=coreText,fontWeight=FontWeight.Black,fontSize=18.sp,letterSpacing=2.sp);Text("FUTURE CORE",color=coreText.copy(alpha=.78f),fontSize=9.sp,letterSpacing=1.sp)}
                    }
                }
            }
            Text(if(progress.value>.02f)"芯核充能 ${(progress.value*100).toInt()}%" else "长按未来芯核进入",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text("中大课表助手 · xiaoyle 制作",color=Color.White.copy(alpha=.74f),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun LaunchScene(style: LaunchStyle, animations: Boolean) {
    val transition=rememberInfiniteTransition(label="launch-scene")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(if(animations)9000 else 1,easing=LinearEasing),RepeatMode.Restart),label="phase")
    val context=LocalContext.current
    val custom=remember(style.image) { DiyStore.imageFile(context,style.image)?.takeIf{it.exists()}?.let{BitmapFactory.decodeFile(it.path)?.asImageBitmap()} }
    if(normalizedLaunchScene(style.scene)=="custom"&&custom!=null) {
        Image(custom,null,Modifier.fillMaxSize().blur((style.blur*20).dp).graphicsLayer {val m=if(animations)style.motion else 0f;scaleX=style.zoom+m*.018f*sin(phase*6.28f);scaleY=scaleX;translationX=(style.x-.5f)*120;translationY=(style.y-.5f)*160},contentScale=ContentScale.Crop)
        return
    }
    val scene=normalizedLaunchScene(style.scene)
    val resource=when(scene){"orbit"->R.drawable.launch_orbital_dome;"kapok"->R.drawable.launch_kapok_matrix;else->R.drawable.launch_mountain_sea}
    val m=if(animations)style.motion.coerceIn(0f,1f) else 0f
    Image(painterResource(resource),null,Modifier.fillMaxSize().graphicsLayer{val drift=sin(phase*6.28f)*m;scaleX=1.012f+m*.008f;scaleY=scaleX;translationX=drift*8.dp.toPx();translationY=cos(phase*6.28f)*m*10.dp.toPx()},contentScale=ContentScale.Crop)
    Canvas(Modifier.fillMaxSize()) {
        val accent=when(scene){"orbit"->Color(0xFFB9C7FF);"kapok"->Color(0xFFFFC47B);else->Color(0xFF8FF4D0)}
        val t=if(animations)phase else .35f
        repeat(14){i->
            val x=((i*.173f+t*.035f*m)%1f)*size.width
            val y=((i*.287f+t*.07f*m)%1f)*size.height
            drawCircle(accent.copy(alpha=.06f+(i%3)*.025f),1.2.dp.toPx()+(i%3),Offset(x,y))
        }
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.10f),Color.Transparent),center=Offset(size.width*.5f,size.height*.49f),radius=size.width*.46f),size.width*.46f,Offset(size.width*.5f,size.height*.49f))
    }
}
