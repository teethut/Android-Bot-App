package com.teethut.twiprobot.presentation.tracker.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.domain.model.RobotHandGesture
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.VisionTrackerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AnimatedCatFaceView(
    trackerState: VisionTrackerState,
    robotPitch: Float,
    isBalancing: Boolean,
    useFrontCamera: Boolean,
    onToggleCamera: () -> Unit,
    onExitCatMode: () -> Unit,
    onToggleTracking: () -> Unit,
    onEmergencyStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onSpeakGreeting: () -> Unit = {},
    onWakeUp: () -> Unit = {},
    onSleepSnore: () -> Unit = {},
    onPetLeftEar: () -> Unit = {},
    onPetRightEar: () -> Unit = {},
    onBoopNose: () -> Unit = {},
    onStrokeChin: () -> Unit = {},
    onToggleVoiceListening: () -> Unit = {},
    showTrackingControls: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 1. Inactivity & Sleep Watchdog (15s idle triggers nap)
    var lastActiveTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentClockMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val hasActiveInteraction = trackerState.currentTarget != null ||
        trackerState.activeGesture != RobotHandGesture.NONE ||
        trackerState.isTrackingActive ||
        trackerState.isRobotSpeaking ||
        kotlin.math.abs(robotPitch) > 14.0f

    LaunchedEffect(hasActiveInteraction) {
        lastActiveTimeMs = System.currentTimeMillis()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500L)
            val now = System.currentTimeMillis()
            currentClockMs = now
            if (hasActiveInteraction) {
                lastActiveTimeMs = now
            }
        }
    }

    val idleDurationMs = if (hasActiveInteraction) 0L else (currentClockMs - lastActiveTimeMs).coerceAtLeast(0L)

    // 2. Resolve Target Emotional Parameters via Procedural Engine
    val targetEmotion = remember(
        isBalancing,
        trackerState.activeGesture,
        trackerState.isWaveDetected,
        trackerState.isStopGestureActive,
        trackerState.lockStatus,
        trackerState.isFistHoldActive,
        robotPitch,
        trackerState.activeFelineDirective,
        idleDurationMs >= 15000L
    ) {
        CatEmotionEngine.resolveEmotion(
            isBalancing = isBalancing,
            gesture = trackerState.activeGesture,
            isWaveTriggered = trackerState.isWaveDetected,
            isStopGestureActive = trackerState.isStopGestureActive,
            lockStatus = trackerState.lockStatus,
            isFistHoldActive = trackerState.isFistHoldActive,
            pitchAngle = robotPitch,
            idleDurationMs = idleDurationMs,
            felineDirective = trackerState.activeFelineDirective
        )
    }

    // Wake-up and periodic sleep snore trigger when transitioning in/out of nap
    var wasNapping by remember { mutableStateOf(false) }
    LaunchedEffect(targetEmotion.isNapping) {
        if (targetEmotion.isNapping) {
            wasNapping = true
            while (isActive && targetEmotion.isNapping) {
                onSleepSnore()
                delay(8000L)
            }
        } else if (wasNapping) {
            wasNapping = false
            onWakeUp()
        }
    }

    // 3. Natural Blinking Animation Loop
    val blinkAnim = remember { Animatable(1f) }
    LaunchedEffect(targetEmotion.eyeShape) {
        if (targetEmotion.eyeShape == CatEyeShape.SLEEPY_CLOSED) {
            blinkAnim.snapTo(0.05f)
            return@LaunchedEffect
        }
        while (isActive) {
            delay((2800L..4400L).random())
            // Fast eye close
            blinkAnim.animateTo(0.05f, animationSpec = tween(90, easing = LinearEasing))
            // Quick eye open
            blinkAnim.animateTo(1f, animationSpec = tween(130, easing = LinearEasing))
        }
    }

    // 4. Idle Breathing & Micro-Movement Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "CatBreathing")
    val idleGazeX by infiniteTransition.animateFloat(
        initialValue = -0.25f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdleGaze"
    )
    val earTwitchAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EarTwitch"
    )
    val blushPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlushPulse"
    )
    val talkMouthScale by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(135, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TalkLipSync"
    )
    val sleepBubblePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SleepBubblePhase"
    )
    val sweatWobblePhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SweatWobblePhase"
    )
    val tremblePhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TremblePhase"
    )

    // 5. Smooth Parametric Spring State Interpolation (Anki Vector / Cozmo style physics)
    val rawTargetX = trackerState.currentTarget?.normalizedX ?: idleGazeX
    val rawTargetY = trackerState.currentTarget?.normalizedY ?: 0f

    val smoothedGazeX by animateFloatAsState(
        targetValue = rawTargetX.coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "GazeX"
    )
    val smoothedGazeY by animateFloatAsState(
        targetValue = rawTargetY.coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "GazeY"
    )

    val animatedOpenness by animateFloatAsState(
        targetValue = targetEmotion.eyeOpennessScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "EyeOpenness"
    )
    val animatedPupilDilation by animateFloatAsState(
        targetValue = targetEmotion.pupilDilationScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "PupilDilation"
    )
    val animatedBlush by animateFloatAsState(
        targetValue = targetEmotion.blushIntensity,
        animationSpec = tween(400),
        label = "BlushIntensity"
    )
    val animatedEarDroop by animateFloatAsState(
        targetValue = targetEmotion.earDroopDeg,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "EarDroop"
    )
    val animatedSweatDrop by animateFloatAsState(
        targetValue = targetEmotion.sweatDropIntensity,
        animationSpec = tween(300),
        label = "SweatDrop"
    )
    val animatedPuffCheeks by animateFloatAsState(
        targetValue = targetEmotion.puffedCheeksScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "PuffCheeks"
    )

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showControls by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val leftEarPetTwitch = remember { Animatable(0f) }
    val rightEarPetTwitch = remember { Animatable(0f) }

    val handlePetLeftEar: () -> Unit = {
        coroutineScope.launch {
            leftEarPetTwitch.snapTo(12f)
            leftEarPetTwitch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
        }
        onPetLeftEar()
    }

    val handlePetRightEar: () -> Unit = {
        coroutineScope.launch {
            rightEarPetTwitch.snapTo(12f)
            rightEarPetTwitch.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
        }
        onPetRightEar()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                var lastDragPetTime = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    val startPos = down.position
                    val w = containerSize.width.toFloat()
                    val h = containerSize.height.toFloat()

                    fun triggerAction(nx: Float, ny: Float, isFromDrag: Boolean) {
                        when {
                            // Left ear petting (upper-left corner)
                            nx < 0.38f && ny < 0.40f -> handlePetLeftEar()
                            // Right ear petting (upper-right corner)
                            nx > 0.62f && ny < 0.40f -> handlePetRightEar()
                            // Nose boop (center button nose)
                            nx in 0.38f..0.62f && ny in 0.44f..0.65f -> onBoopNose()
                            // Chin stroke (lower center)
                            nx in 0.32f..0.68f && ny > 0.65f -> onStrokeChin()
                            else -> {
                                if (!isFromDrag) {
                                    showControls = !showControls
                                }
                            }
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!isDrag && w > 0f && h > 0f) {
                                triggerAction(startPos.x / w, startPos.y / h, isFromDrag = false)
                            }
                            break
                        } else {
                            val dragDist = (change.position - startPos).getDistance()
                            if (dragDist > viewConfiguration.touchSlop) {
                                isDrag = true
                                if (w > 0f && h > 0f) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastDragPetTime > 650L) {
                                        val nx = change.position.x / w
                                        val ny = change.position.y / h
                                        val isHotspot = (nx < 0.38f && ny < 0.40f) ||
                                            (nx > 0.62f && ny < 0.40f) ||
                                            (nx in 0.38f..0.62f && ny in 0.44f..0.65f) ||
                                            (nx in 0.32f..0.68f && ny > 0.65f)
                                        if (isHotspot) {
                                            triggerAction(nx, ny, isFromDrag = true)
                                            lastDragPetTime = now
                                        }
                                    }
                                }
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        // Cat Face Canvas - Fills 100% of the screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // Background Radial Depth Glow
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF070D18)),
                    center = Offset(cx, cy),
                    radius = kotlin.math.max(w, h) * 0.75f
                )
            )

            // A. CAT EARS (Vector/Cozmo styled robotic-organic ears with reactive twitches and droop)
            val earSpreadX = w * 0.32f
            val earWidth = (h * 0.36f).coerceAtLeast(70f)
            val earHeight = (h * 0.32f).coerceAtLeast(75f)
            val earBaseY = cy - h * 0.12f
            val earFlattenOffset = if (targetEmotion.earFlattenBack) h * 0.08f else 0f

            // Left Ear Outer
            val leftEarTipX = cx - earSpreadX - w * 0.03f - (animatedEarDroop * 1.5f)
            val leftEarTipY = earBaseY - earHeight - ((earTwitchAngle + leftEarPetTwitch.value) * 2.2f) + earFlattenOffset + (animatedEarDroop * 2.0f)
            val leftEarPath = Path().apply {
                moveTo(cx - earSpreadX - earWidth * 0.65f, earBaseY)
                lineTo(leftEarTipX, leftEarTipY)
                lineTo(cx - earSpreadX + earWidth * 0.35f, earBaseY + h * 0.04f)
                close()
            }
            drawPath(leftEarPath, Color(0xFF1E293B))

            // Left Ear Inner (Pink)
            val leftEarInnerPath = Path().apply {
                moveTo(cx - earSpreadX - earWidth * 0.52f, earBaseY - h * 0.02f)
                lineTo(leftEarTipX + w * 0.01f, leftEarTipY + h * 0.06f)
                lineTo(cx - earSpreadX + earWidth * 0.22f, earBaseY + h * 0.02f)
                close()
            }
            drawPath(leftEarInnerPath, Color(0xFFF472B6))

            // Right Ear Outer
            val rightEarTipX = cx + earSpreadX + w * 0.03f + (animatedEarDroop * 1.5f)
            val rightEarTipY = earBaseY - earHeight + ((earTwitchAngle + rightEarPetTwitch.value) * 2.2f) + earFlattenOffset + (animatedEarDroop * 2.0f)
            val rightEarPath = Path().apply {
                moveTo(cx + earSpreadX - earWidth * 0.35f, earBaseY + h * 0.04f)
                lineTo(rightEarTipX, rightEarTipY)
                lineTo(cx + earSpreadX + earWidth * 0.65f, earBaseY)
                close()
            }
            drawPath(rightEarPath, Color(0xFF1E293B))

            // Right Ear Inner (Pink)
            val rightEarInnerPath = Path().apply {
                moveTo(cx + earSpreadX - earWidth * 0.22f, earBaseY + h * 0.02f)
                lineTo(rightEarTipX - w * 0.01f, rightEarTipY + h * 0.06f)
                lineTo(cx + earSpreadX + earWidth * 0.52f, earBaseY - h * 0.02f)
                close()
            }
            drawPath(rightEarInnerPath, Color(0xFFF472B6))

            // B. ROSY BLUSHING CHEEKS
            val eyeSpacing = w * 0.23f
            val eyeWidth = (w * 0.21f).coerceIn(120f, h * 0.65f)
            val maxEyeHeight = h * 0.46f * animatedOpenness
            val eyeCenterY = cy - h * 0.03f

            val blushRadiusX = eyeWidth * 0.44f * blushPulse * animatedBlush * animatedPuffCheeks
            val blushRadiusY = (h * 0.08f) * blushPulse * animatedBlush * (if (animatedPuffCheeks > 1.1f) 1.25f else 1.0f)
            val blushY = eyeCenterY + maxEyeHeight * 0.58f
            val blushColor = if (trackerState.lockStatus == TargetLockStatus.LOCKED) Color(0x77FB7185) else Color(0x44FB7185)

            // Left & Right Cheek Blush
            drawOval(
                color = blushColor,
                topLeft = Offset(cx - eyeSpacing - blushRadiusX, blushY - blushRadiusY),
                size = Size(blushRadiusX * 2f, blushRadiusY * 2f)
            )
            drawOval(
                color = blushColor,
                topLeft = Offset(cx + eyeSpacing - blushRadiusX, blushY - blushRadiusY),
                size = Size(blushRadiusX * 2f, blushRadiusY * 2f)
            )

            // C. CAT WHISKERS
            val whiskerStroke = 4f
            val whiskerColor = Color(0x6694A3B8)
            val whiskerStartX = eyeSpacing + eyeWidth * 0.52f
            val whiskerLen = (w * 0.16f).coerceAtLeast(50f)

            // Left Whiskers
            drawLine(whiskerColor, Offset(cx - whiskerStartX, cy + h * 0.02f), Offset(cx - whiskerStartX - whiskerLen, cy - h * 0.04f), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx - whiskerStartX, cy + h * 0.08f), Offset(cx - whiskerStartX - whiskerLen * 1.15f, cy + h * 0.08f), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx - whiskerStartX, cy + h * 0.14f), Offset(cx - whiskerStartX - whiskerLen, cy + h * 0.20f), whiskerStroke, StrokeCap.Round)

            // Right Whiskers
            drawLine(whiskerColor, Offset(cx + whiskerStartX, cy + h * 0.02f), Offset(cx + whiskerStartX + whiskerLen, cy - h * 0.04f), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx + whiskerStartX, cy + h * 0.08f), Offset(cx + whiskerStartX + whiskerLen * 1.15f, cy + h * 0.08f), whiskerStroke, StrokeCap.Round)
            drawLine(whiskerColor, Offset(cx + whiskerStartX, cy + h * 0.14f), Offset(cx + whiskerStartX + whiskerLen, cy + h * 0.20f), whiskerStroke, StrokeCap.Round)

            // D. PROCEDURAL EXPRESSIVE EYES (Vector/Cozmo parametric shapes)
            val leftEyeCenter = Offset(cx - eyeSpacing, eyeCenterY)
            val rightEyeCenter = Offset(cx + eyeSpacing, eyeCenterY)
            val currentEyeHeight = (maxEyeHeight * blinkAnim.value).coerceAtLeast(6f)

            when (targetEmotion.eyeShape) {
                CatEyeShape.DIZZY_CROSS -> {
                    // DIZZY / TILTED "X X" EYES
                    val crossSize = eyeWidth * 0.28f
                    val crossColor = Color(0xFFEF4444)
                    val crossStroke = 7f

                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        drawLine(crossColor, Offset(eyePos.x - crossSize, eyePos.y - crossSize), Offset(eyePos.x + crossSize, eyePos.y + crossSize), crossStroke, StrokeCap.Round)
                        drawLine(crossColor, Offset(eyePos.x - crossSize, eyePos.y + crossSize), Offset(eyePos.x + crossSize, eyePos.y - crossSize), crossStroke, StrokeCap.Round)
                    }
                }

                CatEyeShape.SURPRISED_WIDE -> {
                    // SURPRISED WIDE ROUND ALERT EYES (O O)
                    val alertRadius = eyeWidth * 0.44f * animatedOpenness
                    val alertColor = Color(0xFFF59E0B)

                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        drawCircle(color = Color(0xFF1E293B), radius = alertRadius, center = eyePos)
                        drawCircle(color = alertColor, radius = alertRadius, center = eyePos, style = Stroke(5f))

                        val pupilRadius = alertRadius * 0.65f * animatedPupilDilation
                        drawCircle(color = Color(0xFF0F172A), radius = pupilRadius, center = eyePos)
                        drawCircle(color = Color.White, radius = alertRadius * 0.22f, center = Offset(eyePos.x - alertRadius * 0.22f, eyePos.y - alertRadius * 0.22f))
                    }
                }

                CatEyeShape.HAPPY_SQUINT -> {
                    // JOYFUL HAPPY SQUINT ARCS (^ ^)
                    val squintWidth = eyeWidth * 0.95f
                    val squintStroke = 8.5f
                    val squintColor = Color(0xFFF472B6)

                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        val squintPath = Path().apply {
                            moveTo(eyePos.x - squintWidth / 2f, eyePos.y + 6.dp.toPx())
                            quadraticTo(eyePos.x, eyePos.y - 28.dp.toPx(), eyePos.x + squintWidth / 2f, eyePos.y + 6.dp.toPx())
                        }
                        drawPath(squintPath, squintColor, style = Stroke(squintStroke, cap = StrokeCap.Round))
                    }
                }

                CatEyeShape.PLAYFUL_WINK -> {
                    // PLAYFUL WINK: Left eye squint (>), right eye open
                    val squintWidth = eyeWidth * 0.90f
                    val squintStroke = 8f
                    val winkPath = Path().apply {
                        moveTo(leftEyeCenter.x - squintWidth / 2f, leftEyeCenter.y - 12.dp.toPx())
                        lineTo(leftEyeCenter.x + squintWidth / 2f, leftEyeCenter.y)
                        lineTo(leftEyeCenter.x - squintWidth / 2f, leftEyeCenter.y + 12.dp.toPx())
                    }
                    drawPath(winkPath, Color(0xFF38BDF8), style = Stroke(squintStroke, cap = StrokeCap.Round))

                    // Right eye fully open & tracking
                    drawAnimeTrackingEye(
                        eyePos = rightEyeCenter,
                        eyeWidth = eyeWidth,
                        currentEyeHeight = currentEyeHeight,
                        smoothedGazeX = smoothedGazeX,
                        smoothedGazeY = smoothedGazeY,
                        animatedPupilDilation = animatedPupilDilation,
                        lockStatus = trackerState.lockStatus
                    )
                }

                CatEyeShape.HEART_EYES -> {
                    // CUTE GLOWING HEART EYES (♥ ♥) FOR LOVE GESTURE
                    val heartSize = eyeWidth * 0.65f
                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        drawHeart(center = eyePos, size = heartSize, color = Color(0xFFEC4899))
                    }
                }

                CatEyeShape.SLEEPY_CLOSED -> {
                    // SLEEPY CLOSED RESTING ARCS (u u)
                    val lidWidth = eyeWidth * 0.90f
                    val lidStroke = 7.5f
                    val lidColor = Color(0xFF38BDF8)
                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        val sleepArc = Path().apply {
                            moveTo(eyePos.x - lidWidth / 2f, eyePos.y - 4.dp.toPx())
                            quadraticTo(eyePos.x, eyePos.y + 16.dp.toPx(), eyePos.x + lidWidth / 2f, eyePos.y - 4.dp.toPx())
                        }
                        drawPath(sleepArc, lidColor, style = Stroke(lidStroke, cap = StrokeCap.Round))
                    }
                }

                CatEyeShape.NERVOUS_WOBBLE -> {
                    // WOBBLE PUPIL JITTER
                    val jitter = tremblePhase * 3.5.dp.toPx()
                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        drawAnimeTrackingEye(
                            eyePos = eyePos,
                            eyeWidth = eyeWidth,
                            currentEyeHeight = currentEyeHeight,
                            smoothedGazeX = smoothedGazeX,
                            smoothedGazeY = smoothedGazeY,
                            animatedPupilDilation = animatedPupilDilation,
                            lockStatus = trackerState.lockStatus,
                            pupilJitterX = jitter
                        )
                    }
                }

                CatEyeShape.POUTY_HALF -> {
                    // HALF-LIDDED POUT EYES
                    listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                        drawAnimeTrackingEye(
                            eyePos = eyePos,
                            eyeWidth = eyeWidth,
                            currentEyeHeight = currentEyeHeight,
                            smoothedGazeX = smoothedGazeX,
                            smoothedGazeY = smoothedGazeY,
                            animatedPupilDilation = animatedPupilDilation,
                            lockStatus = trackerState.lockStatus,
                            isPoutyHalf = true
                        )
                    }
                }

                CatEyeShape.NORMAL_ROUNDED -> {
                    if (blinkAnim.value < 0.18f) {
                        // Natural blinking slit (^ ^)
                        val lidWidth = eyeWidth * 0.9f
                        val lidStroke = 7f
                        val lidColor = Color(0xFF38BDF8)
                        listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                            val lid = Path().apply {
                                moveTo(eyePos.x - lidWidth / 2f, eyePos.y)
                                quadraticTo(eyePos.x, eyePos.y - 14.dp.toPx(), eyePos.x + lidWidth / 2f, eyePos.y)
                            }
                            drawPath(lid, lidColor, style = Stroke(lidStroke, cap = StrokeCap.Round))
                        }
                    } else {
                        // Open anime tracking eyes
                        listOf(leftEyeCenter, rightEyeCenter).forEach { eyePos ->
                            drawAnimeTrackingEye(
                                eyePos = eyePos,
                                eyeWidth = eyeWidth,
                                currentEyeHeight = currentEyeHeight,
                                smoothedGazeX = smoothedGazeX,
                                smoothedGazeY = smoothedGazeY,
                                animatedPupilDilation = animatedPupilDilation,
                                lockStatus = trackerState.lockStatus
                            )
                        }
                    }
                }
            }

            // Draw Golden Star Sparkles if active
            if (targetEmotion.hasStarSparkles) {
                val starColor = Color(0xFFFDE047)
                listOf(
                    Offset(leftEyeCenter.x - eyeWidth * 0.50f, leftEyeCenter.y - 24.dp.toPx()),
                    Offset(rightEyeCenter.x + eyeWidth * 0.50f, rightEyeCenter.y - 24.dp.toPx())
                ).forEach { starPos ->
                    drawStar4(center = starPos, radius = 9.dp.toPx(), color = starColor)
                }
            }

            // Manga Sweat-Drop for Tilt Wobble
            if (animatedSweatDrop > 0.02f) {
                val sweatTipPos = Offset(
                    x = leftEyeCenter.x - eyeWidth * 0.44f,
                    y = eyeCenterY - maxEyeHeight * 0.58f + (sweatWobblePhase * 3.dp.toPx())
                )
                drawSweatDrop(
                    tipPos = sweatTipPos,
                    size = eyeWidth * 0.36f * animatedSweatDrop,
                    intensity = animatedSweatDrop
                )
            }

            // E. CUTE NOSE
            val noseY = eyeCenterY + maxEyeHeight * 0.36f
            val noseW = 12.dp.toPx()
            val noseH = 9.dp.toPx()
            val nosePath = Path().apply {
                moveTo(cx - noseW, noseY)
                lineTo(cx + noseW, noseY)
                lineTo(cx, noseY + noseH)
                close()
            }
            drawPath(nosePath, Color(0xFFFB7185))

            // F. KAWAII CAT MOUTH MORPHING (WITH LIP-SYNC & GRUMPY POUT)
            val mouthY = noseY + noseH + 3.dp.toPx()
            val mouthW = (h * 0.11f).coerceAtLeast(24f)
            val mouthH = (h * 0.06f).coerceAtLeast(14f)
            val mouthStroke = 5f
            val mouthColor = Color(0xFFF1F5F9)

            if (trackerState.isRobotSpeaking) {
                // Animated Lip-Sync Talking Mouth with Cute Pink Tongue
                val openDepth = mouthH * talkMouthScale
                val talkMouthPath = Path().apply {
                    moveTo(cx - mouthW * 0.82f, mouthY)
                    quadraticTo(cx, mouthY + openDepth, cx + mouthW * 0.82f, mouthY)
                    close()
                }
                drawPath(talkMouthPath, Color(0xFF1E293B))
                if (talkMouthScale > 0.9f) {
                    val tonguePath = Path().apply {
                        moveTo(cx - mouthW * 0.42f, mouthY + openDepth * 0.45f)
                        quadraticTo(cx, mouthY + openDepth * 0.95f, cx + mouthW * 0.42f, mouthY + openDepth * 0.45f)
                        close()
                    }
                    drawPath(tonguePath, Color(0xFFFB7185))
                }
                drawPath(talkMouthPath, mouthColor, style = Stroke(mouthStroke, cap = StrokeCap.Round))
            } else {
                when (targetEmotion.mouthStyle) {
                    CatMouthStyle.POUT_PUFFED -> {
                        // Grumpy cute downturned wavy pout (~ 3 ~)
                        val poutPath = Path().apply {
                            moveTo(cx - mouthW * 0.70f, mouthY + mouthH * 0.55f)
                            quadraticTo(cx - mouthW * 0.25f, mouthY + mouthH * 0.15f, cx, mouthY + mouthH * 0.50f)
                            quadraticTo(cx + mouthW * 0.25f, mouthY + mouthH * 0.15f, cx + mouthW * 0.70f, mouthY + mouthH * 0.55f)
                        }
                        drawPath(poutPath, Color(0xFFF43F5E), style = Stroke(mouthStroke, cap = StrokeCap.Round))
                        drawCircle(color = Color(0xFFFB7185), radius = 3.dp.toPx(), center = Offset(cx, mouthY + mouthH * 0.95f))
                    }

                    CatMouthStyle.WAVY_SAD -> {
                        val sadMouth = Path().apply {
                            moveTo(cx - mouthW, mouthY + mouthH)
                            quadraticTo(cx - mouthW / 2f, mouthY + mouthH * 0.3f, cx, mouthY + mouthH)
                            quadraticTo(cx + mouthW / 2f, mouthY + mouthH * 1.7f, cx + mouthW, mouthY + mouthH)
                        }
                        drawPath(sadMouth, Color(0xFFEF4444), style = Stroke(mouthStroke, cap = StrokeCap.Round))
                    }

                    CatMouthStyle.OPEN_LAUGH -> {
                        // Joyful open smiling mouth with pink tongue
                        val waveMouthPath = Path().apply {
                            moveTo(cx - mouthW * 0.85f, mouthY)
                            quadraticTo(cx, mouthY + mouthH * 2.3f, cx + mouthW * 0.85f, mouthY)
                            close()
                        }
                        drawPath(waveMouthPath, Color(0xFF1E293B))
                        val tonguePath = Path().apply {
                            moveTo(cx - mouthW * 0.45f, mouthY + mouthH * 1.1f)
                            quadraticTo(cx, mouthY + mouthH * 2.2f, cx + mouthW * 0.45f, mouthY + mouthH * 1.1f)
                            close()
                        }
                        drawPath(tonguePath, Color(0xFFFB7185))
                        drawPath(waveMouthPath, mouthColor, style = Stroke(mouthStroke, cap = StrokeCap.Round))
                    }

                    CatMouthStyle.SURPRISED_O -> {
                        // Small surprised o mouth
                        drawOval(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(cx - mouthW * 0.35f, mouthY),
                            size = Size(mouthW * 0.70f, mouthH * 1.3f)
                        )
                        drawOval(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(cx - mouthW * 0.35f, mouthY),
                            size = Size(mouthW * 0.70f, mouthH * 1.3f),
                            style = Stroke(3.5f)
                        )
                    }

                    CatMouthStyle.CONFIDENT_SMIRK -> {
                        // Playful cute cat smirk
                        val smirkPath = Path().apply {
                            moveTo(cx - mouthW * 0.7f, mouthY + mouthH * 0.4f)
                            quadraticTo(cx, mouthY + mouthH * 0.9f, cx + mouthW * 0.9f, mouthY)
                        }
                        drawPath(smirkPath, mouthColor, style = Stroke(mouthStroke, cap = StrokeCap.Round))
                    }

                    CatMouthStyle.NEUTRAL_W -> {
                        // Happy ":3" curved cat mouth (two gentle loops)
                        val happyMouth = Path().apply {
                            moveTo(cx - mouthW, mouthY + mouthH * 0.5f)
                            quadraticTo(cx - mouthW / 2f, mouthY + mouthH * 1.3f, cx, mouthY + mouthH * 0.2f)
                            quadraticTo(cx + mouthW / 2f, mouthY + mouthH * 1.3f, cx + mouthW, mouthY + mouthH * 0.5f)
                        }
                        drawPath(happyMouth, mouthColor, style = Stroke(mouthStroke, cap = StrokeCap.Round))
                    }
                }
            }

            // Drifting zZz Sleep Bubbles when napping
            if (targetEmotion.isNapping) {
                for (i in 0..2) {
                    val progress = (sleepBubblePhase + i * 0.33f) % 1.0f
                    val bubbleY = (cy - h * 0.12f) - progress * (h * 0.32f)
                    val bubbleX = (cx + w * 0.28f) + sin(progress * 2f * PI.toFloat()) * (w * 0.04f) + i * 16.dp.toPx()
                    val bubbleRadius = (16.dp.toPx() + progress * 12.dp.toPx())
                    val bubbleAlpha = sin(progress * PI.toFloat()).coerceIn(0f, 1f) * 0.90f
                    drawSleepBubble(
                        center = Offset(bubbleX, bubbleY),
                        radius = bubbleRadius,
                        alpha = bubbleAlpha
                    )
                }
            }
        }

        // Top Status / Emotion Banner
        AnimatedVisibility(
            visible = targetEmotion.statusBannerText != null || trackerState.isRobotSpeaking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val bannerText = targetEmotion.statusBannerText ?: "🗣️ Speaking..."
            val bannerColor = if (targetEmotion.statusBannerText != null) {
                Color(targetEmotion.statusBannerColorHex)
            } else {
                Color(0xFFF472B6)
            }
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(2.dp, bannerColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bannerText,
                        color = bannerColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Floating Minimized UI Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Top-Center: Minimal companion mood capsule (shown only when no large banner is active)
                val isBannerActive = targetEmotion.statusBannerText != null || trackerState.isRobotSpeaking
                if (!isBannerActive) {
                    Surface(
                        modifier = Modifier
                            .align(if (showTrackingControls) Alignment.TopStart else Alignment.TopCenter)
                            .padding(top = 4.dp),
                        color = Color(0x990F172A),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0x3338BDF8))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isBalancing) Color(0xFF10B981) else Color(0xFFEF4444),
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (isBalancing) {
                                    if (targetEmotion.isNapping) "Cat Companion (Napping)" else "Cat Companion Active"
                                } else "Robot Fallen",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (showTrackingControls) {
                    // Top-Right: Camera switch & settings icon buttons
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onToggleCamera,
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0x990F172A), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0x990F172A), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onExitCatMode,
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0x990F172A), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Cat Mode",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Bottom-Left: Emergency Stop Pill
                    Button(
                        onClick = onEmergencyStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xCCEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "E-Stop",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "E-STOP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Bottom-Right: Start / Stop Tracking toggle
                    Button(
                        onClick = onToggleTracking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (trackerState.isTrackingActive) Color(0xCCEF4444) else Color(0xCC10B981)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = if (trackerState.isTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (trackerState.isTrackingActive) "STOP FOLLOW" else "START FOLLOW",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // AI Thinking Glow Pill
                if (trackerState.isThinkingAi) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                        color = Color(0xEE0F172A),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF38BDF8))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "🧠 Gemma 4 Thinking...",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // AI Voice Listening / Tap-to-Talk Pill
                Surface(
                    onClick = onToggleVoiceListening,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (trackerState.isThinkingAi) 42.dp else 4.dp),
                    color = if (trackerState.isListeningVoice) Color(0xEE065F46) else Color(0x990F172A),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        1.dp,
                        if (trackerState.isListeningVoice) Color(0xFF34D399) else Color(0xFF38BDF8).copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (trackerState.isListeningVoice) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Voice Input",
                            tint = if (trackerState.isListeningVoice) Color(0xFF34D399) else Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (trackerState.isListeningVoice) "Listening... (Speak!)" else "Tap to Speak",
                            color = if (trackerState.isListeningVoice) Color(0xFF34D399) else Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Autonomous Scene Commentary Object Pill
                val topSceneObj = trackerState.detectedSceneObjects.maxByOrNull { it.confidence }
                if (topSceneObj != null && trackerState.settings.sceneCommentaryEnabled) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = if (trackerState.isThinkingAi) 78.dp else 42.dp),
                        color = Color(0xDD1E1B4B),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "👁️ ${topSceneObj.label} (${(topSceneObj.confidence * 100).toInt()}%)",
                                color = Color(0xFFE9D5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Dialogue Subtitle Banner at Bottom Center
                val subtitle = trackerState.aiSpeechSubtitle
                if (!subtitle.isNullOrBlank() && (trackerState.isRobotSpeaking || trackerState.isThinkingAi)) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                        color = Color(0xF00F172A),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFF472B6).copy(alpha = 0.75f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = null,
                                tint = Color(0xFFF472B6),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = subtitle,
                                color = Color(0xFFFDE8E8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws an expressive anime tracking eye with pupil gaze offset and multi-layer sparkles (original Cozmo/Vector style).
 */
private fun DrawScope.drawAnimeTrackingEye(
    eyePos: Offset,
    eyeWidth: Float,
    currentEyeHeight: Float,
    smoothedGazeX: Float,
    smoothedGazeY: Float,
    animatedPupilDilation: Float,
    lockStatus: TargetLockStatus,
    pupilJitterX: Float = 0f,
    isPoutyHalf: Boolean = false
) {
    // Eye Socket Background
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(eyePos.x - eyeWidth / 2f, eyePos.y - currentEyeHeight / 2f),
        size = Size(eyeWidth, currentEyeHeight),
        cornerRadius = CornerRadius(eyeWidth / 2f, currentEyeHeight / 2f)
    )
    // Eye Socket Border
    drawRoundRect(
        color = Color(0xFF38BDF8),
        topLeft = Offset(eyePos.x - eyeWidth / 2f, eyePos.y - currentEyeHeight / 2f),
        size = Size(eyeWidth, currentEyeHeight),
        cornerRadius = CornerRadius(eyeWidth / 2f, currentEyeHeight / 2f),
        style = Stroke(4.5f)
    )

    // Pupil with Target Tracking Offset
    val maxPupilShiftX = eyeWidth * 0.24f
    val maxPupilShiftY = currentEyeHeight * 0.22f
    val pupilShiftX = smoothedGazeX * maxPupilShiftX + pupilJitterX
    val pupilShiftY = smoothedGazeY * maxPupilShiftY
    val pupilPos = Offset(eyePos.x + pupilShiftX, eyePos.y + pupilShiftY)

    val pupilRadiusX = eyeWidth * 0.28f * animatedPupilDilation
    val pupilRadiusY = (currentEyeHeight * 0.36f * animatedPupilDilation).coerceAtLeast(3f)

    // Glowing Anime Iris
    val irisBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFF38BDF8),
            if (lockStatus == TargetLockStatus.LOCKED) Color(0xFF10B981) else Color(0xFF0284C7),
            Color(0xFF0F172A)
        ),
        center = pupilPos,
        radius = pupilRadiusX * 1.25f
    )
    drawOval(
        brush = irisBrush,
        topLeft = Offset(pupilPos.x - pupilRadiusX, pupilPos.y - pupilRadiusY),
        size = Size(pupilRadiusX * 2f, pupilRadiusY * 2f)
    )

    // Big Specular Sparkle Highlight (Anime twinkle)
    val glintRadius = 0.09f * eyeWidth
    drawCircle(
        color = Color.White,
        radius = glintRadius,
        center = Offset(pupilPos.x - pupilRadiusX * 0.35f, pupilPos.y - pupilRadiusY * 0.35f)
    )

    // Smaller secondary sparkle
    drawCircle(
        color = Color(0xCCFFFFFF),
        radius = glintRadius * 0.45f,
        center = Offset(pupilPos.x + pupilRadiusX * 0.30f, pupilPos.y + pupilRadiusY * 0.25f)
    )

    // If half-lidded pout, droop upper lid
    if (isPoutyHalf) {
        val hoodY = eyePos.y + currentEyeHeight * 0.08f
        val hoodedLid = Path().apply {
            moveTo(eyePos.x - eyeWidth / 2f, eyePos.y - currentEyeHeight / 2f)
            lineTo(eyePos.x + eyeWidth / 2f, eyePos.y - currentEyeHeight / 2f)
            lineTo(eyePos.x + eyeWidth / 2f, hoodY)
            quadraticTo(eyePos.x, hoodY + 6.dp.toPx(), eyePos.x - eyeWidth / 2f, hoodY)
            close()
        }
        drawPath(hoodedLid, Color(0xFF0F172A))
        val poutStroke = Path().apply {
            moveTo(eyePos.x - eyeWidth / 2f, hoodY)
            quadraticTo(eyePos.x, hoodY + 6.dp.toPx(), eyePos.x + eyeWidth / 2f, hoodY)
        }
        drawPath(poutStroke, Color(0xFF38BDF8), style = Stroke(4f, cap = StrokeCap.Round))
    }
}

