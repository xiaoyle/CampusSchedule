package cn.campus.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Coordinates use page top/left; glyphs retain PDF content stream order. No OCR. */
data class PdfGlyph(val text: String, val x: Double, val y: Double)
data class PdfLine(val x0: Double, val y0: Double, val x1: Double, val y1: Double)
data class PdfPage(val glyphs: List<PdfGlyph>, val lines: List<PdfLine>)

class PdfScheduleParser {
    private data class Cell(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        fun contains(x: Double, y: Double) = x >= left && x < right && y >= top && y < bottom
    }
    private data class Fragment(val left: Double, val right: Double, val top: Double, var bottom: Double, var text: String)
    private data class DayColumn(val left: Double, val right: Double, val day: Int)
    private data class Marker(val period: Period, val y: Double)
    private val time = Regex("第(\\d+)节\\s*(\\d{2}:\\d{2})[~～—-](\\d{2}:\\d{2})")
    private val weekStart = Regex("^[0-9,、，\\-]+(?:周)?(?:每周|单周|双周|单|双)?/")

    fun parse(pages: List<PdfPage>, firstMonday: String): ImportResult {
        require(LocalDate.parse(firstMonday).dayOfWeek == DayOfWeek.MONDAY) { "第 1 周起始日期必须是周一" }
        require(pages.isNotEmpty() && pages.size <= 40) { "PDF 页数异常，请导出完整课表（最多 40 页）" }
        require(pages.sumOf { it.glyphs.size } > 30) { "这个 PDF 没有可读取的文字，暂不支持扫描件或图片 PDF，请从课表页面直接导出 PDF" }
        val fragments = mutableListOf<Fragment>()
        val leftGlyphs = mutableListOf<PdfGlyph>()
        var columns = emptyList<DayColumn>()
        var previous = emptyList<Fragment>()
        var offset = 0.0
        var heading = ""
        pages.forEachIndexed { index, page ->
            val cells = cells(page.lines)
            require(cells.isNotEmpty()) { "第 ${index + 1} 页未找到课表边框，请使用带完整表格的文字 PDF" }
            val top = cells.minOf { it.top }; val bottom = cells.maxOf { it.bottom }
            if (index == 0) {
                val text = page.glyphs.joinToString("") { it.text }
                heading = Regex("\\d{4}学年度第[一二三123]学期").find(text)?.value ?: "导入学期"
                val indexed = page.glyphs.flatMap { g -> g.text.map { it to g } }
                val all = indexed.joinToString("") { it.first.toString() }
                columns = Regex("星期([一二三四五六日天])").findAll(all).map { m ->
                    // The first glyph can overhang a narrow header cell. The final day glyph is its anchor.
                    val anchor = indexed[m.range.last].second
                    val cell = cells.singleOrNull { it.contains(anchor.x, anchor.y) }
                        ?: throw IllegalArgumentException("PDF 星期表头位置不清晰")
                    DayColumn(cell.left, cell.right, "一二三四五六日天".indexOf(m.groupValues[1]).let { if (it >= 6) 7 else it + 1 })
                }.distinct().toList()
                require(columns.size == 7 && columns.map { it.day }.toSet().size == 7) { "PDF 星期表头不完整，请导出全部课表" }
                require(columns.sortedBy { it.left }.zipWithNext().all { abs(it.first.right - it.second.left) < 1 }) { "PDF 星期表格无法对齐" }
            }
            val scheduleLeft = columns.minOf { it.left }
            page.glyphs.filter { it.x < scheduleLeft && it.y >= top && it.y < bottom }.forEach {
                leftGlyphs += it.copy(y = offset + it.y - top)
            }
            val current = mutableListOf<Fragment>()
            cells.filter { it.left >= scheduleLeft - 1 }.forEach { cell ->
                // Use glyph origins, not glyph centres: Word's narrow columns let Chinese glyphs overhang.
                val text = page.glyphs.filter { cell.contains(it.x, it.y) }.joinToString("") { it.text }
                var next = Fragment(cell.left, cell.right, offset + cell.top - top, offset + cell.bottom - top, text)
                val before = if (abs(cell.top - top) < 1) previous.singleOrNull {
                    abs(it.left - cell.left) < 1 && abs(it.right - cell.right) < 1
                } else null
                val newRecord = before != null && Regex("\\d+人\\s*$").containsMatchIn(before.text) && weekStart.containsMatchIn(text)
                if (before != null && !newRecord) {
                    before.text += text; before.bottom = next.bottom; next = before
                } else fragments += next
                if (abs(cell.bottom - bottom) < 1) current += next
            }
            previous = current
            offset += bottom - top
        }
        val indexed = leftGlyphs.flatMap { g -> g.text.map { it to g.y } }.filterNot { it.first.isWhitespace() }
        val periodText = indexed.joinToString("") { it.first.toString() }
        val markers = time.findAll(periodText).map { m ->
            val period = Period(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3])
            require(LocalTime.parse(period.start) < LocalTime.parse(period.end)) { "PDF 节次时间异常" }
            Marker(period, indexed[m.range.first].second)
        }.toList()
        require(markers.isNotEmpty() && markers.map { it.period.number } == (1..markers.size).toList()) { "PDF 节次不连续或缺页，未导入" }
        val raws = fragments.filter { '/' in it.text }.map { f ->
            require(weekStart.containsMatchIn(f.text)) { "PDF 跨页课程未能完整拼接：${f.text.take(35)}" }
            require(Regex("/[0-9,、，\\-]+(?:每周|单周|双周)/").find(f.text) == null) { "PDF 分页边界不明确，请重新导出" }
            val day = columns.singleOrNull { f.left >= it.left - 1 && f.right <= it.right + 1 }
                ?: throw IllegalArgumentException("PDF 课程跨越星期边界，请核对文件")
            val covered = markers.filter { it.y >= f.top - 1 && it.y < f.bottom }
            require(covered.isNotEmpty()) { "无法定位 PDF 课程节次：${f.text.take(35)}" }
            ParsedLesson(day.day, covered.first().period.number, covered.last().period.number, f.text)
        }
        require(raws.isNotEmpty()) { "PDF 中没有可识别的课程" }
        // Do not silently import a partial table when a nonblank course fragment was disconnected.
        require(fragments.none { it.top > markers.first().y && it.text.isNotBlank() && '/' !in it.text }) { "PDF 中有不完整课程片段，请导出完整课表" }
        return ScheduleAssembler.assemble(heading, firstMonday, markers.map { it.period }, raws)
    }

    /** Find minimal rectangles between connected axis-aligned rules, retaining merged cells. */
    private fun cells(lines: List<PdfLine>): List<Cell> {
        val vertical = lines.filter { abs(it.x0 - it.x1) < 0.8 && abs(it.y1 - it.y0) > 4 }
        val horizontal = lines.filter { abs(it.y0 - it.y1) < 0.8 && abs(it.x1 - it.x0) > 4 }
        fun cluster(values: List<Double>): List<Double> {
            val groups = mutableListOf<MutableList<Double>>()
            values.sorted().forEach { v -> if (groups.isEmpty() || v - groups.last().last() > 1) groups.add(mutableListOf(v)) else groups.last().add(v) }
            return groups.map { it.average() }
        }
        val ys = cluster(horizontal.map { (it.y0 + it.y1) / 2 })
        require(ys.size <= 200 && vertical.size <= 2000) { "PDF 表格过于复杂" }
        val result = linkedSetOf<Cell>()
        ys.zipWithNext().forEach { (a, b) ->
            val middle = (a + b) / 2
            val xs = cluster(vertical.filter { min(it.y0, it.y1) <= middle && max(it.y0, it.y1) >= middle }.map { (it.x0 + it.x1) / 2 })
            xs.zipWithNext().forEach { (left, right) ->
                val boundaries = horizontal.filter { min(it.x0, it.x1) <= left + 1 && max(it.x0, it.x1) >= right - 1 }.map { line -> ys.minBy { abs(it - line.y0) } }.distinct()
                val top = boundaries.filter { it <= middle }.maxOrNull()
                val bottom = boundaries.filter { it >= middle }.minOrNull()
                if (top != null && bottom != null && right - left > 4) result += Cell(left, top, right, bottom)
            }
        }
        return result.toList()
    }
}
