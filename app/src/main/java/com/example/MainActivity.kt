package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.UserSettings
import com.example.data.WaterLog
import com.example.ui.WaterViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val viewModel: WaterViewModel = viewModel()
    val totalTodayIntake by viewModel.totalTodayIntake.collectAsStateWithLifecycle()
    val progress by viewModel.currentWaterProgress.collectAsStateWithLifecycle()
    val nextInterval by viewModel.nextHydrationIntervalMinutes.collectAsStateWithLifecycle()
    val currentActivity by viewModel.currentActivityLevel.collectAsStateWithLifecycle()
    val showBreakAlert by viewModel.showBreakAlert.collectAsStateWithLifecycle()
    val isBreakTimerActive by viewModel.isBreakTimerActive.collectAsStateWithLifecycle()
    val breakTimerSeconds by viewModel.activeBreakTimerSeconds.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Display a beautiful stretch break invitation overlay
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Hydro Hub", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.WaterDrop else Icons.Outlined.WaterDrop,
                                contentDescription = "Hydro Hub Dashboard",
                                tint = if (selectedTab == 0) Color(0xFF38BDF8) else LocalContentColor.current
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Move Sensor", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 1) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.AutoMirrored.Outlined.DirectionsWalk,
                                contentDescription = "Movement Tracker",
                                tint = if (selectedTab == 1) Color(0xFF10B981) else LocalContentColor.current
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("Analytics", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Default.QueryStats else Icons.Outlined.QueryStats,
                                contentDescription = "History & Settings",
                                tint = if (selectedTab == 2) Color(0xFFF59E0B) else LocalContentColor.current
                            )
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    )
            ) {
                Crossfade(targetState = selectedTab, label = "TabNavigation") { tab ->
                    when (tab) {
                        0 -> DashboardScreen(viewModel)
                        1 -> MovementScreen(viewModel)
                        2 -> AnalyticsAndSettingsScreen(viewModel)
                    }
                }
            }
        }

        // Animated overlay break alert modal
        if (showBreakAlert) {
            BreakAlertOverlay(
                onAccept = { viewModel.startStretchBreakTimer() },
                onDismiss = { viewModel.dismissBreakAlert() }
            )
        }

        // Active Stretch Break Fullscreen Overlay Countdown Timer
        if (isBreakTimerActive) {
            StretchBreakTimerScreen(
                secondsLeft = breakTimerSeconds,
                onSkip = { viewModel.skipStretchBreakTimer() }
            )
        }
    }
}

