package com.teethut.twiprobot.presentation.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.teethut.twiprobot.domain.model.DriveMode
import com.teethut.twiprobot.presentation.components.ConnectionBadge
import com.teethut.twiprobot.presentation.components.IsometricChassisVisualizer
import com.teethut.twiprobot.presentation.components.RobotStateBadge
import com.teethut.twiprobot.presentation.components.TelemetryCard
import com.teethut.twiprobot.presentation.components.TiltDriveControl
import com.teethut.twiprobot.presentation.components.VirtualJoystick
import com.teethut.twiprobot.theme.CardBackground
import kotlin.math.roundToInt

@Composable
fun DriveScreen(
    viewModel: DriveViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    var showAdvancedTuning by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .verticalScroll(scrollState, enabled = !uiState.isDriving)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF23354D), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionBadge(state = connectionState)
                    RobotStateBadge(
                        stateText = telemetry.state,
                        isBalancing = telemetry.isBalancing
                    )
                }

                val heapKb = (telemetry.freeHeapBytes / 1024).toInt()
                Text(
                    text = if (heapKb > 0) "Heap: ${heapKb}KB" else "Heap: --",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Isometric 3D Wireframe Chassis Tilt Visualizer (Taller Mast, 135dp)
        IsometricChassisVisualizer(
            pitchAngleDeg = telemetry.pitch,
            isBalancing = telemetry.isBalancing,
            height = 135.dp,
            gForce = telemetry.gForce
        )

        // Drive Mode Selector Chips (Joystick vs Phone Tilt)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = uiState.driveMode == DriveMode.JOYSTICK,
                    onClick = { viewModel.setDriveMode(DriveMode.JOYSTICK) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Joystick",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Virtual Joystick", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )

                FilterChip(
                    selected = uiState.driveMode == DriveMode.PHONE_TILT,
                    onClick = { viewModel.setDriveMode(DriveMode.PHONE_TILT) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Tilt Drive",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Phone Tilt Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        // Drive Control Cockpit Card (Hero Joystick Surface + Quick HUD)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF23354D), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Primary Control Surface (Immediate Thumb Reach, No Scrolling)
                if (uiState.driveMode == DriveMode.JOYSTICK) {
                    VirtualJoystick(
                        size = 205.dp,
                        onMove = { throttle, steer ->
                            viewModel.onJoystickMove(throttle, steer)
                        },
                        onRelease = {
                            viewModel.onDriveRelease()
                        }
                    )
                } else {
                    TiltDriveControl(
                        pitchDeg = uiState.phoneTilt.pitchDeg,
                        rollDeg = uiState.phoneTilt.rollDeg,
                        isDriving = uiState.isDriving,
                        onHoldStart = { viewModel.onTiltHoldStart() },
                        onHoldEnd = { viewModel.onDriveRelease() },
                        onRecenter = { viewModel.recenterTiltZero() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Live command speed readout
                val spdDisplay = uiState.calculatedSpeed.roundToInt()
                val strDisplay = uiState.calculatedSteer.roundToInt()
                Text(
                    text = "V: $spdDisplay · S: $strDisplay steps/s",
                    color = if (uiState.isDriving) Color(0xFF38BDF8) else Color(0xFF64748B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Emergency STOP Button (Safely Accessible)
                Button(
                    onClick = { viewModel.emergencyStop() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EMERGENCY STOP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Quick Sensitivity Presets & Sliders Disclosure
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed:",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        for (pct in listOf(25, 40, 75, 100)) {
                            val isSelected = uiState.driveSensitivityPercent == pct
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                                    .clickable { viewModel.onDriveSensitivityChanged(pct) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$pct%",
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Toggle Button for advanced sliders
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { showAdvancedTuning = !showAdvancedTuning }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showAdvancedTuning) Icons.Default.ExpandLess else Icons.Default.Tune,
                            contentDescription = "Sliders",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showAdvancedTuning) "Hide" else "Tune",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 5. Expandable Detailed Sliders & Invert Option
                AnimatedVisibility(visible = showAdvancedTuning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        // Drive Speed Scale slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Drive Speed Scale",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${uiState.driveSensitivityPercent}%",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Slider(
                            value = uiState.driveSensitivityPercent.toFloat(),
                            onValueChange = { viewModel.onDriveSensitivityChanged(it.roundToInt()) },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Steer Sensitivity slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Steer Sensitivity",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${uiState.steerSensitivityPercent}%",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Slider(
                            value = uiState.steerSensitivityPercent.toFloat(),
                            onValueChange = { viewModel.onSteerSensitivityChanged(it.roundToInt()) },
                            valueRange = 20f..250f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8),
                                inactiveTrackColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Invert Drive Direction toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Invert Direction (Fwd/Rev)",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Flip forward/reverse motor directions",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = uiState.invertDirection,
                                onCheckedChange = { viewModel.setInvertDirection(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF38BDF8),
                                    checkedTrackColor = Color(0xFF0369A1)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Quick Telemetry Overview Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Pitch Angle",
                value = String.format(java.util.Locale.US, "%.2f", telemetry.pitch),
                unit = "°",
                highlightColor = if (telemetry.isBalancing) Color(0xFF4ADE80) else Color(0xFFF87171),
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Target Setpoint",
                value = String.format(java.util.Locale.US, "%.2f", telemetry.setpoint),
                unit = "°",
                highlightColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Total Accel",
                value = String.format(java.util.Locale.US, "%.2f", telemetry.gForce),
                unit = "g",
                highlightColor = if (telemetry.gForce in 0.85f..1.15f) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Motor Output",
                value = String.format(java.util.Locale.US, "%.0f", telemetry.motorOut),
                unit = "steps/s",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Est. Speed",
                value = String.format(java.util.Locale.US, "%.0f", telemetry.speed),
                unit = "steps/s",
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Steer Offset",
                value = String.format(java.util.Locale.US, "%.0f", telemetry.steer),
                unit = "steps/s",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Left Encoder",
                value = telemetry.encLeft.toString(),
                unit = "cts",
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Right Encoder",
                value = telemetry.encRight.toString(),
                unit = "cts",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
