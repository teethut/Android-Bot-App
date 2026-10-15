package com.teethut.twiprobot.presentation.telemetry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiOff
import com.teethut.twiprobot.domain.model.ConnectionState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.presentation.components.TelemetryCard
import com.teethut.twiprobot.presentation.components.WaveformChart
import com.teethut.twiprobot.theme.CardBackground

@Composable
fun TelemetryScreen(
    viewModel: TelemetryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.currentTelemetry.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Offline Warning Banner
        if (connectionState !is ConnectionState.Connected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Robot Disconnected (Offline)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Charts will populate when connected to Wi-Fi 'TeethutBOT_AP' (192.168.4.1).",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Toolbar: Controls & Chart State
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Waveform Scopes",
                color = Color(0xFF38BDF8),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.togglePause() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (uiState.isPaused) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isPaused) "Resume" else "Pause",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { viewModel.clearCharts() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Telemetry Session Recorder & CSV Export Card
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            )
                        }
                        Text(
                            text = if (uiState.isRecording) "RECORDING SESSION" else "SESSION RECORDER",
                            color = if (uiState.isRecording) Color(0xFFF87171) else Color(0xFF93C5FD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val mins = uiState.recordingDurationSeconds / 60
                    val secs = uiState.recordingDurationSeconds % 60
                    Text(
                        text = String.format(java.util.Locale.US, "%02d:%02d · %d pts", mins, secs, uiState.recordedEntriesCount),
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!uiState.isRecording) {
                        Button(
                            onClick = { viewModel.startRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Record",
                                modifier = Modifier.size(18.dp).padding(end = 4.dp)
                            )
                            Text("Start Record", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp).padding(end = 4.dp)
                            )
                            Text("Stop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            val intent = viewModel.exportCsv(context)
                            intent?.let { context.startActivity(it) }
                        },
                        enabled = uiState.recordedEntriesCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export CSV",
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text("Export CSV", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Telemetry Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Pitch",
                value = String.format(java.util.Locale.US, "%.2f", telemetry.pitch),
                unit = "°",
                highlightColor = if (telemetry.isBalancing) Color(0xFF4ADE80) else Color(0xFFF87171),
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Setpoint",
                value = String.format(java.util.Locale.US, "%.2f", telemetry.setpoint),
                unit = "°",
                highlightColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Offset",
                value = String.format(java.util.Locale.US, "%.2f", telemetry.offset),
                unit = "°",
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
                label = "Steer Offset",
                value = String.format(java.util.Locale.US, "%.0f", telemetry.steer),
                unit = "steps/s",
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Motor Out",
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
                label = "Tgt Speed",
                value = String.format(java.util.Locale.US, "%.0f", telemetry.targetSpeed),
                unit = "steps/s",
                modifier = Modifier.weight(1f)
            )
            val heapKb = (telemetry.freeHeapBytes / 1024).toInt()
            TelemetryCard(
                label = "Free Heap",
                value = if (heapKb > 0) heapKb.toString() else "--",
                unit = "KB",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Encoder L",
                value = telemetry.encLeft.toString(),
                unit = "cts",
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Encoder R",
                value = telemetry.encRight.toString(),
                unit = "cts",
                modifier = Modifier.weight(1f)
            )
        }

        // Live Oscilloscope Charts
        WaveformChart(
            title = "Balance Angle & Setpoint",
            series1Name = "Pitch",
            series1Data = uiState.pitchHistory,
            series1Color = Color(0xFF38BDF8),
            series2Name = "Setpoint",
            series2Data = uiState.setpointHistory,
            series2Color = Color(0xFFFBBF24),
            unit = "°"
        )

        WaveformChart(
            title = "Total Acceleration (g)",
            series1Name = "G-Force",
            series1Data = uiState.gForceHistory,
            series1Color = Color(0xFF38BDF8),
            fixedMin = 0f,
            fixedMax = 2.5f,
            unit = " g"
        )

        WaveformChart(
            title = "Speed & Target",
            series1Name = "Speed",
            series1Data = uiState.speedHistory,
            series1Color = Color(0xFF4ADE80),
            series2Name = "Target",
            series2Data = uiState.targetSpeedHistory,
            series2Color = Color(0xFFA78BFA),
            unit = " steps/s"
        )

        WaveformChart(
            title = "Motor Output",
            series1Name = "MotorOut",
            series1Data = uiState.motorOutHistory,
            series1Color = Color(0xFFF87171),
            unit = " steps/s"
        )

        WaveformChart(
            title = "Virtual Encoders",
            series1Name = "Enc L",
            series1Data = uiState.encLHistory,
            series1Color = Color(0xFF38BDF8),
            series2Name = "Enc R",
            series2Data = uiState.encRHistory,
            series2Color = Color(0xFFFBBF24),
            unit = " cts"
        )
    }
}
