package com.teethut.twiprobot.presentation.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teethut.twiprobot.data.local.entity.PidPresetEntity
import com.teethut.twiprobot.data.local.entity.TelemetrySessionEntity
import com.teethut.twiprobot.data.repository.PresetHistoryRepositoryImpl
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.domain.model.PidSettings
import com.teethut.twiprobot.domain.repository.PresetHistoryRepository
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

data class HistoryUiState(
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApplying: Boolean = false
)

class HistoryViewModel(
    private val historyRepository: PresetHistoryRepository,
    private val robotRepository: RobotRepository = RobotRepositoryImpl.instance
) : ViewModel() {

    val presets: StateFlow<List<PidPresetEntity>> = historyRepository.presetsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<TelemetrySessionEntity>> = historyRepository.sessionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activePresetId = MutableStateFlow<Long?>(null)
    val activePresetId: StateFlow<Long?> = _activePresetId.asStateFlow()

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun applyPresetToRobot(preset: PidPresetEntity): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            _activePresetId.value = preset.id
            _uiState.value = _uiState.value.copy(
                isApplying = true,
                statusMessage = "Applying '${preset.name}' to robot RAM...",
                isError = false
            )

            val result = robotRepository.updatePidSettings(preset.toPidSettings())
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isApplying = false,
                    statusMessage = "Successfully applied '${preset.name}' to robot!",
                    isError = false
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isApplying = false,
                    statusMessage = "Applied '${preset.name}' locally (Robot offline: ${err.message})",
                    isError = false
                )
            }
        }
    }

    fun duplicatePreset(preset: PidPresetEntity): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            val copyEntity = preset.copy(
                id = 0L,
                name = "${preset.name} (Copy)",
                createdAt = System.currentTimeMillis()
            )
            historyRepository.savePreset(copyEntity)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Cloned '${copyEntity.name}'.",
                isError = false
            )
        }
    }

    fun getActiveRobotSettings(): PidSettings {
        val activeId = _activePresetId.value
        val active = presets.value.find { it.id == activeId }
        return active?.toPidSettings() ?: PidSettings()
    }

    fun saveNewPreset(name: String, surfaceType: String, settings: PidSettings): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            val entity = PidPresetEntity.fromPidSettings(name, surfaceType, settings)
            historyRepository.savePreset(entity)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Preset '$name' saved to database.",
                isError = false
            )
        }
    }

    fun updatePreset(preset: PidPresetEntity): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            val updated = historyRepository.updatePreset(preset)
            if (updated) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Profile '${preset.name}' updated.",
                    isError = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to update profile '${preset.name}'.",
                    isError = true
                )
            }
        }
    }

    fun deletePreset(id: Long): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            historyRepository.deletePreset(id)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Preset deleted.",
                isError = false
            )
        }
    }

    fun saveTelemetrySession(name: String, durationSec: Int, sampleCount: Int, maxSpeed: Float, csvData: String): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            val entity = TelemetrySessionEntity(
                sessionName = name,
                durationSeconds = durationSec,
                sampleCount = sampleCount,
                maxSpeed = maxSpeed,
                csvData = csvData
            )
            historyRepository.saveSession(entity)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Telemetry session '$name' saved to history.",
                isError = false
            )
        }
    }

    fun deleteSession(id: Long): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            historyRepository.deleteSession(id)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Session run deleted.",
                isError = false
            )
        }
    }

    fun exportSavedSessionCsv(context: Context, session: TelemetrySessionEntity): Intent? {
        try {
            val filename = "${session.sessionName.replace(" ", "_")}_${session.startedAtMs}.csv"
            val file = File(context.cacheDir, filename)
            FileWriter(file).use { it.write(session.csvData) }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "TWIP Telemetry Session: ${session.sessionName}")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(shareIntent, "Share Session CSV")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    companion object {
        fun createWithContext(context: Context): HistoryViewModel {
            return HistoryViewModel(
                historyRepository = PresetHistoryRepositoryImpl.getInstance(context)
            )
        }
    }
}

