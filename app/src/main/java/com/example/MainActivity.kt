package com.example

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF070A13) // Custom ultra-dark cyberpunk background
                ) {
                    PerformanceDashboardScreen()
                }
            }
        }
    }
}

@Composable
fun PerformanceDashboardScreen() {
    val context = LocalContext.current
    
    // Core performance metrics engine
    val tracker = remember { FpsPerformanceTracker() }
    val metrics by tracker.liveMetrics.collectAsState()
    val fpsHistory by tracker.fpsHistory.collectAsState()
    val isAlertActive by tracker.isAlertActive.collectAsState()

    // Stress particle simulator controls & states
    val simulator = remember { ParticleSimulator() }
    var particleCount by remember { mutableStateOf(100) }
    var speedMultiplier by remember { mutableStateOf(1.0f) }
    var physicsCollisionsOn by remember { mutableStateOf(false) }
    var meshNodeConnectionsOn by remember { mutableStateOf(false) }
    var cpuStressMultiplier by remember { mutableStateOf(0) } // 0 = None, index 1..20
    
    // UI layout tracking
    var isTrackerActive by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Cockpit Dashboard, 1 = Hard specs & Diagnostics

    // Active screen limits read
    val activeSpecs = remember(context) { DeviceHardwareDatabase.getActiveDeviceDetails(context) }
    val nativeSupportedModes = remember(context) { DeviceHardwareDatabase.getFullNativeModes(context) }

    // Start tracker on initialization
    DisposableEffect(isTrackerActive) {
        if (isTrackerActive) {
            tracker.start()
        } else {
            tracker.stop()
        }
        onDispose {
            tracker.stop()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        tracker.setBackgroundState(true)
    }
    
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        tracker.setBackgroundState(false)
    }

    // Live frame clock to cycle animation variables
    var simulatorWidth by remember { mutableStateOf(800f) }
    var simulatorHeight by remember { mutableStateOf(500f) }

    // Stepping simulation loop linked closely to Choreographer / Compose Frame Time
    LaunchedEffect(isTrackerActive, particleCount, speedMultiplier, physicsCollisionsOn, tracker.resolutionPreset, cpuStressMultiplier, meshNodeConnectionsOn) {
        if (isTrackerActive) {
            while (isActive) {
                withFrameMillis { _ ->
                    // Set correct pool size
                    simulator.setParticleCount(particleCount, simulatorWidth, simulatorHeight)
                    // Step physics and computation bounds, passing resolution presets
                    simulator.update(
                        width = simulatorWidth,
                        height = simulatorHeight,
                        physicsOn = physicsCollisionsOn,
                        speedMultiplier = speedMultiplier,
                        resolutionPreset = tracker.resolutionPreset,
                        cpuStressMultiplier = cpuStressMultiplier,
                        nodeConnectionsOn = meshNodeConnectionsOn
                    )
                }
            }
        }
    }

    // Dynamic colors for state highlights
    val primaryColor = Color(0xFF00FFCC) // Cyber neon teal
    val healthyGreen = Color(0xFF22C55E)
    val warningAmber = Color(0xFFF59E0B)
    val criticalRed = Color(0xFFEF4444)

    val currentStatusColor = when {
        metrics.currentFps >= tracker.activeAlertFpsThreshold -> healthyGreen
        metrics.currentFps >= tracker.activeAlertFpsThreshold * 0.85 -> warningAmber
        else -> criticalRed
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF070A13),
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "FPS METER",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryColor,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Blinking recording dot
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
                            val alphaPulse by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isTrackerActive) healthyGreen else Color.Gray)
                                    .alpha(if (isTrackerActive) alphaPulse else 1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTrackerActive) "PROFILING ENGINE ACTIVE" else "MONITORING SUSPENDED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTrackerActive) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    // Top Action Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                tracker.resetStatistics()
                                simulator.particles.clear()
                            },
                            modifier = Modifier
                                .testTag("reset_stats_button")
                                .background(Color(0xFF141A2E), RoundedCornerShape(12.dp))
                                .size(44.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Stats", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { isTrackerActive = !isTrackerActive },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrackerActive) criticalRed.copy(alpha = 0.15f) else healthyGreen.copy(alpha = 0.2f),
                                contentColor = if (isTrackerActive) criticalRed else healthyGreen
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isTrackerActive) criticalRed.copy(alpha = 0.3f) else healthyGreen.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("toggle_tracking_button")
                        ) {
                            Icon(
                                imageVector = if (isTrackerActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isTrackerActive) "Pause" else "Resume",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTrackerActive) "STOP" else "START",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Triple Tabs: Dashboard vs Background vs System Diagnostics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF11172A), RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = "MONITOR PANEL",
                        isActive = activeTab == 0,
                        modifier = Modifier.weight(1.1f).testTag("tab_monitor_panel")
                    ) { activeTab = 0 }
                    TabButton(
                        text = "BACKGROUND FPS",
                        isActive = activeTab == 1,
                        modifier = Modifier.weight(1.1f).testTag("tab_background_telemetry")
                    ) { activeTab = 1 }
                    TabButton(
                        text = "CAPABILITIES",
                        isActive = activeTab == 2,
                        modifier = Modifier.weight(1f).testTag("tab_hardware_capabilities")
                    ) { activeTab = 2 }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 30.dp) // extra padding to avoid system navigation overlap
            ) {
                if (activeTab == 0) {
                    // TAB 1: Real-time Monitor Cockpit
                    TelemetryCockpitSection(
                        tracker = tracker,
                        metrics = metrics,
                        fpsHistory = fpsHistory,
                        isAlertActive = isAlertActive,
                        currentStatusColor = currentStatusColor,
                        simulator = simulator,
                        particleCount = particleCount,
                        onParticleCountChange = { particleCount = it },
                        speedMultiplier = speedMultiplier,
                        onSpeedMultiplierChange = { speedMultiplier = it },
                        physicsCollisionsOn = physicsCollisionsOn,
                        onPhysicsCollisionsOnChange = { physicsCollisionsOn = it },
                        meshNodeConnectionsOn = meshNodeConnectionsOn,
                        onMeshNodeConnectionsOnChange = { meshNodeConnectionsOn = it },
                        cpuStressMultiplier = cpuStressMultiplier,
                        onCpuStressMultiplierChange = { cpuStressMultiplier = it },
                        widthState = simulatorWidth,
                        heightState = simulatorHeight,
                        onWidthChange = { simulatorWidth = it },
                        onHeightChange = { simulatorHeight = it }
                    )
                } else if (activeTab == 1) {
                    // TAB 2: Background Telemetry Reports
                    BackgroundTelemetrySection(tracker = tracker)
                } else {
                    // TAB 3: System Capabilities & Benchmarks
                    CapabilitiesSection(
                        activeSpecs = activeSpecs,
                        nativeSupportedModes = nativeSupportedModes
                    )
                }
            }
            
            // Screen edge visual alarm indicator when dropped frames or alert triggers
            if (isAlertActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            BorderStroke(
                                width = 3.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(criticalRed, criticalRed.copy(alpha = 0f), criticalRed)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun TabButton(text: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color.Transparent)
            .border(
                1.dp,
                if (isActive) Color(0xFF00FFCC).copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isActive) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun TelemetryCockpitSection(
    tracker: FpsPerformanceTracker,
    metrics: PerformanceMetrics,
    fpsHistory: List<Float>,
    isAlertActive: Boolean,
    currentStatusColor: Color,
    simulator: ParticleSimulator,
    particleCount: Int,
    onParticleCountChange: (Int) -> Unit,
    speedMultiplier: Float,
    onSpeedMultiplierChange: (Float) -> Unit,
    physicsCollisionsOn: Boolean,
    onPhysicsCollisionsOnChange: (Boolean) -> Unit,
    meshNodeConnectionsOn: Boolean,
    onMeshNodeConnectionsOnChange: (Boolean) -> Unit,
    cpuStressMultiplier: Int,
    onCpuStressMultiplierChange: (Int) -> Unit,
    widthState: Float,
    heightState: Float,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Toggles for dynamic displayed metrics visibility
        var showCurrentFps by remember { mutableStateOf(true) }
        var showAverageFps by remember { mutableStateOf(true) }
        var showOnePercentLow by remember { mutableStateOf(true) }
        var showZeroOnePercentLow by remember { mutableStateOf(true) }
        var showFrameJitter by remember { mutableStateOf(true) }
        var showDropRate by remember { mutableStateOf(true) }

        // HUD Metrics Customizer Card
        Text(
            text = "CUSTOMIZE ACTIVE TELEMETRY DISPLAY",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Select active performance metric layers to display in the cockpit on-screen panel interface. Unchecked metrics will collapse dynamically.",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricToggleChip(label = "Current FPS", checked = showCurrentFps, modifier = Modifier.weight(1f)) { showCurrentFps = it }
                        MetricToggleChip(label = "1% Low FPS", checked = showOnePercentLow, modifier = Modifier.weight(1f)) { showOnePercentLow = it }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricToggleChip(label = "0.1% Floor", checked = showZeroOnePercentLow, modifier = Modifier.weight(1f)) { showZeroOnePercentLow = it }
                        MetricToggleChip(label = "Frame Jitter", checked = showFrameJitter, modifier = Modifier.weight(1f)) { showFrameJitter = it }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricToggleChip(label = "Drop Rate", checked = showDropRate, modifier = Modifier.weight(1f)) { showDropRate = it }
                        MetricToggleChip(label = "Average FPS", checked = showAverageFps, modifier = Modifier.weight(1f)) { showAverageFps = it }
                    }
                }
            }
        }

        // Horizontal Hud Row (Dynamic Collapse depending on toggles)
        val anySecondaryVisible = showOnePercentLow || showZeroOnePercentLow || showFrameJitter || showDropRate
        if (showCurrentFps || anySecondaryVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showCurrentFps) {
                    // Circle Radial Gauge
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .background(Color(0xFF11172A), RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color(0xFF1E294B)), RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Target gauge arc
                        val animatedFpsArc = animateFloatAsState(
                            targetValue = (metrics.currentFps.toFloat() / tracker.activeTargetFps).coerceIn(0f, 1.2f),
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                            label = "fpsArc"
                        )
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer background trace arc
                            drawArc(
                                color = Color(0xFF1E294B),
                                startAngle = 140f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Inner glowing telemetry arc
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFEF4444),
                                        Color(0xFFF59E0B),
                                        Color(0xFF22C55E),
                                        Color(0xFF00FFCC)
                                    )
                                ),
                                startAngle = 140f,
                                sweepAngle = 260f * animatedFpsArc.value,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.0f", metrics.currentFps),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = currentStatusColor,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "CURRENT FPS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                            // Alert text indicator
                            if (isAlertActive) {
                                Text(
                                    text = "STUTTER WARNING",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF4444),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            } else {
                                Text(
                                    text = if (metrics.currentFps >= tracker.activeAlertFpsThreshold * 0.95) "STABLE PACE" else "MODERATE PACE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (metrics.currentFps >= tracker.activeAlertFpsThreshold * 0.95) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    if (anySecondaryVisible) {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                // Dashboard Secondary Benchmarks Grid column (2 rows of columns)
                if (anySecondaryVisible) {
                    Column(
                        modifier = if (showCurrentFps) {
                            Modifier.weight(1f).height(130.dp)
                        } else {
                            Modifier.fillMaxWidth().height(130.dp)
                        },
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val row1Visible = showOnePercentLow || showZeroOnePercentLow
                        if (row1Visible) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (showOnePercentLow) {
                                    MiniMetricCard(
                                        title = "1% LOW FPS",
                                        value = String.format(Locale.US, "%.1f", metrics.onePercentLowFps),
                                        caption = "Gaming Drop Index",
                                        modifier = Modifier.weight(1f),
                                        accentColor = if (metrics.onePercentLowFps >= tracker.activeAlertFpsThreshold * 0.8) Color(0xFF00FFCC) else Color(0xFFF59E0B)
                                    )
                                }
                                if (showZeroOnePercentLow) {
                                    MiniMetricCard(
                                        title = "0.1% FLOOR",
                                        value = String.format(Locale.US, "%.1f", metrics.zeroOnePercentLowFps),
                                        caption = "Worst Stutter Dip",
                                        modifier = Modifier.weight(1f),
                                        accentColor = if (metrics.zeroOnePercentLowFps >= tracker.activeAlertFpsThreshold * 0.6) Color(0xFF00FFCC) else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        if (row1Visible && (showFrameJitter || showDropRate)) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val row2Visible = showFrameJitter || showDropRate
                        if (row2Visible) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (showFrameJitter) {
                                    MiniMetricCard(
                                        title = "FRAME JITTER",
                                        value = String.format(Locale.US, "%.2f ms", metrics.frameJitterMs),
                                        caption = "Target SD: <${tracker.activeAlertJitterMs}ms",
                                        modifier = Modifier.weight(1f),
                                        accentColor = if (metrics.frameJitterMs <= tracker.activeAlertJitterMs) Color(0xFF22C55E) else Color(0xFFF59E0B)
                                    )
                                }
                                if (showDropRate) {
                                    MiniMetricCard(
                                        title = "DROP RATE",
                                        value = String.format(Locale.US, "%.1f%%", metrics.frameDropRatePercent),
                                        caption = "Missed Budgets",
                                        modifier = Modifier.weight(1f),
                                        accentColor = if (metrics.frameDropRatePercent < 5.0) Color(0xFF22C55E) else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Average Frame Time HUD row
        if (showAverageFps) {
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
                border = BorderStroke(1.dp, Color(0xFF1E294B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Stats",
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Telemetry Averages:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        text = "AVG FPS: ${String.format(Locale.US, "%.1f", metrics.averageFps)}  •  INTERVAL: ${String.format(Locale.US, "%.2f ms", metrics.avgFrameTimeMs)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: Real-time Rolling Plot Line Chart
        Text(
            text = "REAL-TIME FRAME LATENCY MAP",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                val maxLimitVal = tracker.activeTargetFps.toFloat() * 1.15f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Draw reference grid lines horizontally at 30, 60, 90, 120 FPS
                    val thresholdLevels = listOf(30f, 60f, 90f, 120f).filter { it <= maxLimitVal }
                    for (level in thresholdLevels) {
                        val yNormalized = canvasHeight - (level / maxLimitVal * canvasHeight)
                        if (yNormalized >= 0 && yNormalized <= canvasHeight) {
                            drawLine(
                                color = Color(0xFF1E294B).copy(alpha = 0.6f),
                                start = Offset(0f, yNormalized),
                                end = Offset(canvasWidth, yNormalized),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                    }

                    // Map the historic values down
                    if (fpsHistory.isNotEmpty()) {
                        val count = fpsHistory.size
                        val stepX = canvasWidth / (count - 1).coerceAtLeast(1)
                        val points = fpsHistory.mapIndexed { index, fps ->
                            val x = index * stepX
                            val normalizedFps = fps.coerceAtMost(maxLimitVal)
                            val y = canvasHeight - (normalizedFps / maxLimitVal * canvasHeight)
                            Offset(x, y)
                        }

                        // Build gradient area curve
                        val areaPath = Path().apply {
                            moveTo(0f, canvasHeight)
                            for (p in points) {
                                lineTo(p.x, p.y)
                            }
                            lineTo(canvasWidth, canvasHeight)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF00FFCC).copy(alpha = 0.25f),
                                    Color(0xFF00FFCC).copy(alpha = 0.0f)
                                )
                            )
                        )

                        // Draw connecting path lines
                        val linePath = Path().apply {
                            val first = points.first()
                            moveTo(first.x, first.y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                        drawPath(
                            path = linePath,
                            color = Color(0xFF00FFCC),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                
                // Text labels overlays for benchmarks
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${tracker.activeTargetFps} MAX TARGET",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.35f),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "60 FPS BUDGET",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.35f),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "0 FPS FLOOR",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.35f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION: Active Hardware Load Stress-Simulator Canvas
        Text(
            text = "HARDWARE STRESS & PERFORMANCE TESTING",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Toggle synthetic load metrics, physics complexities, node matrix connection, and customized specs resolution scaling to witness dropped frame behaviors.",
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // The interactive physics simulation view space
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090D1A))
                        .onGloballyPositioned { coordinates ->
                            onWidthChange(coordinates.size.width.toFloat())
                            onHeightChange(coordinates.size.height.toFloat())
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color(0xFF10162B),
                            topLeft = Offset.Zero,
                            size = size
                        )

                        // 1. Draw interconnected node network lines if O(N^2) mesh network is active
                        if (meshNodeConnectionsOn && simulator.particles.size > 1) {
                            val limitSize = simulator.particles.size.coerceAtMost(350)
                            for (i in 0 until limitSize) {
                                val p1 = simulator.particles[i]
                                for (j in (i + 1) until limitSize) {
                                    val p2 = simulator.particles[j]
                                    val dx = p2.x - p1.x
                                    val dy = p2.y - p1.y
                                    val dist = sqrt(dx * dx + dy * dy)
                                    // Make links fade based on relative distance
                                    if (dist < 80f) {
                                        drawLine(
                                            color = Color(0xFF00FFCC).copy(alpha = (1f - dist / 80f) * 0.35f),
                                            start = Offset(p1.x, p1.y),
                                            end = Offset(p2.x, p2.y),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Render all particles
                        for (particle in simulator.particles) {
                            drawCircle(
                                color = particle.color,
                                radius = particle.radius,
                                center = Offset(particle.x, particle.y)
                            )
                        }

                        // 3. Render watermark grid simulating resolution scale bounding box
                        drawRect(
                            color = Color(0xFF00FFCC).copy(alpha = 0.05f),
                            size = size,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Watermark diagnostics on simulator display
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "SIMULATOR GRID: ${tracker.resolutionPreset.displayName}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FFCC).copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "ACTIVE RENDERS: ${simulator.particles.size} SHAPES",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.35f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stress Simulator tuning parameters
                Text(
                    text = "STRESS ENVIRONMENT CONTROLS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Custom control slider: Volume of shapes (Particle Count)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Active Shapes Load:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$particleCount Particles",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00FFCC),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = particleCount.toFloat(),
                        onValueChange = { onParticleCountChange(it.toInt()) },
                        valueRange = 5f..1200f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FFCC),
                            activeTrackColor = Color(0xFF00FFCC),
                            inactiveTrackColor = Color(0xFF1E294B)
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("shape_load_slider")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Simulated CPU Strain Core Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CPU Core Stress Level:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (cpuStressMultiplier == 0) "OFF (No Strain)" else "Strain Lv: $cpuStressMultiplier / 20",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (cpuStressMultiplier == 0) Color.White.copy(alpha = 0.4f) else Color(0xFFEF4444),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = cpuStressMultiplier.toFloat(),
                        onValueChange = { onCpuStressMultiplierChange(it.toInt()) },
                        valueRange = 0f..20f,
                        steps = 20,
                        colors = SliderDefaults.colors(
                            thumbColor = if (cpuStressMultiplier == 0) Color.White else Color(0xFFEF4444),
                            activeTrackColor = Color(0xFFEF4444),
                            inactiveTrackColor = Color(0xFF1E294B)
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("cpu_stress_slider")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Switch parameter toggles row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Physics Collisions switch (Pairwise vector logic check)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0E1325), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E294B), RoundedCornerShape(8.dp))
                            .clickable { onPhysicsCollisionsOnChange(!physicsCollisionsOn) }
                            .padding(10.dp)
                            .testTag("physics_collisions_toggle")
                    ) {
                        Text(
                            text = "Rigid Physics",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "O(N²) Impulse loop",
                            fontSize = 8.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Switch(
                            checked = physicsCollisionsOn,
                            onCheckedChange = { onPhysicsCollisionsOnChange(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00FFCC),
                                checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.25f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1E294B)
                            ),
                            modifier = Modifier.graphicsLayer(scaleX = 0.81f, scaleY = 0.81f)
                        )
                    }

                    // Network lines O(N^2) links drawer switch
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0E1325), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E294B), RoundedCornerShape(8.dp))
                            .clickable { onMeshNodeConnectionsOnChange(!meshNodeConnectionsOn) }
                            .padding(10.dp)
                            .testTag("network_mesh_toggle")
                    ) {
                        Text(
                            text = "Mesh Network Lines",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "O(N²) Path draw lines",
                            fontSize = 8.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Switch(
                            checked = meshNodeConnectionsOn,
                            onCheckedChange = { onMeshNodeConnectionsOnChange(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00FFCC),
                                checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.25f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1E294B)
                            ),
                            modifier = Modifier.graphicsLayer(scaleX = 0.81f, scaleY = 0.81f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Hardware Profile Settings & Monitor Rules Adjuster
        Text(
            text = "TELEMETRY MONITOR ADVANCED SETTINGS",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Hardware profiles dropdown presets
                Text(
                    text = "SPEC TARGET MODEL PROFILE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HardwareProfile.values().forEach { profile ->
                    val isSelected = tracker.profile == profile
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.1f) else Color(0xFF0D1222))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.4f) else Color(0xFF1E294B),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { tracker.profile = profile }
                            .padding(12.dp)
                            .testTag("profile_option_${profile.name}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF00FFCC) else Color.White
                                )
                                Text(
                                    text = profile.description,
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { tracker.profile = profile },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF00FFCC),
                                    unselectedColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Simulated Screen Resolution Level Preset selector
                Text(
                    text = "RESOLUTION GRAPHICS DENSITY PRESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScreenResolutionPreset.values().forEach { preset ->
                        val isSelected = tracker.resolutionPreset == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color(0xFF0D1222))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF00FFCC).copy(alpha = 0.4f) else Color(0xFF1E294B),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { tracker.resolutionPreset = preset }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("resolution_preset_${preset.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = preset.name.substringBefore("_"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) Color(0xFF00FFCC) else Color.White
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", preset.multiplier)}x",
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                // If CUSTOM_TUNABLE is active, provide sliding metric thresholds adjusting
                AnimatedVisibility(
                    visible = tracker.profile == HardwareProfile.CUSTOM_TUNABLE,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .background(Color(0xFF0B1020), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF1B233D), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "CUSTOM PARAMETER TUNER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00FFCC),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 1. Target FPS
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Custom Target Refresh Rate:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                Text("${tracker.customTargetFps} FPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                            }
                            Slider(
                                value = tracker.customTargetFps.toFloat(),
                                onValueChange = { tracker.customTargetFps = it.toInt() },
                                valueRange = 30f..144f,
                                steps = 114,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00FFCC)),
                                modifier = Modifier.testTag("custom_target_fps_slider")
                            )
                        }

                        // 2. Alert Low FPS Alert Level
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Trigger Alert if FPS dips below:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                Text("${tracker.customAlertFpsThreshold} FPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            }
                            Slider(
                                value = tracker.customAlertFpsThreshold.toFloat(),
                                onValueChange = { tracker.customAlertFpsThreshold = it.toInt() },
                                valueRange = 24f..120f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFEF4444)),
                                modifier = Modifier.testTag("custom_alert_fps_slider")
                            )
                        }

                        // 3. Jitter Trigger Limits
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Alarm on Frame Pacing Jitter >", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                Text(String.format(Locale.US, "%.1f ms", tracker.customAlertJitterMs), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            }
                            Slider(
                                value = tracker.customAlertJitterMs,
                                onValueChange = { tracker.customAlertJitterMs = it },
                                valueRange = 0.5f..8.0f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B)),
                                modifier = Modifier.testTag("custom_jitter_slider")
                            )
                        }

                        // 4. Sample window interval rate
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Telemetry Update Sample Window:", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                Text("${tracker.customSampleIntervalMs} ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Slider(
                                value = tracker.customSampleIntervalMs.toFloat(),
                                onValueChange = { tracker.customSampleIntervalMs = it.toInt() },
                                valueRange = 100f..2000f,
                                steps = 19,
                                colors = SliderDefaults.colors(thumbColor = Color.White),
                                modifier = Modifier.testTag("custom_sample_interval_slider")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilitiesSection(
    activeSpecs: Map<String, String>,
    nativeSupportedModes: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // SECTION: Running Device Physical Specs
        Text(
            text = "ACTIVE PHYSICAL SCREEN SPECS",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                activeSpecs.forEach { (key, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = value,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Divider(color = Color(0xFF1E294B).copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Native Display hardware support lists
        Text(
            text = "HARDWARE NATIVE RUNTIME MODES",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                nativeSupportedModes.forEachIndexed { index, mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF00FFCC))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Mode #$index:  $mode",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (index < nativeSupportedModes.size - 1) {
                        Divider(color = Color(0xFF1E294B).copy(alpha = 0.5f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: Reference specs for comparison lookup database
        Text(
            text = "MOBILE PHONE HARDWARE PROFILES MAP",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        DeviceHardwareDatabase.benchmarkReferences.forEach { cap ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A).copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF1E294B).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cap.deviceName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${cap.maxRefreshRate} Hz Max",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FFCC),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Native Res: ${cap.nativeResolution}  |  SoC: ${cap.socName}",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Thermal Enclosure Rating: ${cap.thermalRating}",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniMetricCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00FFCC)
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
        border = BorderStroke(1.dp, Color(0xFF1E294B)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = caption,
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.35f),
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
fun MetricToggleChip(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color(0xFF0D1222))
            .border(
                1.dp,
                if (checked) Color(0xFF00FFCC).copy(alpha = 0.4f) else Color(0xFF1E294B),
                RoundedCornerShape(8.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("metric_chip_${label.replace(" ", "_").lowercase()}"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (checked) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.2f))
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (checked) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BackgroundTelemetrySection(tracker: FpsPerformanceTracker) {
    val bgFpsHistory by tracker.bgFpsHistory.collectAsState()

    val primaryColor = Color(0xFF00FFCC)
    val warningColor = Color(0xFFF59E0B)
    val criticalColor = Color(0xFFEF4444)
    val successColor = Color(0xFF22C55E)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // App lifecycle state status card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (tracker.isAppInBackground) Color(0xFF1E1A22) else Color(0xFF11172A)
            ),
            border = BorderStroke(
                1.dp,
                if (tracker.isAppInBackground) warningColor.copy(alpha = 0.5f) else primaryColor.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_beacon")
                val alphaPulse by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_latch"
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (tracker.isAppInBackground) warningColor else successColor)
                        .alpha(alphaPulse)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (tracker.isAppInBackground) "LIFECYCLE STATE: BACKGROUND" else "LIFECYCLE STATE: ACTIVE FOREGROUND",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (tracker.isAppInBackground)
                            "Vsync rendering suspended. Tracking via internal high-fidelity coroutine clock emitter."
                            else "Normal Vsync active. Interactive particle solver and main canvas tracing live.",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Live stats grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniMetricCard(
                title = "DAEMON RATE (FPS)",
                value = String.format(Locale.US, "%.1f", tracker.currentBgFps),
                caption = "Thread Tick Interval",
                modifier = Modifier.weight(1f),
                accentColor = if (tracker.currentBgFps >= 50.0) successColor else warningColor
            )
            MiniMetricCard(
                title = "BG AVG SPEED",
                value = String.format(Locale.US, "%.1f Hz", tracker.averageBgFps),
                caption = "Core Target: 60Hz",
                modifier = Modifier.weight(1f),
                accentColor = if (tracker.averageBgFps >= 45.0) primaryColor else criticalColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniMetricCard(
                title = "SCHEDULING JITTER",
                value = String.format(Locale.US, "%.2f ms", tracker.bgJitterMs),
                caption = "Deviation limit: <4ms",
                modifier = Modifier.weight(1f),
                accentColor = if (tracker.bgJitterMs <= 3.0) successColor else warningColor
            )
            MiniMetricCard(
                title = "THROTTLE EVENTS",
                value = "${tracker.bgThrottleEventsAlertCount}",
                caption = "OS Deep Sleep Sinks",
                modifier = Modifier.weight(1f),
                accentColor = if (tracker.bgThrottleEventsAlertCount == 0) successColor else criticalColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background historical rate trace (mini-chart)
        Text(
            text = "DAEMON TICK EMITTER VELOCITY HISTORY",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = primaryColor,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11172A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val pointsCount = bgFpsHistory.size

                    if (pointsCount > 1) {
                        val maxVal = 70f
                        val minVal = 0f
                        val yRange = maxVal - minVal

                        val path = Path()
                        val stepX = width / (pointsCount - 1)

                        bgFpsHistory.forEachIndexed { idx, value ->
                            val cValue = value.coerceIn(minVal, maxVal)
                            val x = idx * stepX
                            val y = height - ((cValue - minVal) / yRange) * height

                            if (idx == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        // Drawing grid lines helper
                        val gridCount = 4
                        for (i in 0..gridCount) {
                            val yOffset = (height / gridCount) * i
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(0f, yOffset),
                                end = Offset(width, yOffset),
                                strokeWidth = 1f
                            )
                        }

                        // Glow path stroke
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        // Live scroll transitions log
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ASYNC LIFE EVENT HISTORY & FAULTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = primaryColor,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "PEAK LATENCY: ${String.format(Locale.US, "%.1f", tracker.backgroundMaxDelayMs)}ms",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = FontFamily.Monospace
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090D1A)),
            border = BorderStroke(1.dp, Color(0xFF1E294B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(150.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (tracker.backgroundLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No cycle event reports registered yet.",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.35f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    tracker.backgroundLogs.forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[${log.timestamp}]",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (log.isWarning) criticalColor else successColor,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = log.description,
                                fontSize = 9.sp,
                                color = if (log.isWarning) Color.White else Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Instructional message for background verification
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A2E).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Instruction",
                    tint = primaryColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Developer Instruction: Move this application to the background (by minimizing or returning to Home screen / locking your device) and return after a couple of seconds. You will see detailed statistics of the device background CPU throttling behaviors and sleep state delays mapped above.",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 12.sp
                )
            }
        }
    }
}

// Preview definition
@Preview(showBackground = true)
@Composable
fun PerformanceDashboardScreenPreview() {
    MyApplicationTheme(darkTheme = true) {
        Surface(color = Color(0xFF070A13)) {
            PerformanceDashboardScreen()
        }
    }
}
