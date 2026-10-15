package com.teethut.twiprobot.presentation.tracker.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.teethut.twiprobot.domain.model.DistanceCategory
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.VisionTrackerState
import kotlin.math.abs

@Composable
fun TrackingHudOverlay(
    trackerState: VisionTrackerState,
    robotPitch: Float,
    isBalancing: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val primaryColor = when {
            !isBalancing -> Color(0xFFEF4444) // Red when fallen
            trackerState.lockStatus == TargetLockStatus.LOCKED -> Color(0xFF4ADE80) // Green
            trackerState.lockStatus == TargetLockStatus.LOST -> Color(0xFFF59E0B) // Amber
            else -> Color(0xFF38BDF8) // Cyan searching
        }

        // 1. Center Reference Reticle & Deadband Zone
        val deadbandHalfW = (w / 2f) * trackerState.settings.deadbandX
        val deadbandHalfH = (h / 2f) * 0.12f

        // Center crosshairs
        val crosshairLength = 24.dp.toPx()
        drawLine(
            color = Color(0x6638BDF8),
            start = Offset(cx - crosshairLength, cy),
            end = Offset(cx + crosshairLength, cy),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0x6638BDF8),
            start = Offset(cx, cy - crosshairLength),
            end = Offset(cx, cy + crosshairLength),
            strokeWidth = 2f
        )

        // Deadband alignment box
        drawRect(
            color = Color(0x3338BDF8),
            topLeft = Offset(cx - deadbandHalfW, cy - deadbandHalfH),
            size = Size(deadbandHalfW * 2f, deadbandHalfH * 2f),
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        )

        // 2. Pitch Horizon Leveler Guide (Left and right side ticks)
        val pitchOffsetPx = (robotPitch / 30f) * 80.dp.toPx()
        val horizonY = (cy + pitchOffsetPx).coerceIn(40f, h - 40f)
        drawLine(
            color = if (isBalancing) Color(0x8838BDF8) else Color(0xAAEF4444),
            start = Offset(24f, horizonY),
            end = Offset(72f, horizonY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = if (isBalancing) Color(0x8838BDF8) else Color(0xAAEF4444),
            start = Offset(w - 72f, horizonY),
            end = Offset(w - 24f, horizonY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // 3. Draw Detected Target Reticle
        val target = trackerState.currentTarget
        if (target != null) {
            val targetCenterX = cx + (target.normalizedX * (w / 2f))
            val targetCenterY = cy + (target.normalizedY * (h / 2f))
            val targetBoxW = (target.normalizedWidth * w).coerceAtLeast(60.dp.toPx())
            val targetBoxH = (target.normalizedHeight * h).coerceAtLeast(60.dp.toPx())

            val left = (targetCenterX - targetBoxW / 2f).coerceIn(0f, w - targetBoxW)
            val top = (targetCenterY - targetBoxH / 2f).coerceIn(0f, h - targetBoxH)
            val cornerLen = (targetBoxW * 0.25f).coerceIn(16.dp.toPx(), 40.dp.toPx())

            // Draw HUD Corner Brackets around target
            val cornerColor = primaryColor
            val cornerStroke = 4f

            // Top-Left corner
            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), cornerStroke)
            drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), cornerStroke)

            // Top-Right corner
            drawLine(cornerColor, Offset(left + targetBoxW, top), Offset(left + targetBoxW - cornerLen, top), cornerStroke)
            drawLine(cornerColor, Offset(left + targetBoxW, top), Offset(left + targetBoxW, top + cornerLen), cornerStroke)

            // Bottom-Left corner
            drawLine(cornerColor, Offset(left, top + targetBoxH), Offset(left + cornerLen, top + targetBoxH), cornerStroke)
            drawLine(cornerColor, Offset(left, top + targetBoxH), Offset(left, top + targetBoxH - cornerLen), cornerStroke)

            // Bottom-Right corner
            drawLine(cornerColor, Offset(left + targetBoxW, top + targetBoxH), Offset(left + targetBoxW - cornerLen, top + targetBoxH), cornerStroke)
            drawLine(cornerColor, Offset(left + targetBoxW, top + targetBoxH), Offset(left + targetBoxW, top + targetBoxH - cornerLen), cornerStroke)

            // Target center lock point
            drawCircle(
                color = cornerColor,
                radius = 6.dp.toPx(),
                center = Offset(targetCenterX, targetCenterY),
                style = Stroke(width = 3f)
            )

            // Vector connecting center to target
            if (abs(target.normalizedX) > trackerState.settings.deadbandX) {
                drawLine(
                    color = Color(0x664ADE80),
                    start = Offset(cx, cy),
                    end = Offset(targetCenterX, targetCenterY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
            }
        }

        // 4. Steering Vector Indicator (Bottom arc / arrow)
        val steerVal = trackerState.calculatedSteer
        if (abs(steerVal) > 10f) {
            val isSteeringRight = steerVal > 0
            val arrowY = h - 140.dp.toPx()
            val arrowStartX = cx
            val steerFraction = (steerVal / trackerState.settings.maxSteer).coerceIn(-1f, 1f)
            val arrowEndX = cx + (steerFraction * (w * 0.35f))

            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(arrowStartX, arrowY),
                end = Offset(arrowEndX, arrowY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            // Arrow head
            val arrowHeadSize = 14.dp.toPx()
            val dirSign = if (isSteeringRight) 1f else -1f
            val headPath = Path().apply {
                moveTo(arrowEndX, arrowY)
                lineTo(arrowEndX - (dirSign * arrowHeadSize), arrowY - (arrowHeadSize * 0.6f))
                lineTo(arrowEndX - (dirSign * arrowHeadSize), arrowY + (arrowHeadSize * 0.6f))
                close()
            }
            drawPath(headPath, Color(0xFF38BDF8))
        }

        // 5. Distance Category indicator tick at bottom
        val distanceColor = when (trackerState.distanceCategory) {
            DistanceCategory.OPTIMAL -> Color(0xFF4ADE80)
            DistanceCategory.TOO_CLOSE -> Color(0xFFEF4444)
            DistanceCategory.TOO_FAR -> Color(0xFF38BDF8)
            DistanceCategory.UNKNOWN -> Color(0xFF64748B)
        }
        drawCircle(
            color = distanceColor,
            radius = 5.dp.toPx(),
            center = Offset(cx, h - 110.dp.toPx())
        )
    }
}

