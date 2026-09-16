package cn.campus.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

enum class ImageImportKind { AUTO, FULL_TERM_TABLE, WEEKLY_MOBILE }

data class ImageOcrToken(
    val pageIndex: Int,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float = 1f
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val height get() = (bottom - top).coerceAtLeast(1f)
}

data class ImageOcrPage(
    val index: Int,
    val width: Int,
    val height: Int,
    val tokens: List<ImageOcrToken>
)

data class ImageImportRow(
    val id: String,
    val weekExpression: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val title: String,
    val teacher: String = "",
    val location: String = "",
    val confidence: Float = 1f,
    val issues: List<String> = emptyList()
)

data class ImageImportDraft(
    val kind: ImageImportKind,
    val term: String,
    val firstMonday: String,
    val periods: List<Period>,
    val rows: List<ImageImportRow>,
    val capturedWeeks: List<Int>,
    val warnings: List<String>
) {
    fun toImportResult(): ImportResult {
        require(LocalDate.parse(firstMonday).dayOfWeek == DayOfWeek.MONDAY) { "第 1 周起始日期必须是周一" }
        require(rows.isNotEmpty()) { "图片中没有可保存的课程" }
        val raws = rows.map { row ->
            require(row.weekday in 1..7) { "请检查课程星期" }
            require(row.startPeriod in periods.indices.map { it + 1 } && row.endPeriod in row.startPeriod..periods.size) { "请检查课程节次" }
            require(row.title.trim().isNotEmpty()) { "请填写课程名称" }
            ParsedLesson(
                row.weekday,
                row.startPeriod,
                row.endPeriod,
                "${row.weekExpression.trim()}/${row.title.trim()}/${row.teacher.trim()}/${row.location.trim()}"
            )
        }
        val assembled = ScheduleAssembler.assemble(term.ifBlank { "图片导入学期" }, firstMonday, periods, raws)
        return assembled.copy(warnings = (warnings + assembled.warnings).distinct())
    }
}

/** Pure layout parser. Android OCR only supplies text boxes; no image or account data reaches core. */
object ImageScheduleParser {
    private val weekPattern = Regex("([0-9]{1,2}(?:[-－—~～][0-9]{1,2})?(?:[、,，][0-9]{1,2}(?:[-－—~～][0-9]{1,2})?)*)\\s*(?:周)?\\s*(每周|单周|双周|单|双)?")
    private val semesterPattern = Regex("\\d{4}\\s*学年度?\\s*第[一二三123]学期")
    private val weekNumberPattern = Regex("第\\s*([0-9]{1,2})\\s*周")
    private val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val defaultPeriods = listOf(
        Period(1,"08:00","08:45"), Period(2,"08:55","09:40"),
        Period(3,"10:10","10:55"), Period(4,"11:05","11:50"),
        Period(5,"14:20","15:05"), Period(6,"15:15","16:00"),
        Period(7,"16:30","17:15"), Period(8,"17:25","18:10"),
        Period(9,"19:00","19:45"), Period(10,"19:55","20:40")
    )

    fun parse(pages: List<ImageOcrPage>, firstMonday: String, requestedKind: ImageImportKind = ImageImportKind.AUTO): ImageImportDraft {
        require(LocalDate.parse(firstMonday).dayOfWeek == DayOfWeek.MONDAY) { "第 1 周起始日期必须是周一" }
        require(pages.isNotEmpty() && pages.size <= 20) { "请选择 1 至 20 张课表图片" }
        require(pages.all { it.width > 0 && it.height > 0 }) { "图片尺寸无效" }
        val term = pages.asSequence().flatMap { it.tokens.asSequence() }.map { normalize(it.text) }
            .mapNotNull { semesterPattern.find(it)?.value?.replace(" ", "") }.firstOrNull() ?: "图片导入学期"
        val detected = if (pages.sumOf { page -> page.tokens.count { token -> dayIndex(token.text) != null } } >= 5)
            ImageImportKind.FULL_TERM_TABLE else ImageImportKind.WEEKLY_MOBILE
        val kind = if (requestedKind == ImageImportKind.AUTO) detected else requestedKind
        val rows = when (kind) {
            ImageImportKind.FULL_TERM_TABLE -> pages.flatMap(::parseFullPage)
            ImageImportKind.WEEKLY_MOBILE -> pages.flatMap(::parseWeeklyPage)
            ImageImportKind.AUTO -> error("unreachable")
        }.distinctBy { listOf(it.weekExpression,it.weekday,it.startPeriod,it.endPeriod,it.title,it.teacher,it.location).joinToString("|") }
        require(rows.isNotEmpty()) {
            if (kind == ImageImportKind.WEEKLY_MOBILE) "没有识别到课程卡片，请选择包含星期、节次和课程卡片的单周截图"
            else "没有识别到完整课表，请确保图片包含星期表头、节次和课程周次"
        }
        val captured = rows.flatMap { runCatching { WordScheduleParser().parseWeeks(it.weekExpression) }.getOrDefault(emptyList()) }.distinct().sorted()
        val warnings = buildList {
            if (kind == ImageImportKind.WEEKLY_MOBILE) add("手机单周图片只生成已选择的周次：${captured.joinToString("、")}")
            if (rows.any { it.confidence < .72f }) add("部分文字识别可信度较低，请逐项校对")
            if (rows.any { it.location.isBlank() }) add("部分课程没有识别到地点，可在校对页补充")
            add("图片识别可能出现错字，确认保存前请核对课程、周次和节次")
        }
        return ImageImportDraft(kind, term, firstMonday, defaultPeriods, rows, captured, warnings)
    }

