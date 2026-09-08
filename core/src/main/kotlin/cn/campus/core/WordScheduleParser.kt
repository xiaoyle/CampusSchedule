package cn.campus.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** School exports Flat OPC XML using a .doc suffix. Read the content, never infer by suffix. */
class WordScheduleParser {
    private val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private fun Element.children(name: String): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }.filter { it.localName == name && it.namespaceURI == ns }
    private fun Element.text(): String = getElementsByTagNameNS(ns, "t").let { nodes ->
        (0 until nodes.length).joinToString("") { nodes.item(it).textContent }.trim()
    }
    // The school's Flat OPC contains repeated tcPr blocks in the same cell.
    // In particular vMerge may be in the second block while gridSpan is in the first.
    private fun Element.property(name: String): Element? = children("tcPr").flatMap { it.children(name) }.lastOrNull()
    private fun Element.span() = property("gridSpan")?.getAttributeNS(ns, "val")?.toIntOrNull() ?: 1

    fun parse(bytes: ByteArray, firstMonday: String): ImportResult {
        require(bytes.size <= 8 * 1024 * 1024) { "文件超过 8 MB，请使用教务系统导出的课表" }
        require(LocalDate.parse(firstMonday).dayOfWeek == DayOfWeek.MONDAY) { "第 1 周起始日期必须是周一" }
        val xml = if (bytes.take(2) == listOf(80.toByte(), 75.toByte())) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var found: ByteArray? = null
                var entry = zip.nextEntry
                var count = 0
                while (entry != null && count++ < 1000) {
                    if (entry.name == "word/document.xml") {
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count = zip.read(buffer)
                        while(count >= 0) {
                            require(out.size() + count <= 8 * 1024 * 1024) { "解压后的课表过大" }
                            out.write(buffer, 0, count); count = zip.read(buffer)
                        }
                        found = out.toByteArray(); break
                    }
                    entry = zip.nextEntry
                }
                requireNotNull(found) { "文件中没有 Word 课表内容" }
            }
        } else bytes
        require(xml.size <= 8 * 1024 * 1024) { "解压后的课表过大" }
        val prefix = xml.toString(Charsets.UTF_8)
        require(!prefix.contains("<!DOCTYPE", true) && !prefix.contains("<!ENTITY", true)) { "不支持含外部实体的文件" }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val doc = try { factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)) }
        catch (_: Exception) { throw IllegalArgumentException("无法读取文件，请选择学校导出的原始 Word 课表") }
        val tables = doc.getElementsByTagNameNS(ns, "tbl")
        val table = (0 until tables.length).map { tables.item(it) as Element }.firstOrNull {
            it.children("tr").firstOrNull()?.text()?.contains("星期一") == true
        } ?: throw IllegalArgumentException("未找到包含星期表头的课表，请导出全部周次")
        val rows = table.children("tr")
        val days = mutableMapOf<Int, Int>()
        var col = 0
        val dayNames = mapOf("星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4, "星期五" to 5, "星期六" to 6, "星期日" to 7, "星期天" to 7)
        rows.first().children("tc").forEach { cell ->
            val span = cell.span()
            dayNames[cell.text()]?.let { day -> (col until col + span).forEach { days[it] = day } }
            col += span
        }
        require(days.values.toSet().size == 7) { "课表星期表头不完整，请导出全部课表" }
        val periods = mutableListOf<Period>()
        val raws = mutableListOf<ParsedLesson>()
        var previous = mutableMapOf<Int, ParsedLesson>()
        val timePattern = Regex("第(\\d+)节\\s*(\\d{2}:\\d{2})[~～—-](\\d{2}:\\d{2})")
        rows.drop(1).forEach { row ->
            val cells = row.children("tc")
            val match = timePattern.find(cells.firstOrNull()?.text().orEmpty()) ?: throw IllegalArgumentException("无法识别节次时间，未导入")
            val number = match.groupValues[1].toInt()
            val start = match.groupValues[2]; val end = match.groupValues[3]
            require(LocalTime.parse(start) < LocalTime.parse(end)) { "课表节次时间异常" }
            periods += Period(number, start, end)
            val current = mutableMapOf<Int, ParsedLesson>()
            col = 0
            cells.forEach { cell ->
                val span = cell.span(); val text = cell.text(); val merge = cell.property("vMerge")
                val range = col until col + span
                if (col > 0) {
                    if (merge != null && merge.getAttributeNS(ns, "val") != "restart") {
                        range.forEach { c -> previous[c]?.let { raw -> raw.end = number; current[c] = raw } }
                    } else if (text.isNotBlank()) {
                        val mapped = range.mapNotNull { days[it] }.toSet()
                        require(mapped.size == 1) { "发现跨星期的课程单元格，请核对文件" }
                        val raw = ParsedLesson(mapped.single(), number, number, text)
                        raws += raw
                        if (merge != null) range.forEach { current[it] = raw }
                    }
                }
                col += span
            }
            previous = current
        }
        require(raws.isNotEmpty()) { "文件中没有课程，请确认导出的是全部周次" }
        val body = doc.getElementsByTagNameNS(ns, "body").item(0) as? Element
        // The exported heading includes the student's name. Persist only the semester, never the name.
        val heading = body?.children("p")?.joinToString("") { it.text() }.orEmpty()
        val term = Regex("\\d{4}学年度第[一二三123]学期").find(heading)?.value ?: "导入学期"
        return ScheduleAssembler.assemble(term, firstMonday, periods, raws)
    }

    fun parseWeeks(value: String): List<Int> {
        val normalized = value.replace(" ", "").replace('，', ',').replace('、', ',').replace('－', '-').replace('～', '-')
        val match = Regex("^([0-9,\\-]+)(?:周)?(每周|单周|双周|单|双)?$").matchEntire(normalized)
            ?: throw IllegalArgumentException("无法识别周次：$value")
        val weeks = match.groupValues[1].split(',').flatMap { part ->
            val nums = part.split('-').map { it.toInt() }
            require(nums.size in 1..2 && nums.all { it in 1..60 } && nums.first() <= nums.last()) { "周次范围异常：$value" }
            (nums.first()..nums.last()).toList()
        }.distinct().sorted()
        return weeks.filter { when(match.groupValues[2]) { "单周", "单" -> it % 2 == 1; "双周", "双" -> it % 2 == 0; else -> true } }
            .also { require(it.isNotEmpty()) { "周次为空：$value" } }
    }
}
