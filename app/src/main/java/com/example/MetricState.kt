package com.example

import android.content.Context
import android.os.Build
import android.view.Choreographer
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.pow
import kotlin.math.sqrt

// Hardware Preset Profiles
enum class HardwareProfile(
    val displayName: String,
    val targetFps: Int,
    val alertFpsThreshold: Int,
    val alertJitterMs: Float,
    val sampleIntervalMs: Int,
    val description: String
) {
    ENTRY_BUDGET("Entry Level (60Hz)", 60, 52, 5.0f, 800, "Optimized for 60Hz screens, high-tolerance pacing thresholds."),
    MID_RANGE("Mid Range (90Hz)", 90, 80, 3.0f, 400, "Adaptive 90Hz profiling, balanced pacing alerts."),
    FLAGSHIP_ULTRA("Flagship Ultra (120Hz+)", 120, 110, 1.5f, 250, "Aggressive 120Hz+ monitoring, tighter low-latency rules."),
    CUSTOM_TUNABLE("Custom User Config", 60, 55, 2.5f, 500, "Manually adjustable parameters for personalized analysis.")
}

// Simulated Resolution Levels
enum class ScreenResolutionPreset(
    val displayName: String,
    val multiplier: Float,
    val basePagingWeight: Int, // artificial math weight to simulate rendering overhead
    val description: String
) {
    SD_480P("480p SD Mobile", 0.5f, 1, "Light canvas density, low fill-rate pressure."),
    HD_720P("720p HD Compact", 1.0f, 3, "Standard resolution grid, minimal budget impact."),
    FHD_1080P("1080p FHD Native", 1.5f, 8, "Vibrant high definition pixels, moderate rendering load."),
    QHD_1440P("1440p QHD Ultra", 2.0f, 20, "Flagship density simulator, heavy pixel fill-rate calculations."),
    K4_UHD("2160p 4K Extreme", 3.0f, 50, "Max performance envelope stress test, massive GPU raster loop.")
}

// Performance metrics data container
data class PerformanceMetrics(
    val currentFps: Double = 0.0,
    val averageFps: Double = 0.0,
    val avgFrameTimeMs: Double = 0.0,
    val onePercentLowFps: Double = 0.0,
    val zeroOnePercentLowFps: Double = 0.0,
    val frameJitterMs: Double = 0.0,
    val frameDropRatePercent: Double = 0.0,
    val totalProcessedFrames: Long = 0,
    val alertTriggered: Boolean = false,
    val sampleCountInWindow: Int = 0
)

// Main FPS tracking engine
class FpsPerformanceTracker {
    private var isRunning = false
    private val choreographer = Choreographer.getInstance()

    // Configuration states backed by simple variables but editable
    var profile by mutableStateOf(HardwareProfile.ENTRY_BUDGET)
    var resolutionPreset by mutableStateOf(ScreenResolutionPreset.HD_720P)

    // Manual configurations if profile is set to CUSTOM_TUNABLE
    var customTargetFps by mutableStateOf(60)
    var customAlertFpsThreshold by mutableStateOf(55)
    var customAlertJitterMs by mutableStateOf(3.0f)
    var customSampleIntervalMs by mutableStateOf(500)

    // Current thresholds in active use (resolves based on profile choice)
    val activeTargetFps: Int
        get() = if (profile == HardwareProfile.CUSTOM_TUNABLE) customTargetFps else profile.targetFps

    val activeAlertFpsThreshold: Int
        get() = if (profile == HardwareProfile.CUSTOM_TUNABLE) customAlertFpsThreshold else profile.alertFpsThreshold

    val activeAlertJitterMs: Float
        get() = if (profile == HardwareProfile.CUSTOM_TUNABLE) customAlertJitterMs else profile.alertJitterMs

    val activeSampleIntervalMs: Int
        get() = if (profile == HardwareProfile.CUSTOM_TUNABLE) customSampleIntervalMs else profile.sampleIntervalMs

    // Live Metrics exposed via StateFlow so Compose updates cleanly
    private val _liveMetrics = MutableStateFlow(PerformanceMetrics())
    val liveMetrics = _liveMetrics.asStateFlow()

    // Rolling FPS history for real-time chart (last 50 values)
    private val _fpsHistory = MutableStateFlow<List<Float>>(List(50) { 0f })
    val fpsHistory = _fpsHistory.asStateFlow()

    // Alert indicator state
    private val _isAlertActive = MutableStateFlow(false)
    val isAlertActive = _isAlertActive.asStateFlow()

    // Background tracking daemon properties
    var isAppInBackground by mutableStateOf(false)
    private var bgTimerJob: Job? = null
    private val bgScope = CoroutineScope(Dispatchers.Default)