// ----------------------------------------------------
// SCREEN 1: WATER HUB (DASHBOARD)
// ----------------------------------------------------
@Composable
fun DashboardScreen(viewModel: WaterViewModel) {
    val totalIntake by viewModel.totalTodayIntake.collectAsStateWithLifecycle()
    val progress by viewModel.currentWaterProgress.collectAsStateWithLifecycle()
    val nextInterval by viewModel.nextHydrationIntervalMinutes.collectAsStateWithLifecycle()
    val activityLevel by viewModel.currentActivityLevel.collectAsStateWithLifecycle()
    val isSimulated by viewModel.sensorTracker.isSimulatedMode.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title Banner
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "HydroSense",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Syncing hydration with your physical body",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Circular Water Progress and Wave Animation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Today's Hydration Ratio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Interactive Custom Water Wave Composable
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WaterWaveAnimation(progress = progress)

                        // Center Percentage Overlay Text
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (progress > 0.45f) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$totalIntake / ${settingsState.dailyTargetMl} ml",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (progress > 0.45f) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick-add serving controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    viewModel.logWaterServing()
                                    Toast.makeText(context, "+${settingsState.waterQuantityLevelMl}ml Liquid Saved!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFF38BDF8), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Quick Add serving size",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "+${settingsState.waterQuantityLevelMl}ml Cup",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    viewModel.logCustomWater(150)
                                    Toast.makeText(context, "+150ml Hydrated!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalCafe,
                                    contentDescription = "Standard cup size",
                                    tint = Color(0xFF0369A1)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "150ml glass", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    viewModel.logCustomWater(500)
                                    Toast.makeText(context, "+500ml Hydrated!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sports,
                                    contentDescription = "Large Bottle size",
                                    tint = Color(0xFF0369A1)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "500ml active", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Dynamic Frequency / Recommendation Alert Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (activityLevel) {
                        "High" -> Color(0xFFFEF3C7) // Amber/Yellow-tint warning (faster hydration alert)
                        "Medium" -> Color(0xFFECFDF5) // Greenish comfort
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(
                                color = when (activityLevel) {
                                    "High" -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                    "Medium" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                    else -> Color(0xFF0284C7).copy(alpha = 0.1f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activityLevel) {
                                "High" -> Icons.Default.DirectionsRun
                                "Medium" -> Icons.Default.DirectionsWalk
                                else -> Icons.Default.Hotel
                            },
                            contentDescription = "Current Body State",
                            tint = when (activityLevel) {
                                "High" -> Color(0xFFB45309)
                                "Medium" -> Color(0xFF047857)
                                else -> Color(0xFF0369A1)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Activity Adjusted Hydration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (activityLevel) {
                                "High" -> "High sweat activity detected! Safe intervals shortened by 30% to prevent dehydration."
                                "Medium" -> "Normal dynamic movements active. Drink targets optimal intervals (15% faster)."
                                else -> "Physical state is sitting/sedentary. Hydration interval set to standard."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF374151)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Calculated interval: Remind every $nextInterval mins",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF111827)
                        )
                    }
                }
            }
        }

        // Live Physical Movement Tracker Quick Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = AssistChipDefaults.assistChipBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Physical Body Tracking",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("Sensors Active") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Green, CircleShape)
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current State:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = activityLevel.uppercase(Locale.getDefault()),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = when (activityLevel) {
                                "High" -> Color(0xFFEF4444)
                                "Medium" -> Color(0xFF10B981)
                                else -> Color(0xFF3F3F46)
                            }
                        )
                    }

                    // Ticker details
                    val stationarySeconds by viewModel.stationarySeconds.collectAsStateWithLifecycle()
                    val targetSeconds = settingsState.movementBreakIntervalMinutes * 60
                    val currentProgressPercentage = (stationarySeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Sedentary Duration:", fontSize = 13.sp)
                            Text(
                                text = formatSecondsToMinutes(stationarySeconds),
                                fontWeight = FontWeight.Bold,
                                color = if (stationarySeconds > targetSeconds * 0.8f) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        LinearProgressIndicator(
                            progress = { currentProgressPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (stationarySeconds > targetSeconds * 0.8f) Color(0xFFEF4444) else Color(0xFF3B82F6),
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stationary Period limit: ${settingsState.movementBreakIntervalMinutes}m",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Stand up reset: Auto on motion",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 2: MOVEMENT TRACKER & SENSORS
// ----------------------------------------------------
@Composable
fun MovementScreen(viewModel: WaterViewModel) {
    val activityLevel by viewModel.currentActivityLevel.collectAsStateWithLifecycle()
    val motionIntensity by viewModel.sensorMotionIntensity.collectAsStateWithLifecycle()
    val stationarySeconds by viewModel.stationarySeconds.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val isSimulated by viewModel.sensorTracker.isSimulatedMode.collectAsStateWithLifecycle()
    
    val accelValues by viewModel.sensorTracker.accelerometerValues.collectAsStateWithLifecycle()
    val gyroValues by viewModel.sensorTracker.gyroscopeValues.collectAsStateWithLifecycle()

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Heading Block
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Motion Sensors",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "Combines Gyroscope and Accelerometer data to prevent sedentary diseases",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Real-Time Sensor Readings Canvas and details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Live Telemetry Feed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    // Moving Bar representation of current motion
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Estimated Motion Force: ${"%.2f".format(motionIntensity)} m/s²",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val limitIntensity = 10f
                            val sensorFraction = (motionIntensity / limitIntensity).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(sensorFraction)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF10B981), Color(0xFF34D399))
                                        )
                                    )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Raw coordinates
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Accelerometer Axis (X, Y, Z)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "[ ${"%.2f".format(accelValues.first)}, ${"%.2f".format(accelValues.second)}, ${"%.2f".format(accelValues.third)} ]",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Rotational Gyro Scope", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "[ ${"%.2f".format(gyroValues.first)}, ${"%.2f".format(gyroValues.second)}, ${"%.2f".format(gyroValues.third)} ]",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Demo Interactive Controls (Essential for simulator evaluation!)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEFF6FF)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Simulator Evaluation Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E3A8A)
                        )
                        Text(
                            text = "Simulate motion if the device is stationary on your computer.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF2563EB)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                viewModel.sensorTracker.enableSimulation(true)
                                viewModel.sensorTracker.simulateState("Low")
                                Toast.makeText(context, "Sitting state simulated", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text("Sim Sitting", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                viewModel.sensorTracker.enableSimulation(true)
                                viewModel.sensorTracker.simulateState("Medium")
                                Toast.makeText(context, "Movement/Walking simulated", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text("Sim Moving", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.triggerDemoBreakAlertImmediately()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Timer, contentDescription = "Trigger break immediately")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger Break Prompt Immediately", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Science & Internet Best-Practice Corner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Medical Source",
                            tint = Color(0xFF3B82F6)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Sedentary Science & Best Practices", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Text(
                        text = "• The Mayo Clinic recommends rising and performing light movements for at least 2 minutes straight for every 45-50 minutes of continuous seated workspace.\n" +
                               "• Prolonged physical stillness triggers insulin level drops and elevates body fat conversion. Standing up immediately restarts beneficial enzyme functions.\n" +
                               "• Combining a glass of water with each stretch break yields maximum metabolic synergy.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 3: ANALYTICS & SETTINGS
// ----------------------------------------------------
@Composable
fun AnalyticsAndSettingsScreen(viewModel: WaterViewModel) {
    val allLogs by viewModel.allWaterLogs.collectAsStateWithLifecycle()
    val todayLogs by viewModel.todayWaterLogs.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val movementLogs by viewModel.movementLogs.collectAsStateWithLifecycle()

    var activeViewTab by remember { mutableIntStateOf(0) } // 0 = Reports, 1 = Config Settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (activeViewTab == 0) "Reports" else "Preferences",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (activeViewTab == 0) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
            )

            // Minimalist Tab Swivel Slider
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                Text(
                    text = "Reports",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeViewTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeViewTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeViewTab = 0 }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )

                Text(
                    text = "Settings",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeViewTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeViewTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeViewTab = 1 }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeViewTab == 0) {
                ReportsTab(this@Column, allLogs, todayLogs, movementLogs, settingsState, viewModel)
            } else {
                SettingsTab(settingsState, viewModel)
            }
        }
    }
}

