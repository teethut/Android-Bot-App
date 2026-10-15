package com.teethut.twiprobot.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.theme.CardBackground
import com.teethut.twiprobot.theme.SurfaceDark
import kotlin.math.max
import kotlin.math.min

@Composable
fun WaveformChart(
    title: String,
    series1Name: String,
    series1Data: List<Float>,
    series1Color: Color = Color(0xFF38BDF8),
    series2Name: String? = null,
    series2Data: List<Float>? = null,
    series2Color: Color = Color(0xFFFBBF24),
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    unit: String = ""
) {
    val containerShape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(CardBackground, SurfaceDark)
                )
            )
            .border(width = 1.dp, color = Color(0xFF2A3D58), shape = containerShape)
            .padding(12.dp)
    ) {
        // Chart Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFF93C5FD),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Legend & Latest Value
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val latest1 = series1Data.lastOrNull() ?: 0f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Canvas(modifier = Modifier.padding(top = 1.dp)) {
                        drawCircle(color = series1Color, radius = 3.5.dp.toPx())
                    }
                    Text(
                        text = "$series1Name: ${String.format(java.util.Locale.US, "%.1f", latest1)}$unit",
                        color = series1Color,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (series2Name != null && series2Data != null) {
                    val latest2 = series2Data.lastOrNull() ?: 0f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Canvas(modifier = Modifier.padding(top = 1.dp)) {
                            drawCircle(color = series2Color, radius = 3.5.dp.toPx())
                        }
                        Text(
                            text = "$series2Name: ${String.format(java.util.Locale.US, "%.1f", latest2)}$unit",
                            color = series2Color,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Oscilloscope Canvas Plot
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(top = 8.dp)
        ) {
            val width = size.width
            val chartHeight = size.height

            // Calculate auto or fixed bounds
            var minY = fixedMin ?: Float.MAX_VALUE
            var maxY = fixedMax ?: Float.MIN_VALUE

            if (fixedMin == null || fixedMax == null) {
                if (series1Data.isNotEmpty()) {
                    minY = min(minY, series1Data.minOrNull() ?: 0f)
                    maxY = max(maxY, series1Data.maxOrNull() ?: 0f)
                }
                if (series2Data != null && series2Data.isNotEmpty()) {
                    minY = min(minY, series2Data.minOrNull() ?: 0f)
                    maxY = max(maxY, series2Data.maxOrNull() ?: 0f)
                }
                if (minY == Float.MAX_VALUE) minY = -10f
                if (maxY == Float.MIN_VALUE) maxY = 10f
                if (maxY - minY < 1f) {
                    maxY += 0.5f
                    minY -= 0.5f
                }
                val padding = (maxY - minY) * 0.1f
                minY -= padding
                maxY += padding
            }

            // Draw Background Grid Lines
            val gridColor = Color(0x1F475569)
            val gridCountY = 4
            for (i in 0..gridCountY) {
                val y = chartHeight * i / gridCountY
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val gridCountX = 6
            for (i in 0..gridCountX) {
                val x = width * i / gridCountX
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Zero line if in range (with subtle dash)
            if (minY < 0f && maxY > 0f) {
                val zeroY = chartHeight * (1f - (0f - minY) / (maxY - minY))
                drawLine(
                    color = Color(0x5594A3B8),
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            fun plotSeries(data: List<Float>, color: Color, fillGradient: Boolean) {
                if (data.size < 2) return
                val path = Path()
                val fillPath = Path()
                val stepX = width / (data.size - 1)

                fillPath.moveTo(0f, chartHeight)
                data.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normY = (value.coerceIn(minY, maxY) - minY) / (maxY - minY)
                    val y = chartHeight * (1f - normY)
                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(width, chartHeight)
                fillPath.close()

                if (fillGradient) {
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            plotSeries(series1Data, series1Color, fillGradient = true)
            if (series2Data != null) {
                plotSeries(series2Data, series2Color, fillGradient = false)
            }
        }
    }
}
