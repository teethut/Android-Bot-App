package com.teethut.twiprobot.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.theme.CardBackground
import com.teethut.twiprobot.theme.SurfaceElevated

@Composable
fun TelemetryCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = Color(0xFFE2E8F0)
) {
    val shape = RoundedCornerShape(12.dp)

    // Dynamically adjust font size based on digit count to prevent wrapping or overflow
    val valueFontSize = when {
        value.length >= 7 -> 11.5.sp
        value.length >= 5 -> 13.5.sp
        else -> 15.5.sp
    }

    Column(
        modifier = modifier
            .height(60.dp) // Enforce strictly uniform box height across all grid cells
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SurfaceElevated.copy(alpha = 0.85f),
                        CardBackground
                    )
                )
            )
            .border(
                width = 1.dp,
                color = highlightColor.copy(alpha = 0.25f),
                shape = shape
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            color = Color(0xFF94A3B8),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value,
                color = highlightColor,
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}
