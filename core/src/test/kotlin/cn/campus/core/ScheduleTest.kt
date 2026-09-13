package cn.campus.core

import java.io.File
import java.time.*
import kotlin.test.*
import org.junit.Assume.assumeTrue

class ScheduleTest {
    private val parser = WordScheduleParser()
    private fun real(): Schedule {
        val file = File(System.getProperty("fixture"))
        assumeTrue("Private source fixture is not distributed", file.exists())
        return parser.parse(file.readBytes(), "2026-09-07").schedule
    }
    @Test fun sourceDatesAndMergedPeriods() {
        val s = real()
        assertEquals(11, s.periods.size)
        assertEquals(17, s.lastWeek)
        val all = ScheduleEngine.occurrences(AppData(s))
        val monday = all.filter { it.date == LocalDate.parse("2026-09-07") }
        val math = monday.single { it.title == "高等数学一（I）" }
        assertEquals(LocalTime.of(8, 0), math.start)
        assertEquals(LocalTime.of(9, 40), math.end)
        assertEquals("2026-09-07T07:50+08:00[Asia/Shanghai]", math.startInstant.minusSeconds(600).atZone(SCHOOL_ZONE).toString())
        assertEquals(9, ScheduleEngine.week(LocalDate.parse("2026-11-02"), s.firstMonday))
        assertEquals(0, ScheduleEngine.week(LocalDate.parse("2026-09-06"), s.firstMonday))
        assertTrue(all.none { it.title == "高等数学一（I）" && it.date == LocalDate.parse("2026-11-02") })
        assertTrue(all.any { it.title == "高等数学一（I）" && it.date == LocalDate.parse("2026-11-09") })
        val lab = all.single { it.title == "电路理论基础实验" && it.date == LocalDate.parse("2026-11-09") }
        assertEquals(LocalTime.of(14, 20), lab.start)
        assertEquals(LocalTime.of(18, 10), lab.end)
    }
    @Test fun ambiguousLocationsAndWeekend() {
        val all = ScheduleEngine.occurrences(AppData(real()))
        val algebra = all.single { it.title == "线性代数" && it.date == LocalDate.parse("2026-09-29") && it.start.hour == 8 }
        assertEquals(3, algebra.candidates.size)
        assertTrue(algebra.candidates.any { "1305" in it.location })
        assertTrue(all.any { it.date.dayOfWeek == DayOfWeek.SUNDAY })
        assertTrue(all.any { it.date.dayOfWeek == DayOfWeek.SATURDAY })
        assertTrue(all.any { it.candidates.any { c -> c.location.isEmpty() } })
        assertEquals(all.size, all.map { it.key }.distinct().size)
    }
    @Test fun idempotentImportAndEdits() {
        val schedule = real()
        val lesson = ScheduleEngine.occurrences(AppData(schedule)).first()
        val edit = LessonEdit(lesson.ruleId, lesson.originalDate, date = "2026-09-10", start = "12:00", end = "12:45", location = "自定义教室")
        val state = AppData(schedule, listOf(edit))
        val diff = ScheduleEngine.diff(state, schedule)
        assertEquals(ImportDiff(0, 0, 0, emptyList()), diff)
        assertEquals(listOf(edit), ScheduleEngine.merge(state, schedule).edits)
        val moved = ScheduleEngine.occurrences(state).single { it.key == lesson.key }
        assertEquals(LocalDate.parse("2026-09-10"), moved.date)
        assertEquals("自定义教室", moved.locationText)
        val removed = schedule.copy(rules = schedule.rules.filterNot { it.id == lesson.ruleId })
        assertEquals(listOf(edit), ScheduleEngine.diff(state, removed).unmatched)
        assertTrue(ScheduleEngine.merge(state, removed).edits.isEmpty())
        assertTrue(ScheduleEngine.occurrences(state.copy(edits = listOf(edit.copy(cancelled = true)))).none { it.key == lesson.key })
    }
    @Test fun weekSyntaxAndInvalidInput() {
        assertEquals(listOf(1,3,5,7), parser.parseWeeks("1-8单周"))
        assertEquals(listOf(2,4,6,8), parser.parseWeeks("1-8双周"))
        assertEquals(listOf(1,3,4,5,9), parser.parseWeeks("1,3-5,9每周"))
        assertFailsWith<IllegalArgumentException> { parser.parseWeeks("18-1每周") }
        assertFailsWith<IllegalArgumentException> { parser.parseWeeks("未知") }
        assertFailsWith<IllegalArgumentException> { parser.parse("<!DOCTYPE x><x/>".toByteArray(), "2026-09-07") }
        assertFailsWith<IllegalArgumentException> { ScheduleEngine.validateEdit(LessonEdit("x", "2026-09-07", start="10:00", end="09:00")) }
    }
    @Test fun overlapKeepsBothLessons() {
        val source = ScheduleEngine.occurrences(AppData(real())).first()
        val other = source.copy(key="another", title="另一门课程")
        assertEquals(setOf(source.key, other.key), ScheduleEngine.conflicts(listOf(source, other)))
        assertTrue(ScheduleEngine.conflicts(listOf(source, other.copy(start=source.end, end=source.end.plusHours(1)))) .isEmpty())
    }

