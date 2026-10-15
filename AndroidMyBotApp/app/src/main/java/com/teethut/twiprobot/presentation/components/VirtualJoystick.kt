package com.teethut.twiprobot.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    deadZoneRatio: Float = 0.05f,
    onMove: (throttle: Float, steer: Float) -> Unit,
    onRelease: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val animatedKnobOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    val baseRadius = this.size.width / 2f
                    val knobMaxRadius = baseRadius * 0.72f

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            val center = Offset(baseRadius, baseRadius)
                            val delta = startOffset - center
                            val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                            val clampedDist = dist.coerceAtMost(knobMaxRadius)
                            val angle = atan2(delta.y, delta.x)

                            val offset = Offset(
                                x = clampedDist * cos(angle),
                                y = clampedDist * sin(angle)
                            )
                            coroutineScope.launch {
                                animatedKnobOffset.snapTo(offset)
                            }

                            val normX = (offset.x / knobMaxRadius).coerceIn(-1f, 1f)
                            val normY = -(offset.y / knobMaxRadius).coerceIn(-1f, 1f) // Y inverted for forward throttle

                            val steer = if (kotlin.math.abs(normX) < deadZoneRatio) 0f else normX
                            val throttle = if (kotlin.math.abs(normY) < deadZoneRatio) 0f else normY
                            onMove(throttle, steer)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val cur = animatedKnobOffset.value
                            val newX = cur.x + dragAmount.x
                            val newY = cur.y + dragAmount.y
                            val dist = sqrt(newX * newX + newY * newY)
                            val angle = atan2(newY, newX)
                            val clampedDist = dist.coerceAtMost(knobMaxRadius)

                            val offset = Offset(
                                x = clampedDist * cos(angle),
                                y = clampedDist * sin(angle)
                            )
                            coroutineScope.launch {
                                animatedKnobOffset.snapTo(offset)
                            }

                            val normX = (offset.x / knobMaxRadius).coerceIn(-1f, 1f)
                            val normY = -(offset.y / knobMaxRadius).coerceIn(-1f, 1f)

                            val steer = if (kotlin.math.abs(normX) < deadZoneRatio) 0f else normX
                            val throttle = if (kotlin.math.abs(normY) < deadZoneRatio) 0f else normY
                            onMove(throttle, steer)
                        },
                        onDragEnd = {
                            isDragging = false
                            coroutineScope.launch {
                                animatedKnobOffset.animateTo(
                                    targetValue = Offset.Zero,
                                    animationSpec = spring(stiffness = 1200f)
                                )
                            }
                            onRelease()
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                animatedKnobOffset.animateTo(
                                    targetValue = Offset.Zero,
                                    animationSpec = spring(stiffness = 1200f)
                                )
                            }
                            onRelease()
                        }
                    )
                }
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f
            val knobRadius = radius * 0.28f

            // Outer Base circle gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0C1322)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Outer Border
            drawCircle(
                color = if (isDragging) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF334155),
                radius = radius - 1.5f,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Directional Compass / Axis ticks (N, S, E, W)
            val tickLen = 8.dp.toPx()
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(center.x, center.y - radius + 4f),
                end = Offset(center.x, center.y - radius + 4f + tickLen),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(center.x, center.y + radius - 4f - tickLen),
                end = Offset(center.x, center.y + radius - 4f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(center.x - radius + 4f, center.y),
                end = Offset(center.x - radius + 4f + tickLen, center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(center.x + radius - 4f - tickLen, center.y),
                end = Offset(center.x + radius - 4f, center.y),
                strokeWidth = 2.dp.toPx()
            )

            // Concentric guide rings
            drawCircle(
                color = Color(0x3338BDF8),
                radius = radius * 0.65f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0x2238BDF8),
                radius = radius * 0.35f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Deadzone circle indicator
            drawCircle(
                color = Color(0x1F94A3B8),
                radius = radius * 0.72f * deadZoneRatio,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Crosshair lines
            drawLine(
                color = Color(0x33475569),
                start = Offset(center.x, center.y - radius * 0.85f),
                end = Offset(center.x, center.y + radius * 0.85f),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = Color(0x33475569),
                start = Offset(center.x - radius * 0.85f, center.y),
                end = Offset(center.x + radius * 0.85f, center.y),
                strokeWidth = 1.5.dp.toPx()
            )

            // Glowing line connecting center to knob
            val currentKnobCenter = center + animatedKnobOffset.value
            if (animatedKnobOffset.value != Offset.Zero) {
                drawLine(
                    color = Color(0x8838BDF8),
                    start = center,
                    end = currentKnobCenter,
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Knob Glow
            drawCircle(
                color = if (isDragging) Color(0x5538BDF8) else Color(0x2238BDF8),
                radius = knobRadius * 1.35f,
                center = currentKnobCenter
            )

            // Knob Body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                    center = currentKnobCenter,
                    radius = knobRadius
                ),
                radius = knobRadius,
                center = currentKnobCenter
            )

            // Knob Center Pip
            drawCircle(
                color = Color.White,
                radius = knobRadius * 0.3f,
                center = currentKnobCenter
            )
        }
    }
}
