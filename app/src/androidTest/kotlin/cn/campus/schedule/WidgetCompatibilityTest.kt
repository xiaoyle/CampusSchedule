package cn.campus.schedule

import android.appwidget.*
import android.content.ComponentName
import android.graphics.Rect
import android.os.*
import android.view.View
import android.widget.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cn.campus.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.*
import java.time.temporal.TemporalAdjusters

class WidgetCompatibilityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun labels(v:View):List<TextView> = when(v) {is TextView->listOf(v);is android.view.ViewGroup->(0 until v.childCount).flatMap {labels(v.getChildAt(it))};else->emptyList()}
    @Test fun registeredWidgetsFitAtBothSizesAndAllStates() {
        val manager=AppWidgetManager.getInstance(context)
        assertEquals(2,WidgetSupport.count(context))
        val host=AppWidgetHost(context,902)
        val today=LocalDate.now(SCHOOL_ZONE)
        fun schedule(date:LocalDate):Schedule {
            val monday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return Schedule("组件验证",monday.toString(),listOf(cn.campus.core.Period(1,"00:00","23:59")),listOf(LessonRule("widget-check","课程名称较长的显示检查",date.dayOfWeek.value,1,1,listOf(Variant(listOf(1),listOf(Candidate("教师","第一教学楼 1208")))))))
        }
        for((width,height) in listOf(220 to 180,300 to 300)) {
            val ids=mutableListOf<Int>()
            lateinit var layout:LinearLayout
            compose.runOnIdle {
                layout=LinearLayout(compose.activity).apply {orientation=LinearLayout.VERTICAL;setPadding(12,80,12,12);setBackgroundColor(android.graphics.Color.GRAY)}
                host.startListening()
                for(receiver in listOf(NextWidgetReceiver::class.java,TodayWidgetReceiver::class.java)) {
                    val id=host.allocateAppWidgetId();ids+=id
                    val options=Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,width);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height)
                        if(Build.VERSION.SDK_INT>=31) putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(android.util.SizeF(width.toFloat(),height.toFloat())))
                    }
                    assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,receiver),options))
                    val view=host.createView(compose.activity,id,manager.getAppWidgetInfo(id))
                    // Zero host padding: test the advertised exact content size, including the footer.
                    view.setPadding(0,0,0,0)
                    val density=context.resources.displayMetrics.density
                    layout.addView(view,LinearLayout.LayoutParams((width*density).toInt(),(height*density).toInt()))
                }
                compose.activity.setContentView(layout)
            }
            try {
                for((name,data) in listOf("empty" to AppData(),"active" to AppData(schedule(today)),"noclass" to AppData(schedule(today.plusDays(1))),"ended" to AppData(schedule(today.minusYears(1))))) {
                    runBlocking {context.scheduleApp.store.update {data};NextWidget().updateAllSafe(context);TodayWidget().updateAllSafe(context)}
                    val deadline=System.currentTimeMillis()+30000
                    var credits=emptyList<TextView>()
                    do {
                        Thread.sleep(300)
                        instrumentation.runOnMainSync {credits=labels(layout).filter {it.text.toString()=="xiaoyle 制作"}}
                    } while(credits.size<2 && System.currentTimeMillis()<deadline)
                    Thread.sleep(1000)
                    instrumentation.runOnMainSync {
                        assertEquals(2,credits.size)
                        credits.forEach {view-> val visible=Rect();assertTrue("Credit visible $name/$width",view.getGlobalVisibleRect(visible));assertEquals("Credit not cropped",view.height,visible.height())}
                        if(name=="active") {
                            val places=labels(layout).filter {it.text.toString().contains("1208")}
                            assertEquals(2,places.size)
                            places.forEach {view-> val visible=Rect();assertTrue(view.getGlobalVisibleRect(visible));assertEquals("Location view visible at $width x $height",view.height,visible.height()); assertTrue("Location text height fits at $width x $height",view.layout.getLineBottom(0)+view.totalPaddingTop+view.totalPaddingBottom<=view.height)}
                        }
                    }
                    val bmp=instrumentation.uiAutomation.takeScreenshot()
                    File(context.getExternalFilesDir(null),"widget-${Build.VERSION.SDK_INT}-$width-$name.png").outputStream().use {bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                }
            } finally {ids.forEach {host.deleteAppWidgetId(it)};host.stopListening()}
        }
    }
}
