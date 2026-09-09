package cn.campus.core

import java.time.*
import java.time.temporal.WeekFields

object FocusEngine {
    const val MAX_STOPWATCH_SECONDS=12*60*60L
    fun validate(active:ActiveFocusState) {
        require(active.id.isNotBlank()&&active.title.trim().isNotEmpty()){"请填写专注目标"}
        require(active.plannedSeconds==null||active.plannedSeconds in 60..10800){"倒计时须为1至180分钟"}
        require(active.accumulatedSeconds>=0){"专注时长无效"}
        require(active.volume in 0f..1f){"环境音音量无效"}
        Instant.parse(active.startedAt);Instant.parse(active.runStartedAt)
    }
    fun elapsedSeconds(active:ActiveFocusState,now:Instant=Instant.now(),elapsedRealtimeMs:Long=active.runStartedElapsedMs):Long {
        if(active.status!=FocusStatus.RUNNING)return active.accumulatedSeconds
        val delta=if(elapsedRealtimeMs>=active.runStartedElapsedMs)
            (elapsedRealtimeMs-active.runStartedElapsedMs)/1000
        else Duration.between(Instant.parse(active.runStartedAt),now).seconds
        return (active.accumulatedSeconds+delta.coerceAtLeast(0)).coerceAtMost(MAX_STOPWATCH_SECONDS)
    }
    fun remainingSeconds(active:ActiveFocusState,now:Instant=Instant.now(),elapsedRealtimeMs:Long=active.runStartedElapsedMs):Long? =
        active.plannedSeconds?.let{(it-elapsedSeconds(active,now,elapsedRealtimeMs)).coerceAtLeast(0)}
    fun pause(active:ActiveFocusState,now:Instant,elapsedRealtimeMs:Long)=active.copy(
        accumulatedSeconds=elapsedSeconds(active,now,elapsedRealtimeMs),status=FocusStatus.PAUSED,
        runStartedAt=now.toString(),runStartedElapsedMs=elapsedRealtimeMs)
    fun resume(active:ActiveFocusState,now:Instant,elapsedRealtimeMs:Long)=active.copy(
        status=FocusStatus.RUNNING,runStartedAt=now.toString(),runStartedElapsedMs=elapsedRealtimeMs)
    fun finish(active:ActiveFocusState,now:Instant,elapsedRealtimeMs:Long,status:FocusStatus):FocusSession {
        val seconds=elapsedSeconds(active,now,elapsedRealtimeMs).let{value->active.plannedSeconds?.let{minOf(value,it)}?:value}
        return FocusSession(active.id,active.mode,active.title,active.courseRuleId,active.taskId,active.taskOccurrenceKey,
            active.startedAt,now.toString(),seconds,active.plannedSeconds,active.ambientSound,status)
    }
    fun valid(session:FocusSession)=session.mode!=FocusMode.BREAK&&session.focusedSeconds>=60&&session.status!=FocusStatus.INTERRUPTED
}

data class ReviewSummary(
    val focusedMinutes:Long,val sessionCount:Int,val completedTasks:Int,val onTimeTasks:Int,
    val overdueTasks:Int,val scheduledTasks:Int,val finishedScheduledTasks:Int,
    val dailyMinutes:Map<LocalDate,Long>,val courseMinutes:Map<String,Long>,val futureLoad:Map<LocalDate,Int>
)

object ReviewEngine {
    fun review(data:AppData,from:LocalDate,to:LocalDate):ReviewSummary {
        val sessions=data.focusSessions.filter{FocusEngine.valid(it)}.filter{
            val date=Instant.parse(it.endedAt).atZone(SCHOOL_ZONE).toLocalDate();!date.isBefore(from)&&!date.isAfter(to)
        }
        val completions=AchievementEngine.taskCompletions(data).filter{!it.completedDate.isBefore(from)&&!it.completedDate.isAfter(to)}
        val occurrences=StudyTaskEngine.occurrences(data,from,to).filter{it.dueAt!=null}
        val today=LocalDate.now(SCHOOL_ZONE)
        val future=StudyTaskEngine.occurrences(data,today,today.plusDays(6),includeCompleted=false).filter{it.dueAt!=null}
        return ReviewSummary(
            sessions.sumOf{it.focusedSeconds}/60,sessions.size,completions.size,completions.count{it.onTime},
            occurrences.count{it.completedAt==null&&it.dueAt?.isBefore(ZonedDateTime.now(SCHOOL_ZONE))==true},
            occurrences.size,occurrences.count{it.completedAt!=null},
            sessions.groupBy{Instant.parse(it.endedAt).atZone(SCHOOL_ZONE).toLocalDate()}.mapValues{(_,v)->v.sumOf{it.focusedSeconds}/60},
            sessions.groupBy{it.courseRuleId?.let{id->courseName(data,id)}?:"未关联"}.mapValues{(_,v)->v.sumOf{it.focusedSeconds}/60},
            future.groupingBy{it.dueAt!!.toLocalDate()}.eachCount()
        )
    }
    private fun courseName(data:AppData,id:String)=data.schedule?.rules?.firstOrNull{it.id==id}?.title
        ?:data.manualLessons.firstOrNull{it.id==id}?.title?:"原课程已移除"
}