    @Test fun manualSingleLessonWorksWithoutImportedSchedule() {
        val manual=ManualLesson("personal-1","2026-09-09","自习","19:00","20:30",location="图书馆",color=0xFF126B91)
        val occurrence=ScheduleEngine.occurrences(AppData(manualLessons=listOf(manual))).single()
        assertTrue(occurrence.manual)
        assertEquals("图书馆",occurrence.locationText)
        assertEquals(LocalDate.parse("2026-09-09"),occurrence.date)
        assertEquals(0xFF126B91,occurrence.color)
        assertFailsWith<IllegalArgumentException> {ScheduleEngine.validateManualLesson(manual.copy(end="18:00"))}
    }

    @Test fun weeklyManualLessonsExpandWithStableKeysAndOldJsonDefaults() {
        val two=ManualLesson("series","2026-09-07","芯片导论","08:00","09:40",repeatCount=2)
        val twice=ScheduleEngine.occurrences(AppData(manualLessons=listOf(two)))
        assertEquals(listOf("manual@series@0","manual@series@1"),twice.map{it.key})
        assertEquals(listOf(7,14),twice.map{it.date.dayOfMonth})
        assertEquals(2,twice.last().manualRepeatCount)
        assertEquals(30,ScheduleEngine.occurrences(AppData(manualLessons=listOf(two.copy(repeatCount=30)))).size)
        val old=dataJson.decodeFromString<ManualLesson>("""{"id":"old","date":"2026-09-07","title":"旧课程","start":"08:00","end":"08:45"}""")
        assertEquals(1,old.repeatCount)
        assertEquals("manual@old@0",ScheduleEngine.occurrences(AppData(manualLessons=listOf(old))).single().key)
    }

    @Test fun weeklyManualLessonSupportsInstanceAndFutureChanges() {
        val base=ManualLesson("series","2026-09-07","电路","08:00","09:40",location="A",repeatCount=4)
        val secondDraft=base.copy(date="2026-09-16",title="电路实验",start="10:10",end="11:50",location="B")
        val one=ScheduleEngine.updateManualLesson(base,secondDraft,ManualLessonEditScope.INSTANCE,1)
        val oneItems=ScheduleEngine.occurrences(AppData(manualLessons=listOf(one)))
        assertEquals(LocalDate.parse("2026-09-16"),oneItems.single{it.manualIndex==1}.date)
        assertEquals("电路",oneItems.single{it.manualIndex==2}.title)
        val futureDraft=base.copy(date="2026-09-21",title="新电路",start="14:20",end="16:00",location="C")
        val future=ScheduleEngine.updateManualLesson(base,futureDraft,ManualLessonEditScope.FUTURE,2)
        val futureItems=ScheduleEngine.occurrences(AppData(manualLessons=listOf(future)))
        assertEquals(listOf("电路","电路","新电路","新电路"),futureItems.map{it.title})
        assertEquals(listOf(7,14,21,28),futureItems.map{it.date.dayOfMonth})
        assertEquals("manual@series@2",futureItems[2].key)
    }

