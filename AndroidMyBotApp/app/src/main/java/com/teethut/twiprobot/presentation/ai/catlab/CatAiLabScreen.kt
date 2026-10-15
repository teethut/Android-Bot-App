package com.teethut.twiprobot.presentation.ai.catlab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.domain.model.CatPersonalitySliders
import com.teethut.twiprobot.domain.model.FelineDrives
import com.teethut.twiprobot.presentation.ai.catlab.components.TwipMotionVisualizer
import com.teethut.twiprobot.presentation.tracker.components.AnimatedCatFaceView
import com.teethut.twiprobot.theme.BackgroundDark
import com.teethut.twiprobot.theme.BorderActive
import com.teethut.twiprobot.theme.BorderSubtle
import com.teethut.twiprobot.theme.CardBackground
import com.teethut.twiprobot.theme.CyanPrimary
import com.teethut.twiprobot.theme.StatusAlert
import com.teethut.twiprobot.theme.StatusSuccess
import com.teethut.twiprobot.theme.SurfaceDark
import com.teethut.twiprobot.theme.SurfaceElevated
import com.teethut.twiprobot.theme.TextMuted
import com.teethut.twiprobot.theme.TextPrimary
import com.teethut.twiprobot.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatAiLabScreen(
    viewModel: CatLabViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var customSpeechInput by remember { mutableStateOf("") }
    var showSlidersSheet by remember { mutableStateOf(false) }
    var showRawJson by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceListening()
        }
    }

    // Intercept hardware and gesture back button to pop Cat AI Lab smoothly
    BackHandler(enabled = true) {
        onBack()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Header Bar (Clean, no status bar overlap, compact elements)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Cat AI Lab",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "Safe Feline Simulation",
                            color = CyanPrimary.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Backend & Model Badge (Clickable to toggle Gemma 4)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !uiState.isLoadingGemma) {
                                viewModel.toggleGemmaModel()
                            }
                            .background(
                                if (uiState.isModelLoaded) Color(0xFF064E3B)
                                else if (uiState.isLoadingGemma) Color(0xFF082F49)
                                else Color(0xFF451A03)
                            )
                            .border(
                                1.dp,
                                if (uiState.isModelLoaded) StatusSuccess
                                else if (uiState.isLoadingGemma) CyanPrimary
                                else Color(0xFFF59E0B),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isLoadingGemma) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = CyanPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (uiState.isLoadingGemma) "Loading Gemma..."
                                else if (uiState.isModelLoaded) "🧠 Gemma 4 (GPU)"
                                else "⚡ Mock Sim (Tap to Load Gemma)",
                                color = if (uiState.isModelLoaded) StatusSuccess
                                else if (uiState.isLoadingGemma) CyanPrimary
                                else Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = { showSlidersSheet = true },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Personality Sliders",
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Error banner if Gemma load fails
            uiState.gemmaLoadError?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF450A0A))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚠️ $err",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp
                    )
                }
            }

            // 2. Embedded Live Animated Cat Face View (Sandbox Display - compact for landscape)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color.Black)
            ) {
                AnimatedCatFaceView(
                    trackerState = uiState.trackerState,
                    robotPitch = uiState.simulatedPitch,
                    isBalancing = uiState.simulatedBalancing,
                    useFrontCamera = true,
                    onToggleCamera = {},
                    onExitCatMode = onBack,
                    onToggleTracking = {},
                    onEmergencyStop = {},
                    onOpenSettings = { showSlidersSheet = true },
                    onPetLeftEar = { viewModel.onPetLeftEar() },
                    onPetRightEar = { viewModel.onPetRightEar() },
                    onBoopNose = { viewModel.onBoopNose() },
                    onStrokeChin = { viewModel.onStrokeChin() },
                    showTrackingControls = false
                )

                // Thinking / Generating Indicator Overlay
                if (uiState.isThinking) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC0F172A))
                            .border(1.dp, CyanPrimary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = CyanPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemma 4 Brain Running...",
                                color = CyanPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Interactive Petting Floating Hint Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xBB0F172A))
                        .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🐾 Touch ears, boop nose, or scratch chin on face!",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3. Lower Control Deck & Diagnostics (Scrollable)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section: Cyber-Cat Needs & Drives HUD
                item {
                    DrivesHudCard(drives = uiState.drives)
                }

                // Section: Live Directive HUD
                item {
                    DirectiveStatusCard(
                        directive = uiState.currentDirective,
                        isSpeaking = uiState.isSpeaking,
                        showRawJson = showRawJson,
                        onToggleRawJson = { showRawJson = !showRawJson }
                    )
                }

                // Section: 2D Chassis Motion Simulator
                item {
                    TwipMotionVisualizer(
                        wheelAction = uiState.currentDirective.wheelAction,
                        simulatedPitch = uiState.simulatedPitch
                    )
                }

                // Section: On-Device Neural Voice Engine (Piper VITS / Kokoro Studio)
                item {
                    NeuralVoiceCard(
                        isReady = uiState.isNeuralVoiceReady,
                        isEnabled = uiState.useNeuralVoice,
                        status = uiState.neuralVoiceStatus,
                        currentProfile = uiState.selectedNeuralProfile,
                        voicePitch = uiState.sliders.voicePitch,
                        onPitchChange = { viewModel.setVoicePitch(it) },
                        onSelectProfile = { viewModel.selectNeuralProfile(it) },
                        onToggle = { viewModel.toggleNeuralVoice(it) },
                        onTestVoice = { viewModel.testNeuralVoiceGreeting() }
                    )
                }

                // Section: Human Event Simulator Deck
                item {
                    Text(
                        text = "HUMAN EVENT SIMULATOR",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SimulateActionButton(
                            icon = "🖐️",
                            title = "Wave Hello",
                            subtitle = "1.5m Distance",
                            onClick = { viewModel.triggerWaveEvent() },
                            modifier = Modifier.weight(1f)
                        )
                        SimulateActionButton(
                            icon = "✌️",
                            title = "Peace / Love",
                            subtitle = "Affection gesture",
                            onClick = { viewModel.triggerPeaceLoveEvent() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SimulateActionButton(
                            icon = "🏃",
                            title = "Lunge (<0.4m)",
                            subtitle = "Too close trigger",
                            onClick = { viewModel.triggerApproachCloseEvent() },
                            modifier = Modifier.weight(1f)
                        )
                        SimulateActionButton(
                            icon = "🚶",
                            title = "Walk Away",
                            subtitle = "Lost in frame",
                            onClick = { viewModel.triggerHumanDisappearedEvent() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SimulateActionButton(
                            icon = "⚡",
                            title = "Wobble Tilt",
                            subtitle = "Simulate bump",
                            onClick = { viewModel.triggerWobbleTiltEvent() },
                            modifier = Modifier.weight(1f)
                        )
                        SimulateActionButton(
                            icon = "😴",
                            title = "Simulate Idle",
                            subtitle = "Boredom / Nap",
                            onClick = { viewModel.triggerBoredomEvent() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Section: Talk to Cat Input
                item {
                    Text(
                        text = "TALK TO TWIPPY",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hands-Free Microphone STT Button
                        IconButton(
                            onClick = {
                                val hasMicPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMicPermission) {
                                    viewModel.toggleVoiceListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (uiState.isListeningVoice) Color(0xFFEF4444) else SurfaceDark
                                )
                                .border(
                                    1.dp,
                                    if (uiState.isListeningVoice) Color(0xFFEF4444) else BorderSubtle,
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (uiState.isListeningVoice) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = "Voice Input",
                                tint = if (uiState.isListeningVoice) Color.White else CyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = customSpeechInput,
                            onValueChange = { customSpeechInput = it },
                            placeholder = {
                                Text(
                                    text = if (uiState.isListeningVoice) "Listening to your voice..." else "Say something to Twippy...",
                                    color = if (uiState.isListeningVoice) Color(0xFFF87171) else TextMuted,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceDark,
                                unfocusedContainerColor = SurfaceDark,
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (customSpeechInput.isNotBlank()) {
                                    viewModel.submitCustomHumanSpeech(customSpeechInput)
                                    customSpeechInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (uiState.voiceSttError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.voiceSttError ?: "",
                            color = Color(0xFFF87171),
                            fontSize = 11.sp
                        )
                    }
                }

                // Section: Event Log History
                if (uiState.eventLog.isNotEmpty()) {
                    item {
                        Text(
                            text = "RECENT INTERACTIONS",
                            color = CyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                        )
                    }

                    items(uiState.eventLog.take(6)) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (entry.isGenerated) "🧠 GEMMA" else "⚡ PRESET",
                                        color = if (entry.isGenerated) StatusSuccess else Color(0xFFF59E0B),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = entry.speech,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${entry.mood.label} • Action: ${entry.wheelAction.label}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "${entry.latencyMs} ms",
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Personality Sliders Bottom Sheet
    if (showSlidersSheet) {
        PersonalitySlidersSheet(
            currentSliders = uiState.sliders,
            onSlidersChanged = { viewModel.updateSliders(it) },
            onDismiss = { showSlidersSheet = false }
        )
    }
}

@Composable
private fun PetHotspotChip(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, Color(0xFFFB7185).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color(0xFFFB7185),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SimulateActionButton(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun DirectiveStatusCard(
    directive: com.teethut.twiprobot.domain.model.CatPersonalityDirective,
    isSpeaking: Boolean,
    showRawJson: Boolean,
    onToggleRawJson: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanPrimary.copy(alpha = 0.3f))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(directive.mood.badgeColor))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = directive.mood.label,
                        color = Color(directive.mood.badgeColor),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source Pill: Gemma 4 (GPU) vs Preset Fallback
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (directive.isGenerated) Color(0xFF064E3B) else Color(0xFF451A03)
                            )
                            .border(
                                1.dp,
                                if (directive.isGenerated) StatusSuccess else Color(0xFFF59E0B),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (directive.isGenerated) "🧠 Gemma 4 (GPU)" else "⚡ Preset / Mock",
                            color = if (directive.isGenerated) StatusSuccess else Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${directive.latencyMs} ms",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Speech Text
            Text(
                text = "\"${directive.speech}\"",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wheel Action
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Wheel: ${directive.wheelAction.label}",
                        color = CyanPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Vocalization
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Sound: ${directive.vocalization}",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Toggle Raw JSON
                IconButton(
                    onClick = onToggleRawJson,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Raw JSON",
                        tint = if (showRawJson) CyanPrimary else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Raw JSON View
            AnimatedVisibility(visible = showRawJson && directive.rawJson.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = directive.rawJson,
                        color = Color(0xFFA5B4FC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceDark)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalitySlidersSheet(
    currentSliders: CatPersonalitySliders,
    onSlidersChanged: (CatPersonalitySliders) -> Unit,
    onDismiss: () -> Unit
) {
    var playfulness by remember { mutableStateOf(currentSliders.playfulness) }
    var sassiness by remember { mutableStateOf(currentSliders.sassiness) }
    var voicePitch by remember { mutableStateOf(currentSliders.voicePitch) }
    var voiceRate by remember { mutableStateOf(currentSliders.voiceRate) }
    var allowCannedPresets by remember { mutableStateOf(currentSliders.allowCannedPresets) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Feline Personality Sliders",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tune Twippy's temperament and voice synthesis",
                color = TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Playfulness Slider
            Text(
                text = "Playfulness & Energy: ${(playfulness * 100).toInt()}%",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = playfulness,
                onValueChange = {
                    playfulness = it
                    onSlidersChanged(currentSliders.copy(playfulness = it))
                },
                colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
            )

            // Sassiness Slider
            Text(
                text = "Sassiness & Aloofness: ${(sassiness * 100).toInt()}%",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = sassiness,
                onValueChange = {
                    sassiness = it
                    onSlidersChanged(currentSliders.copy(sassiness = it))
                },
                colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFF59E0B))
            )

            // Voice Pitch Slider
            Text(
                text = "Cute Voice Pitch: ${String.format("%.2f", voicePitch)}x",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = voicePitch,
                onValueChange = {
                    voicePitch = it
                    onSlidersChanged(currentSliders.copy(voicePitch = it))
                },
                valueRange = 0.9f..2.0f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFFB7185), activeTrackColor = Color(0xFFFB7185))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Canned Presets Switch (Gemma-Only Mode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow Canned Presets ⚡",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (allowCannedPresets) "Preset canned phrases enabled" else "Gemma-Only Mode (no canned speech)",
                        color = if (allowCannedPresets) Color(0xFFF59E0B) else StatusSuccess,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = allowCannedPresets,
                    onCheckedChange = {
                        allowCannedPresets = it
                        onSlidersChanged(currentSliders.copy(allowCannedPresets = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFF59E0B),
                        uncheckedThumbColor = StatusSuccess
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DrivesHudCard(
    drives: FelineDrives,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFB7185))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEEDS & DRIVES",
                        color = Color(0xFFFB7185),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Persona State",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Dynamic Need Bars: Affection, Energy, Curiosity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DriveMeterItem(
                    label = "Affection",
                    value = drives.affection,
                    percentText = "${(drives.affection * 100).toInt()}%",
                    icon = "💖",
                    barColor = Color(0xFFFB7185),
                    modifier = Modifier.weight(1f)
                )
                DriveMeterItem(
                    label = "Energy",
                    value = drives.energy,
                    percentText = "${(drives.energy * 100).toInt()}%",
                    icon = "⚡",
                    barColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                DriveMeterItem(
                    label = "Curiosity",
                    value = drives.curiosity,
                    percentText = "${(drives.curiosity * 100).toInt()}%",
                    icon = "🔍",
                    barColor = CyanPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DriveMeterItem(
    label: String,
    value: Float,
    percentText: String,
    icon: String,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        label = label
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = percentText,
                color = barColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun NeuralVoiceCard(
    isReady: Boolean,
    isEnabled: Boolean,
    status: String,
    currentProfile: com.teethut.twiprobot.data.sound.NeuralVoiceProfile,
    voicePitch: Float,
    onPitchChange: (Float) -> Unit,
    onSelectProfile: (com.teethut.twiprobot.data.sound.NeuralVoiceProfile) -> Unit,
    onToggle: (Boolean) -> Unit,
    onTestVoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isReady && isEnabled) Color(0xFF10B981).copy(alpha = 0.5f) else BorderSubtle
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Neural Voice",
                        tint = if (isReady && isEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ON-DEVICE NEURAL TTS",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isReady && isEnabled) Color(0xFF064E3B) else Color(0xFF1E293B))
                        .border(
                            1.dp,
                            if (isReady && isEnabled) Color(0xFF10B981).copy(alpha = 0.6f) else BorderSubtle,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isReady && isEnabled) "ACTIVE" else if (isReady) "STANDBY" else "OFFLINE",
                        color = if (isReady && isEnabled) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Voice Profile Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.teethut.twiprobot.data.sound.NeuralVoiceProfile.values().forEach { profile ->
                    val isSelected = currentProfile == profile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) CyanPrimary.copy(alpha = 0.2f) else Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (isSelected) CyanPrimary else BorderSubtle,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectProfile(profile) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = profile.label,
                                color = if (isSelected) CyanPrimary else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = "${profile.sampleRate / 1000}kHz",
                                color = if (isSelected) Color(0xFFA5F3FC) else TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = currentProfile.description,
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            // Quick Pitch Slider for Cuteness tuning
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cute Voice Pitch: ${String.format(java.util.Locale.US, "%.2f", voicePitch)}x",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (voicePitch >= 1.40f) "Kawaii / Anime" else if (voicePitch >= 1.25f) "Sweet & Youthful" else "Original Pitch",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Slider(
                    value = voicePitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.9f..1.8f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPrimary,
                        activeTrackColor = CyanPrimary,
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = isEnabled && isReady,
                        onCheckedChange = { onToggle(it) },
                        enabled = isReady,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = if (isEnabled && isReady) "Neural Mode" else "System TTS",
                        color = if (isEnabled && isReady) Color(0xFF6EE7B7) else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onTestVoice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReady && isEnabled) Color(0xFF047857) else CyanPrimary.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🔊 Test Voice",
                        color = if (isReady && isEnabled) Color.White else CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

