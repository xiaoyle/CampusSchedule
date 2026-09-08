package cn.campus.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.*
import java.time.temporal.ChronoUnit

val SCHOOL_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
val dataJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable data class Period(val number: Int, val start: String, val end: String)
@Serializable data class Candidate(val teacher: String, val location: String)
@Serializable data class Variant(val weeks: List<Int>, val candidates: List<Candidate>)
@Serializable data class LessonRule(
    val id: String, val title: String, val weekday: Int,
    val startPeriod: Int, val endPeriod: Int, val variants: List<Variant>
)
@Serializable data class Schedule(
    val term: String, val firstMonday: String, val periods: List<Period>, val rules: List<LessonRule>
) {
    val lastWeek get() = rules.flatMap { it.variants }.flatMap { it.weeks }.maxOrNull() ?: 0
}
@Serializable data class LessonEdit(
    val ruleId: String, val originalDate: String, val cancelled: Boolean = false,
    val date: String? = null, val start: String? = null, val end: String? = null,
    val location: String? = null, val teacher: String? = null
)
@Serializable data class AppData(
    val schedule: Schedule? = null, val edits: List<LessonEdit> = emptyList(),
    val reminderMinutes: Int = 10, val importedAt: String? = null, val alarmEnabled: Boolean = true
)
data class ImportResult(val schedule: Schedule, val warnings: List<String>)
data class ImportDiff(val added: Int, val removed: Int, val changed: Int, val unmatched: List<LessonEdit>)
data class Occurrence(
    val key: String, val ruleId: String, val title: String, val originalDate: String,
    val date: LocalDate, val start: LocalTime, val end: LocalTime, val candidates: List<Candidate>,
    val modified: Boolean, val cancelled: Boolean = false
) {
    val startInstant: Instant get() = date.atTime(start).atZone(SCHOOL_ZONE).toInstant()
    val endInstant: Instant get() = date.atTime(end).atZone(SCHOOL_ZONE).toInstant()
    val locationText: String get() = if (candidates.size > 1) "多个安排待确认" else candidates.firstOrNull()?.location?.ifBlank { "地点待确认" } ?: "地点待确认"
}
fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

object ScheduleEngine {
    fun week(date: LocalDate, firstMonday: String) = Math.floorDiv(
        ChronoUnit.DAYS.between(LocalDate.parse(firstMonday), date), 7L).toInt() + 1

    fun occurrences(data: AppData, includeCancelled: Boolean = false): List<Occurrence> {
        val schedule = data.schedule ?: return emptyList()
        val monday = LocalDate.parse(schedule.firstMonday)
        val edits = data.edits.associateBy { "${it.ruleId}@${it.originalDate}" }
        val periods = schedule.periods.associateBy { it.number }
        return schedule.rules.flatMap { rule -> rule.variants.flatMap { variant -> variant.weeks.mapNotNull { week ->
            val original = monday.plusWeeks((week - 1).toLong()).plusDays((rule.weekday - 1).toLong())
            val key = "${rule.id}@$original"
            val edit = edits[key]
            if (edit?.cancelled == true && !includeCancelled) null else Occurrence(
                key, rule.id, rule.title, original.toString(), LocalDate.parse(edit?.date ?: original.toString()),
                LocalTime.parse(edit?.start ?: periods.getValue(rule.startPeriod).start),
                LocalTime.parse(edit?.end ?: periods.getValue(rule.endPeriod).end),
                if (edit?.location != null || edit?.teacher != null) listOf(Candidate(
                    edit.teacher ?: variant.candidates.map { it.teacher }.distinct().joinToString("、"),
                    edit.location ?: variant.candidates.map { it.location }.distinct().joinToString(" / ")
                )) else variant.candidates,
                edit != null, edit?.cancelled ?: false
            )
        } } }.sortedWith(compareBy({ it.date }, { it.start }, { it.title }))
    }

    fun conflicts(items: List<Occurrence>): Set<String> {
        val result = mutableSetOf<String>()
        val active = items.filterNot { it.cancelled }
        active.forEachIndexed { i, a -> active.drop(i + 1).forEach { b ->
            if (a.date == b.date && a.start < b.end && b.start < a.end) { result += a.key; result += b.key }
        } }
        return result
    }

    // PDF exporters can drop layout spaces. Match only a unique semantic course, preserving old edit IDs.
    private fun alignIds(old: AppData, incoming: Schedule): Schedule {
        fun key(r: LessonRule) = "${r.weekday}|${r.startPeriod}|${r.endPeriod}|${r.title.replace(Regex("\\s+"), "")}"
        val before = old.schedule?.rules.orEmpty().groupBy(::key)
        val after = incoming.rules.groupBy(::key)
        val exact = old.schedule?.rules.orEmpty().map { it.id }.toSet()
        return incoming.copy(rules = incoming.rules.map { r ->
            val match = before[key(r)]?.singleOrNull()
            if (r.id !in exact && match != null && after[key(r)]?.size == 1) r.copy(id = match.id) else r
        })
    }

    fun diff(old: AppData, source: Schedule): ImportDiff {
        val incoming = alignIds(old, source)
        val before = old.schedule?.rules?.associateBy { it.id }.orEmpty()
        val after = incoming.rules.associateBy { it.id }
        val valid = occurrences(AppData(incoming)).map { it.key }.toSet()
        return ImportDiff((after.keys - before.keys).size, (before.keys - after.keys).size,
            after.count { (id, rule) -> before[id]?.let { it != rule || old.schedule?.periods != incoming.periods || old.schedule.firstMonday != incoming.firstMonday } ?: false },
            old.edits.filter { "${it.ruleId}@${it.originalDate}" !in valid })
    }

    fun merge(old: AppData, source: Schedule): AppData {
        val incoming = alignIds(old, source)
        val unmatched = diff(old, incoming).unmatched.toSet()
        return old.copy(schedule = incoming, edits = old.edits.filterNot { it in unmatched }, importedAt = Instant.now().toString())
    }

    fun validateEdit(edit: LessonEdit) {
        LocalDate.parse(edit.originalDate)
        edit.date?.let { LocalDate.parse(it) }
        require((edit.start == null) == (edit.end == null)) { "请同时填写开始和结束时间" }
        if (edit.start != null) require(LocalTime.parse(edit.start) < LocalTime.parse(edit.end)) { "结束时间须晚于开始时间" }
    }
}