    @Test fun weeklyManualLessonDeletionScopesWork() {
        val base=ManualLesson("series","2026-09-07","英语","08:00","09:40",repeatCount=5)
        val one=ScheduleEngine.deleteManualLesson(base,ManualLessonEditScope.INSTANCE,2)!!
        assertEquals(listOf(0,1,3,4),ScheduleEngine.occurrences(AppData(manualLessons=listOf(one))).map{it.manualIndex})
        val future=ScheduleEngine.deleteManualLesson(base,ManualLessonEditScope.FUTURE,3)!!
        assertEquals(3,future.repeatCount)
        assertEquals(3,ScheduleEngine.occurrences(AppData(manualLessons=listOf(future))).size)
        assertNull(ScheduleEngine.deleteManualLesson(base,ManualLessonEditScope.FUTURE,0))
        assertNull(ScheduleEngine.deleteManualLesson(base,ManualLessonEditScope.SERIES,2))
    }

    @Test fun oldStateDecodesWithEmptyStudyTasks() {
        val oldJson="""{"schedule":null,"edits":[],"reminderMinutes":10,"importedAt":null,"alarmEnabled":true,"manualLessons":[],"courseColors":{}}"""
        val state=dataJson.decodeFromString<AppData>(oldJson)
        assertTrue(state.studyTasks.isEmpty())
        assertEquals(5,state.learningGoal.weeklyTarget)
    }

    @Test fun studyTasksValidateSortAndScheduleReminder() {
        val later=StudyTask("later","线代作业",dueAt="2026-09-10T22:00",remindBeforeMinutes=60,createdAt="2026-09-09T00:00:00Z")
        val sooner=StudyTask("soon","英语作业",dueAt="2026-09-10T20:00",priority=StudyTaskPriority.URGENT,createdAt="2026-09-09T00:00:00Z")
        val open=StudyTask("open","阅读第五章",type=StudyTaskType.REVIEW,createdAt="2026-09-09T00:00:00Z")
        listOf(later,sooner,open).forEach(StudyTaskEngine::validate)
        assertEquals(listOf("soon","later","open"),StudyTaskEngine.sorted(listOf(open,later,sooner)).map {it.id})
        assertEquals(Instant.parse("2026-09-10T13:00:00Z"),StudyTaskEngine.reminderAt(later))
        val custom=later.copy(remindBeforeMinutes=95)
        StudyTaskEngine.validate(custom)
        assertEquals(Instant.parse("2026-09-10T12:25:00Z"),StudyTaskEngine.reminderAt(custom))
        assertFailsWith<IllegalArgumentException>{StudyTaskEngine.validate(later.copy(remindBeforeMinutes=StudyTaskEngine.MAX_REMINDER_MINUTES+1))}
        assertFailsWith<IllegalArgumentException> {StudyTaskEngine.validate(open.copy(remindBeforeMinutes=10))}
    }

    @Test fun oldStateDecodesWithEmptyNotes() {
        val oldJson="""{"schedule":null,"edits":[],"studyTasks":[]}"""
        val state=dataJson.decodeFromString<AppData>(oldJson)
        assertTrue(state.noteCategories.isEmpty())
        assertTrue(state.studyNotes.isEmpty())
        assertEquals(NotePaperStyle.CLEAN,state.noteStyle.paper)
    }

    @Test fun removedCourseKeepsTaskAsUnlinkedSnapshot() {
        val task=StudyTask("task","完成报告",courseRuleId="missing",courseTitle="原课程",createdAt="2026-09-09T00:00:00Z")
        val data=AppData(studyTasks=listOf(task))
        assertFalse(StudyTaskEngine.courseExists(task,data))
        assertEquals(task,StudyTaskEngine.sorted(data.studyTasks).single())
    }