    // Background FPS statistics and parameters
    var currentBgFps by mutableStateOf(0.0)
    var averageBgFps by mutableStateOf(0.0)
    var bgJitterMs by mutableStateOf(0.0)
    var totalBgSamples by mutableStateOf(0L)
    var backgroundMaxDelayMs by mutableStateOf(0.0)
    var bgThrottleEventsAlertCount by mutableStateOf(0)

    private val _bgFpsHistory = MutableStateFlow<List<Float>>(List(50) { 0f })
    val bgFpsHistory = _bgFpsHistory.asStateFlow()

    // Structuring background events history
    data class BackgroundLogEntry(
        val timestamp: String,
        val description: String,
        val isWarning: Boolean = false
    )
    val backgroundLogs = androidx.compose.runtime.mutableStateListOf<BackgroundLogEntry>()

    // Internal sampling structures
    private val frameSampleTimes = mutableListOf<Long>() // nano-seconds values
    private var totalSessionFrames: Long = 0
    private var totalSessionTimeNs: Long = 0
    private var lastFrameTimeNanos: Long = 0
    private var lastUpdateTimestampNs: Long = 0

    // Callback hook for Choreographer
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameTimeNanos != 0L) {
                val frameDurationNs = frameTimeNanos - lastFrameTimeNanos
                frameSampleTimes.add(frameDurationNs)
                totalSessionFrames++
                totalSessionTimeNs += frameDurationNs

                // Check sample window elapsed (ms)
                val elapsedSinceLastUpdateMs = (frameTimeNanos - lastUpdateTimestampNs) / 1_000_000
                if (elapsedSinceLastUpdateMs >= activeSampleIntervalMs) {
                    processMetrics(frameTimeNanos)
                }
            } else {
                lastUpdateTimestampNs = frameTimeNanos
            }

            lastFrameTimeNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

    fun startBackgroundDaemon() {
        if (bgTimerJob != null) return
        bgTimerJob = bgScope.launch {
            var lastUpdateTimestampNs = System.nanoTime()
            val sampleIntervalMs = 500L
            var sampleTicks = 0
            val bgDurationsMs = mutableListOf<Double>()

            while (isActive) {
                val targetCycleMs = 16L
                val startCycleNs = System.nanoTime()
                delay(targetCycleMs)
                val endCycleNs = System.nanoTime()

                val actualDurationNs = endCycleNs - startCycleNs
                val actualDurationMs = actualDurationNs / 1_000_000.0

                bgDurationsMs.add(actualDurationMs)
                sampleTicks++

                // Trigger CPU throttle alerts if system sleep states delayed us significantly
                if (actualDurationMs > 80.0) {
                    bgThrottleEventsAlertCount++
                    val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    val excessDelay = actualDurationMs - targetCycleMs
                    if (backgroundLogs.size > 100) {
                        backgroundLogs.removeAt(0)
                    }
                    backgroundLogs.add(
                        BackgroundLogEntry(
                            timestamp = timeString,
                            description = "OS CPU Throttle Alert: Daemon thread execution delayed by +${String.format(java.util.Locale.US, "%.1f", excessDelay)} ms (${if (isAppInBackground) "Background" else "Foreground"} mode)",
                            isWarning = true
                        )
                    )
                }

                if (actualDurationMs > backgroundMaxDelayMs) {
                    backgroundMaxDelayMs = actualDurationMs
                }

                // Batch up samples
                val elapsedMsSinceLastBatch = (endCycleNs - lastUpdateTimestampNs) / 1_000_000L
                if (elapsedMsSinceLastBatch >= sampleIntervalMs && bgDurationsMs.isNotEmpty()) {
                    val batchTimeMs = bgDurationsMs.sum()
                    val calculatedFps = (sampleTicks * 1000.0 / batchTimeMs).coerceAtMost(240.0)

                    currentBgFps = calculatedFps
                    totalBgSamples++

                    averageBgFps = if (totalBgSamples == 1L) {
                        calculatedFps
                    } else {
                        ((averageBgFps * (totalBgSamples - 1) + calculatedFps) / totalBgSamples).coerceAtMost(240.0)
                    }

                    val bgMean = bgDurationsMs.average()
                    val bgVariance = bgDurationsMs.map { (it - bgMean).pow(2) }.average()
                    bgJitterMs = sqrt(bgVariance)

                    val updatedBgHistory = _bgFpsHistory.value.drop(1) + calculatedFps.toFloat()
                    _bgFpsHistory.value = updatedBgHistory

                    bgDurationsMs.clear()
                    sampleTicks = 0
                    lastUpdateTimestampNs = endCycleNs
                }
            }
        }
    }

    fun stopBackgroundDaemon() {
        bgTimerJob?.cancel()
        bgTimerJob = null
    }

    fun setBackgroundState(inBackground: Boolean) {
        if (isAppInBackground == inBackground) return
        isAppInBackground = inBackground
        val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        if (inBackground) {
            if (backgroundLogs.size > 100) backgroundLogs.removeAt(0)
            backgroundLogs.add(
                BackgroundLogEntry(
                    timestamp = timeString,
                    description = "App minimized to background. Vsync UI frames halted, switching tracking to Async Daemon Thread Loop.",
                    isWarning = false
                )
            )
        } else {
            if (backgroundLogs.size > 100) backgroundLogs.removeAt(0)
            backgroundLogs.add(
                BackgroundLogEntry(
                    timestamp = timeString,
                    description = "App returned to active screen focus. Resuming normal display layer monitoring.",
                    isWarning = false
                )
            )
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameTimeNanos = 0
        frameSampleTimes.clear()
        choreographer.postFrameCallback(frameCallback)
        startBackgroundDaemon()
    }

    fun stop() {
        isRunning = false
        choreographer.removeFrameCallback(frameCallback)
        stopBackgroundDaemon()
    }

    fun resetStatistics() {
        totalSessionFrames = 0
        totalSessionTimeNs = 0
        frameSampleTimes.clear()
        _liveMetrics.value = PerformanceMetrics()
        _fpsHistory.value = List(50) { 0f }
        _isAlertActive.value = false
        
        currentBgFps = 0.0
        averageBgFps = 0.0
        bgJitterMs = 0.0
        totalBgSamples = 0L
        backgroundMaxDelayMs = 0.0
        bgThrottleEventsAlertCount = 0
        _bgFpsHistory.value = List(50) { 0f }
        backgroundLogs.clear()
        backgroundLogs.add(
            BackgroundLogEntry(
                timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                description = "Stats database cleared. Core loop monitoring reinstituted.",
                isWarning = false
            )
        )
    }

    private fun processMetrics(currentFrameNanos: Long) {
        val totalSampleCount = frameSampleTimes.size
        if (totalSampleCount == 0) {
            lastUpdateTimestampNs = currentFrameNanos
            return
        }

        // Copy list to prevent concurrent modifications
        val frameDurationsNs = frameSampleTimes.toList()
        frameSampleTimes.clear()
        lastUpdateTimestampNs = currentFrameNanos

        // Convert to ms duration for statistical calculations
        val frameDurationsMs = frameDurationsNs.map { it / 1_000_000.0 }
        val sumFrameTimeMs = frameDurationsMs.sum()

        // 1. Current FPS inside this sample window
        val currentFps = (totalSampleCount * 1000.0 / sumFrameTimeMs).coerceAtMost(240.0)

        // 2. Cumulative session metrics
        val averageFps = if (totalSessionTimeNs > 0) {
            (totalSessionFrames * 1_000_000_000.0 / totalSessionTimeNs).coerceAtMost(240.0)
        } else {
            currentFps
        }

        // 3. Average frame interval
        val avgFrameTimeMs = sumFrameTimeMs / totalSampleCount

        // Sort ascending frame times to find slower percentiles (longer intervals = lower fps)
        val sortedFramerates = frameDurationsMs.sortedDescending() // Slowest frame times first (longest)

        // Calculate 1% Low FPS
        val index1Percent = (totalSampleCount * 0.01).toInt().coerceIn(0, totalSampleCount - 1)
        val onePercentLowFrameMs = sortedFramerates[index1Percent]
        val onePercentLowFps = if (onePercentLowFrameMs > 0) (1000.0 / onePercentLowFrameMs).coerceAtMost(240.0) else currentFps

        // Calculate 0.1% Low FPS (or absolute minimum)
        val index01Percent = (totalSampleCount * 0.001).toInt().coerceIn(0, totalSampleCount - 1)
        val zeroOnePercentLowFrameMs = sortedFramerates[index01Percent]
        val zeroOnePercentLowFps = if (zeroOnePercentLowFrameMs > 0) (1000.0 / zeroOnePercentLowFrameMs).coerceAtMost(240.0) else currentFps

        // 4. Jitter calculation (Standard deviation of frame times in milliseconds)
        val mean = frameDurationsMs.average()
        val variance = frameDurationsMs.map { (it - mean).pow(2) }.average()
        val jitterMs = sqrt(variance)

        // 5. Frame drop rate percentage (Frame drops = frames exceeding budget for the target FPS)
        val targetFrameBudgetMs = 1000.0 / activeTargetFps
        val droppedFrames = frameDurationsMs.count { it > targetFrameBudgetMs }
        val dropRatePercent = (droppedFrames.toDouble() / totalSampleCount) * 100.0

        // Determine alert status
        val alertTriggered = currentFps < activeAlertFpsThreshold || jitterMs > activeAlertJitterMs

        _isAlertActive.value = alertTriggered

        // Update live stats container
        _liveMetrics.value = PerformanceMetrics(
            currentFps = currentFps,
            averageFps = averageFps,
            avgFrameTimeMs = avgFrameTimeMs,
            onePercentLowFps = onePercentLowFps,
            zeroOnePercentLowFps = zeroOnePercentLowFps,
            frameJitterMs = jitterMs,
            frameDropRatePercent = dropRatePercent,
            totalProcessedFrames = totalSessionFrames,
            alertTriggered = alertTriggered,
            sampleCountInWindow = totalSampleCount
        )

        // Update sliding FPS history
        val updatedHistory = _fpsHistory.value.drop(1) + currentFps.toFloat()
        _fpsHistory.value = updatedHistory
    }
}

