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
@Serializable data class ManualLesson(
    val id: String, val date: String, val title: String,
    val start: String, val end: String,
    val teacher: String = "", val location: String = "",
    val color: Long = 0xFF176B52,
    val repeatCount: Int = 1,
    val revisions: List<ManualLessonRevision> = emptyList(),
    val instanceEdits: List<ManualLessonInstanceEdit> = emptyList()
)
@Serializable data class ManualLessonRevision(
    val effectiveIndex: Int, val date: String, val title: String,
    val start: String, val end: String, val teacher: String = "",
    val location: String = "", val color: Long = 0xFF176B52
)
@Serializable data class ManualLessonInstanceEdit(
    val index: Int, val cancelled: Boolean = false,
    val date: String? = null, val title: String? = null,
    val start: String? = null, val end: String? = null,
    val teacher: String? = null, val location: String? = null,
    val color: Long? = null
)
enum class ManualLessonEditScope { INSTANCE, FUTURE, SERIES }
@Serializable enum class StudyTaskType { HOMEWORK, EXAM, REVIEW, OTHER }
@Serializable enum class StudyTaskPriority { NORMAL, IMPORTANT, URGENT }
@Serializable enum class TaskRepeatKind { DAILY, WEEKLY, CUSTOM_WEEKDAYS }
@Serializable data class StudySubtask(val id: String, val title: String, val order: Int = 0)
@Serializable data class TaskRepeatRule(
    val kind: TaskRepeatKind,
    val weekdays: List<Int> = emptyList(),
    val endsOn: String? = null
)
@Serializable data class TaskInstanceOverride(
    val title: String,
    val type: StudyTaskType,
    val courseRuleId: String? = null,
    val courseTitle: String = "",
    val dueAt: String,
    val priority: StudyTaskPriority,
    val note: String = "",
    val remindBeforeMinutes: Int? = null,
    val subtasks: List<StudySubtask> = emptyList(),
    val estimatedMinutes: Int? = null
)
@Serializable data class TaskInstanceState(
    val occurrenceKey: String,
    val completedAt: String? = null,
    val completedSubtaskIds: List<String> = emptyList(),
    val deleted: Boolean = false,
    val override: TaskInstanceOverride? = null
)
@Serializable data class StudyTask(
    val id: String,
    val title: String,
    val type: StudyTaskType = StudyTaskType.HOMEWORK,
    val courseRuleId: String? = null,
    val courseTitle: String = "",
    val dueAt: String? = null,
    val priority: StudyTaskPriority = StudyTaskPriority.NORMAL,
    val note: String = "",
    val remindBeforeMinutes: Int? = null,
    val completedAt: String? = null,
    val createdAt: String = Instant.now().toString(),
    val subtasks: List<StudySubtask> = emptyList(),
    val repeatRule: TaskRepeatRule? = null,
    val instanceStates: List<TaskInstanceState> = emptyList(),
    val estimatedMinutes: Int? = null
)
@Serializable data class LearningGoal(val weeklyTarget: Int = 5)
@Serializable enum class FocusMode { COUNTDOWN, STOPWATCH, BREAK }
@Serializable enum class FocusStatus { RUNNING, PAUSED, COMPLETED, STOPPED, INTERRUPTED }
@Serializable enum class AmbientSound { NONE, RAIN, WAVES, LIBRARY }
@Serializable data class FocusSession(
    val id:String, val mode:FocusMode, val title:String,
    val courseRuleId:String?=null, val taskId:String?=null, val taskOccurrenceKey:String?=null,
    val startedAt:String, val endedAt:String, val focusedSeconds:Long,
    val plannedSeconds:Long?=null, val ambientSound:AmbientSound=AmbientSound.NONE,
    val status:FocusStatus=FocusStatus.COMPLETED, val note:String=""
)
@Serializable data class ActiveFocusState(
    val id:String, val mode:FocusMode, val title:String,
    val courseRuleId:String?=null, val taskId:String?=null, val taskOccurrenceKey:String?=null,
    val plannedSeconds:Long?=null, val accumulatedSeconds:Long=0,
    val startedAt:String, val runStartedAt:String, val runStartedElapsedMs:Long=0,
    val status:FocusStatus=FocusStatus.RUNNING,
    val ambientSound:AmbientSound=AmbientSound.NONE, val volume:Float=.35f
)
@Serializable data class FocusSettings(
    val lastMinutes:Int=25, val weeklyMinutesTarget:Int=300,
    val ambientSound:AmbientSound=AmbientSound.NONE, val ambientVolume:Float=.35f
)
@Serializable enum class AchievementUnlockMode { AUTO, MANUAL }
@Serializable enum class AchievementMetric {
    TOTAL_FOCUS_MINUTES, SINGLE_FOCUS_MINUTES, FOCUS_STREAK_DAYS,
    COMPLETED_TASKS, ON_TIME_TASKS, WEEKLY_FOCUS_MINUTES, COURSE_FOCUS_MINUTES
}
@Serializable enum class BadgeShape { CIRCLE, SHIELD, HEXAGON, DIAMOND }
@Serializable enum class BadgeTexture { SOLID, RADIAL, STARS, WAVES, GRID }
@Serializable enum class BadgeBorder { GOLD, SILVER, NONE }
@Serializable data class BadgeDesign(
    val shape:BadgeShape=BadgeShape.CIRCLE, val primary:Long=0xFF176B52,
    val accent:Long=0xFFFFC75F, val border:BadgeBorder=BadgeBorder.GOLD,
    val texture:BadgeTexture=BadgeTexture.RADIAL, val icon:String="star",
    val glyph:String="", val image:String="", val imageZoom:Float=1f,
    val imageX:Float=.5f, val imageY:Float=.5f
)
@Serializable data class AchievementDefinition(
    val id:String, val name:String, val description:String,
    val unlockMode:AchievementUnlockMode=AchievementUnlockMode.AUTO,
    val metric:AchievementMetric?=null, val target:Long=1,
    val courseRuleId:String?=null, val badge:BadgeDesign=BadgeDesign(),
    val builtIn:Boolean=false, val createdAt:String=Instant.now().toString()
)
@Serializable data class AchievementProgress(
    val achievementId:String, val current:Long=0, val secondaryCurrent:Long=0,
    val unlockedAt:String?=null, val celebrationSeen:Boolean=false,
    val evidenceKeys:List<String> = emptyList()
)
@Serializable data class AppData(
    val schedule: Schedule? = null, val edits: List<LessonEdit> = emptyList(),
    val reminderMinutes: Int = 10, val importedAt: String? = null, val alarmEnabled: Boolean = true,
    val manualLessons: List<ManualLesson> = emptyList(),
    val courseColors: Map<String, Long> = emptyMap(),
    val studyTasks: List<StudyTask> = emptyList(),
    val learningGoal: LearningGoal = LearningGoal(),
    val focusSessions:List<FocusSession> = emptyList(),
    val activeFocus:ActiveFocusState? = null,
    val focusSettings:FocusSettings = FocusSettings(),
    val customAchievements:List<AchievementDefinition> = emptyList(),
    val achievementProgress:List<AchievementProgress> = emptyList(),
    val featuredAchievementIds:List<String> = emptyList()
)
data class ImportResult(val schedule: Schedule, val warnings: List<String>)
data class ImportDiff(val added: Int, val removed: Int, val changed: Int, val unmatched: List<LessonEdit>)
data class Occurrence(
    val key: String, val ruleId: String, val title: String, val originalDate: String,
    val date: LocalDate, val start: LocalTime, val end: LocalTime, val candidates: List<Candidate>,
    val modified: Boolean, val cancelled: Boolean = false,
    val manual: Boolean = false, val color: Long? = null,
    val manualIndex: Int? = null, val manualRepeatCount: Int = 1
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
        val imported = data.schedule?.let { schedule ->
            val monday = LocalDate.parse(schedule.firstMonday)
            val edits = data.edits.associateBy { "${it.ruleId}@${it.originalDate}" }
            val periods = schedule.periods.associateBy { it.number }
            schedule.rules.flatMap { rule -> rule.variants.flatMap { variant -> variant.weeks.mapNotNull { week ->
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
                    edit != null, edit?.cancelled ?: false, color=data.courseColors[rule.id]
                )
            } } }
        }.orEmpty()
        val manual = data.manualLessons.flatMap { lesson ->
            validateManualLesson(lesson)
            (0 until lesson.repeatCount).mapNotNull { index ->
                val revision=lesson.revisions.filter{it.effectiveIndex<=index}.maxByOrNull{it.effectiveIndex}
                val baseDate=revision?.let{LocalDate.parse(it.date).plusWeeks((index-it.effectiveIndex).toLong())}
                    ?:LocalDate.parse(lesson.date).plusWeeks(index.toLong())
                val edit=lesson.instanceEdits.lastOrNull{it.index==index}
                if(edit?.cancelled==true&&!includeCancelled)null else Occurrence(
                    key="manual@${lesson.id}@$index",ruleId=lesson.id,
                    title=edit?.title?:revision?.title?:lesson.title,
                    originalDate=baseDate.toString(),date=edit?.date?.let(LocalDate::parse)?:baseDate,
                    start=LocalTime.parse(edit?.start?:revision?.start?:lesson.start),
                    end=LocalTime.parse(edit?.end?:revision?.end?:lesson.end),
                    candidates=listOf(Candidate(edit?.teacher?:revision?.teacher?:lesson.teacher,edit?.location?:revision?.location?:lesson.location)),
                    modified=true,cancelled=edit?.cancelled?:false,manual=true,
                    color=edit?.color?:revision?.color?:lesson.color,manualIndex=index,manualRepeatCount=lesson.repeatCount
                )
            }
        }
        return (imported + manual).sortedWith(compareBy({ it.date }, { it.start }, { it.title }))
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

    fun validateManualLesson(lesson: ManualLesson) {
        require(lesson.id.isNotBlank()) { "课程编号不能为空" }
        require(lesson.title.trim().isNotEmpty()) { "请填写课程名称" }
        LocalDate.parse(lesson.date)
        val start = LocalTime.parse(lesson.start)
        val end = LocalTime.parse(lesson.end)
        require(start < end) { "结束时间须晚于开始时间" }
        require(lesson.repeatCount in 1..30) { "课程总次数须为1至30次" }
        lesson.revisions.forEach { revision ->
            require(revision.effectiveIndex in 0 until lesson.repeatCount) { "课程系列修改位置无效" }
            LocalDate.parse(revision.date)
            require(revision.title.trim().isNotEmpty()) { "请填写课程名称" }
            require(LocalTime.parse(revision.start)<LocalTime.parse(revision.end)) { "结束时间须晚于开始时间" }
        }
        lesson.instanceEdits.forEach { edit ->
            require(edit.index in 0 until lesson.repeatCount) { "单次课程修改位置无效" }
            edit.date?.let(LocalDate::parse)
            require((edit.start==null)==(edit.end==null)) { "请同时填写开始和结束时间" }
            if(edit.start!=null)require(LocalTime.parse(edit.start)<LocalTime.parse(edit.end)) { "结束时间须晚于开始时间" }
        }
    }

    fun updateManualLesson(original:ManualLesson?,draft:ManualLesson,scope:ManualLessonEditScope,index:Int=0):ManualLesson {
        validateManualLesson(draft)
        if(original==null)return draft.copy(revisions=emptyList(),instanceEdits=emptyList())
        validateManualLesson(original)
        require(index in 0 until original.repeatCount) { "课程次数无效" }
        val updated=when(if(original.repeatCount==1)ManualLessonEditScope.SERIES else scope) {
            ManualLessonEditScope.SERIES->draft.copy(
                id=original.id,
                revisions=emptyList(),
                instanceEdits=emptyList()
            )
            ManualLessonEditScope.FUTURE->original.copy(
                revisions=original.revisions.filter{it.effectiveIndex<index}+ManualLessonRevision(index,draft.date,draft.title,draft.start,draft.end,draft.teacher,draft.location,draft.color),
                instanceEdits=original.instanceEdits.filter{it.index<index}
            )
            ManualLessonEditScope.INSTANCE->original.copy(
                instanceEdits=original.instanceEdits.filterNot{it.index==index}+ManualLessonInstanceEdit(index,date=draft.date,title=draft.title,start=draft.start,end=draft.end,teacher=draft.teacher,location=draft.location,color=draft.color)
            )
        }
        validateManualLesson(updated);return updated
    }

    fun deleteManualLesson(original:ManualLesson,scope:ManualLessonEditScope,index:Int=0):ManualLesson? {
        validateManualLesson(original);require(index in 0 until original.repeatCount) { "课程次数无效" }
        return when(if(original.repeatCount==1)ManualLessonEditScope.SERIES else scope) {
            ManualLessonEditScope.SERIES->null
            ManualLessonEditScope.FUTURE->if(index==0)null else original.copy(
                repeatCount=index,revisions=original.revisions.filter{it.effectiveIndex<index},instanceEdits=original.instanceEdits.filter{it.index<index}
            ).also(::validateManualLesson)
            ManualLessonEditScope.INSTANCE->original.copy(
                instanceEdits=original.instanceEdits.filterNot{it.index==index}+ManualLessonInstanceEdit(index,cancelled=true)
            ).also(::validateManualLesson)
        }
    }
}
data class TaskOccurrence(
    val key: String,
    val taskId: String,
    val originalDueAt: String?,
    val title: String,
    val type: StudyTaskType,
    val courseRuleId: String?,
    val courseTitle: String,
    val dueAt: ZonedDateTime?,
    val priority: StudyTaskPriority,
    val note: String,
    val remindBeforeMinutes: Int?,
    val subtasks: List<StudySubtask>,
    val completedSubtaskIds: Set<String>,
    val completedAt: String?,
    val repeated: Boolean,
    val estimatedMinutes: Int?
)

