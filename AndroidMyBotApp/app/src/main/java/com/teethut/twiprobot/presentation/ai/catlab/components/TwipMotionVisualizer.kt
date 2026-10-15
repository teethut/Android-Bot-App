package com.teethut.twiprobot.presentation.ai.catlab.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.domain.model.FelineWheelAction
import com.teethut.twiprobot.theme.BorderSubtle
import com.teethut.twiprobot.theme.CyanPrimary
import com.teethut.twiprobot.theme.SurfaceDark
import com.teethut.twiprobot.theme.SurfaceElevated
import com.teethut.twiprobot.theme.TextMuted
import com.teethut.twiprobot.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated 2D physics simulation of the TWIP robot chassis, wheels, and inverted pendulum.
 * Renders dynamic tilt (pitch), yaw wiggle dance, purr rocking, and forward/backward wheel actions.
 */
@Composable
fun TwipMotionVisualizer(
    wheelAction: FelineWheelAction,
    simulatedPitch: Float = 0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TwipMotion")

    // Fast 4 Hz yaw / lateral dance oscillation
    val fastDancePhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(130, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FastDance"
    )

    // Smooth 1 Hz gentle rocking sine wave
    val smoothRockPhase by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SmoothRock"
    )

    // Wheel continuous rolling rotation phase
    val wheelRollAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WheelRoll"
    )

    // Micro sway when holding still
    val idleSwayPhase by infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdleSway"
    )

    // Compute dynamic physical posture from wheelAction
    val effectivePitchAngle: Float
    val lateralOffsetPx: Float
    val wheelSpinAngle: Float

    when (wheelAction) {
        FelineWheelAction.HOLD_STILL -> {
            effectivePitchAngle = simulatedPitch + idleSwayPhase
            lateralOffsetPx = 0f
            wheelSpinAngle = 0f
        }
        FelineWheelAction.WIGGLE_DANCE -> {
            effectivePitchAngle = simulatedPitch + fastDancePhase * 3.5f
            lateralOffsetPx = fastDancePhase * 18f
            wheelSpinAngle = fastDancePhase * 30f
        }
        FelineWheelAction.BUNT_FORWARD -> {
            effectivePitchAngle = simulatedPitch - 5.5f + (smoothRockPhase * 0.8f)
            lateralOffsetPx = 14f + smoothRockPhase * 4f
            wheelSpinAngle = wheelRollAngle
        }
        FelineWheelAction.RECOIL_BACK -> {
            effectivePitchAngle = simulatedPitch + 6.0f + (smoothRockPhase * 0.6f)
            lateralOffsetPx = -16f + smoothRockPhase * 3f
            wheelSpinAngle = -wheelRollAngle * 0.6f
        }
        FelineWheelAction.PURR_ROCK -> {
            effectivePitchAngle = simulatedPitch + smoothRockPhase * 4.2f
            lateralOffsetPx = smoothRockPhase * 8f
            wheelSpinAngle = smoothRockPhase * 20f
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Action label and telemetry badge
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
                            .background(CyanPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2D CHASSIS MOTION SIMULATOR",
                        color = CyanPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Dynamic Action Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = wheelAction.label,
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2D Physics Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070D18))
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                    val w = size.width
                    val h = size.height
                    val groundY = h * 0.82f
                    val centerX = w / 2f + lateralOffsetPx
                    val wheelRadius = 26f
                    val wheelCenterY = groundY - wheelRadius

                    // 1. Ground plane & motion grid lines
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, groundY),
                        end = Offset(w, groundY),
                        strokeWidth = 3f
                    )
                    val gridSpacing = 32f
                    val gridShift = (lateralOffsetPx * 0.8f) % gridSpacing
                    var gx = -gridSpacing + gridShift
                    while (gx < w + gridSpacing) {
                        drawLine(
                            color = Color(0x33334155),
                            start = Offset(gx, groundY),
                            end = Offset(gx - 8f, groundY + 12f),
                            strokeWidth = 1.5f
                        )
                        gx += gridSpacing
                    }

                    // 2. Robot Pendulum Body (Rotates by pitch angle around axle)
                    rotate(degrees = effectivePitchAngle, pivot = Offset(centerX, wheelCenterY)) {
                        val pendulumHeight = 58f
                        val pendulumWidth = 24f
                        val bodyTop = wheelCenterY - pendulumHeight

                        // Central Mast / Pendulum Spine
                        drawLine(
                            color = Color(0xFF0284C7),
                            start = Offset(centerX, wheelCenterY),
                            end = Offset(centerX, bodyTop),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )

                        // Chassis Main Electronic Enclosure
                        val chassisBoxTop = bodyTop + 6f
                        val chassisBoxHeight = 36f
                        drawRoundRect(
                            color = Color(0xFF1E293B),
                            topLeft = Offset(centerX - pendulumWidth / 2f, chassisBoxTop),
                            size = Size(pendulumWidth, chassisBoxHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = CyanPrimary,
                            topLeft = Offset(centerX - pendulumWidth / 2f, chassisBoxTop),
                            size = Size(pendulumWidth, chassisBoxHeight),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(2f)
                        )

                        // LED Status Strip on chassis
                        drawRoundRect(
                            color = Color(0xFF38BDF8),
                            topLeft = Offset(centerX - pendulumWidth * 0.35f, chassisBoxTop + 8f),
                            size = Size(pendulumWidth * 0.70f, 6f),
                            cornerRadius = CornerRadius(3f, 3f)
                        )

                        // Cute cyber-cat antenna ears on top of chassis
                        val earPath = Path().apply {
                            // Left ear
                            moveTo(centerX - pendulumWidth * 0.40f, chassisBoxTop)
                            lineTo(centerX - pendulumWidth * 0.45f, chassisBoxTop - 12f)
                            lineTo(centerX - pendulumWidth * 0.10f, chassisBoxTop)
                            // Right ear
                            moveTo(centerX + pendulumWidth * 0.10f, chassisBoxTop)
                            lineTo(centerX + pendulumWidth * 0.45f, chassisBoxTop - 12f)
                            lineTo(centerX + pendulumWidth * 0.40f, chassisBoxTop)
                        }
                        drawPath(earPath, color = Color(0xFFFB7185), style = Stroke(2.5f, cap = StrokeCap.Round))
                    }

                    // 3. Wheels & Spokes (Side profile with rotating spokes)
                    // Outer rubber tire
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = wheelRadius,
                        center = Offset(centerX, wheelCenterY)
                    )
                    drawCircle(
                        color = Color(0xFF334155),
                        radius = wheelRadius,
                        center = Offset(centerX, wheelCenterY),
                        style = Stroke(4f)
                    )

                    // Inner Rim & Hub
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = wheelRadius * 0.65f,
                        center = Offset(centerX, wheelCenterY)
                    )
                    drawCircle(
                        color = CyanPrimary,
                        radius = wheelRadius * 0.65f,
                        center = Offset(centerX, wheelCenterY),
                        style = Stroke(2f)
                    )

                    // Rotating spokes (3-spoke sports wheel)
                    for (i in 0..2) {
                        val angleRad = (wheelSpinAngle + i * 120.0) * (PI / 180.0)
                        val sx = centerX + cos(angleRad).toFloat() * (wheelRadius * 0.60f)
                        val sy = wheelCenterY + sin(angleRad).toFloat() * (wheelRadius * 0.60f)
                        drawLine(
                            color = CyanPrimary,
                            start = Offset(centerX, wheelCenterY),
                            end = Offset(sx, sy),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Center axle hub
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 4.5f,
                        center = Offset(centerX, wheelCenterY)
                    )
                }

                // Tilt Angle Telemetry Readout
                Text(
                    text = "Pitch: ${String.format(java.util.Locale.US, "%+.1f", effectivePitchAngle)}°",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0x990F172A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