// Device Hardware Profiles Utility
object DeviceHardwareDatabase {
    // Info about modern devices and their performance envelopes
    data class DeviceCapabilities(
        val deviceName: String,
        val tier: String,
        val nativeResolution: String,
        val maxRefreshRate: Int,
        val socName: String,
        val thermalRating: String
    )

    val benchmarkReferences = listOf(
        DeviceCapabilities("Asus ROG Phone 8 Pro", "Gaming Flagship", "1080 x 2400", 165, "Snapdragon 8 Gen 3", "Excellent (Active Cooler)"),
        DeviceCapabilities("Samsung Galaxy S24 Ultra", "Premium Tier", "1440 x 3120", 120, "Snapdragon 8 Gen 3 Mobile Platform", "Excellent (Vapor Chamber)"),
        DeviceCapabilities("Google Pixel 8 Pro", "High Tier", "1344 x 2992", 120, "Google Tensor G3", "Good (Slight Thermal Throttle)"),
        DeviceCapabilities("OnePlus 12", "High Tier", "1440 x 3168", 120, "Snapdragon 8 Gen 3", "Outstanding (Dual Cryo-velocity)"),
        DeviceCapabilities("Samsung Galaxy A55", "Mid Tier", "1080 x 2340", 120, "Exynos 1480", "Moderate (Stable 60/90Hz)"),
        DeviceCapabilities("Redmi Note 13 Pro", "Mid Tier", "1220 x 2712", 120, "Snapdragon 7s Gen 2", "Fair (Standard Heat Sink)"),
        DeviceCapabilities("Nokia G42 5G", "Budget Tier", "720 x 1612", 90, "Snapdragon 480+", "Standard (Passive Thermal)")
    )

