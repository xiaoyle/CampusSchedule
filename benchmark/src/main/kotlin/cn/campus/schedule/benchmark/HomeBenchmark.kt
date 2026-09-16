package cn.campus.schedule.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "cn.campus.schedule"

@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollToday() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(mainIntent())
            device.wait(Until.hasObject(By.textContains("今天")), 5_000)
        }
    ) {
        val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        repeat(3) { list.fling(Direction.DOWN); device.waitForIdle() }
        repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
    }
}

@LargeTest
@RunWith(AndroidJUnit4::class)
class AppBaselineProfile {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun criticalUserJourneys() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait(mainIntent())
        device.wait(Until.hasObject(By.textContains("今天")), 5_000)
        device.findObject(By.text("计划"))?.click()
        device.waitForIdle()
        device.findObject(By.text("我的"))?.click()
        device.waitForIdle()
        device.findObject(By.textContains("笔记"))?.click()
        device.waitForIdle()
    }
}

private fun mainIntent() = Intent().apply {
    component = ComponentName(PACKAGE_NAME, "$PACKAGE_NAME.MainActivity")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
