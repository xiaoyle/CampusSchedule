package cn.campus.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteDashboardTest {
    @Test fun oldNotesReceiveSafeDefaults() {
        val note=dataJson.decodeFromString<StudyNote>("""{"id":"old","title":"旧笔记","content":"正文"}""")
        assertEquals(NoteContentMode.PLAIN,note.contentMode)
        assertNull(note.appearance)
        val style=dataJson.decodeFromString<NoteStyle>("""{"paper":"GRID","accent":4279724882,"compact":true}""")
        assertEquals(NotePaperStyle.GRID,style.paper)
        assertEquals(NoteFontStyle.SYSTEM,style.font)
        assertEquals(4,style.previewLines)
    }

    @Test fun noteAppearanceAndMarkdownModeRoundTrip() {
        val appearance=NoteAppearance(NotePaperStyle.DOT,0xFF1976A3,NoteFontStyle.SERIF,1.2f,1.8f,28,.4f,6,0xFF14243A)
        val source=StudyNote("note","","标题","# 正文",contentMode=NoteContentMode.MARKDOWN,appearance=appearance)
        val restored=dataJson.decodeFromString<StudyNote>(dataJson.encodeToString(StudyNote.serializer(),source))
        assertEquals(source,restored)
    }

    @Test fun dashboardUsesOneSharedSetOfOccurrences() {
        val today=LocalDate.parse("2026-09-07")
        val lesson=ManualLesson("course","2026-09-07","芯片导论","08:00","09:40",repeatCount=2)
        val due=StudyTask("due","今天作业",dueAt="2026-09-07T20:00")
        val tomorrow=StudyTask("tomorrow","明天复习",dueAt="2026-09-08T20:00")
        val completed=StudyTask("done","已完成",dueAt="2026-09-07T12:00",completedAt="2026-09-07T10:00:00Z")
        val data=AppData(manualLessons=listOf(lesson),studyTasks=listOf(due,tomorrow,completed),learningGoal=LearningGoal(6))
        val snapshot=TodayDashboardEngine.build(data,today)
        assertEquals(2,snapshot.allLessons.size)
        assertEquals(1,snapshot.todayLessons.size)
        assertEquals(2,snapshot.pendingTasks.size)
        assertEquals(1,snapshot.completedThisWeek)
        assertEquals(6,snapshot.weeklyTarget)
        assertEquals(2,snapshot.futureLoads.first().taskCount+snapshot.futureLoads.first().lessonCount)
        assertEquals(1,snapshot.futureLoads[1].taskCount+snapshot.futureLoads[1].lessonCount)
    }
}
