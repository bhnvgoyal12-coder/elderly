package com.simple.elderlylauncher.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import java.text.DecimalFormat

/**
 * Performance monitoring utility for tracking launcher metrics.
 * Enable DEBUG_MODE to see real-time performance data.
 */
object PerformanceMonitor {

    private const val TAG = "LauncherPerf"

    // Set to true to enable performance logging
    var DEBUG_MODE = false

    // Timing trackers
    private var coldStartTime: Long = 0
    private var warmStartTime: Long = 0
    private var appLaunchStartTime: Long = 0
    private var drawerOpenStartTime: Long = 0

    // Frame rate tracking
    private var frameCount = 0
    private var droppedFrames = 0
    private var lastFrameTime: Long = 0
    private var fpsStartTime: Long = 0
    private var isTrackingFrames = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isTrackingFrames) return

            frameCount++

            if (lastFrameTime > 0) {
                val frameDuration = (frameTimeNanos - lastFrameTime) / 1_000_000 // to ms
                // A frame taking more than 16.67ms (60fps) is considered dropped
                if (frameDuration > 17) {
                    droppedFrames++
                }
            }
            lastFrameTime = frameTimeNanos

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // Metrics storage
    data class PerformanceMetrics(
        var coldStartMs: Long = 0,
        var warmStartMs: Long = 0,
        var avgAppLaunchMs: Long = 0,
        var drawerLoadMs: Long = 0,
        var currentFps: Float = 0f,
        var droppedFramePercent: Float = 0f,
        var memoryUsageMb: Float = 0f,
        var nativeHeapMb: Float = 0f
    )

    private val metrics = PerformanceMetrics()
    private val appLaunchTimes = mutableListOf<Long>()

    // Listeners for real-time updates
    private var metricsListener: ((PerformanceMetrics) -> Unit)? = null

    /**
     * Call at the very start of Application or MainActivity onCreate
     */
    fun markColdStart() {
        coldStartTime = SystemClock.elapsedRealtime()
        log("Cold start began")
    }

    /**
     * Call when the launcher is fully visible and interactive
     */
    fun markColdStartComplete() {
        if (coldStartTime > 0) {
            metrics.coldStartMs = SystemClock.elapsedRealtime() - coldStartTime
            log("Cold start complete: ${metrics.coldStartMs}ms")
            coldStartTime = 0
        }
    }

    /**
     * Call in onResume when coming back to launcher
     */
    fun markWarmStart() {
        warmStartTime = SystemClock.elapsedRealtime()
    }

    /**
     * Call when launcher is fully visible after warm start
     */
    fun markWarmStartComplete() {
        if (warmStartTime > 0) {
            metrics.warmStartMs = SystemClock.elapsedRealtime() - warmStartTime
            log("Warm start: ${metrics.warmStartMs}ms")
            warmStartTime = 0
        }
    }

    /**
     * Call just before launching an app
     */
    fun markAppLaunchStart() {
        appLaunchStartTime = SystemClock.elapsedRealtime()
    }

    /**
     * Call when app launch intent is sent
     */
    fun markAppLaunchComplete() {
        if (appLaunchStartTime > 0) {
            val launchTime = SystemClock.elapsedRealtime() - appLaunchStartTime
            appLaunchTimes.add(launchTime)
            // Keep last 10 launches for average
            if (appLaunchTimes.size > 10) {
                appLaunchTimes.removeAt(0)
            }
            metrics.avgAppLaunchMs = appLaunchTimes.average().toLong()
            log("App launch time: ${launchTime}ms (avg: ${metrics.avgAppLaunchMs}ms)")
            appLaunchStartTime = 0
        }
    }

    /**
     * Call when opening app drawer
     */
    fun markDrawerOpenStart() {
        drawerOpenStartTime = SystemClock.elapsedRealtime()
    }

    /**
     * Call when app drawer is fully loaded with apps
     */
    fun markDrawerOpenComplete() {
        if (drawerOpenStartTime > 0) {
            metrics.drawerLoadMs = SystemClock.elapsedRealtime() - drawerOpenStartTime
            log("Drawer load time: ${metrics.drawerLoadMs}ms")
            drawerOpenStartTime = 0
        }
    }

    /**
     * Start tracking frame rate
     */
    fun startFrameTracking() {
        if (isTrackingFrames) return

        isTrackingFrames = true
        frameCount = 0
        droppedFrames = 0
        lastFrameTime = 0
        fpsStartTime = SystemClock.elapsedRealtime()

        Choreographer.getInstance().postFrameCallback(frameCallback)
        log("Frame tracking started")
    }

    /**
     * Stop tracking frame rate and calculate metrics
     */
    fun stopFrameTracking() {
        if (!isTrackingFrames) return

        isTrackingFrames = false

        val duration = (SystemClock.elapsedRealtime() - fpsStartTime) / 1000f // seconds
        if (duration > 0 && frameCount > 0) {
            metrics.currentFps = frameCount / duration
            metrics.droppedFramePercent = (droppedFrames.toFloat() / frameCount) * 100
            log("FPS: ${formatDecimal(metrics.currentFps)}, Dropped: ${formatDecimal(metrics.droppedFramePercent)}%")
        }
    }

    /**
     * Update memory usage metrics
     */
    fun updateMemoryMetrics(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        // App's memory usage
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

        // Native heap
        val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)

        metrics.memoryUsageMb = usedMemory
        metrics.nativeHeapMb = nativeHeap

        log("Memory - Java Heap: ${formatDecimal(usedMemory)}MB, Native: ${formatDecimal(nativeHeap)}MB")
    }

    /**
     * Get current metrics snapshot
     */
    fun getMetrics(): PerformanceMetrics = metrics.copy()

    /**
     * Set listener for real-time metric updates
     */
    fun setMetricsListener(listener: ((PerformanceMetrics) -> Unit)?) {
        metricsListener = listener
    }

    /**
     * Log all current metrics
     */
    fun logAllMetrics(context: Context) {
        updateMemoryMetrics(context)

        Log.i(TAG, """
            |========== PERFORMANCE METRICS ==========
            | Cold Start:      ${metrics.coldStartMs}ms
            | Warm Start:      ${metrics.warmStartMs}ms
            | Avg App Launch:  ${metrics.avgAppLaunchMs}ms
            | Drawer Load:     ${metrics.drawerLoadMs}ms
            | FPS:             ${formatDecimal(metrics.currentFps)}
            | Dropped Frames:  ${formatDecimal(metrics.droppedFramePercent)}%
            | Memory (Java):   ${formatDecimal(metrics.memoryUsageMb)}MB
            | Memory (Native): ${formatDecimal(metrics.nativeHeapMb)}MB
            |==========================================
        """.trimMargin())

        metricsListener?.invoke(metrics)
    }

    /**
     * Get a formatted summary string
     */
    fun getMetricsSummary(context: Context): String {
        updateMemoryMetrics(context)
        return buildString {
            appendLine("⏱ Startup: ${metrics.coldStartMs}ms")
            appendLine("📱 App Launch: ${metrics.avgAppLaunchMs}ms avg")
            appendLine("📂 Drawer: ${metrics.drawerLoadMs}ms")
            appendLine("🎞 FPS: ${formatDecimal(metrics.currentFps)}")
            appendLine("💾 Memory: ${formatDecimal(metrics.memoryUsageMb)}MB")
        }
    }

    private fun log(message: String) {
        if (DEBUG_MODE) {
            Log.d(TAG, message)
        }
    }

    private fun formatDecimal(value: Float): String {
        return DecimalFormat("#.##").format(value)
    }
}
