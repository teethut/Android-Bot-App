package com.teethut.twiprobot.presentation.tuning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teethut.twiprobot.data.local.entity.PidPresetEntity
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.domain.model.PidSettings
import com.teethut.twiprobot.domain.repository.PresetHistoryRepository
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class TuningUiState(
    val kp1Text: String = "40.0",
    val ki1Text: String = "0.1",
    val kd1Text: String = "0.0",
    val kp2Text: String = "-0.0015",
    val ki2Text: String = "-0.0000",
    val kd2Text: String = "0.00002",
    val angleOffsetText: String = "6.0",
    val maxAccelText: String = "40000.0",
    val maxStepsPerSecText: String = "20000.0",
    val maxSteerText: String = "2500.0",
    val steerAccelText: String = "8000.0",
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

class TuningViewModel(
    private val repository: RobotRepository = RobotRepositoryImpl.instance,
    private val historyRepository: PresetHistoryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TuningUiState())
    val uiState: StateFlow<TuningUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun onKp1Changed(v: String) { _uiState.value = _uiState.value.copy(kp1Text = v) }
    fun onKi1Changed(v: String) { _uiState.value = _uiState.value.copy(ki1Text = v) }
    fun onKd1Changed(v: String) { _uiState.value = _uiState.value.copy(kd1Text = v) }

    fun onKp2Changed(v: String) { _uiState.value = _uiState.value.copy(kp2Text = v) }
    fun onKi2Changed(v: String) { _uiState.value = _uiState.value.copy(ki2Text = v) }
    fun onKd2Changed(v: String) { _uiState.value = _uiState.value.copy(kd2Text = v) }

    fun onAngleOffsetChanged(v: String) { _uiState.value = _uiState.value.copy(angleOffsetText = v) }
    fun onMaxAccelChanged(v: String) { _uiState.value = _uiState.value.copy(maxAccelText = v) }
    fun onMaxStepsPerSecChanged(v: String) { _uiState.value = _uiState.value.copy(maxStepsPerSecText = v) }
    fun onMaxSteerChanged(v: String) { _uiState.value = _uiState.value.copy(maxSteerText = v) }
    fun onSteerAccelChanged(v: String) { _uiState.value = _uiState.value.copy(steerAccelText = v) }

    fun loadSettings(): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                statusMessage = "Fetching settings from robot...",
                isError = false
            )

            val result = repository.fetchPidSettings()
            result.onSuccess { settings ->
                _uiState.value = _uiState.value.copy(
                    kp1Text = String.format(Locale.US, "%.4f", settings.kp1),
                    ki1Text = String.format(Locale.US, "%.4f", settings.ki1),
                    kd1Text = String.format(Locale.US, "%.4f", settings.kd1),
                    kp2Text = String.format(Locale.US, "%.5f", settings.kp2),
                    ki2Text = String.format(Locale.US, "%.5f", settings.ki2),
                    kd2Text = String.format(Locale.US, "%.5f", settings.kd2),
                    angleOffsetText = String.format(Locale.US, "%.2f", settings.angleOffset),
                    maxAccelText = String.format(Locale.US, "%.1f", settings.maxAccel),
                    maxStepsPerSecText = String.format(Locale.US, "%.1f", settings.maxStepsPerSec),
                    maxSteerText = String.format(Locale.US, "%.1f", settings.maxSteer),
                    steerAccelText = String.format(Locale.US, "%.1f", settings.steerAccel),
                    isLoading = false,
                    statusMessage = "Settings loaded successfully.",
                    isError = false
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Failed to fetch: ${err.message}",
                    isError = true
                )
            }
        }
    }

    fun applySettings(): kotlinx.coroutines.Job {
        val s = _uiState.value
        val kp1 = s.kp1Text.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val ki1 = s.ki1Text.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val kd1 = s.kd1Text.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val kp2 = s.kp2Text.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val ki2 = s.ki2Text.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val kd2 = s.kd2Text.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val ao = s.angleOffsetText.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val ma = s.maxAccelText.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val msps = s.maxStepsPerSecText.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val mstr = s.maxSteerText.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }
        val sta = s.steerAccelText.toFloatOrNull() ?: return kotlinx.coroutines.Job().apply { complete() }

        val settings = PidSettings(
            kp1 = kp1, ki1 = ki1, kd1 = kd1,
            kp2 = kp2, ki2 = ki2, kd2 = kd2,
            angleOffset = ao, maxAccel = ma, maxStepsPerSec = msps,
            maxSteer = mstr, steerAccel = sta
        )

        return viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                statusMessage = "Updating parameters in RAM...",
                isError = false
            )

            val result = repository.updatePidSettings(settings)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Parameters updated successfully in robot RAM!",
                    isError = false
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Update failed: ${err.message}",
                    isError = true
                )
            }
        }
    }

    fun applyPreset(preset: String) {
        when (preset) {
            "Default" -> {
                _uiState.value = _uiState.value.copy(
                    kp1Text = "40.0", ki1Text = "0.1", kd1Text = "0.0",
                    kp2Text = "-0.0015", ki2Text = "-0.0000", kd2Text = "0.00002",
                    angleOffsetText = "6.0", maxAccelText = "40000.0", maxStepsPerSecText = "20000.0",
                    maxSteerText = "2500.0", steerAccelText = "8000.0",
                    statusMessage = "Default preset applied to inputs. Tap UPDATE to send."
                )
            }
            "Soft" -> {
                _uiState.value = _uiState.value.copy(
                    kp1Text = "32.0", ki1Text = "0.08", kd1Text = "0.0",
                    kp2Text = "-0.0010", ki2Text = "-0.0000", kd2Text = "0.00001",
                    angleOffsetText = "6.0", maxAccelText = "25000.0", maxStepsPerSecText = "12000.0",
                    maxSteerText = "1500.0", steerAccelText = "4000.0",
                    statusMessage = "Soft dampening preset applied. Tap UPDATE to send."
                )
            }
            "Aggressive" -> {
                _uiState.value = _uiState.value.copy(
                    kp1Text = "48.0", ki1Text = "0.15", kd1Text = "0.02",
                    kp2Text = "-0.0022", ki2Text = "-0.0000", kd2Text = "0.00003",
                    angleOffsetText = "6.0", maxAccelText = "50000.0", maxStepsPerSecText = "25000.0",
                    maxSteerText = "3000.0", steerAccelText = "10000.0",
                    statusMessage = "Aggressive response preset applied. Tap UPDATE to send."
                )
            }
        }
    }

    fun getCurrentSettings(): PidSettings {
        val s = _uiState.value
        return PidSettings(
            kp1 = s.kp1Text.toFloatOrNull() ?: 40.0f,
            ki1 = s.ki1Text.toFloatOrNull() ?: 0.1f,
            kd1 = s.kd1Text.toFloatOrNull() ?: 0.0f,
            kp2 = s.kp2Text.toFloatOrNull() ?: -0.0015f,
            ki2 = s.ki2Text.toFloatOrNull() ?: 0.0f,
            kd2 = s.kd2Text.toFloatOrNull() ?: 0.00002f,
            angleOffset = s.angleOffsetText.toFloatOrNull() ?: 6.0f,
            maxAccel = s.maxAccelText.toFloatOrNull() ?: 40000.0f,
            maxStepsPerSec = s.maxStepsPerSecText.toFloatOrNull() ?: 20000.0f,
            maxSteer = s.maxSteerText.toFloatOrNull() ?: 2500.0f,
            steerAccel = s.steerAccelText.toFloatOrNull() ?: 8000.0f
        )
    }

    fun savePreset(
        name: String,
        surfaceType: String,
        settings: PidSettings = getCurrentSettings()
    ): kotlinx.coroutines.Job? {
        val historyRepo = historyRepository ?: return null
        return viewModelScope.launch {
            val entity = PidPresetEntity.fromPidSettings(name, surfaceType, settings)
            historyRepo.savePreset(entity)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Profile '$name' saved to database!",
                isError = false
            )
        }
    }
}

