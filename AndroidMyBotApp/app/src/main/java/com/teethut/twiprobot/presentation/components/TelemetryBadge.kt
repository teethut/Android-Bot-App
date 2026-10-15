package com.teethut.twiprobot.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.domain.model.ConnectionState

@Composable
fun StatusBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    showPulsingDot: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
            .border(width = 1.dp, color = textColor.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showPulsingDot) {
                Box(
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .size(6.dp)
                        .alpha(dotAlpha)
                        .background(color = textColor, shape = CircleShape)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
fun ConnectionBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    when (state) {
        is ConnectionState.Connected -> {
            StatusBadge(
                text = "ONLINE",
                backgroundColor = Color(0xFF14532D),
                textColor = Color(0xFF4ADE80),
                modifier = modifier,
                showPulsingDot = true
            )
        }
        is ConnectionState.Connecting -> {
            StatusBadge(
                text = "CONNECTING",
                backgroundColor = Color(0xFF78350F),
                textColor = Color(0xFFFBBF24),
                modifier = modifier,
                showPulsingDot = true
            )
        }
        is ConnectionState.Disconnected -> {
            StatusBadge(
                text = "OFFLINE",
                backgroundColor = Color(0xFF7F1D1D),
                textColor = Color(0xFFF87171),
                modifier = modifier,
                showPulsingDot = false
            )
        }
        is ConnectionState.Error -> {
            StatusBadge(
                text = "ERROR",
                backgroundColor = Color(0xFF7F1D1D),
                textColor = Color(0xFFF87171),
                modifier = modifier,
                showPulsingDot = true
            )
        }
    }
}

@Composable
fun RobotStateBadge(
    stateText: String,
    isBalancing: Boolean,
    modifier: Modifier = Modifier
) {
    val (bg, text) = if (isBalancing) {
        Color(0xFF166534) to Color(0xFF4ADE80)
    } else {
        Color(0xFF991B1B) to Color(0xFFF87171)
    }

    StatusBadge(
        text = stateText.ifEmpty { "UNKNOWN" },
        backgroundColor = bg,
        textColor = text,
        modifier = modifier,
        showPulsingDot = isBalancing
    )
}