data class TaskCompletion(val key:String,val completedAt:Instant,val completedDate:LocalDate,val onTime:Boolean)

object AchievementEngine {
    const val FIRST_LIGHT="builtin:first_light"
    const val KAPOK_ORDER="builtin:kapok_order"
    const val CLOCK_PROMISE="builtin:clock_promise"
    const val SEVEN_STARS="builtin:seven_stars"
    const val MOUNTAIN_SEA="builtin:mountain_sea"
    val builtIns=listOf(
        AchievementDefinition(FIRST_LIGHT,"初光启页","完成第一次不少于10分钟的有效专注",metric=AchievementMetric.SINGLE_FOCUS_MINUTES,target=10,badge=BadgeDesign(BadgeShape.CIRCLE,0xFF176B52,0xFFFFC75F,BadgeBorder.GOLD,BadgeTexture.RADIAL,"book"),builtIn=true),
        AchievementDefinition(KAPOK_ORDER,"木棉成序","完成10个不同任务实例",metric=AchievementMetric.COMPLETED_TASKS,target=10,badge=BadgeDesign(BadgeShape.HEXAGON,0xFFC95454,0xFFFFD1A6,BadgeBorder.GOLD,BadgeTexture.GRID,"flower"),builtIn=true),
        AchievementDefinition(CLOCK_PROMISE,"钟楼守约","按时完成7个有截止时间的任务",metric=AchievementMetric.ON_TIME_TASKS,target=7,badge=BadgeDesign(BadgeShape.SHIELD,0xFF174B3C,0xFFFFD36B,BadgeBorder.GOLD,BadgeTexture.SOLID,"clock"),builtIn=true),
        AchievementDefinition(SEVEN_STARS,"七日星轨","连续7天每天专注不少于10分钟",metric=AchievementMetric.FOCUS_STREAK_DAYS,target=7,badge=BadgeDesign(BadgeShape.CIRCLE,0xFF173B69,0xFFD8E8FF,BadgeBorder.SILVER,BadgeTexture.STARS,"moon"),builtIn=true),
        AchievementDefinition(MOUNTAIN_SEA,"山海同频","同一周专注300分钟并完成5项任务",metric=null,target=300,badge=BadgeDesign(BadgeShape.DIAMOND,0xFF176B52,0xFF69BDE0,BadgeBorder.GOLD,BadgeTexture.WAVES,"waves"),builtIn=true)
    )
    fun definitions(data:AppData)=builtIns+data.customAchievements
    fun taskCompletions(data:AppData):List<TaskCompletion> = data.studyTasks.flatMap{task->
        val simple=listOfNotNull(task.completedAt?.let{value->
            val completed=Instant.parse(value);val due=StudyTaskEngine.deadline(task)?.toInstant()
            TaskCompletion(task.id,completed,completed.atZone(SCHOOL_ZONE).toLocalDate(),due!=null&&completed<=due)
        })
        simple+task.instanceStates.mapNotNull{state->state.completedAt?.let{value->
            val completed=Instant.parse(value);val occurrence=StudyTaskEngine.occurrence(task,state.occurrenceKey)
            val due=occurrence?.dueAt?.toInstant();TaskCompletion(state.occurrenceKey,completed,completed.atZone(SCHOOL_ZONE).toLocalDate(),due!=null&&completed<=due)
        }}
    }.distinctBy{it.key}
    fun progress(def:AchievementDefinition,data:AppData,now:ZonedDateTime=ZonedDateTime.now(SCHOOL_ZONE)):Pair<Long,Long> {
        val valid=data.focusSessions.filter(FocusEngine::valid)
        fun minutes(session:FocusSession)=session.focusedSeconds/60
        val primary=when(def.metric){
            AchievementMetric.TOTAL_FOCUS_MINUTES->valid.sumOf(::minutes)
            AchievementMetric.SINGLE_FOCUS_MINUTES->valid.maxOfOrNull(::minutes)?:0
            AchievementMetric.FOCUS_STREAK_DAYS->maxFocusStreak(valid).toLong()
            AchievementMetric.COMPLETED_TASKS->taskCompletions(data).size.toLong()
            AchievementMetric.ON_TIME_TASKS->taskCompletions(data).count{it.onTime}.toLong()
            AchievementMetric.WEEKLY_FOCUS_MINUTES->weeklyFocus(valid,now.toLocalDate())
            AchievementMetric.COURSE_FOCUS_MINUTES->valid.filter{it.courseRuleId==def.courseRuleId}.sumOf(::minutes)
            null->if(def.id==MOUNTAIN_SEA)mountainSea(data).first else 0
        }
        val secondary=if(def.id==MOUNTAIN_SEA)mountainSea(data).second else 0
        return primary to secondary
    }
    fun evaluate(data:AppData,now:Instant=Instant.now()):Pair<AppData,List<String>> {
        val zoneNow=now.atZone(SCHOOL_ZONE);val old=data.achievementProgress.associateBy{it.achievementId};val unlocked=mutableListOf<String>()
        val updates=definitions(data).map{def->
            val previous=old[def.id]?:AchievementProgress(def.id);val values=progress(def,data,zoneNow)
            val met=if(def.id==MOUNTAIN_SEA)values.first>=300&&values.second>=5 else def.unlockMode==AchievementUnlockMode.AUTO&&values.first>=def.target
            if(previous.unlockedAt==null&&met)unlocked+=def.id
            previous.copy(current=values.first,secondaryCurrent=values.second,unlockedAt=previous.unlockedAt?:if(met)now.toString() else null,
                evidenceKeys=when(def.metric){AchievementMetric.COMPLETED_TASKS,AchievementMetric.ON_TIME_TASKS->taskCompletions(data).map{it.key}.take(500);else->previous.evidenceKeys})
        }
        return data.copy(achievementProgress=updates) to unlocked
    }
    fun manualUnlock(data:AppData,id:String,now:Instant=Instant.now()):AppData {
        val def=data.customAchievements.firstOrNull{it.id==id}?:return data
        require(def.unlockMode==AchievementUnlockMode.MANUAL){"该成就由进度自动解锁"}
        val old=data.achievementProgress.firstOrNull{it.achievementId==id}?:AchievementProgress(id)
        return data.copy(achievementProgress=data.achievementProgress.filterNot{it.achievementId==id}+old.copy(current=1,unlockedAt=old.unlockedAt?:now.toString(),celebrationSeen=false))
    }
    fun focusStreak(sessions:List<FocusSession>,today:LocalDate):Int {
        val totals=sessions.groupBy{Instant.parse(it.endedAt).atZone(SCHOOL_ZONE).toLocalDate()}.mapValues{(_,v)->v.sumOf{it.focusedSeconds}/60}
        var day=if((totals[today]?:0)>=10)today else today.minusDays(1);var count=0
        while((totals[day]?:0)>=10){count++;day=day.minusDays(1)}
        return count
    }
    fun maxFocusStreak(sessions:List<FocusSession>):Int {
        val days=sessions.groupBy{Instant.parse(it.endedAt).atZone(SCHOOL_ZONE).toLocalDate()}.filterValues{values->values.sumOf{it.focusedSeconds}/60>=10}.keys.sorted()
        var best=0;var run=0;var previous:LocalDate?=null
        days.forEach{day->run=if(previous?.plusDays(1)==day)run+1 else 1;best=maxOf(best,run);previous=day}
        return best
    }
    private fun weeklyFocus(sessions:List<FocusSession>,day:LocalDate):Long {val start=day.minusDays((day.dayOfWeek.value-1).toLong());return sessions.filter{Instant.parse(it.endedAt).atZone(SCHOOL_ZONE).toLocalDate() in start..start.plusDays(6)}.sumOf{it.focusedSeconds}/60}
    private fun mountainSea(data:AppData):Pair<Long,Long> {
        val weeks=mutableMapOf<String,Pair<Long,Long>>();val wf=WeekFields.ISO
        data.focusSessions.filter(FocusEngine::valid).forEach{s->val d=Instant.parse(s.endedAt).atZone(SCHOOL_ZONE).toLocalDate();val k="${d.get(wf.weekBasedYear())}-${d.get(wf.weekOfWeekBasedYear())}";val p=weeks[k]?:0L to 0L;weeks[k]=p.copy(first=p.first+s.focusedSeconds/60)}
        taskCompletions(data).forEach{c->val d=c.completedDate;val k="${d.get(wf.weekBasedYear())}-${d.get(wf.weekOfWeekBasedYear())}";val p=weeks[k]?:0L to 0L;weeks[k]=p.copy(second=p.second+1)}
        return weeks.values.maxWithOrNull(compareBy<Pair<Long,Long>>{minOf(it.first/300,it.second/5)}.thenBy{it.first})?: (0L to 0L)
    }
}