    private fun parseFullPage(page: ImageOcrPage): List<ImageImportRow> {
        val headers = page.tokens.mapNotNull { token -> dayIndex(token.text)?.let { it to token } }
            .groupBy({it.first},{it.second}).mapValues { (_, values) -> values.minBy { it.top } }
        require(headers.size >= 5) { "第 ${page.index + 1} 张图片缺少星期表头" }
        val ordered = (1..7).mapNotNull { day -> headers[day]?.let { day to it.centerX } }
        val headerBottom = headers.values.maxOf { it.bottom }
        val periodAnchors = periodAnchors(page, headerBottom)
        return page.tokens.mapNotNull { token ->
            val text = normalize(token.text)
            val week = weekPattern.find(text)?.takeIf { match ->
                match.value.any(Char::isDigit) && (text.contains("周") || Regex("每周|单周|双周").containsMatchIn(text))
            } ?: return@mapNotNull null
            if (token.top <= headerBottom) return@mapNotNull null
            val weekday = nearestDay(token.centerX, ordered, page.width) ?: return@mapNotNull null
            val (start,end) = periodRange(token, periodAnchors)
            val details = courseDetails(text.substring(week.range.first))
            val title = details.first.ifBlank { return@mapNotNull null }
            val issues = buildList {
                if (token.confidence < .72f) add("文字可信度较低")
                if (details.third.isBlank()) add("地点未识别")
            }
            ImageImportRow("image-${page.index}-${token.left.toInt()}-${token.top.toInt()}", normalizeWeeks(week.value), weekday, start, end,
                title, details.second, details.third, token.confidence, issues)
        }
    }

    private fun parseWeeklyPage(page: ImageOcrPage): List<ImageImportRow> {
        val allText = page.tokens.joinToString(" ") { normalize(it.text) }
        val week = weekNumberPattern.find(allText)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IllegalArgumentException("第 ${page.index + 1} 张图片没有识别到“第几周”")
        require(week in 1..60) { "图片周次异常" }
        val headerCandidates = page.tokens.filter { token ->
            val text = normalize(token.text)
            (text.matches(Regex("(?:0?[1-9]|[12][0-9]|3[01])")) || shortDay(text) != null) && token.top < page.height * .45f
        }
        val rows = headerCandidates.groupBy { (it.centerY / (page.height * .035f).coerceAtLeast(12f)).toInt() }
        val best = rows.values.maxByOrNull { group -> group.map { it.centerX.toInt() }.distinct().size }.orEmpty().sortedBy { it.centerX }
        val xs = if (best.size >= 7) best.take(7).mapIndexed { i, token -> (i + 1) to token.centerX }
        else page.tokens.mapNotNull { token -> shortDay(normalize(token.text))?.let { it to token.centerX } }.distinctBy { it.first }.sortedBy { it.first }
        require(xs.size >= 5) { "第 ${page.index + 1} 张图片没有识别到完整星期栏" }
        val headerBottom = best.maxOfOrNull { it.bottom } ?: page.height * .28f
        val anchors = periodAnchors(page, headerBottom)
        val excluded = Regex("上一周|下一周|更多|调停|已改时间|学期|星期|课表|返回|导入")
        val approximateColumn = page.width / 7f
        return page.tokens.mapNotNull { token ->
            val text = normalize(token.text).trim('/',' ')
            if (token.top <= headerBottom || text.length < 2 || excluded.containsMatchIn(text) || weekNumberPattern.containsMatchIn(text)) return@mapNotNull null
            if (Regex("^\\d{1,2}(?::\\d{2})?$").matches(text)) return@mapNotNull null
            val weekday = nearestDay(token.centerX, xs, page.width) ?: return@mapNotNull null
            val expectedX = xs.minBy { abs(it.second - token.centerX) }.second
            if (abs(expectedX - token.centerX) > approximateColumn * .65f) return@mapNotNull null
            val (start,end) = periodRange(token, anchors)
            val parts = text.replace(Regex("/{2,}"), "/").split('/').map(String::trim).filter(String::isNotBlank)
            val title = parts.firstOrNull()?.replace(Regex("^本[（(][^）)]*[）)]"), "")?.trim().orEmpty()
            if (title.length < 2 || title.all { it.isDigit() || it == ':' || it == '-' }) return@mapNotNull null
            val location = parts.drop(1).firstOrNull { locationHint(it) }.orEmpty()
            val teacher = parts.drop(1).firstOrNull { it != location }.orEmpty()
            val issues = buildList {
                if (token.confidence < .72f) add("文字可信度较低")
                if (location.isBlank()) add("地点未识别")
            }
            ImageImportRow("image-${page.index}-${token.left.toInt()}-${token.top.toInt()}", "${week}周每周", weekday,start,end,title,teacher,location,token.confidence,issues)
        }.filterNot { candidate ->
            // A course card is normally wider/taller than a single navigation label.
            tokenLikeNavigation(candidate.title) && candidate.location.isBlank()
        }
    }