/**
 * Draws a 4-pointed diamond star sparkle.
 */
private fun DrawScope.drawStar4(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

/**
 * Draws a cute heart shape centered at a position for the LOVE hand gesture.
 */
private fun DrawScope.drawHeart(
    center: Offset,
    size: Float,
    color: Color
) {
    val h = size
    val w = size * 1.15f
    val path = Path().apply {
        moveTo(center.x, center.y + h * 0.45f)
        cubicTo(
            center.x - w * 0.60f, center.y - h * 0.15f,
            center.x - w * 0.60f, center.y - h * 0.60f,
            center.x - w * 0.25f, center.y - h * 0.60f
        )
        cubicTo(
            center.x - w * 0.05f, center.y - h * 0.60f,
            center.x, center.y - h * 0.40f,
            center.x, center.y - h * 0.30f
        )
        cubicTo(
            center.x, center.y - h * 0.40f,
            center.x + w * 0.05f, center.y - h * 0.60f,
            center.x + w * 0.25f, center.y - h * 0.60f
        )
        cubicTo(
            center.x + w * 0.60f, center.y - h * 0.60f,
            center.x + w * 0.60f, center.y - h * 0.15f,
            center.x, center.y + h * 0.45f
        )
        close()
    }
    drawPath(path, color)
    drawPath(path, Color.White, style = Stroke(3.5f))
}

/**
 * Draws a floating semi-transparent sleep bubble with a cute 'Z' inside.
 */
private fun DrawScope.drawSleepBubble(
    center: Offset,
    radius: Float,
    alpha: Float
) {
    if (alpha <= 0.01f || radius <= 2f) return
    // Bubble body
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = alpha * 0.25f),
        radius = radius,
        center = center
    )
    // Bubble outline
    drawCircle(
        color = Color(0xFF93C5FD).copy(alpha = alpha * 0.75f),
        radius = radius,
        center = center,
        style = Stroke(2.5f)
    )
    // Bubble specular sheen
    drawArc(
        color = Color.White.copy(alpha = alpha * 0.85f),
        startAngle = 200f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.75f, center.y - radius * 0.75f),
        size = Size(radius * 1.5f, radius * 1.5f),
        style = Stroke(2f, cap = StrokeCap.Round)
    )
    // Cute 'Z' inside bubble
    val zSize = radius * 0.70f
    val zPath = Path().apply {
        moveTo(center.x - zSize * 0.45f, center.y - zSize * 0.45f)
        lineTo(center.x + zSize * 0.45f, center.y - zSize * 0.45f)
        lineTo(center.x - zSize * 0.45f, center.y + zSize * 0.45f)
        lineTo(center.x + zSize * 0.45f, center.y + zSize * 0.45f)
    }
    drawPath(
        path = zPath,
        color = Color.White.copy(alpha = alpha * 0.90f),
        style = Stroke(2.8f, cap = StrokeCap.Round)
    )
}

