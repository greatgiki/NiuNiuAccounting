package com.example.localledger.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release-like frame and startup timings.
 *
 * ```
 * ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 * ```
 *
 * Needs a device or emulator (API 28+). The scroll test seeds 500 expense rows.
 */
@RunWith(AndroidJUnit4::class)
class LedgerBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun swipeAcrossTabs() = benchmarkRule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            acknowledgeIfNeeded()
        },
    ) {
        val y = device.displayHeight / 3
        val startX = device.displayWidth * 4 / 5
        val endX = device.displayWidth / 5
        repeat(2) {
            device.swipe(startX, y, endX, y, 24)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(endX, y, startX, y, 24)
            device.waitForIdle()
        }
    }

    @Test
    fun scrollTransactionList() = benchmarkRule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait(seedIntent(500))
            acknowledgeIfNeeded()
            device.wait(Until.hasObject(By.text("明细")), 8_000)
            device.findObject(By.text("明细")).click()
            device.wait(Until.hasObject(By.textContains("基准")), 20_000)
        },
    ) {
        val list = device.findObject(By.res("transaction-list"))
            ?: device.findObject(By.scrollable(true))
        list.setGestureMargin(device.displayWidth / 8)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }

    @Test
    fun openEditorAndTypeAmount() = benchmarkRule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            acknowledgeIfNeeded()
        },
    ) {
        device.findObject(By.text("记一笔")).click()
        device.wait(Until.hasObject(By.text("1")), 8_000)
        listOf("1", "2", "8", ".", "5").forEach { key ->
            device.findObject(By.text(key)).click()
        }
        device.waitForIdle()
    }

    private fun seedIntent(count: Int) = Intent().apply {
        setPackage(TARGET)
        putExtra("com.example.localledger.SEED_COUNT", count)
    }
}

private const val TARGET = "com.example.localledger"

private fun androidx.benchmark.macro.MacrobenchmarkScope.acknowledgeIfNeeded() {
    val notice = device.wait(Until.hasObject(By.text("我已了解，开始使用")), 4_000)
    if (notice) {
        device.findObject(By.text("我已了解，开始使用")).click()
        device.wait(Until.hasObject(By.text("首页")), 8_000)
    }
}