    private fun periodAnchors(page: ImageOcrPage, headerBottom: Float): List<Pair<Int,Float>> {
        val explicit = page.tokens.flatMap { token ->
            Regex("第?\\s*([0-9]{1,2})\\s*节").findAll(normalize(token.text)).mapNotNull { match ->
                match.groupValues[1].toIntOrNull()?.takeIf { it in 1..10 }?.let { it to token.centerY }
            }.toList()
        }.distinctBy { it.first }.sortedBy { it.first }
        if (explicit.size >= 4) return explicit
        val startTimes = defaultPeriods.associateBy { it.start }
        val times = page.tokens.mapNotNull { token ->
            Regex("(?:0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]").find(normalize(token.text))?.value?.let { time -> startTimes[time]?.number?.let { it to token.centerY } }
        }.distinctBy { it.first }.sortedBy { it.first }
        if (times.size >= 4) return times
        val usableTop = headerBottom + page.height * .025f
        val usableHeight = (page.height - usableTop).coerceAtLeast(100f)
        return defaultPeriods.mapIndexed { index, period -> period.number to (usableTop + usableHeight * (index + .5f) / defaultPeriods.size) }
    }

    private fun periodRange(token: ImageOcrToken, anchors: List<Pair<Int,Float>>): Pair<Int,Int> {
        val start = anchors.minBy { abs(it.second - (token.top + token.height * .18f)) }.first
        val end = anchors.minBy { abs(it.second - (token.bottom - token.height * .18f)) }.first
        return minOf(start,end) to maxOf(start,end)
    }

    private fun nearestDay(x: Float, anchors: List<Pair<Int,Float>>, width: Int): Int? {
        val nearest = anchors.minByOrNull { abs(it.second - x) } ?: return null
        val tolerance = if (anchors.size > 1) anchors.sortedBy { it.second }.zipWithNext().map { it.second.second - it.first.second }.average().toFloat() * .75f else width / 5f
        return nearest.first.takeIf { abs(nearest.second - x) <= tolerance.coerceAtLeast(width * .07f) }
    }

    private fun courseDetails(text: String): Triple<String,String,String> {
        val normalized = normalize(text).replace(Regex("/{2,}"), "/")
        val week = weekPattern.find(normalized) ?: return Triple("","","")
        val rest = normalized.substring(week.range.last + 1).trim('/',' ')
        val parts = rest.split('/').map(String::trim).filter(String::isNotBlank)
        val title = parts.firstOrNull()?.replace(Regex("^本[（(][^）)]*[）)]"), "")?.trim().orEmpty()
        val location = parts.drop(1).firstOrNull(::locationHint) ?: parts.getOrNull(2).orEmpty()
        val teacher = parts.drop(1).firstOrNull { it != location }.orEmpty()
        return Triple(title,teacher,location)
    }

    private fun dayIndex(value: String): Int? {
        val text = normalize(value)
        return dayNames.indexOfFirst { text.contains(it) }.takeIf { it >= 0 }?.plus(1)
            ?: if (text.contains("星期天")) 7 else null
    }
    private fun shortDay(value:String):Int? = when(value.trim()) {"一"->1;"二"->2;"三"->3;"四"->4;"五"->5;"六"->6;"日","天"->7;else->null}
    private fun locationHint(value:String)=Regex("楼|室|馆|场|校区|园|中心|教室|体育").containsMatchIn(value)
    private fun tokenLikeNavigation(value:String)=value in setOf("课程","考试","任务","今天","全部","首页","消息")
    private fun normalizeWeeks(value:String):String=value.replace(" ","").replace('－','-').replace('—','-').replace('~','-').replace('～','-').replace('，',',').replace('、',',')
        .let { if (it.contains("周")) it else it.replace(Regex("(每周|单周|双周|单|双)$"), "周$1") }
    private fun normalize(value:String)=value.replace('\n','/').replace('\r','/').replace('／','/').replace(Regex("\\s+"), " ").trim()
}
