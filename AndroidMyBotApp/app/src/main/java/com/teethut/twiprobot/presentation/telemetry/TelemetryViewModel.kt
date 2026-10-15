package com.teethut.twiprobot.presentation.telemetry

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teethut.twiprobot.data.logging.TelemetryCsvExporter
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.model.TelemetryLogEntry
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScopesUiState(
    val isPaused: Boolean = false,
    val pitchHistory: List<Float> = emptyList(),
    val setpointHistory: List<Float> = emptyList(),
    val gForceHistory: List<Float> = emptyList(),
    val speedHistory: List<Float> = emptyList(),
    val targetSpeedHistory: List<Float> = emptyList(),
    val motorOutHistory: List<Float> = emptyList(),
    val steerHistory: List<Float> = emptyList(),
    val encLHistory: List<Float> = emptyList(),
    val encRHistory: List<Float> = emptyList(),
    // Session Recorder
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val recordedEntriesCount: Int = 0
)

class TelemetryViewModel(
    private val repository: RobotRepository = RobotRepositoryImpl.instance
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val currentTelemetry: StateFlow<RobotTelemetry> = repository.currentTelemetry

    private val _uiState = MutableStateFlow(ScopesUiState())
    val uiState: StateFlow<ScopesUiState> = _uiState.asStateFlow()

    private val maxPoints = 80
    private val sessionLogs = mutableListOf<TelemetryLogEntry>()
    private var recordingTimerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.telemetryFlow.collect { t ->
                if (!_uiState.value.isPaused) {
                    appendTelemetry(t)
                }
                if (_uiState.value.isRecording) {
                    sessionLogs.add(TelemetryLogEntry.fromTelemetry(t))
                    _uiState.value = _uiState.value.copy(
                        recordedEntriesCount = sessionLogs.size
                    )
                }
            }
        }
    }

    private fun appendTelemetry(t: RobotTelemetry) {
        val s = _uiState.value
        _uiState.value = s.copy(
            pitchHistory = (s.pitchHistory + t.pitch).takeLast(maxPoints),
            setpointHistory = (s.setpointHistory + t.setpoint).takeLast(maxPoints),
            gForceHistory = (s.gForceHistory + t.gForce).takeLast(maxPoints),
            speedHistory = (s.speedHistory + t.speed).takeLast(maxPoints),
            targetSpeedHistory = (s.targetSpeedHistory + t.targetSpeed).takeLast(maxPoints),
            motorOutHistory = (s.motorOutHistory + t.motorOut).takeLast(maxPoints),
            steerHistory = (s.steerHistory + t.steer).takeLast(maxPoints),
            encLHistory = (s.encLHistory + t.encLeft.toFloat()).takeLast(maxPoints),
            encRHistory = (s.encRHistory + t.encRight.toFloat()).takeLast(maxPoints)
        )
    }

    fun togglePause() {
        _uiState.value = _uiState.value.copy(isPaused = !_uiState.value.isPaused)
    }

    fun clearCharts() {
        _uiState.value = _uiState.value.copy(
            pitchHistory = emptyList(),
            setpointHistory = emptyList(),
            gForceHistory = emptyList(),
            speedHistory = emptyList(),
            targetSpeedHistory = emptyList(),
            motorOutHistory = emptyList(),
            steerHistory = emptyList(),
            encLHistory = emptyList(),
            encRHistory = emptyList()
        )
    }

    fun startRecording() {
        sessionLogs.clear()
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            recordingDurationSeconds = 0,
            recordedEntriesCount = 0
        )

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRecording) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    recordingDurationSeconds = _uiState.value.recordingDurationSeconds + 1
                )
            }
        }
    }

    fun stopRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _uiState.value = _uiState.value.copy(isRecording = false)
    }

    fun clearRecordedSession() {
        stopRecording()
        sessionLogs.clear()
        _uiState.value = _uiState.value.copy(
            recordingDurationSeconds = 0,
            recordedEntriesCount = 0
        )
    }

    fun exportCsv(context: Context): Intent? {
        if (sessionLogs.isEmpty()) return null
        return TelemetryCsvExporter.exportToShareIntent(context, sessionLogs)
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
    }
}