    @Test fun repeatingTasksExpandWithoutWritingInstances() {
        val daily=StudyTask("daily","背单词",dueAt="2026-09-07T20:00",repeatRule=TaskRepeatRule(TaskRepeatKind.DAILY,endsOn="2026-09-09"),createdAt="2026-09-01T00:00:00Z")
        val weekly=StudyTask("weekly","周总结",dueAt="2026-09-07T21:00",repeatRule=TaskRepeatRule(TaskRepeatKind.WEEKLY,endsOn="2026-09-30"),createdAt="2026-09-01T00:00:00Z")
        val custom=StudyTask("custom","跑步",dueAt="2026-09-07T07:00",repeatRule=TaskRepeatRule(TaskRepeatKind.CUSTOM_WEEKDAYS,listOf(2,4),"2026-09-13"),createdAt="2026-09-01T00:00:00Z")
        listOf(daily,weekly,custom).forEach(StudyTaskEngine::validate)
        assertEquals(3,StudyTaskEngine.occurrences(daily,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30")).size)
        assertEquals(listOf(7,14,21,28),StudyTaskEngine.occurrences(weekly,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30")).map{it.dueAt!!.dayOfMonth})
        assertEquals(listOf(8,10),StudyTaskEngine.occurrences(custom,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30")).map{it.dueAt!!.dayOfMonth})
        assertTrue(daily.instanceStates.isEmpty())
    }

    @Test fun repeatedInstancesKeepIndependentProgressAndOverrides() {
        val sub=StudySubtask("read","阅读",0)
        val base=StudyTask("series","复习",dueAt="2026-09-07T20:00",subtasks=listOf(sub),remindBeforeMinutes=30,repeatRule=TaskRepeatRule(TaskRepeatKind.DAILY,endsOn="2026-09-09"),createdAt="2026-09-01T00:00:00Z")
        val firstKey="series@2026-09-07T20:00"
        val secondKey="series@2026-09-08T20:00"
        val edited=base.copy(instanceStates=listOf(
            TaskInstanceState(firstKey,completedAt="2026-09-07T12:00:00Z",completedSubtaskIds=listOf("read")),
            TaskInstanceState(secondKey,override=TaskInstanceOverride("补做复习",StudyTaskType.REVIEW,null,"","2026-10-01T19:00",StudyTaskPriority.IMPORTANT,"",10,listOf(sub)))
        ))
        val september=StudyTaskEngine.occurrences(edited,LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30"))
        assertEquals("2026-09-07T12:00:00Z",september.single{it.key==firstKey}.completedAt)
        assertFalse(september.any{it.key==secondKey})
        val october=StudyTaskEngine.occurrences(edited,LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31")).single()
        assertEquals("补做复习",october.title)
        assertEquals(LocalDate.parse("2026-10-01"),october.dueAt!!.toLocalDate())
        assertEquals(Instant.parse("2026-10-01T10:50:00Z"),StudyTaskEngine.reminderAt(october))
    }

    @Test fun gapRadarReservesCourseBuffersAndPicksTaskThatFits() {
        val now=ZonedDateTime.of(2026,9,7,9,0,0,0,SCHOOL_ZONE)
        val lesson=Occurrence("c@2026-09-07","c","高等数学","2026-09-07",LocalDate.parse("2026-09-07"),LocalTime.of(10,0),LocalTime.of(11,0),emptyList(),false)
        val urgent=StudyTask("urgent","短任务",dueAt="2026-09-07T18:00",priority=StudyTaskPriority.URGENT,estimatedMinutes=45,createdAt="2026-09-01T00:00:00Z")
        val long=StudyTask("long","长任务",dueAt="2026-09-07T17:00",estimatedMinutes=90,createdAt="2026-09-01T00:00:00Z")
        val tasks=StudyTaskEngine.occurrences(AppData(studyTasks=listOf(long,urgent)),LocalDate.parse("2026-09-01"),LocalDate.parse("2026-09-30"))
        val pick=assertNotNull(GapRadarEngine.recommend(now,listOf(lesson),tasks))
        assertEquals(LocalTime.of(9,50),pick.gap.end.toLocalTime())
        assertEquals("urgent",pick.task?.taskId)
        assertEquals(45,pick.suggestedMinutes)
        assertEquals(30,GapRadarEngine.effectiveMinutes(tasks.first().copy(estimatedMinutes=null)))
        assertFailsWith<IllegalArgumentException>{StudyTaskEngine.validate(urgent.copy(estimatedMinutes=9))}
        StudyTaskEngine.validate(urgent.copy(estimatedMinutes=180))
    }
}
