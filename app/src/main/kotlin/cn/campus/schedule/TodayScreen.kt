package cn.campus.schedule

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.campus.core.*
import java.time.LocalDate

@Immutable
data class TodayUiState(
    val dashboard:TodayDashboardSnapshot,
    val today:LocalDate,
    val profile:ProfileData,
    val activeFocus:ActiveFocusState?,
    val focusSettings:FocusSettings,
    val hasImportedSchedule:Boolean,
    val hasAnySchedule:Boolean
)

@Composable fun TodayScreen(
    state:TodayUiState,
    context:Context,
    onOpenTasks:()->Unit,
    onOpenDate:(LocalDate)->Unit,
    onRequestFocus:(String,String?,String?,String?)->Unit,
    onOpenFocus:()->Unit,
    onAddTask:()->Unit,
    onStartGapFocus:(TaskOccurrence,Int)->Unit,
    onImport:()->Unit,
    onOpenLesson:(Occurrence)->Unit
){
    val dashboard=state.dashboard
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(20.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        item(key="today-header",contentType="header"){
            TodayAssistantHeader(dashboard,state.profile,context,onOpenTasks,onOpenDate)
        }
        item(key="focus-quick",contentType="focus"){
            FocusQuickStartCard(state.activeFocus,dashboard.pendingTasks,onRequestFocus,onOpenFocus)
        }
        item(key="gap-radar",contentType="radar"){
            GapRadarCard(dashboard,state.activeFocus,onOpenTasks,onAddTask,onOpenFocus,onStartGapFocus)
        }
        if(!state.hasAnySchedule)item(key="empty-schedule",contentType="empty"){
            EmptyCard("把课表带到桌面","导入学校课表或添加一项课程后，就能在这里查看安排。","导入课表",onImport)
        }else{
            item(key="next-lesson",contentType="next"){
                NextLessonCard(dashboard,state.hasImportedSchedule,onOpenLesson)
            }
            item(key="today-count",contentType="section"){
                Text("今日安排 · ${dashboard.todayLessons.size} 次课",style=MaterialTheme.typography.titleMedium)
            }
            if(dashboard.todayItems.isEmpty())item(key="today-empty",contentType="empty"){
                Text("今天没有课程，留一点时间给自己。",modifier=Modifier.padding(vertical=12.dp))
            }
            items(dashboard.todayItems,key={it.key},contentType={"lesson"}){lesson->
                LessonCard(lesson,lesson.key in dashboard.conflicts){onOpenLesson(lesson)}
            }
            item(key="today-footer",contentType="footer"){
                Text("课表保存在本机 · 调课后请重新导入或修改本次课程",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
