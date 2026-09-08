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
}
