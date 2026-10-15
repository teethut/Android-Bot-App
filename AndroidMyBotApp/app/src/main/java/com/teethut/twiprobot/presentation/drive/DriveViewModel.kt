package com.teethut.twiprobot.presentation.drive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teethut.twiprobot.data.haptic.HapticManager
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.data.sensor.PhoneOrientationSensor
import com.teethut.twiprobot.data.sensor.PhoneTiltData
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.DriveMode
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

data class DriveUiState(
    val driveMode: DriveMode = DriveMode.JOYSTICK,
    val throttleNorm: Float = 0f,
    val steerNorm: Float = 0f,
    val driveSensitivityPercent: Int = 40,
    val steerSensitivityPercent: Int = 100,
    val calculatedSpeed: Float = 0f,
    val calculatedSteer: Float = 0f,
    val isDriving: Boolean = false,
    val phoneTilt: PhoneTiltData = PhoneTiltData(),
    val maxRawSteer: Float = 2500f,
    val invertDirection: Boolean = false
) {
    // Backwards compatibility property
    val sensitivityPercent: Int get() = driveSensitivityPercent
}

class DriveViewModel(
    private val repository: RobotRepository = RobotRepositoryImpl.instance,
    private val phoneOrientationSensor: PhoneOrientationSensor? = null,
    private val hapticManager: HapticManager? = null
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val telemetry: StateFlow<RobotTelemetry> = repository.currentTelemetry

    private val _uiState = MutableStateFlow(DriveUiState())
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private var driveLoopJob: Job? = null
    private var tiltCollectorJob: Job? = null
    private var wasBalancing: Boolean = false

    // Robot calibration limits (matching robot firmware)
    private val maxRawSpeed = 3000f

    init {
        // Monitor Fall Alarm
        viewModelScope.launch {
            repository.telemetryFlow.collect { t ->
                if (wasBalancing && !t.isBalancing && t.state == "FALLEN") {
                    hapticManager?.triggerFallAlert()
                }
                wasBalancing = t.isBalancing
            }
        }

        // Synchronize live maxRawSteer parameter from robot PID settings if available
        viewModelScope.launch {
            val result = repository.fetchPidSettings()
            result.onSuccess { settings ->
                if (settings.maxSteer > 0f) {
                    updateMaxRawSteer(settings.maxSteer)
                }
            }
        }
    }

    fun updateMaxRawSteer(maxSteer: Float) {
        _uiState.value = _uiState.value.copy(maxRawSteer = maxSteer)
        recalculateCommands()
    }

    fun setDriveMode(mode: DriveMode) {
        if (_uiState.value.driveMode == mode) return

        onDriveRelease()
        _uiState.value = _uiState.value.copy(driveMode = mode)

        if (mode == DriveMode.PHONE_TILT) {
            phoneOrientationSensor?.startListening()
            startTiltCollector()
        } else {
            phoneOrientationSensor?.stopListening()
            tiltCollectorJob?.cancel()
        }
    }

    private fun startTiltCollector() {
        val sensor = phoneOrientationSensor ?: return
        tiltCollectorJob?.cancel()
        tiltCollectorJob = viewModelScope.launch {
            sensor.tiltData.collect { tilt ->
                _uiState.value = _uiState.value.copy(phoneTilt = tilt)
                if (_uiState.value.isDriving && _uiState.value.driveMode == DriveMode.PHONE_TILT) {
                    _uiState.value = _uiState.value.copy(
                        throttleNorm = tilt.normThrottle,
                        steerNorm = tilt.normSteer
                    )
                    recalculateCommands()
                }
            }
        }
    }

    fun onDriveSensitivityChanged(percent: Int) {
        val clamped = percent.coerceIn(10, 100)
        _uiState.value = _uiState.value.copy(driveSensitivityPercent = clamped)
        recalculateCommands()
    }

    fun onSteerSensitivityChanged(percent: Int) {
        val clamped = percent.coerceIn(20, 250)
        _uiState.value = _uiState.value.copy(steerSensitivityPercent = clamped)
        recalculateCommands()
    }

    fun onSensitivityChanged(percent: Int) = onDriveSensitivityChanged(percent)

    fun setInvertDirection(invert: Boolean) {
        _uiState.value = _uiState.value.copy(invertDirection = invert)
        recalculateCommands()
    }

    fun onJoystickMove(throttle: Float, steer: Float) {
        if (_uiState.value.driveMode != DriveMode.JOYSTICK) return
        _uiState.value = _uiState.value.copy(
            throttleNorm = throttle,
            steerNorm = steer,
            isDriving = true
        )
        recalculateCommands()
        startDriveLoopIfNeeded()
    }

    fun recenterTiltZero() {
        if (phoneOrientationSensor != null) {
            phoneOrientationSensor.recenter()
        } else {
            val current = _uiState.value.phoneTilt
            _uiState.value = _uiState.value.copy(
                phoneTilt = current.copy(
                    pitchDeg = 0f,
                    rollDeg = 0f,
                    referencePitchDeg = current.rawPitchDeg,
                    referenceRollDeg = current.rawRollDeg,
                    normThrottle = 0f,
                    normSteer = 0f,
                    hasReference = true
                )
            )
        }
    }

    fun onTiltHoldStart() {
        if (_uiState.value.driveMode != DriveMode.PHONE_TILT) return
        recenterTiltZero()
        val currentTilt = phoneOrientationSensor?.tiltData?.value ?: _uiState.value.phoneTilt
        _uiState.value = _uiState.value.copy(
            phoneTilt = currentTilt,
            throttleNorm = currentTilt.normThrottle,
            steerNorm = currentTilt.normSteer,
            isDriving = true
        )
        recalculateCommands()
        startDriveLoopIfNeeded()
    }

    fun onDriveRelease() {
        _uiState.value = _uiState.value.copy(
            throttleNorm = 0f,
            steerNorm = 0f,
            calculatedSpeed = 0f,
            calculatedSteer = 0f,
            isDriving = false
        )
        stopDriveLoop()
        repository.sendDriveCommand(0f, 0f)
    }

    fun onJoystickRelease() = onDriveRelease()

    fun emergencyStop() {
        onDriveRelease()
    }

    fun connect(host: String = "192.168.4.1", port: Int = 80) {
        repository.connect(host, port)
    }

    fun disconnect() {
        onDriveRelease()
        repository.disconnect()
    }

    private fun recalculateCommands() {
        val driveScale = _uiState.value.driveSensitivityPercent / 100f
        val steerScale = _uiState.value.steerSensitivityPercent / 100f

        val throttle = _uiState.value.throttleNorm
        val steer = _uiState.value.steerNorm

        // Expo curves matching the updated robot controller:
        // V: Math.sign(throttle) * Math.pow(Math.abs(throttle), 1.3) * maxRawSpeed * driveScale
        // S: Math.sign(steer) * Math.pow(Math.abs(steer), 1.05) * maxRawSteer * steerScale
        val throttleExpo = if (throttle == 0f) 0f else {
            sign(throttle) * abs(throttle).pow(1.3f)
        }
        val steerExpo = if (steer == 0f) 0f else {
            sign(steer) * abs(steer).pow(1.05f)
        }

        val throttleMultiplier = if (_uiState.value.invertDirection) -1f else 1f
        val speed = throttleExpo * maxRawSpeed * driveScale * throttleMultiplier
        val steerSpeed = steerExpo * _uiState.value.maxRawSteer * steerScale

        _uiState.value = _uiState.value.copy(
            calculatedSpeed = speed,
            calculatedSteer = steerSpeed
        )
    }

    private fun startDriveLoopIfNeeded() {
        if (driveLoopJob?.isActive == true) return

        driveLoopJob = viewModelScope.launch {
            while (isActive && _uiState.value.isDriving) {
                repository.sendDriveCommand(
                    speed = _uiState.value.calculatedSpeed,
                    steer = _uiState.value.calculatedSteer
                )
                delay(50) // 20 Hz loop keeping ESP32 watchdog lease fed
            }
        }
    }

    private fun stopDriveLoop() {
        driveLoopJob?.cancel()
        driveLoopJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDriveLoop()
        phoneOrientationSensor?.stopListening()
    }

    companion object {
        fun createWithContext(context: Context): DriveViewModel {
            val appCtx = context.applicationContext
            return DriveViewModel(
                repository = RobotRepositoryImpl.instance,
                phoneOrientationSensor = PhoneOrientationSensor(appCtx),
                hapticManager = HapticManager(appCtx)
            )
        }
    }
}
