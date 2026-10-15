package com.teethut.twiprobot.presentation.tracker

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.TrackerResolution
import com.teethut.twiprobot.domain.model.TrackingMode
import com.teethut.twiprobot.presentation.components.ConnectionBadge
import com.teethut.twiprobot.presentation.components.RobotStateBadge
import com.teethut.twiprobot.presentation.tracker.components.AnimatedCatFaceView
import com.teethut.twiprobot.presentation.tracker.components.CameraTrackerPreview
import com.teethut.twiprobot.presentation.tracker.components.TrackingHudOverlay
import kotlin.math.roundToInt

@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Keep screen on while tracker cockpit is open & support Landscape Mode for robot mount
    val isCatFace = uiState.isCatFaceMode
    val isLandscapeLocked = uiState.isLandscapeLocked
    DisposableEffect(isCatFace, isLandscapeLocked) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val previousOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }

        if (isCatFace) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else if (isLandscapeLocked) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = previousOrientation
        }
    }

    // Permission handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.toggleVoiceListening()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        viewModel.setCameraPermissionGranted(granted)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.setCameraPermissionGranted(true)
        }
    }

    if (!hasCameraPermission) {
        // Camera Permission Request Screen
        CameraPermissionPrompt(onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        })
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. CameraX Preview Stream (Always active in background so ML Kit & camera stream run with battery saving)
        CameraTrackerPreview(
            useFrontCamera = uiState.useFrontCamera,
            resolution = uiState.settings.resolution,
            analyzer = viewModel.analyzer,
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.isCatFaceMode) {
            // Cute Animated Cat Companion Face Overlay
            AnimatedCatFaceView(
                trackerState = uiState,
                robotPitch = telemetry.pitch,
                isBalancing = telemetry.isBalancing,
                useFrontCamera = uiState.useFrontCamera,
                onToggleCamera = { viewModel.toggleCameraFacing() },
                onExitCatMode = { viewModel.setCatFaceMode(false) },
                onToggleTracking = { viewModel.toggleTracking() },
                onEmergencyStop = { viewModel.emergencyStop("Emergency Stop from Cat Face") },
                onOpenSettings = { showSettingsSheet = true },
                onSpeakGreeting = { viewModel.triggerCuteVoiceGreeting() },
                onWakeUp = { viewModel.triggerWakeUpGreeting() },
                onSleepSnore = { viewModel.triggerSleepSnore() },
                onPetLeftEar = { viewModel.onPetLeftEar() },
                onPetRightEar = { viewModel.onPetRightEar() },
                onBoopNose = { viewModel.onBoopNose() },
                onStrokeChin = { viewModel.onStrokeChin() },
                onToggleVoiceListening = {
                    if (hasMicPermission) {
                        viewModel.toggleVoiceListening()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 2. Futuristic HUD Canvas Overlay
            TrackingHudOverlay(
                trackerState = uiState,
                robotPitch = telemetry.pitch,
                isBalancing = telemetry.isBalancing,
                modifier = Modifier.fillMaxSize()
            )

            if (isLandscape) {
                // Optimized Landscape HUD Top Bar
                LandscapeTopHudBar(
                    uiState = uiState,
                    connectionState = connectionState,
                    isBalancing = telemetry.isBalancing,
                    telemetryPitch = telemetry.pitch,
                    onToggleCamera = { viewModel.toggleCameraFacing() },
                    onToggleSettings = { showSettingsSheet = !showSettingsSheet },
                    onToggleCatFace = { viewModel.setCatFaceMode(true) },
                    onToggleOrientationLock = { viewModel.toggleLandscapeLock() },
                    onOpenOffsetDialog = { showOffsetDialog = true },
                    onAdjustOffset = { delta -> viewModel.adjustAngleOffset(delta) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )

                // Optimized Landscape Bottom Cockpit
                LandscapeBottomCockpit(
                    uiState = uiState,
                    onToggleTracking = { viewModel.toggleTracking() },
                    onEmergencyStop = { viewModel.emergencyStop("Manual Emergency Stop") },
                    onSelectMode = { viewModel.setTrackingMode(it) },
                    onOpenOffsetDialog = { showOffsetDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            } else {
                // 3. Portrait Top HUD Status Bar
                TopHudBar(
                    uiState = uiState,
                    connectionState = connectionState,
                    isBalancing = telemetry.isBalancing,
                    onToggleCamera = { viewModel.toggleCameraFacing() },
                    onToggleSettings = { showSettingsSheet = !showSettingsSheet },
                    onToggleCatFace = { viewModel.setCatFaceMode(true) },
                    onToggleOrientationLock = { viewModel.toggleLandscapeLock() },
                    onOpenOffsetDialog = { showOffsetDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                )

                // 4. Portrait Cockpit Controls Overlay at bottom
                BottomCockpitControls(
                    uiState = uiState,
                    telemetryPitch = telemetry.pitch,
                    isBalancing = telemetry.isBalancing,
                    connectionState = connectionState,
                    onToggleTracking = { viewModel.toggleTracking() },
                    onEmergencyStop = { viewModel.emergencyStop("Manual Emergency Stop") },
                    onSelectMode = { viewModel.setTrackingMode(it) },
                    onToggleCatFace = { viewModel.setCatFaceMode(true) },
                    onOpenOffsetDialog = { showOffsetDialog = true },
                    onAdjustOffset = { delta -> viewModel.adjustAngleOffset(delta) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }

        // 4. Wave Detected Celebration Badge in HUD
        AnimatedVisibility(
            visible = uiState.isWaveDetected && !uiState.isFistHoldActive && !uiState.isStopGestureActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 52.dp else 80.dp)
        ) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Color(0xFFF472B6))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "👋 Wave Detected! *Meow* 🐾",
                        color = Color(0xFFF472B6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4b. Stop Gesture Active Badge in HUD
        AnimatedVisibility(
            visible = uiState.isStopGestureActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 52.dp else 80.dp)
        ) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Color(0xFFF59E0B))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "✋ Stop Gesture: Backing Up ◀◀",
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4c. Fist Hold & Extended MediaPipe Gestures Badge in HUD
        AnimatedVisibility(
            visible = !uiState.isStopGestureActive && (
                uiState.isFistHoldActive ||
                    (!uiState.isWaveDetected && uiState.activeGesture != com.teethut.twiprobot.domain.model.RobotHandGesture.NONE)
            ),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isLandscape) 52.dp else 80.dp)
        ) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val badgeText = if (uiState.isFistHoldActive || uiState.activeGesture == com.teethut.twiprobot.domain.model.RobotHandGesture.CLOSED_FIST) {
                        "✊ Fist (Halt): Holding Position"
                    } else {
                        "${uiState.activeGesture.icon} ${uiState.activeGesture.label}"
                    }
                    Text(
                        text = badgeText,
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. Expandable Settings Sheet
        if (showSettingsSheet) {
            TrackerSettingsDialog(
                uiState = uiState,
                onDismiss = { showSettingsSheet = false },
                onUpdateSettings = { viewModel.updateSettings(it) },
                onToggleCatFace = { viewModel.setCatFaceMode(it) },
                onToggleCamera = { viewModel.toggleCameraFacing() },
                onTestMeow = { viewModel.triggerWaveMeowManual() },
                onTestVoice = { viewModel.triggerCuteVoiceGreeting() },
                onTestSynth = { sound -> viewModel.triggerSynthSound(sound) },
                onSetAngleOffset = { viewModel.setAngleOffset(it) },
                onAdjustAngleOffset = { viewModel.adjustAngleOffset(it) },
                onUpdateCatSliders = { viewModel.updateCatSliders(it) },
                onToggleGemma = { viewModel.toggleGemmaModel() }
            )
        }

        // 6. Balance Offset Phone Mount Modal Dialog
        if (showOffsetDialog) {
            BalanceOffsetDialog(
                uiState = uiState,
                telemetryPitch = telemetry.pitch,
                isBalancing = telemetry.isBalancing,
                onDismiss = { showOffsetDialog = false },
                onSetOffset = { viewModel.setAngleOffset(it) },
                onAdjustOffset = { viewModel.adjustAngleOffset(it) }
            )
        }
    }
}

@Composable
private fun LandscapeTopHudBar(
    uiState: com.teethut.twiprobot.domain.model.VisionTrackerState,
    connectionState: ConnectionState,
    isBalancing: Boolean,
    telemetryPitch: Float,
    onToggleCamera: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleCatFace: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onOpenOffsetDialog: () -> Unit,
    onAdjustOffset: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Connection, State, Lock Status, Telemetry
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionBadge(state = connectionState)
                RobotStateBadge(
                    stateText = if (isBalancing) "BALANCING" else "FALLEN",
                    isBalancing = isBalancing
                )
                // Lock status pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (uiState.lockStatus) {
                        TargetLockStatus.LOCKED -> Color(0x334ADE80)
                        TargetLockStatus.LOST -> Color(0x33F59E0B)
                        TargetLockStatus.SEARCHING -> Color(0x3338BDF8)
                    }
                ) {
                    Text(
                        text = when (uiState.lockStatus) {
                            TargetLockStatus.LOCKED -> "LOCKED"
                            TargetLockStatus.LOST -> "LOST"
                            TargetLockStatus.SEARCHING -> "SCAN"
                        },
                        color = when (uiState.lockStatus) {
                            TargetLockStatus.LOCKED -> Color(0xFF4ADE80)
                            TargetLockStatus.LOST -> Color(0xFFF59E0B)
                            TargetLockStatus.SEARCHING -> Color(0xFF38BDF8)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Telemetry V/S/P pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x221E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "V:${uiState.calculatedSpeed.roundToInt()}",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "S:${uiState.calculatedSteer.roundToInt()}",
                            color = Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "P:${String.format(java.util.Locale.US, "%.1f°", telemetryPitch)}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Center Group: Balance Offset Stepper (Phone Weight Adaptation)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x441E293B))
                    .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                        .clickable { onAdjustOffset(-0.1f) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-0.1", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier
                        .clickable { onOpenOffsetDialog() }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("⚖️ Offset:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text(
                        String.format(java.util.Locale.US, "%.2f°", uiState.currentAngleOffset),
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                        .clickable { onAdjustOffset(0.1f) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+0.1", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Right Group: Tools & Navigation
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.fps} FPS",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Landscape Lock Toggle
                IconButton(
                    onClick = onToggleOrientationLock,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Toggle Landscape Lock",
                        tint = if (uiState.isLandscapeLocked) Color(0xFF38BDF8) else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Cat Face Mode
                IconButton(
                    onClick = onToggleCatFace,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = "Cat Companion Mode",
                        tint = Color(0xFFFF69B4),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Camera Switch
                IconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = if (uiState.useFrontCamera) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Settings
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHudBar(
    uiState: com.teethut.twiprobot.domain.model.VisionTrackerState,
    connectionState: ConnectionState,
    isBalancing: Boolean,
    onToggleCamera: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleCatFace: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onOpenOffsetDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionBadge(state = connectionState)
                RobotStateBadge(
                    stateText = if (isBalancing) "BALANCING" else "FALLEN",
                    isBalancing = isBalancing
                )
                // Lock status pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (uiState.lockStatus) {
                        TargetLockStatus.LOCKED -> Color(0x334ADE80)
                        TargetLockStatus.LOST -> Color(0x33F59E0B)
                        TargetLockStatus.SEARCHING -> Color(0x3338BDF8)
                    }
                ) {
                    Text(
                        text = when (uiState.lockStatus) {
                            TargetLockStatus.LOCKED -> "LOCKED"
                            TargetLockStatus.LOST -> "LOST"
                            TargetLockStatus.SEARCHING -> "SCAN"
                        },
                        color = when (uiState.lockStatus) {
                            TargetLockStatus.LOCKED -> Color(0xFF4ADE80)
                            TargetLockStatus.LOST -> Color(0xFFF59E0B)
                            TargetLockStatus.SEARCHING -> Color(0xFF38BDF8)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Balance Offset Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x3338BDF8),
                    border = BorderStroke(1.dp, Color(0x6638BDF8)),
                    modifier = Modifier.clickable { onOpenOffsetDialog() }
                ) {
                    Text(
                        text = "⚖️ ${String.format(java.util.Locale.US, "%.1f°", uiState.currentAngleOffset)}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FPS Counter
                Text(
                    text = "${uiState.fps} FPS",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Landscape Lock Toggle
                IconButton(
                    onClick = onToggleOrientationLock,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Toggle Landscape Lock",
                        tint = if (uiState.isLandscapeLocked) Color(0xFF38BDF8) else Color(0xFF64748B)
                    )
                }

                // Animated Cat Face Toggle Button
                IconButton(
                    onClick = onToggleCatFace,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = "Cute Cat Face Mode",
                        tint = Color(0xFFFF69B4)
                    )
                }

                // Camera Toggle (Front / Back)
                IconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = if (uiState.useFrontCamera) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                    )
                }

                // Settings Toggle
                IconButton(
                    onClick = onToggleSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Tuning Settings",
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeBottomCockpit(
    uiState: com.teethut.twiprobot.domain.model.VisionTrackerState,
    onToggleTracking: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSelectMode: (TrackingMode) -> Unit,
    onOpenOffsetDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xD90F172A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Status & Mode chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = uiState.statusMessage,
                    color = if (uiState.isTrackingActive) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                TrackingMode.values().forEach { mode ->
                    val isSelected = uiState.trackingMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = if (mode == TrackingMode.PAN_ONLY) "Pan" else "Follow",
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E293B),
                            selectedLabelColor = Color(0xFF38BDF8),
                            containerColor = Color(0xFF0B1220),
                            labelColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }

            // Right: Balance quick button + Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable { onOpenOffsetDialog() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚖️ Balance (${String.format(java.util.Locale.US, "%.1f°", uiState.currentAngleOffset)})",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = onToggleTracking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isTrackingActive) Color(0xFF059669) else Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isTrackingActive) "STOP" else "START TRACKING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "E-STOP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomCockpitControls(
    uiState: com.teethut.twiprobot.domain.model.VisionTrackerState,
    telemetryPitch: Float,
    isBalancing: Boolean,
    connectionState: ConnectionState,
    onToggleTracking: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSelectMode: (TrackingMode) -> Unit,
    onToggleCatFace: () -> Unit,
    onOpenOffsetDialog: () -> Unit,
    onAdjustOffset: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xDD0F172A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (connectionState !is ConnectionState.Connected) {
                Surface(
                    color = Color(0x33EF4444),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⚠️ Robot is Offline. Connect to Wi-Fi 'TeethutBOT_AP' to drive.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live Status Header & Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.statusMessage,
                    color = if (uiState.isTrackingActive) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "V: ${uiState.calculatedSpeed.roundToInt()}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "S: ${uiState.calculatedSteer.roundToInt()}",
                        color = Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "P: ${String.format(java.util.Locale.US, "%.1f°", telemetryPitch)}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Balance Offset Phone Weight Adaptation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onOpenOffsetDialog() }
                ) {
                    Text("⚖️ Balance Offset:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.2f", uiState.currentAngleOffset)}°",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .clickable { onAdjustOffset(-0.1f) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("-0.1°", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .clickable { onAdjustOffset(0.1f) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("+0.1°", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0284C7))
                            .clickable { onOpenOffsetDialog() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Presets", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tracking Mode Selection Chips & Cat Mode Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackingMode.values().forEach { mode ->
                    val isSelected = uiState.trackingMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = mode.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E293B),
                            selectedLabelColor = Color(0xFF38BDF8),
                            containerColor = Color(0xFF0B1220),
                            labelColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Cat Companion Shortcut Chip
                FilterChip(
                    selected = false,
                    onClick = onToggleCatFace,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = null,
                                tint = Color(0xFFFF69B4),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cat Face",
                                fontSize = 11.sp,
                                color = Color(0xFFFFB6C1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0x33FF69B4)
                    )
                )
            }

            // Action Buttons: Engage / Disengage + Emergency STOP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tracking Toggle Button
                Button(
                    onClick = onToggleTracking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isTrackingActive) Color(0xFF059669) else Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isTrackingActive) "STOP TRACKING" else "START TRACKING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Emergency STOP Button
                Button(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "E-STOP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceOffsetDialog(
    uiState: com.teethut.twiprobot.domain.model.VisionTrackerState,
    telemetryPitch: Float,
    isBalancing: Boolean,
    onDismiss: () -> Unit,
    onSetOffset: (Float) -> Unit,
    onAdjustOffset: (Float) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF70F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚖️ Robot Balance & Phone Mount Offset",
                        color = Color(0xFF38BDF8),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Text(
                    text = "Mounting a phone shifts the robot's center of gravity forward or backward. Adjust this offset angle until the robot stands balanced without drifting.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                // Live Offset & Live Pitch Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ACTIVE OFFSET", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.2f", uiState.currentAngleOffset)}°",
                                color = Color(0xFF38BDF8),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ROBOT PITCH", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format(java.util.Locale.US, "%+.1f", telemetryPitch)}°",
                                color = if (isBalancing) Color(0xFF4ADE80) else Color(0xFFF87171),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Steppers row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "-0.5°" to -0.5f,
                        "-0.1°" to -0.1f,
                        "+0.1°" to 0.1f,
                        "+0.5°" to 0.5f
                    ).forEach { (label, delta) ->
                        Button(
                            onClick = { onAdjustOffset(delta) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(label, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                // Continuous Fine-Tuning Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fine Tuning Slider", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${String.format(java.util.Locale.US, "%.2f", uiState.currentAngleOffset)}°",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = uiState.currentAngleOffset.coerceIn(0.0f, 15.0f),
                    onValueChange = { onSetOffset(it) },
                    valueRange = 0.0f..15.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )

                // Phone Weight Presets
                Text("Phone Weight Presets", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Bare (5.5°)" to 5.5f,
                        "Light (6.2°)" to 6.2f,
                        "Standard (6.8°)" to 6.8f,
                        "Heavy (7.5°)" to 7.5f
                    ).forEach { (label, preset) ->
                        val isSelected = kotlin.math.abs(uiState.currentAngleOffset - preset) < 0.05f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSetOffset(preset) },
                            label = { Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                if (uiState.offsetUpdateMessage != null) {
                    Surface(
                        color = Color(0x330284C7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.offsetUpdateMessage,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TrackerSettingsDialog(
    uiState: com.teethut.twiprobot.domain.model.VisionTrackerState,
    onDismiss: () -> Unit,
    onUpdateSettings: (com.teethut.twiprobot.domain.model.TrackerSettings) -> Unit,
    onToggleCatFace: (Boolean) -> Unit,
    onToggleCamera: () -> Unit,
    onTestMeow: () -> Unit,
    onTestVoice: () -> Unit = {},
    onTestSynth: (com.teethut.twiprobot.data.sound.RobotSynthSound) -> Unit = {},
    onSetAngleOffset: (Float) -> Unit = {},
    onAdjustAngleOffset: (Float) -> Unit = {},
    onUpdateCatSliders: (com.teethut.twiprobot.domain.model.CatPersonalitySliders) -> Unit = {},
    onToggleGemma: () -> Unit = {}
) {
    val settings = uiState.settings
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Tracking Parameters",
                    color = Color(0xFF38BDF8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            // Phone Mount Weight & Balance Angle Offset Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚖️ Phone Weight Balance Offset",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.2f", uiState.currentAngleOffset)}°",
                            color = Color(0xFF38BDF8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "Offset compensates for phone mount weight shifting center of gravity.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )

                    // Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "Bare" to 5.5f,
                            "Light" to 6.2f,
                            "Standard" to 6.8f,
                            "Heavy" to 7.5f
                        ).forEach { (label, preset) ->
                            val isSelected = kotlin.math.abs(uiState.currentAngleOffset - preset) < 0.05f
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetAngleOffset(preset) },
                                label = { Text("$label ${preset}°", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    // Steppers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "-0.5°" to -0.5f,
                            "-0.1°" to -0.1f,
                            "+0.1°" to 0.1f,
                            "+0.5°" to 0.5f
                        ).forEach { (label, delta) ->
                            Button(
                                onClick = { onAdjustAngleOffset(delta) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(label, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Slider(
                        value = uiState.currentAngleOffset.coerceIn(0.0f, 15.0f),
                        onValueChange = { onSetAngleOffset(it) },
                        valueRange = 0.0f..15.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )

                    if (uiState.offsetUpdateMessage != null) {
                        Text(
                            text = uiState.offsetUpdateMessage,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Target Follow Distance
            Text(
                text = "Target Distance: ${(settings.targetDistanceFraction * 100).roundToInt()}% (${uiState.distanceCategory.label})",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Close (1.0m)" to 0.25f,
                    "Medium (1.5m)" to 0.18f,
                    "Far (2.2m)" to 0.12f
                ).forEach { (label, fraction) ->
                    val isSelected = (settings.targetDistanceFraction - fraction).let { kotlin.math.abs(it) < 0.02f }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateSettings(settings.copy(targetDistanceFraction = fraction)) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0369A1),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Max Speed Limit Slider (Min: 500, Max: 6000 steps/s, Step: 100)
            Text(
                text = "Max Follow Speed: ${settings.maxSpeed.roundToInt()} steps/s",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp
            )
            Slider(
                value = settings.maxSpeed.coerceIn(500f, 6000f),
                onValueChange = { onUpdateSettings(settings.copy(maxSpeed = it)) },
                valueRange = 500f..6000f,
                steps = 54, // (6000 - 500) / 100 - 1 = 54 steps of 100 steps/s
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF38BDF8)
                )
            )

            // Max Steer Limit Slider (Min: 400, Max: 3200 steps/s, Step: 100)
            Text(
                text = "Max Steer Rate: ${settings.maxSteer.roundToInt()} steps/s",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp
            )
            Slider(
                value = settings.maxSteer.coerceIn(400f, 3200f),
                onValueChange = { onUpdateSettings(settings.copy(maxSteer = it)) },
                valueRange = 400f..3200f,
                steps = 27, // (3200 - 400) / 100 - 1 = 27 steps of 100 steps/s
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF59E0B),
                    activeTrackColor = Color(0xFFF59E0B)
                )
            )

            // Camera Facing Selection (Front vs Rear)
            Text(
                text = "Camera Facing: ${if (uiState.useFrontCamera) "Front (Selfie Screen)" else "Rear (Main Back)"}",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.useFrontCamera,
                    onClick = { if (!uiState.useFrontCamera) onToggleCamera() },
                    label = { Text("Front Camera (Screen)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = !uiState.useFrontCamera,
                    onClick = { if (uiState.useFrontCamera) onToggleCamera() },
                    label = { Text("Rear Camera (Back)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    )
                )
            }

            // Battery Saver: Target Tracking FPS Selection
            Text(
                text = "Vision Processing FPS: ${settings.targetFps} FPS",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "10 FPS (Ultra Eco)" to 10,
                    "15 FPS (Eco)" to 15,
                    "20 FPS (Smooth)" to 20,
                    "30 FPS (Max)" to 30
                ).forEach { (label, fpsVal) ->
                    val isSelected = settings.targetFps == fpsVal
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateSettings(settings.copy(targetFps = fpsVal)) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0D9488),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Battery Saver: Camera Resolution Selection
            Text(
                text = "Camera Resolution: ${settings.resolution.label}",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrackerResolution.values().forEach { res ->
                    val isSelected = settings.resolution == res
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateSettings(settings.copy(resolution = res)) },
                        label = { Text(res.label.substringBefore(" ("), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Invert Steer Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Invert Steering", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text(
                        text = "Enable if phone is mounted inverted",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.invertSteer,
                    onCheckedChange = { onUpdateSettings(settings.copy(invertSteer = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                )
            }

            // Invert Drive Direction Switch (Forward / Reverse)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Invert Drive Direction", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text(
                        text = "Invert forward & reverse (e.g. reverse motor polarity or rear mount)",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.invertThrottle,
                    onCheckedChange = { onUpdateSettings(settings.copy(invertThrottle = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                )
            }

            // Allow Reverse Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Allow Reverse Motion", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text(
                        text = "Back away if user approaches (Caution: blind spots)",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.allowReverse,
                    onCheckedChange = { onUpdateSettings(settings.copy(allowReverse = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEF4444))
                )
            }

            // Animated Cat Face Mode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Cute Cat Face Mode 🐾", color = Color(0xFFFFB6C1), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Shows animated expressive cat face while tracking in background",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = uiState.isCatFaceMode,
                    onCheckedChange = {
                        onToggleCatFace(it)
                        if (it) onDismiss()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF69B4))
                )
            }

            // Feline AI Persona & Brain (Gemma 4) Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B).copy(alpha = 0.55f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧠 Gemma 4 AI Cat Brain",
                                color = Color(0xFFA5B4FC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            onClick = onToggleGemma,
                            shape = RoundedCornerShape(8.dp),
                            color = if (uiState.isGemmaLoaded) Color(0xFF064E3B) else Color(0xFF451A03),
                            border = BorderStroke(1.dp, if (uiState.isGemmaLoaded) Color(0xFF10B981) else Color(0xFFF59E0B))
                        ) {
                            Text(
                                text = if (uiState.isGemmaLoaded) "GPU Online" else "Tap to Load",
                                color = if (uiState.isGemmaLoaded) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val catSliders = uiState.catSliders
                    Text(
                        text = "Playfulness: ${(catSliders.playfulness * 100).toInt()}%",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = catSliders.playfulness,
                        onValueChange = { onUpdateCatSliders(catSliders.copy(playfulness = it)) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFF472B6), activeTrackColor = Color(0xFFF472B6))
                    )

                    Text(
                        text = "Sassiness: ${(catSliders.sassiness * 100).toInt()}%",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = catSliders.sassiness,
                        onValueChange = { onUpdateCatSliders(catSliders.copy(sassiness = it)) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFF59E0B))
                    )

                    Text(
                        text = "Voice Pitch: ${String.format(java.util.Locale.US, "%.2f", catSliders.voicePitch)}x",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    )
                    Slider(
                        value = catSliders.voicePitch,
                        onValueChange = { onUpdateCatSliders(catSliders.copy(voicePitch = it)) },
                        valueRange = 0.8f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                    )

                    // Canned Presets Toggle (Gemma-Only Mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Canned Preset Phrases ⚡",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (catSliders.allowCannedPresets) "Fallback canned lines enabled" else "Gemma-Only Mode (no canned speech)",
                                color = if (catSliders.allowCannedPresets) Color(0xFFF59E0B) else Color(0xFF34D399),
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = catSliders.allowCannedPresets,
                            onCheckedChange = { onUpdateCatSliders(catSliders.copy(allowCannedPresets = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFF59E0B),
                                uncheckedThumbColor = Color(0xFF34D399)
                            )
                        )
                    }

                    // Autonomous Scene Commentary (Cat Eyes) Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Autonomous Scene Commentary (Cat Eyes) 👁️",
                                color = Color(0xFFC084FC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (settings.sceneCommentaryEnabled) "ML Kit + Gemma 4 comments on seen objects" else "Disabled (saves battery/compute)",
                                color = if (settings.sceneCommentaryEnabled) Color(0xFFC084FC) else Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = settings.sceneCommentaryEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(sceneCommentaryEnabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFC084FC)
                            )
                        )
                    }
                }
            }

            // Wave Gesture "Meow" Sound Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wave Gesture Meow 👋🐱",
                        color = Color(0xFFF472B6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Detects hand waves and plays a cute meow sound effect",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.waveToMeowEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(waveToMeowEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF472B6))
                )
            }

            // Stop Gesture Reverse Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Stop Gesture Reverse ✋◀◀",
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hold open upright palm to back the robot away safely",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.stopGestureEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(stopGestureEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF59E0B))
                )
            }

            // Stop Gesture Reverse Speed Slider
            if (settings.stopGestureEnabled) {
                Text(
                    text = "Reverse Speed: ${settings.stopGestureBackwardSpeed.roundToInt()} steps/s",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp
                )
                Slider(
                    value = settings.stopGestureBackwardSpeed.coerceIn(400f, 1500f),
                    onValueChange = { onUpdateSettings(settings.copy(stopGestureBackwardSpeed = it)) },
                    valueRange = 400f..1500f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF59E0B),
                        activeTrackColor = Color(0xFFF59E0B)
                    )
                )

                // Invert Backward Direction Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Invert Backward Direction 🔄",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Flip polarity if robot moves forward instead of backward",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = settings.invertBackwardDirection,
                        onCheckedChange = { onUpdateSettings(settings.copy(invertBackwardDirection = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFF59E0B))
                    )
                }
            }

            // Cute Anime / BMO Speaking Voice Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cute Speaking Voice (TTS) 🗣️✨",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Speaks cute phrases on gestures, lock-on, and falls with lip-sync",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.cuteVoiceEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(cuteVoiceEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF38BDF8))
                )
            }

            // Voice Pitch Slider
            if (settings.cuteVoiceEnabled) {
                Text(
                    text = "Cute Voice Pitch: ${String.format("%.2f", settings.voicePitch)}x",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp
                )
                Slider(
                    value = settings.voicePitch.coerceIn(1.0f, 2.0f),
                    onValueChange = { onUpdateSettings(settings.copy(voicePitch = it)) },
                    valueRange = 1.0f..2.0f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF472B6),
                        activeTrackColor = Color(0xFFF472B6)
                    )
                )
            }

            // Procedural Robot Chirps & Purrs Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Expressive Synth Chirps & Purrs 🎶🤖",
                        color = Color(0xFFA78BFA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Procedural Cozmo/Vector/R2-D2 style trills, purrs, and chimes",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.synthSoundsEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(synthSoundsEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFA78BFA))
                )
            }

            // Wobble Tilt Reaction Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wobble Reaction ⚖️🐱",
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Reacts with cute voice/sound only during severe tilt or stumble",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = settings.wobbleDetectionEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(wobbleDetectionEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFBBF24))
                )
            }

            // Wobble Tilt Threshold Slider
            if (settings.wobbleDetectionEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wobble Tilt Threshold: ${settings.wobblePitchThreshold.roundToInt()}°",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (settings.wobblePitchThreshold >= 20f) "Low Sensitivity"
                        else if (settings.wobblePitchThreshold >= 15f) "Balanced (Recommended)"
                        else "High Sensitivity",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Slider(
                    value = settings.wobblePitchThreshold.coerceIn(10.0f, 30.0f),
                    onValueChange = { onUpdateSettings(settings.copy(wobblePitchThreshold = it)) },
                    valueRange = 10.0f..30.0f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFBBF24),
                        activeTrackColor = Color(0xFFFBBF24),
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )
            }

            // Sound & Voice Test Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTestVoice,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🗣️ Cute Voice",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = { onTestSynth(com.teethut.twiprobot.data.sound.RobotSynthSound.HAPPY_CHIRP) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33A78BFA)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🎶 Chirp",
                        color = Color(0xFFA78BFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = { onTestSynth(com.teethut.twiprobot.data.sound.RobotSynthSound.LOVE_PURR) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EC4899)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "💖 Purr",
                        color = Color(0xFFEC4899),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Test New Manga FX Procedural Synth Vocalizations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onTestSynth(com.teethut.twiprobot.data.sound.RobotSynthSound.SLEEP_SNORE_PURR) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3393C5FD)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "💤 Snore",
                        color = Color(0xFF93C5FD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = { onTestSynth(com.teethut.twiprobot.data.sound.RobotSynthSound.WOBBLE_WARBLE) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "💦 Wobble",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = { onTestSynth(com.teethut.twiprobot.data.sound.RobotSynthSound.POUT_WHIMPER) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F43F5E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "😤 Whimper",
                        color = Color(0xFFF43F5E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Test Meow Sound Button
            Button(
                onClick = onTestMeow,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F472B6)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎵 Test Meow Sound Effect",
                    color = Color(0xFFF472B6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
}

@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF142034)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "AI Vision Person Tracking",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Mount your phone onto the TWIP robot chassis. The front camera will detect and track you to steer and follow you automatically.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Camera Permission", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

