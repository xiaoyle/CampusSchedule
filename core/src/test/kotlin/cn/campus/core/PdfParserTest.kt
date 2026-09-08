package cn.campus.core

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class PdfParserTest {
    private fun pages(): List<PdfPage> {
        val file = File(File(System.getProperty("fixture")).parentFile, "pdf-geometry.json")
        assumeTrue("Private PDF geometry fixture is not distributed", file.exists())
        return Json.parseToJsonElement(file.readText()).jsonArray.map { item ->
            val p = item.jsonObject
            fun JsonObject.n(key: String) = getValue(key).jsonPrimitive.double
            PdfPage(p.getValue("chars").jsonArray.map { it.jsonObject.let { c -> PdfGlyph(c.getValue("text").jsonPrimitive.content, c.n("x0"), (c.n("top")+c.n("bottom"))/2) } },
                p.getValue("lines").jsonArray.map { it.jsonObject.let { l -> PdfLine(l.n("x0"),l.n("top"),l.n("x1"),l.n("bottom")) } })
        }
    }
    @Test fun ninePagesRestoreMergedCellsAndDates() {
        val s = PdfScheduleParser().parse(pages(), "2026-09-07").schedule
        assertEquals(11,s.periods.size)
        assertEquals(17,s.lastWeek)
        val all = ScheduleEngine.occurrences(AppData(s))
        val math = all.single { it.title == "高等数学一（I）" && it.date == LocalDate.parse("2026-09-07") }
        assertEquals(LocalTime.of(8,0),math.start)
        assertEquals(LocalTime.of(9,40),math.end)
        assertEquals("2026-09-07T07:50+08:00[Asia/Shanghai]",math.startInstant.minusSeconds(600).atZone(SCHOOL_ZONE).toString())
        assertEquals(3,all.single { it.title=="线性代数" && it.date==LocalDate.parse("2026-09-29") }.candidates.size)
        assertEquals(24,all.single { it.title.startsWith("新生研讨课") }.candidates.size)
        assertTrue(all.any { it.date.dayOfWeek.value == 7 })
        assertTrue(all.any { it.date.dayOfWeek.value == 6 })
        assertTrue(all.any { it.candidates.any { c -> c.location.isEmpty() } })
        assertEquals(ImportDiff(0,0,0,emptyList()),ScheduleEngine.diff(AppData(s),s))
        assertEquals(29,s.rules.size)
        val word=WordScheduleParser().parse(File(System.getProperty("fixture")).readBytes(),"2026-09-07").schedule
        fun normalized(schedule: Schedule): Set<LessonRule> = schedule.rules.map { r ->
            fun clean(s: String)=s.replace(Regex("\\s+"),"")
            r.copy(id="",title=clean(r.title),variants=r.variants.map { v -> v.copy(candidates=v.candidates.map { Candidate(clean(it.teacher),clean(it.location)) }) })
        }.toSet()
        assertEquals(normalized(word),normalized(s))
        val original=word.rules.first { it.title.startsWith("新时代实践") }
        val event=ScheduleEngine.occurrences(AppData(word)).first { it.ruleId==original.id }
        val edit=LessonEdit(original.id,event.originalDate,location="保留个人地点")
        val before=AppData(word,listOf(edit))
        assertTrue(ScheduleEngine.diff(before,s).unmatched.isEmpty())
        assertEquals(0,ScheduleEngine.diff(before,s).added)
        assertEquals(0,ScheduleEngine.diff(before,s).removed)
        assertEquals(listOf(edit),ScheduleEngine.merge(before,s).edits)
        assertEquals("保留个人地点",ScheduleEngine.occurrences(ScheduleEngine.merge(before,s)).single { it.key==event.key }.locationText)
    }
    @Test fun incompleteOrImageOnlyPdfRejected() {
        assertFailsWith<IllegalArgumentException> { PdfScheduleParser().parse(listOf(PdfPage(emptyList(), emptyList())),"2026-09-07") }
        val source=pages()
        assertFailsWith<IllegalArgumentException> { PdfScheduleParser().parse(source.dropLast(1),"2026-09-07") }
        assertFailsWith<IllegalArgumentException> { PdfScheduleParser().parse(source,"2026-09-08") }
    }
}
