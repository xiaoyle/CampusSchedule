package cn.campus.core

import kotlin.test.*
import java.time.*

class FocusAchievementTest {
    private fun session(id:String,date:String,minutes:Long,course:String?=null)=FocusSession(id,FocusMode.COUNTDOWN,"学习",courseRuleId=course,startedAt="${date}T10:00:00Z",endedAt="${date}T10:${minutes.coerceAtMost(59).toString().padStart(2,'0')}:00Z",focusedSeconds=minutes*60,plannedSeconds=minutes*60)

    @Test fun oldJsonUsesSafeDefaults(){
        val data=dataJson.decodeFromString<AppData>("{}")
        assertNull(data.activeFocus);assertTrue(data.focusSessions.isEmpty());assertEquals(300,data.focusSettings.weeklyMinutesTarget);assertTrue(data.customAchievements.isEmpty())
    }

    @Test fun timerUsesElapsedRealtimeAndPauses(){
        val start=Instant.parse("2026-09-09T02:00:00Z")
        val active=ActiveFocusState("f",FocusMode.COUNTDOWN,"高数",plannedSeconds=1500,startedAt=start.toString(),runStartedAt=start.toString(),runStartedElapsedMs=1000)
        assertEquals(60,FocusEngine.elapsedSeconds(active,start.plusSeconds(60),61000))
        assertEquals(1440,FocusEngine.remainingSeconds(active,start.plusSeconds(60),61000))
        val paused=FocusEngine.pause(active,start.plusSeconds(60),61000);assertEquals(FocusStatus.PAUSED,paused.status);assertEquals(60,paused.accumulatedSeconds)
        val stopwatch=active.copy(mode=FocusMode.STOPWATCH,plannedSeconds=null)
        assertEquals(FocusEngine.MAX_STOPWATCH_SECONDS,FocusEngine.elapsedSeconds(stopwatch,start.plusSeconds(50_000),50_001_000))
    }

    @Test fun firstLightAndSevenDayOrbitUnlock(){
        val sessions=(1..7).map{i->session("s$i","2026-09-${(i+1).toString().padStart(2,'0')}",10)}
        val (evaluated,newIds)=AchievementEngine.evaluate(AppData(focusSessions=sessions),Instant.parse("2026-09-09T04:00:00Z"))
        assertTrue(AchievementEngine.FIRST_LIGHT in newIds);assertTrue(AchievementEngine.SEVEN_STARS in newIds)
        assertNotNull(evaluated.achievementProgress.first{it.achievementId==AchievementEngine.SEVEN_STARS}.unlockedAt)
    }

    @Test fun taskAchievementsAndMountainSeaUseUniqueCompletions(){
        val tasks=(1..10).map{i->StudyTask("t$i","任务$i",dueAt="2026-09-0${if(i<7)i+1 else 7}T20:00:00",completedAt="2026-09-0${if(i<7)i+1 else 7}T10:00:00Z",createdAt="2026-09-01T00:00:00Z")}
        val focuses=(1..5).map{i->session("f$i","2026-09-0${i+1}",60)}
        val (evaluated,newIds)=AchievementEngine.evaluate(AppData(studyTasks=tasks,focusSessions=focuses),Instant.parse("2026-09-09T00:00:00Z"))
        assertTrue(AchievementEngine.KAPOK_ORDER in newIds);assertTrue(AchievementEngine.CLOCK_PROMISE in newIds);assertTrue(AchievementEngine.MOUNTAIN_SEA in newIds)
        assertEquals(10,AchievementEngine.taskCompletions(evaluated).map{it.key}.distinct().size)
    }

    @Test fun customAutoAndManualAchievementsWork(){
        val auto=AchievementDefinition("a","自律一小时","累计专注60分钟",metric=AchievementMetric.TOTAL_FOCUS_MINUTES,target=60)
        val manual=AchievementDefinition("m","特别纪念","由我确认",unlockMode=AchievementUnlockMode.MANUAL)
        val (evaluated,newIds)=AchievementEngine.evaluate(AppData(focusSessions=listOf(session("f","2026-09-08",60)),customAchievements=listOf(auto,manual)),Instant.parse("2026-09-09T00:00:00Z"))
        assertTrue("a" in newIds);assertFalse("m" in newIds);assertNull(evaluated.achievementProgress.first{it.achievementId=="m"}.unlockedAt)
        assertNotNull(AchievementEngine.manualUnlock(evaluated,"m").achievementProgress.first{it.achievementId=="m"}.unlockedAt)
    }
}
