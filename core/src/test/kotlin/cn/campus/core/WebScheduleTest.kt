package cn.campus.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.junit.Assume.assumeTrue
import kotlin.test.*

class WebScheduleTest {
    private val parser = WebScheduleParser()
    private val monday = "2026-09-07"
    private fun sample(): WebTableSnapshot {
        val header = listOf(WebCell("节次名称"), WebCell("星期一",colSpan=2)) + listOf("二","三","四","五","六","日").map {WebCell("星期$it")}
        return WebTableSnapshot("2026学年度第一学期", listOf(header,
            listOf(WebCell("第1节\n08:00~08:45"),WebCell("1-17每周/本(专必)数学/甲老师/A101",2),WebCell("1-17每周/本(专必)数学/乙老师/A102",2)) + List(4) {WebCell("")} + listOf(WebCell("1,3-5每周/实验/丙老师//20人"),WebCell("2-16双周/体育/丁老师/操场")),
            listOf(WebCell("第2节 08:55~09:40")) + List(6) {WebCell("")}
        ),true)
    }
    @Test fun spansCandidatesWeekendsAndDates() {
        val schedule = parser.parse(sample(),monday).schedule
        assertEquals(3,schedule.rules.size);assertEquals(17,schedule.lastWeek)
        val math = schedule.rules.single {it.title=="数学"}
        assertEquals(2,math.endPeriod);assertEquals(2,math.variants.single().candidates.size)
        val lessons=ScheduleEngine.occurrences(AppData(schedule))
        assertEquals("2026-09-07T07:50+08:00[Asia/Shanghai]",lessons.first().startInstant.minusSeconds(600).atZone(SCHOOL_ZONE).toString())
        assertTrue(lessons.any {it.date.dayOfWeek.value==6 && it.candidates.single().location.isEmpty()})
        assertTrue(lessons.any {it.date.dayOfWeek.value==7})
        assertEquals(ImportDiff(0,0,0,emptyList()),ScheduleEngine.diff(AppData(schedule),parser.parse(sample(),monday).schedule))
    }
    @Test fun rejectsPartialInvalidAndCrossDayTables() {
        assertFails {parser.parse(sample().copy(allWeeks=false),monday)}
        assertFails {parser.parse(sample().copy(rows=emptyList()),monday)}
        assertFails {parser.parse(sample(),"2026-09-08")}
        val rows=sample().rows.toMutableList();rows[1]=rows[1].toMutableList().apply {this[1]=this[1].copy(colSpan=3)}
        assertFails {parser.parse(sample().copy(rows=rows),monday)}
        assertFails {parser.parse(sample().copy(rows=sample().rows.take(2)),monday)}
    }
    @Test fun navigationBoundary() {
        assertTrue(SchoolUrlPolicy.school(SchoolUrlPolicy.LOGIN))
        assertTrue(SchoolUrlPolicy.school("https://cas.sysu.edu.cn/cas/login"))
        for(url in listOf("http://jwxt.sysu.edu.cn/","https://sysu.edu.cn.attacker.test/","https://evilsysu.edu.cn/","https://user@jwxt.sysu.edu.cn/","javascript:alert(1)","file:///x","https://jwxt.sysu.edu.cn:444/")) assertFalse(SchoolUrlPolicy.school(url),url)
        assertTrue(SchoolUrlPolicy.timetable("https://jwxt.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint?code=test"))
        assertFalse(SchoolUrlPolicy.timetable(SchoolUrlPolicy.LOGIN))
        assertFalse(SchoolUrlPolicy.timetable("https://other.sysu.edu.cn/jwxt/mk/schedule-web/#/studentTimeTabPrint"))
    }
    @Test fun originalWordGeometryMatches29Arrangements() {
        val file=File(System.getProperty("fixture"));assumeTrue(file.exists())
        val ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        fun Element.children(tag:String)=(0 until childNodes.length).mapNotNull {childNodes.item(it) as? Element}.filter {it.localName==tag && it.namespaceURI==ns}
        fun Element.text()=getElementsByTagNameNS(ns,"t").let {nodes -> (0 until nodes.length).joinToString(""){nodes.item(it).textContent}.trim()}
        fun Element.property(tag:String)=children("tcPr").flatMap {it.children(tag)}.lastOrNull()
        val doc=DocumentBuilderFactory.newInstance().apply {isNamespaceAware=true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)}.newDocumentBuilder().parse(file)
        val nodes=doc.getElementsByTagNameNS(ns,"tbl")
        val table=(0 until nodes.length).map {nodes.item(it) as Element}.first {it.children("tr").first().text().contains("星期一")}
        val rows=mutableListOf<MutableList<WebCell>>()
        val origins=mutableMapOf<Int,Pair<Int,Int>>()
        table.children("tr").forEachIndexed {r,row ->
            val cells=mutableListOf<WebCell>();var col=0
            row.children("tc").forEach {cell ->
                val span=cell.property("gridSpan")?.getAttributeNS(ns,"val")?.toIntOrNull() ?: 1
                val merge=cell.property("vMerge")
                if(merge!=null && merge.getAttributeNS(ns,"val")!="restart") {
                    val (rr,cc)=origins.getValue(col)
                    rows[rr][cc]=rows[rr][cc].copy(rowSpan=r-rr+1)
                } else {
                    val index=cells.size;cells+=WebCell(cell.text(),colSpan=span)
                    (col until col+span).forEach {if(merge!=null)origins[it]=r to index else origins.remove(it)}
                }
                col+=span
            }
            rows+=cells
        }
        val expected=WordScheduleParser().parse(file.readBytes(),monday).schedule
        val actual=parser.parse(WebTableSnapshot(expected.term,rows,true),monday).schedule
        assertEquals(29,actual.rules.size);assertEquals(17,actual.lastWeek);assertEquals(expected,actual)
    }
}