data class StudyGap(val start:ZonedDateTime,val end:ZonedDateTime) {
    val minutes:Long get()=Duration.between(start,end).toMinutes().coerceAtLeast(0)
}
data class GapRecommendation(val gap:StudyGap,val task:TaskOccurrence?,val suggestedMinutes:Int)

object GapRadarEngine {
    fun recommend(now:ZonedDateTime,lessons:List<Occurrence>,tasks:List<TaskOccurrence>):GapRecommendation? {
        val end=now.toLocalDate().atTime(23,0).atZone(SCHOOL_ZONE)
        if(!now.isBefore(end))return null
        val occupied=lessons.filter{!it.cancelled&&it.date==now.toLocalDate()}
            .map{it.date.atTime(it.start).atZone(SCHOOL_ZONE).minusMinutes(10) to it.date.atTime(it.end).atZone(SCHOOL_ZONE).plusMinutes(10)}
            .sortedBy{it.first}
        val gaps=mutableListOf<StudyGap>();var cursor=now
        occupied.forEach{(start,finish)->
            if(finish<=cursor)return@forEach
            val clipped=start.coerceAtMost(end)
            if(cursor<clipped&&Duration.between(cursor,clipped).toMinutes()>=10)gaps+=StudyGap(cursor,clipped)
            if(finish>cursor)cursor=finish
        }
        if(cursor<end&&Duration.between(cursor,end).toMinutes()>=10)gaps+=StudyGap(cursor,end)
        val pending=tasks.filter{it.completedAt==null}.sortedWith(compareBy<TaskOccurrence>{taskRank(it,now)}.thenBy{it.dueAt?.toInstant()?:Instant.MAX}.thenBy{it.title})
        return gaps.firstNotNullOfOrNull{gap->pending.firstOrNull{effectiveMinutes(it)<=gap.minutes}?.let{GapRecommendation(gap,it,effectiveMinutes(it))}}
            ?:gaps.firstOrNull()?.let{GapRecommendation(it,null,0)}
    }
    fun effectiveMinutes(task:TaskOccurrence)=task.estimatedMinutes?:30
    private fun taskRank(task:TaskOccurrence,now:ZonedDateTime)=when {
        task.dueAt?.isBefore(now)==true->0
        task.dueAt?.toLocalDate()==now.toLocalDate()->1
        task.priority==StudyTaskPriority.URGENT->2
        task.dueAt!=null->3
        task.priority==StudyTaskPriority.IMPORTANT->4
        else->5
    }
}

