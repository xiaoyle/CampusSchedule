package cn.campus.core

import kotlinx.serialization.Serializable
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Serializable data class WebCell(val text: String, val rowSpan: Int = 1, val colSpan: Int = 1)
@Serializable data class WebTableSnapshot(val title: String, val rows: List<List<WebCell>>, val allWeeks: Boolean)

object SchoolUrlPolicy {
    const val LOGIN = "https://jwxt.sysu.edu.cn/jwxt/#/login"
    fun school(url: String?): Boolean = runCatching {
        val uri = URI(url ?: "")
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
            (host == "sysu.edu.cn" || host.endsWith(".sysu.edu.cn"))
    }.getOrDefault(false)
    fun timetable(url: String?): Boolean = school(url) && runCatching {
        val uri = URI(url!!)
        uri.host.equals("jwxt.sysu.edu.cn", true) && uri.path == "/jwxt/mk/schedule-web/" &&
            uri.fragment?.substringBefore('?') == "/studentTimeTabPrint"
    }.getOrDefault(false)
}

/** HTML spans are expanded before assigning a course to a weekday and consecutive periods. */
class WebScheduleParser {
    fun parse(snapshot: WebTableSnapshot, firstMonday: String): ImportResult {
        require(LocalDate.parse(firstMonday).dayOfWeek == DayOfWeek.MONDAY) { "第 1 周起始日期必须是周一" }
        require(snapshot.allWeeks) { "请在课表查询中选择“全部”周次后导入" }
        require(snapshot.rows.size in 2..80 && snapshot.rows.sumOf { it.size } <= 6000) { "网页课表为空或过大" }
        val grid = Array(snapshot.rows.size) { arrayOfNulls<WebCell>(256) }
        data class Placed(val row: Int, val col: Int, val cell: WebCell)
        val placed = mutableListOf<Placed>()
        snapshot.rows.forEachIndexed { row, cells ->
            var col = 0
            cells.forEach { cell ->
                require(cell.text.length <= 12000 && cell.rowSpan in 1..80 && cell.colSpan in 1..255) { "网页课表单元格异常" }
                while (col < 256 && grid[row][col] != null) col++
                require(col + cell.colSpan <= 256 && row + cell.rowSpan <= grid.size) { "网页课表合并范围异常" }
                for (r in row until row + cell.rowSpan) for (c in col until col + cell.colSpan) {
                    require(grid[r][c] == null) { "网页课表存在重叠单元格" }
                    grid[r][c] = cell
                }
                placed += Placed(row, col, cell)
                col += cell.colSpan
            }
        }
        val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val header = snapshot.rows.indexOfFirst { row -> row.any { it.text.trim() == "星期一" } }
        require(header >= 0) { "未找到星期表头，请等待完整课表加载" }
        val days = grid[header].map { cell ->
            dayNames.indexOf(cell?.text?.trim()?.replace("星期天", "星期日")).let { if (it >= 0) it + 1 else null }
        }
        require(days.filterNotNull().toSet().size == 7) { "星期表头不完整，请等待完整课表加载" }
        val width = grid[header].indexOfLast { it != null } + 1
        require((header + 1 until grid.size).all { r -> (0 until width).all { grid[r][it] != null } && (width until 256).all { grid[r][it] == null } }) { "网页课表列数不完整，请使用文件导入" }
        val periodPattern = Regex("第(\\d+)节\\s*(\\d{2}:\\d{2})[~～—-](\\d{2}:\\d{2})")
        val rowPeriods = (header + 1 until grid.size).associateWith { row ->
            val match = periodPattern.matchEntire(grid[row][0]?.text.orEmpty().replace(Regex("\\s+"), ""))
                ?: error("无法识别网页节次时间，请使用文件导入")
            Period(match.groupValues[1].toInt(), match.groupValues[2], match.groupValues[3]).also {
                require(LocalTime.parse(it.start) < LocalTime.parse(it.end)) { "网页节次时间异常" }
            }
        }
        val periods = rowPeriods.values.toList()
        require(periods.map { it.number } == (1..periods.size).toList()) { "网页课表节次不连续" }
        val raws = placed.filter { it.row > header && it.col > 0 && it.cell.text.isNotBlank() }.map { p ->
            val mapped = (p.col until p.col + p.cell.colSpan).map { days[it] }.toSet()
            require(mapped.size == 1 && mapped.single() != null) { "发现跨星期课程，请改用文件导入" }
            ParsedLesson(mapped.single()!!, rowPeriods.getValue(p.row).number,
                rowPeriods.getValue(p.row + p.cell.rowSpan - 1).number, p.cell.text.trim())
        }
        require(raws.isNotEmpty()) { "当前课表没有可导入课程，旧课表已保留" }
        val term = Regex("\\d{4}学年度第[一二三123]学期").find(snapshot.title)?.value ?: "导入学期"
        return ScheduleAssembler.assemble(term, firstMonday, periods, raws)
    }
}
