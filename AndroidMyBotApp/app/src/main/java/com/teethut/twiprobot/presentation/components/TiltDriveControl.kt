package com.teethut.twiprobot.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.theme.CardBackground
import com.teethut.twiprobot.theme.SurfaceDark

@Composable
fun TiltDriveControl(
    pitchDeg: Float,
    rollDeg: Float,
    isDriving: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onRecenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "deadmanPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Phone Orientation Attitude Bubble Gauge
        Box(
            modifier = Modifier
                .size(164.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(CardBackground, SurfaceDark)
                    )
                )
                .border(2.dp, if (isPressed) Color(0xFF38BDF8) else Color(0xFF334155), CircleShape)
                .clickable { onRecenter() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(164.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Crosshairs
                drawLine(
                    color = Color(0x33475569),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0x33475569),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 1.dp.toPx()
                )

                // Concentric Limit Rings (10° deadzone, 20° mid, 30° max)
                drawCircle(
                    color = Color(0x2238BDF8),
                    radius = radius * 0.25f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0x1838BDF8),
                    radius = radius * 0.55f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Attitude Ball Offset based on roll (X) and pitch (Y)
                val maxAngle = 35f
                val clampedRoll = rollDeg.coerceIn(-maxAngle, maxAngle)
                val clampedPitch = pitchDeg.coerceIn(-maxAngle, maxAngle)

                val ballX = center.x + (clampedRoll / maxAngle) * (radius * 0.72f)
                val ballY = center.y + (clampedPitch / maxAngle) * (radius * 0.72f)
                val ballCenter = Offset(ballX, ballY)

                // Connecting line
                drawLine(
                    color = if (isPressed) Color(0x8838BDF8) else Color(0x2294A3B8),
                    start = center,
                    end = ballCenter,
                    strokeWidth = 2.dp.toPx()
                )

                // Attitude Indicator Pip Glow
                drawCircle(
                    color = if (isPressed) Color(0x4438BDF8) else Color(0x2264748B),
                    radius = 18.dp.toPx(),
                    center = ballCenter
                )

                // Attitude Indicator Pip
                drawCircle(
                    color = if (isPressed) Color(0xFF38BDF8) else Color(0xFF64748B),
                    radius = 11.dp.toPx(),
                    center = ballCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = ballCenter
                )
            }
        }

        // Live Readout & Recenter Quick Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "P: ${String.format(java.util.Locale.US, "%+.1f°", pitchDeg)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "R: ${String.format(java.util.Locale.US, "%+.1f°", rollDeg)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                    .clickable { onRecenter() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Recenter zero angle",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "RECENTER ZERO",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Hold-to-Drive Safety Button (Deadman Switch)
        // Tracks pointer state directly without timeout or slop cancellation so hand movement never disengages
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isPressed) Color(0xFF0284C7) else Color(0xFF1E293B)
                )
                .border(
                    2.dp,
                    if (isPressed) Color(0xFF38BDF8).copy(alpha = pulseAlpha) else Color(0xFF475569),
                    RoundedCornerShape(16.dp)
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isPressed = true
                        onHoldStart()
                        val pointerId = down.id
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null || !change.pressed) {
                                    break
                                }
                                change.consume()
                            }
                        } finally {
                            isPressed = false
                            onHoldEnd()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = "Hold to drive",
                    tint = if (isPressed) Color.White else Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isPressed) "TRANSMITTING MOTION" else "PRESS & HOLD TO DRIVE",
                        color = if (isPressed) Color.White else Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isPressed) "Zeroed on press · Tilt to steer & throttle" else "Recenters zero on press · Release to stop",
                        color = if (isPressed) Color(0xFFE0F2FE) else Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