object StudyTaskEngine {
    val reminderChoices = setOf(0, 10, 30, 60, 1440, 4320)

    fun validate(task: StudyTask) {
        require(task.id.isNotBlank()) { "任务编号不能为空" }
        require(task.title.trim().isNotEmpty()) { "请填写任务标题" }
        require(task.title.length <= 60) { "任务标题不能超过60字" }
        require(task.note.length <= 500) { "任务备注不能超过500字" }
        val due = task.dueAt?.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) }
        require(task.remindBeforeMinutes == null || task.remindBeforeMinutes in reminderChoices) { "提醒时间无效" }
        require(task.remindBeforeMinutes == null || due != null) { "设置提醒前请填写截止时间" }
        require(task.estimatedMinutes == null || task.estimatedMinutes in 10..180) { "预计用时须为10至180分钟" }
        task.completedAt?.let { Instant.parse(it) }
        Instant.parse(task.createdAt)
        require(task.subtasks.size <= 30) { "子任务不能超过30项" }
        require(task.subtasks.map { it.id }.distinct().size == task.subtasks.size) { "子任务编号不能重复" }
        task.subtasks.forEach { require(it.title.trim().isNotEmpty() && it.title.length <= 60) { "请检查子任务内容" } }
        task.repeatRule?.let { repeat ->
            require(due != null) { "重复任务必须填写首次截止时间" }
            if (repeat.kind == TaskRepeatKind.CUSTOM_WEEKDAYS) require(repeat.weekdays.isNotEmpty()) { "请至少选择一个重复星期" }
            require(repeat.weekdays.all { it in 1..7 }) { "重复星期无效" }
            repeat.endsOn?.let { end -> require(!LocalDate.parse(end).isBefore(due!!.toLocalDate())) { "结束日期不能早于首次日期" } }
        }
        task.instanceStates.forEach { state ->
            require(state.occurrenceKey.isNotBlank()) { "任务实例编号不能为空" }
            state.completedAt?.let { Instant.parse(it) }
            state.override?.let { LocalDateTime.parse(it.dueAt);require(it.estimatedMinutes==null||it.estimatedMinutes in 10..180){"预计用时须为10至180分钟"} }
        }
    }

    fun deadline(task: StudyTask): ZonedDateTime? =
        task.dueAt?.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it).atZone(SCHOOL_ZONE) }

    fun reminderAt(task: StudyTask): Instant? {
        if (task.completedAt != null) return null
        val minutes = task.remindBeforeMinutes ?: return null
        return deadline(task)?.minusMinutes(minutes.toLong())?.toInstant()
    }

    fun sorted(tasks: List<StudyTask>): List<StudyTask> = tasks.sortedWith(
        compareBy<StudyTask> { it.completedAt != null }
            .thenBy { deadline(it)?.toInstant() ?: Instant.MAX }
            .thenByDescending { it.priority }
            .thenBy { it.createdAt }
    )

    fun courseExists(task: StudyTask, data: AppData): Boolean {
        val id = task.courseRuleId ?: return true
        return data.schedule?.rules?.any { it.id == id } == true || data.manualLessons.any { it.id == id }
    }

    fun courseExists(task: TaskOccurrence, data: AppData): Boolean {
        val id = task.courseRuleId ?: return true
        return data.schedule?.rules?.any { it.id == id } == true || data.manualLessons.any { it.id == id }
    }

    fun occurrences(task: StudyTask, from: LocalDate, to: LocalDate): List<TaskOccurrence> {
        if (from > to) return emptyList()
        val due = deadline(task)
        if (task.repeatRule == null) {
            val date = due?.toLocalDate()
            if (date != null && (date < from || date > to)) return emptyList()
            return listOfNotNull(toOccurrence(task, task.id, task.dueAt, due, false))
        }
        val anchor = due ?: return emptyList()
        val repeat = task.repeatRule
        val end = repeat.endsOn?.let(LocalDate::parse)
        val first = maxOf(from, anchor.toLocalDate())
        val last = minOf(to, end ?: to)
        val regularDates = if (first > last) emptyList() else generateSequence(first) { current -> current.plusDays(1).takeIf { it <= last } }
            .filter { date -> when (repeat.kind) {
                TaskRepeatKind.DAILY -> true
                TaskRepeatKind.WEEKLY -> date.dayOfWeek == anchor.dayOfWeek
                TaskRepeatKind.CUSTOM_WEEKDAYS -> date.dayOfWeek.value in repeat.weekdays
            } }.toList()
        val movedOriginalDates = task.instanceStates.mapNotNull { state ->
            val moved = state.override?.dueAt?.let { runCatching { LocalDateTime.parse(it).toLocalDate() }.getOrNull() }
            if (moved != null && !moved.isBefore(from) && !moved.isAfter(to))
                runCatching { LocalDateTime.parse(state.occurrenceKey.substringAfter(task.id + "@")).toLocalDate() }.getOrNull()
            else null
        }
        return (regularDates + movedOriginalDates).distinct().map { date ->
            val original = date.atTime(anchor.toLocalTime()).toString()
            toOccurrence(task, task.id + "@" + original, original, date.atTime(anchor.toLocalTime()).atZone(SCHOOL_ZONE), true)
        }.filterNotNull().filter { item -> item.dueAt?.toLocalDate()?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
    }

    fun occurrences(data: AppData, from: LocalDate, to: LocalDate, includeCompleted: Boolean = true): List<TaskOccurrence> =
        data.studyTasks.flatMap { occurrences(it, from, to) }
            .filter { includeCompleted || it.completedAt == null }
            .sortedWith(compareBy<TaskOccurrence> { it.completedAt != null }
                .thenBy { it.dueAt?.toInstant() ?: Instant.MAX }
                .thenByDescending { it.priority }.thenBy { it.title })

    fun listOccurrences(data: AppData, today: LocalDate, futureDays: Long = 366): List<TaskOccurrence> {
        val anchors = data.studyTasks.mapNotNull { deadline(it)?.toLocalDate() }
        val from = maxOf(today.minusDays(366), anchors.minOrNull() ?: today)
        return occurrences(data, from, today.plusDays(futureDays))
    }

    fun nextReminder(task: StudyTask, now: Instant): Pair<TaskOccurrence, Instant>? {
        val today = now.atZone(SCHOOL_ZONE).toLocalDate()
        return occurrences(task, today.minusDays(1), today.plusYears(2))
            .asSequence().filter { it.completedAt == null }
            .mapNotNull { item -> reminderAt(item)?.let { item to it } }
            .filter { it.second > now }.minByOrNull { it.second }
    }

    fun reminderAt(task: TaskOccurrence): Instant? {
        if (task.completedAt != null) return null
        val minutes = task.remindBeforeMinutes ?: return null
        return task.dueAt?.minusMinutes(minutes.toLong())?.toInstant()
    }

    fun occurrence(task: StudyTask, key: String): TaskOccurrence? {
        if (task.repeatRule == null) return toOccurrence(task, task.id, task.dueAt, deadline(task), false)?.takeIf { it.key == key }
        val original = key.substringAfter(task.id + "@", "")
        val date = runCatching { LocalDateTime.parse(original).toLocalDate() }.getOrNull() ?: return null
        return occurrences(task, date, date).firstOrNull { it.key == key }
    }

    private fun toOccurrence(task: StudyTask, key: String, originalDueAt: String?, due: ZonedDateTime?, repeated: Boolean): TaskOccurrence? {
        val state = task.instanceStates.firstOrNull { it.occurrenceKey == key }
        if (state?.deleted == true) return null
        val override = state?.override
        val actualDue = override?.dueAt?.let { LocalDateTime.parse(it).atZone(SCHOOL_ZONE) } ?: due
        return TaskOccurrence(
            key, task.id, originalDueAt,
            override?.title ?: task.title,
            override?.type ?: task.type,
            if (override != null) override.courseRuleId else task.courseRuleId,
            override?.courseTitle ?: task.courseTitle,
            actualDue,
            override?.priority ?: task.priority,
            override?.note ?: task.note,
            if (override != null) override.remindBeforeMinutes else task.remindBeforeMinutes,
            override?.subtasks ?: task.subtasks,
            state?.completedSubtaskIds.orEmpty().toSet(),
            state?.completedAt ?: if (!repeated) task.completedAt else null,
            repeated, override?.estimatedMinutes ?: task.estimatedMinutes
        )
    }
}
