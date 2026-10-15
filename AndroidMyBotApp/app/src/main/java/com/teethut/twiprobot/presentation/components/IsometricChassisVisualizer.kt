package com.teethut.twiprobot.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.theme.CardBackground
import com.teethut.twiprobot.theme.SurfaceDark
import kotlin.math.cos
import kotlin.math.sin

data class Point3D(val x: Float, val y: Float, val z: Float)

@Composable
fun IsometricChassisVisualizer(
    pitchAngleDeg: Float,
    isBalancing: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 195.dp,
    gForce: Float? = null,
    tiltMultiplier: Float = 2.0f
) {
    val animatedPitch by animateFloatAsState(
        targetValue = pitchAngleDeg,
        animationSpec = spring(stiffness = 800f),
        label = "pitchSpring"
    )

    val accentColor = if (isBalancing) Color(0xFF38BDF8) else Color(0xFFF87171)
    val glowColor = if (isBalancing) Color(0x3338BDF8) else Color(0x33F87171)
    val containerShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(containerShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CardBackground,
                        SurfaceDark
                    )
                )
            )
            .border(1.dp, Color(0xFF2A3D58), containerShape)
            .padding(10.dp)
    ) {
        // Top status overlay: Pitch on Left, Title on Right (Center completely clear for 3D robot)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pitch Angle Overlay (Top-Left)
            Box(
                modifier = Modifier
                    .background(Color(0xE60F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "PITCH: ${String.format(java.util.Locale.US, "%+.1f°", animatedPitch)}",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Title in top-right
            Text(
                text = "3D CHASSIS ATTITUDE",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(4.dp)
            )
        }

        // Bottom status overlay: G-Force in Bottom-Left (clear of 3D chassis)
        if (gForce != null) {
            val gColor = if (gForce in 0.85f..1.15f) Color(0xFF4ADE80) else Color(0xFFFBBF24)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xE60F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, gColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "G: ${String.format(java.util.Locale.US, "%.2f", gForce)}g",
                    color = gColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val cx = size.width / 2f
            val cy = size.height / 2f + 32f // Base slightly below center for taller mast

            // Ambient background radial glow in center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.width * 0.35f
                ),
                radius = size.width * 0.35f,
                center = Offset(cx, cy)
            )

            // Isometric projection formula:
            val cos30 = 0.866025f
            val sin30 = 0.5f

            fun project(p: Point3D): Offset {
                val sx = cx + (p.x - p.z) * cos30
                val sy = cy - p.y + (p.x + p.z) * sin30
                return Offset(sx, sy)
            }

            // Draw Ground Plane Grid
            val groundGrid = 90f
            val gridStep = 30f
            val groundColor = Color(0x28475569)
            var gx = -groundGrid
            while (gx <= groundGrid) {
                drawLine(
                    color = groundColor,
                    start = project(Point3D(gx, 0f, -groundGrid)),
                    end = project(Point3D(gx, 0f, groundGrid)),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = groundColor,
                    start = project(Point3D(-groundGrid, 0f, gx)),
                    end = project(Point3D(groundGrid, 0f, gx)),
                    strokeWidth = 1.dp.toPx()
                )
                gx += gridStep
            }

            // Wheel Axle Constants (Axle lies along X axis)
            val axleHalfWidth = 46f
            val wheelRadius = 26f
            val chassisHeight = 92f // Taller mast so lean/tilt is clearly visible
            val chassisWidth = 26f
            val chassisDepth = 18f

            // Pitch rotation matrix around X axis (Wheel axle):
            // Visual tilt is exaggerated by tiltMultiplier (default 2x) so lean is clearly seen
            val visualPitch = animatedPitch * tiltMultiplier
            val rad = Math.toRadians(-visualPitch.toDouble()).toFloat()
            val cosR = cos(rad)
            val sinR = sin(rad)

            fun rotateChassis(localX: Float, localY: Float, localZ: Float): Point3D {
                val rotY = localY * cosR - localZ * sinR
                val rotZ = localY * sinR + localZ * cosR
                return Point3D(localX, wheelRadius + rotY, rotZ)
            }

            // Draw Axle Line (between left and right wheel centers)
            val leftWheelCenter = Point3D(-axleHalfWidth, wheelRadius, 0f)
            val rightWheelCenter = Point3D(axleHalfWidth, wheelRadius, 0f)

            drawLine(
                color = Color(0xFF64748B),
                start = project(leftWheelCenter),
                end = project(rightWheelCenter),
                strokeWidth = 2.5.dp.toPx()
            )

            // Draw Left & Right Wheels (circles in Y-Z plane)
            fun drawWheel(center: Point3D) {
                val segments = 20
                val path = Path()
                for (i in 0..segments) {
                    val theta = (i * 2 * Math.PI / segments).toFloat()
                    val wy = center.y + wheelRadius * sin(theta)
                    val wz = center.z + wheelRadius * cos(theta)
                    val pt = project(Point3D(center.x, wy, wz))
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 1.75.dp.toPx()))

                // Wheel hub & spoke
                val hubPt = project(center)
                drawCircle(color = accentColor.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = hubPt)
                drawCircle(color = accentColor, radius = 3.dp.toPx(), center = hubPt)
            }

            drawWheel(leftWheelCenter)
            drawWheel(rightWheelCenter)

            // 3D Chassis Box Vertices:
            val p0 = rotateChassis(-chassisWidth, 6f, -chassisDepth)
            val p1 = rotateChassis(chassisWidth, 6f, -chassisDepth)
            val p2 = rotateChassis(chassisWidth, 6f, chassisDepth)
            val p3 = rotateChassis(-chassisWidth, 6f, chassisDepth)

            val p4 = rotateChassis(-chassisWidth, chassisHeight, -chassisDepth)
            val p5 = rotateChassis(chassisWidth, chassisHeight, -chassisDepth)
            val p6 = rotateChassis(chassisWidth, chassisHeight, chassisDepth)
            val p7 = rotateChassis(-chassisWidth, chassisHeight, chassisDepth)

            val proj0 = project(p0); val proj1 = project(p1); val proj2 = project(p2); val proj3 = project(p3)
            val proj4 = project(p4); val proj5 = project(p5); val proj6 = project(p6); val proj7 = project(p7)

            // Draw Chassis Standoff Pillars
            fun drawWireEdge(from: Offset, to: Offset, color: Color, widthDp: Float = 1.5f) {
                drawLine(
                    color = color,
                    start = from,
                    end = to,
                    strokeWidth = widthDp.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Lower Plate Base
            drawWireEdge(proj0, proj1, Color(0xFF64748B))
            drawWireEdge(proj1, proj2, Color(0xFF64748B))
            drawWireEdge(proj2, proj3, Color(0xFF64748B))
            drawWireEdge(proj3, proj0, Color(0xFF64748B))

            // Vertical Standoff Pillars
            drawWireEdge(proj0, proj4, accentColor, 2f)
            drawWireEdge(proj1, proj5, accentColor, 2f)
            drawWireEdge(proj2, proj6, accentColor, 2f)
            drawWireEdge(proj3, proj7, accentColor, 2f)

            // Upper Controller Plate
            drawWireEdge(proj4, proj5, accentColor, 2.5f)
            drawWireEdge(proj5, proj6, accentColor, 2.5f)
            drawWireEdge(proj6, proj7, accentColor, 2.5f)
            drawWireEdge(proj7, proj4, accentColor, 2.5f)

            // Top Crossbar & Status Beacon (IMU/Brain Indicator)
            val topCenter = rotateChassis(0f, chassisHeight + 4f, 0f)
            val topCenterPt = project(topCenter)
            drawCircle(color = glowColor, radius = 10.dp.toPx(), center = topCenterPt)
            drawCircle(color = accentColor, radius = 4.5.dp.toPx(), center = topCenterPt)
        }
    }
}
