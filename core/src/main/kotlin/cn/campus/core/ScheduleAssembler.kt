package cn.campus.core

data class ParsedLesson(val weekday: Int, val start: Int, var end: Int, val text: String)

object ScheduleAssembler {
    fun assemble(term: String, firstMonday: String, periods: List<Period>, raws: List<ParsedLesson>): ImportResult {
        data class Piece(val raw: ParsedLesson, val title: String, val weeks: List<Int>, val candidate: Candidate)
        val pieces = raws.map { raw ->
            val fields = raw.text.split('/')
            require(fields.size >= 4) { "课程信息不完整：${raw.text.take(50)}" }
            val title = fields[1].replace(Regex("^本[（(][^）)]*[）)]"), "").trim()
            require(title.isNotBlank()) { "课程名称为空" }
            Piece(raw, title, WordScheduleParser().parseWeeks(fields[0]), Candidate(fields[2].trim(), fields[3].trim()))
        }
        val rules = pieces.groupBy { "${it.raw.weekday}|${it.raw.start}|${it.raw.end}|${it.title}" }.map { (key, group) ->
            val byWeek = sortedMapOf<Int, MutableSet<Candidate>>()
            group.forEach { piece -> piece.weeks.forEach { byWeek.getOrPut(it) { linkedSetOf() }.add(piece.candidate) } }
            val variants = byWeek.entries.groupBy { it.value.sortedWith(compareBy({ it.teacher }, { it.location })) }
                .map { (candidates, entries) -> Variant(entries.map { it.key }.sorted(), candidates) }.sortedBy { it.weeks.first() }
            val first = group.first()
            LessonRule(stableId(key), first.title, first.raw.weekday, first.raw.start, first.raw.end, variants)
        }.sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.title }))
        val warnings = buildList {
            val ambiguous = rules.count { it.variants.any { v -> v.candidates.size > 1 } }
            if (ambiguous > 0) add("$ambiguous 项课程含多个教师或教室，请在对应日期的课程详情中确认")
            if (rules.any { it.variants.any { v -> v.candidates.any { c -> c.location.isBlank() } } }) add("部分课程未提供地点，可在课程详情补充")
            add("节假日与临时调课请按学校通知修改；已导入课表不会自动更新")
        }
        return ImportResult(Schedule(term, firstMonday, periods, rules), warnings)
    }
}