    // Read details of the active running device screen
    fun getActiveDeviceDetails(context: Context): Map<String, String> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display ?: windowManager.defaultDisplay
            } catch (e: Exception) {
                windowManager.defaultDisplay
            }
        } else {
            windowManager.defaultDisplay
        }

        val metrics = context.resources.displayMetrics
        val densityDpi = metrics.densityDpi
        val activeRefreshRate = display?.refreshRate ?: 60f

        val (physWidth, physHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            metrics.widthPixels to metrics.heightPixels
        }

        val supportedModesList = display?.supportedModes?.toList() ?: emptyList()
        val maxRefreshRateHardware = supportedModesList.map { it.refreshRate }.maxOrNull() ?: activeRefreshRate

        return mapOf(
            "Current Screen" to "$physWidth x $physHeight @ ${activeRefreshRate.toInt()}Hz",
            "Hardware Max FPS" to "${maxRefreshRateHardware.toInt()} FPS Support",
            "Screen Density" to "$densityDpi DPI",
            "Total Supported Modes" to "${supportedModesList.size} Native Configs",
            "Best Mode Profile" to (supportedModesList.maxByOrNull { (it.physicalWidth * it.physicalHeight * it.refreshRate.toInt()).toLong() }?.let {
                "${it.physicalWidth}x${it.physicalHeight} @ ${it.refreshRate.toInt()}Hz"
            } ?: "N/A")
        )
    }

    // Supported display modes helper formatting
    fun getFullNativeModes(context: Context): List<String> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display ?: windowManager.defaultDisplay
            } catch (e: Exception) {
                windowManager.defaultDisplay
            }
        } else {
            windowManager.defaultDisplay
        }
        return display?.supportedModes?.map { mode ->
            "${mode.physicalWidth} x ${mode.physicalHeight} @ ${mode.refreshRate.toInt()}Hz"
        }?.distinct() ?: listOf("60Hz Default Native (Emulator Profile)")
    }
}
