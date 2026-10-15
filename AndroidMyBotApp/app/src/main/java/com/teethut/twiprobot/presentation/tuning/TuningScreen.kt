package com.teethut.twiprobot.presentation.tuning

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teethut.twiprobot.presentation.history.CreatePresetDialog
import com.teethut.twiprobot.theme.CardBackground

@Composable
fun TuningScreen(
    viewModel: TuningViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showSaveDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live PID & System Tuning",
                color = Color(0xFF38BDF8),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                )
            }
        }

        // Status Banner
        uiState.statusMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isError) Color(0xFF7F1D1D) else Color(0xFF14532D)
                )
            ) {
                Text(
                    text = msg,
                    color = if (uiState.isError) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Quick Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Default", "Soft", "Aggressive").forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.applyPreset(preset) },
                    label = { Text(preset, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF93C5FD)
                    )
                )
            }
        }

        // Section 1: Inner Loop (Balance Angle)
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
                Text(
                    text = "INNER LOOP (BALANCE ANGLE)",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PidField(
                        label = "Kp",
                        value = uiState.kp1Text,
                        step = 1.0f,
                        onValueChange = { viewModel.onKp1Changed(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Ki",
                        value = uiState.ki1Text,
                        step = 0.05f,
                        onValueChange = { viewModel.onKi1Changed(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Kd",
                        value = uiState.kd1Text,
                        step = 0.005f,
                        onValueChange = { viewModel.onKd1Changed(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 2: Outer Loop (Speed Control)
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
                Text(
                    text = "OUTER LOOP (SPEED CONTROL)",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PidField(
                        label = "Kp",
                        value = uiState.kp2Text,
                        step = 0.0005f,
                        onValueChange = { viewModel.onKp2Changed(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Ki",
                        value = uiState.ki2Text,
                        step = 0.0001f,
                        onValueChange = { viewModel.onKi2Changed(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Kd",
                        value = uiState.kd2Text,
                        step = 0.0001f,
                        onValueChange = { viewModel.onKd2Changed(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 3: System Configuration
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
                Text(
                    text = "SYSTEM CONFIGURATION",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PidField(
                        label = "Offset (°)",
                        value = uiState.angleOffsetText,
                        step = 1.0f,
                        onValueChange = { viewModel.onAngleOffsetChanged(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Max Accel",
                        value = uiState.maxAccelText,
                        step = 50f,
                        onValueChange = { viewModel.onMaxAccelChanged(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Max Spd",
                        value = uiState.maxStepsPerSecText,
                        step = 100f,
                        onValueChange = { viewModel.onMaxStepsPerSecChanged(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 4: Steering Tuning
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
                Text(
                    text = "STEERING TUNING",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PidField(
                        label = "Max Steer",
                        value = uiState.maxSteerText,
                        step = 100f,
                        onValueChange = { viewModel.onMaxSteerChanged(it) },
                        modifier = Modifier.weight(1f)
                    )
                    PidField(
                        label = "Steer Accel",
                        value = uiState.steerAccelText,
                        step = 500f,
                        onValueChange = { viewModel.onSteerAccelChanged(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action Buttons: Read Current, Save Profile & Update
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.loadSettings() },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Read",
                    modifier = Modifier.padding(end = 4.dp).size(16.dp)
                )
                Text("Read", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { showSaveDialog = true },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.1f)
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = "Save Profile",
                    modifier = Modifier.padding(end = 4.dp).size(16.dp)
                )
                Text("Save Profile", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { viewModel.applySettings() },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier.weight(1.3f)
            ) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = "Update",
                    modifier = Modifier.padding(end = 4.dp).size(16.dp)
                )
                Text("UPDATE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }

    if (showSaveDialog) {
        CreatePresetDialog(
            initialSettings = viewModel.getCurrentSettings(),
            onDismiss = { showSaveDialog = false },
            onSave = { name, surface, settings ->
                viewModel.savePreset(name, surface, settings)
                showSaveDialog = false
            }
        )
    }
}

@Composable
fun PidField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    step: Float = 0.1f,
    onValueChange: (String) -> Unit
) {
    fun adjust(delta: Float) {
        val current = value.toFloatOrNull() ?: 0f
        val updated = current + delta
        onValueChange(String.format(java.util.Locale.US, "%.4f", updated).trimEnd('0').trimEnd('.'))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155)
            )
        )

        // Micro-step adjustment buttons
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                    .clickable { adjust(-step) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                    .clickable { adjust(step) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
            }
        }
    }
}
