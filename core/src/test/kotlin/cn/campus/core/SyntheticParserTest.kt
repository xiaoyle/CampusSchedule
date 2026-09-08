package cn.campus.core

import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.*

class SyntheticParserTest {
    private val parser = WordScheduleParser()
    private fun cell(text: String = "", span: Int = 1, merge: String? = null): String =
        "<w:tc><w:tcPr><w:gridSpan w:val=\"$span\"/></w:tcPr>" +
        (merge?.let { "<w:tcPr><w:vMerge ${if(it.isNotEmpty()) "w:val=\"$it\"" else ""}/></w:tcPr>" } ?: "") +
        "<w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"
    private fun xml(): ByteArray {
        val header = cell() + cell("星期日") + cell("星期一",2) + (2..6).joinToString("") { cell("星期"+listOf("", "一", "二", "三", "四", "五", "六")[it]) }
        val first = cell("第1节08:00~08:45") + cell() + cell("1-3每周/本(专必)测试数学/教师甲/101/30人",merge="restart") + cell("1-3每周/本(专必)测试数学/教师乙/102/30人",merge="restart") + (1..5).joinToString(""){cell()}
        val second = cell("第2节08:55~09:40") + cell() + cell(merge="") + cell(merge="") + (1..5).joinToString(""){cell()}
        return """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>2026学年度第一学期示例课程表</w:t></w:r></w:p><w:tbl><w:tr>$header</w:tr><w:tr>$first</w:tr><w:tr>$second</w:tr></w:tbl></w:body></w:document>""".toByteArray()
    }
    @Test fun duplicatePropertiesAndCandidates() {
        val s=parser.parse(xml(),"2026-09-07").schedule
        assertEquals(1,s.rules.size)
        assertEquals(2,s.rules.single().endPeriod)
        assertEquals(2,s.rules.single().variants.single().candidates.size)
        val events=ScheduleEngine.occurrences(AppData(s))
        assertEquals(3,events.size)
        assertEquals(LocalDate.parse("2026-09-07"),events.first().date)
        assertEquals(LocalTime.parse("09:40"),events.first().end)
    }
    @Test fun zippedWordUsesSameParser() {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("word/document.xml"));it.write(xml());it.closeEntry() }
        assertEquals(parser.parse(xml(),"2026-09-07"),parser.parse(out.toByteArray(),"2026-09-07"))
    }
    @Test fun wrongMondayAndMalformedInputRejected() {
        assertFailsWith<IllegalArgumentException>{parser.parse(xml(),"2026-09-08")}
        assertFailsWith<IllegalArgumentException>{parser.parse("not a document".toByteArray(),"2026-09-07")}
        assertFailsWith<IllegalArgumentException>{parser.parse(ByteArray(8*1024*1024+1),"2026-09-07")}
    }
    @Test fun changedDateDropsUnmatchedEditsExplicitly() {
        val s=parser.parse(xml(),"2026-09-07").schedule
        val edit=LessonEdit(s.rules.single().id,"2026-09-07",cancelled=true)
        val before=AppData(s,listOf(edit))
        val next=s.copy(firstMonday="2026-09-14")
        assertEquals(listOf(edit),ScheduleEngine.diff(before,next).unmatched)
        assertEquals(1,ScheduleEngine.diff(before,next).changed)
        assertTrue(ScheduleEngine.merge(before,next).edits.isEmpty())
    }
}