/**
 * Draws an anime teardrop sweat-drop for nervous wobble.
 */
private fun DrawScope.drawSweatDrop(
    tipPos: Offset,
    size: Float,
    intensity: Float
) {
    if (intensity <= 0.01f || size <= 4f) return
    val alpha = intensity.coerceIn(0f, 1f)
    val dropPath = Path().apply {
        moveTo(tipPos.x, tipPos.y)
        cubicTo(
            tipPos.x - size * 0.45f, tipPos.y + size * 0.55f,
            tipPos.x - size * 0.55f, tipPos.y + size * 1.15f,
            tipPos.x, tipPos.y + size * 1.15f
        )
        cubicTo(
            tipPos.x + size * 0.55f, tipPos.y + size * 1.15f,
            tipPos.x + size * 0.45f, tipPos.y + size * 0.55f,
            tipPos.x, tipPos.y
        )
        close()
    }
    drawPath(dropPath, Color(0xFF38BDF8).copy(alpha = alpha * 0.90f))
    drawPath(dropPath, Color(0xFF0284C7).copy(alpha = alpha), style = Stroke(3f))
    val glintPath = Path().apply {
        moveTo(tipPos.x - size * 0.22f, tipPos.y + size * 0.45f)
        cubicTo(
            tipPos.x - size * 0.32f, tipPos.y + size * 0.70f,
            tipPos.x - size * 0.25f, tipPos.y + size * 0.95f,
            tipPos.x - size * 0.10f, tipPos.y + size * 1.02f
        )
    }
    drawPath(glintPath, Color.White.copy(alpha = alpha * 0.85f), style = Stroke(2.5f, cap = StrokeCap.Round))
}