@Composable
fun ReportsTab(
    columnScope: ColumnScope,
    allLogs: List<WaterLog>,
    todayLogs: List<WaterLog>,
    movementLogs: List<com.example.data.MovementLog>,
    settingsState: UserSettings,
    viewModel: WaterViewModel
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Compact Stat Overviews Rows
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val totalDrunkToday = todayLogs.sumOf { it.amountMl }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F8FD))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Total Intake", fontSize = 11.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                        Text(text = "$totalDrunkToday ml", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF0369A1))
                        Text(text = "Target: ${settingsState.dailyTargetMl}ml", fontSize = 10.sp, color = Color(0xFF075985))
                    }
                }

                val totalBreaksToday = movementLogs.size
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Stretch Breaks", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                        Text(text = "$totalBreaksToday taken", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF065F46))
                        Text(text = "Dynamic sensors active", fontSize = 10.sp, color = Color(0xFF047857))
                    }
                }
            }
        }

        // Beautiful Visual Bar Chart of the Past 7 Days!
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Hydration Level - Last 7 Days",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    WeeklyWaterChart(allLogs = allLogs, targetMl = settingsState.dailyTargetMl)
                }
            }
        }

        // Section label
        item {
            Text(
                text = "Today's Consumption Logs",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // List of entries
        if (todayLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SentimentDissatisfied,
                            contentDescription = "Empty list",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No water logged yet today. Click quick cup below!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(todayLogs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE0F2FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Water drink Log",
                                    tint = Color(0xFF0284C7)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${log.amountMl} ml Liquid Glass",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Activity state: ${log.activityLevel} | Time: ${formatTimestamp(log.timestamp)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                viewModel.deleteWaterLog(log)
                                Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete entry log",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(settings: UserSettings, viewModel: WaterViewModel) {
    var targetMlStr by remember(settings) { mutableStateOf(settings.dailyTargetMl.toString()) }
    var glassSizeStr by remember(settings) { mutableStateOf(settings.waterQuantityLevelMl.toString()) }
    var breakIntervalStr by remember(settings) { mutableStateOf(settings.movementBreakIntervalMinutes.toString()) }
    var selectedProfile by remember(settings) { mutableStateOf(settings.selectedActivityProfile) }
    var rawSensorsEnabled by remember(settings) { mutableStateOf(settings.enableSensorTracking) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "Preferences Config Workspace", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // Daily Target
                    OutlinedTextField(
                        value = targetMlStr,
                        onValueChange = { targetMlStr = it },
                        label = { Text("Daily Water Target (ml)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null) }
                    )

                    // Glass Serving Size
                    OutlinedTextField(
                        value = glassSizeStr,
                        onValueChange = { glassSizeStr = it },
                        label = { Text("Glass Size per Serving (ml)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.LocalCafe, contentDescription = null) }
                    )

                    // Break Interval minutes
                    OutlinedTextField(
                        value = breakIntervalStr,
                        onValueChange = { breakIntervalStr = it },
                        label = { Text("Movement Break Reminder (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.AccessTime, contentDescription = null) }
                    )

                    // Profile selector
                    Text(text = "Physical Activity Multiplier Profile", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Auto", "Low", "Medium", "High").forEach { profile ->
                            val isSelected = selectedProfile == profile
                            Button(
                                onClick = { selectedProfile = profile },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = profile,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = "Auto profile leverages live physical accelerometer and gyroscope sensors on the phone dynamically. Other selects hardcode baseline activity levels.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val targetInt = targetMlStr.toIntOrNull() ?: 2500
                            val glassInt = glassSizeStr.toIntOrNull() ?: 250
                            val breakInt = breakIntervalStr.toIntOrNull() ?: 50
                            viewModel.saveUserSettings(
                                dailyTargetMl = targetInt,
                                waterQuantityLevelMl = glassInt,
                                breakMinutes = breakInt,
                                sensorEnabled = rawSensorsEnabled,
                                activityProfile = selectedProfile
                            )
                            Toast.makeText(context, "Preferences Saved and Live Sync Activated!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Save settings")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Config & Recalculate Intervals")
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// GRAPHICS & DRAWABLES
// ----------------------------------------------------
@Composable
fun WaterWaveAnimation(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseTransition")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhaseOffset"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val waveHeight = 15.dp.toPx()

        // Water line depends directly on progress fractional bounds
        val levelY = height - (height * progress)

        val path = Path()
        path.moveTo(0f, height)

        // Wave formula: y = A * sin(k * x + wt) + levelY
        val points = mutableListOf<Offset>()
        val segments = 100
        for (i in 0..segments) {
            val x = i * (width / segments)
            // Double wave rhythm
            val y = (waveHeight * sin((2 * Math.PI * i / segments).toFloat() + waveOffset)) + levelY
            path.lineTo(x, y.coerceIn(0f, height))
        }

        path.lineTo(width, height)
        path.close()

        // Beautiful Water Flow semi-transparent gradient representation
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.9f),
                    Color(0xFF0284C7).copy(alpha = 0.95f),
                    Color(0xFF1E3A8A)
                )
            )
        )
    }
}

@Composable
fun WeeklyWaterChart(allLogs: List<WaterLog>, targetMl: Int) {
    // Collect last 7 calendar days total sum
    val calendar = Calendar.getInstance()
    val weeklyTotals = remember(allLogs) {
        val list = mutableListOf<Pair<String, Int>>()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        for (i in 6 downTo 0) {
            val tempCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val startOfDay = tempCal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endOfDay = startOfDay + 24 * 60 * 60 * 1000

            val dayVolume = allLogs.filter { it.timestamp in startOfDay until endOfDay }.sumOf { it.amountMl }
            val dayLabel = dayFormat.format(tempCal.time)
            list.add(dayLabel to dayVolume)
        }
        list
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        weeklyTotals.forEach { dayPair ->
            val dayLabel = dayPair.first
            val dayVolume = dayPair.second
            val fraction = (dayVolume.toFloat() / targetMl).coerceIn(0f, 1.2f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Volume popup labels if non-zero
                if (dayVolume > 0) {
                    Text(
                        text = "${dayVolume / 100 * 100}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Drawing solid columns
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .fillMaxHeight(fraction.coerceIn(0.05f, 1.0f) * 0.82f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = if (dayVolume >= targetMl) {
                                    listOf(Color(0xFF10B981), Color(0xFF059669)) // met target
                                } else {
                                    listOf(Color(0xFF38BDF8), Color(0xFF0284C7)) // progressing
                                }
                            )
                        )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = dayLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ----------------------------------------------------
// BREAK ALERTS & DETAILED OVERLAYS
// ----------------------------------------------------
@Composable
fun BreakAlertOverlay(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .padding(32.dp)
            .wrapContentSize(Alignment.Center),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(Color(0xFFFEE2E2), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = "Active stretch notice",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(34.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Time to Move! 🤸",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Our wellness sensors detected you have been stationary for too long. Prolonged sitting blocks blood flow. Take a 2-minute break!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Exercise Suggestions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "Suggested Stretch Drills:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(text = "• Standing reach & side tilts (30s)\n" +
                               "• Back shoulders rolls & neck circles (30s)\n" +
                               "• Walk to fill a cool glass of water (60s)",
                         fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline", fontSize = 13.sp)
                }

                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Stretch", fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun StretchBreakTimerScreen(
    secondsLeft: Int,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF064E3B), Color(0xFF022C22)) // Restful green theme
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Stretch Break Active",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "Relax your shoulders, stand tall, and breathe",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // Circular progress wave clock
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                val progressFraction = secondsLeft.toFloat() / 120f
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF10B981),
                        startAngle = -90f,
                        sweepAngle = progressFraction * 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${secondsLeft / 60}:${String.format("%02d", secondsLeft % 60)}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(text = "Remaining", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }

            // Interactive dynamic activity tip
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = "Zen tips",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            secondsLeft > 90 -> "Tip: Stretch your arms high towards the sky to loosen spinal compression."
                            secondsLeft > 60 -> "Tip: Look away 20 feet in front of you. Focus eye lenses to rest your vision."
                            secondsLeft > 30 -> "Tip: Slow torso rotations. Breathe deep into your abdomen."
                            else -> "Nice work! Preparing to record completed session to your logs."
                        },
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }

            OutlinedButton(
                onClick = onSkip,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = AssistChipDefaults.assistChipBorder(enabled = true),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Skip & Go Back")
            }
        }
    }
}

// ----------------------------------------------------
// UTILS
// ----------------------------------------------------
fun formatTimestamp(timeMs: Long): String {
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(Date(timeMs))
}

fun formatSecondsToMinutes(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "${mins}m ${secs}s"
}
