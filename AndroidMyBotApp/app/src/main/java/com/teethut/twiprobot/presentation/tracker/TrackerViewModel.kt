package com.teethut.twiprobot.presentation.tracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teethut.twiprobot.data.ai.CatPersonalityEngine
import com.teethut.twiprobot.data.ai.GemmaInferenceManager
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.data.sound.CuteRobotVoiceManager
import com.teethut.twiprobot.data.sound.RobotSynthSound
import com.teethut.twiprobot.data.sound.SoundEffectManager
import com.teethut.twiprobot.data.vision.PersonTrackingAnalyzer
import com.teethut.twiprobot.data.vision.TrackingPidController
import com.teethut.twiprobot.domain.model.CatPersonalityDirective
import com.teethut.twiprobot.domain.model.CatPersonalitySliders
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.DetectedSceneObject
import com.teethut.twiprobot.domain.model.DetectedTarget
import com.teethut.twiprobot.domain.model.DistanceCategory
import com.teethut.twiprobot.domain.model.FelineDrives
import com.teethut.twiprobot.domain.model.FelineMood
import com.teethut.twiprobot.domain.model.FelineWheelAction
import com.teethut.twiprobot.domain.model.RobotHandGesture
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.TrackerSettings
import com.teethut.twiprobot.domain.model.TrackingMode
import com.teethut.twiprobot.domain.model.VisionTrackerState
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class TrackerViewModel(
    private val repository: RobotRepository = RobotRepositoryImpl.instance,
    private val pidController: TrackingPidController = TrackingPidController(lostTargetTimeoutMs = 500L),
    private val soundEffectManager: SoundEffectManager? = null,
    private val cuteVoiceManager: CuteRobotVoiceManager? = null,
    private val appContext: Context? = null,
    private val gemmaManager: GemmaInferenceManager? = appContext?.let { GemmaInferenceManager.getInstance(it) },
    private val personalityEngine: CatPersonalityEngine? = gemmaManager?.let { CatPersonalityEngine(it) },
    analyzerProvider: (() -> PersonTrackingAnalyzer)? = null
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val telemetry: StateFlow<RobotTelemetry> = repository.currentTelemetry

    private val _uiState = MutableStateFlow(VisionTrackerState())
    val uiState: StateFlow<VisionTrackerState> = _uiState.asStateFlow()

    private var driveLoopJob: Job? = null
    private var waveResetJob: Job? = null
    private var activeFelineReactionJob: Job? = null
    private var felineWheelActionJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastDetectionTimestamp: Long = 0L
    private var wasBalancing: Boolean = false
    private var wobbleExceededSinceMs: Long = 0L
    private var lastFelineInteractionMs: Long = 0L

    val analyzer: PersonTrackingAnalyzer by lazy {
        val a = analyzerProvider?.invoke() ?: PersonTrackingAnalyzer(
            context = appContext,
            onTargetDetected = { target, _, _, fps ->
                onTargetDetected(target, fps)
            },
            onWaveDetected = {
                onWaveDetected()
            },
            onStopGestureChanged = { isActive ->
                onStopGestureChanged(isActive)
            },
            onGestureChanged = { gesture, confidence ->
                onGestureChanged(gesture, confidence)
            },
            onSceneObjectsDetected = { objects ->
                onSceneObjectsDetected(objects)
            }
        )
        val s = _uiState.value.settings
        a.setTargetFps(s.targetFps)
        a.isGestureDetectionEnabled = true
        a.isSceneCommentaryEnabled = s.sceneCommentaryEnabled
        a
    }

    private var stopGestureActiveSinceMs: Long = 0L

    init {
        // Monitor Fall & Tilt Safety Failsafe: Only trips if robot was actively balancing and then tilted/fell
        viewModelScope.launch {
            repository.telemetryFlow.collect { t ->
                val s = _uiState.value.settings
                if (wasBalancing && (!t.isBalancing || t.state == "FALLEN")) {
                    if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
                        executeFelineInteraction(
                            eventName = "ROBOT_FALLEN",
                            details = "Robot lost balance and fell over",
                            reflexSound = RobotSynthSound.DIZZY_WARBLE,
                            gazeX = 0f,
                            gazeY = 0f
                        )
                    } else {
                        cuteVoiceManager?.reactToFall(
                            enableSynth = s.synthSoundsEnabled,
                            enableVoice = s.cuteVoiceEnabled
                        )
                    }
                    if (_uiState.value.isTrackingActive) {
                        emergencyStop("Robot tilted or fallen - tracking halted for safety")
                    }
                    wobbleExceededSinceMs = 0L
                } else if (t.isBalancing && t.state != "FALLEN" && s.wobbleDetectionEnabled) {
                    val pitchMag = kotlin.math.abs(t.pitch)
                    if (pitchMag > s.wobblePitchThreshold) {
                        val now = System.currentTimeMillis()
                        if (wobbleExceededSinceMs == 0L) {
                            wobbleExceededSinceMs = now
                        } else if (now - wobbleExceededSinceMs >= 250L) {
                            // Sustained severe tilt beyond balancing lean (>250ms), genuine wobble!
                            if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
                                executeFelineInteraction(
                                    eventName = "ROBOT_WOBBLE",
                                    details = "Robot experienced severe balancing tilt wobble",
                                    reflexSound = RobotSynthSound.WOBBLE_WARBLE,
                                    gazeX = 0f,
                                    gazeY = 0f
                                )
                            } else {
                                cuteVoiceManager?.reactToWobble(
                                    enableSynth = s.synthSoundsEnabled,
                                    enableVoice = s.cuteVoiceEnabled
                                )
                            }
                        }
                    } else {
                        wobbleExceededSinceMs = 0L
                    }
                } else {
                    wobbleExceededSinceMs = 0L
                }
                wasBalancing = t.isBalancing

                // Live synchronization of robot angle offset from telemetry stream
                if (t.offset > 0f && !_uiState.value.isUpdatingOffset) {
                    _uiState.value = _uiState.value.copy(currentAngleOffset = t.offset)
                }
            }
        }

        // Fetch current robot PID settings to initialize the angle offset
        viewModelScope.launch {
            val result = repository.fetchPidSettings()
            result.onSuccess { settings ->
                if (settings.angleOffset > 0f) {
                    _uiState.value = _uiState.value.copy(currentAngleOffset = settings.angleOffset)
                }
            }
        }

        // Observe TTS speaking state for Cat Face lip-sync animation
        if (cuteVoiceManager != null) {
            viewModelScope.launch {
                cuteVoiceManager.isSpeaking.collect { speaking ->
                    _uiState.value = _uiState.value.copy(isRobotSpeaking = speaking)
                }
            }
        }

        // Initialize Gemma 4 cognitive engine if available
        if (gemmaManager != null) {
            _uiState.update { current ->
                current.copy(
                    isGemmaLoaded = gemmaManager.isModelLoaded,
                    activeBackend = gemmaManager.activeBackend,
                    felineDrives = personalityEngine?.getDrives() ?: FelineDrives(),
                    catSliders = personalityEngine?.getSliders() ?: CatPersonalitySliders()
                )
            }
            if (!gemmaManager.isModelLoaded) {
                val candidate = gemmaManager.findModelFile()
                if (candidate != null && candidate.exists()) {
                    viewModelScope.launch {
                        gemmaManager.initModel(candidate.absolutePath).onSuccess {
                            _uiState.update { current ->
                                current.copy(
                                    isGemmaLoaded = true,
                                    activeBackend = gemmaManager.activeBackend
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the robot's physical balancing angle offset in RAM.
     * Crucial for adjusting the center of gravity when mounting phones of different weights.
     */
    fun setAngleOffset(newOffset: Float): Job {
        val clamped = ((kotlin.math.round(newOffset * 100f)) / 100f).coerceIn(0.0f, 15.0f)
        _uiState.value = _uiState.value.copy(
            currentAngleOffset = clamped,
            isUpdatingOffset = true,
            offsetUpdateMessage = "Updating offset to ${String.format(java.util.Locale.US, "%.2f", clamped)}°..."
        )
        return viewModelScope.launch {
            val result = repository.updateAngleOffset(clamped)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isUpdatingOffset = false,
                    offsetUpdateMessage = "Offset set to ${String.format(java.util.Locale.US, "%.2f", clamped)}°"
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isUpdatingOffset = false,
                    offsetUpdateMessage = "Failed: ${err.message}"
                )
            }
        }
    }

    fun adjustAngleOffset(delta: Float): Job {
        val target = _uiState.value.currentAngleOffset + delta
        return setAngleOffset(target)
    }

    fun toggleLandscapeLock() {
        val newLocked = !_uiState.value.isLandscapeLocked
        _uiState.value = _uiState.value.copy(isLandscapeLocked = newLocked)
    }

    fun setLandscapeLocked(locked: Boolean) {
        _uiState.value = _uiState.value.copy(isLandscapeLocked = locked)
    }

    fun onTargetDetected(target: DetectedTarget?, fps: Int = 30) {
        val now = System.currentTimeMillis()
        lastDetectionTimestamp = now

        val currentState = _uiState.value
        val isFistHold = currentState.isFistHoldActive ||
            currentState.activeGesture == RobotHandGesture.CLOSED_FIST

        val output = pidController.computeDriveCommand(
            target = target,
            settings = currentState.settings,
            trackingMode = currentState.trackingMode,
            currentTimeMs = now
        )

        if (isFistHold) {
            pidController.clearErrors()
        }

        val baseStatus = when (output.lockStatus) {
            TargetLockStatus.LOCKED -> "Locked on Target #${target?.trackingId ?: 1}"
            TargetLockStatus.SEARCHING -> "Searching for person in front camera..."
            TargetLockStatus.LOST -> {
                if (currentState.lockStatus == TargetLockStatus.LOCKED) {
                    val s = currentState.settings
                    if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
                        executeFelineInteraction(
                            eventName = "TARGET_LOST",
                            details = "Tracked person stepped out of camera view",
                            reflexSound = RobotSynthSound.POUT_WHIMPER,
                            gazeX = 0f,
                            gazeY = 0f
                        )
                    } else {
                        cuteVoiceManager?.reactToPout(
                            enableSynth = s.synthSoundsEnabled,
                            enableVoice = s.cuteVoiceEnabled
                        )
                    }
                }
                "Target lost - holding position"
            }
        }

        val calculatedSpeed = when {
            isFistHold -> 0f
            currentState.isStopGestureActive && currentState.settings.stopGestureEnabled ->
                calculateBackwardSpeed(currentState.settings)
            else -> output.speed
        }

        val calculatedSteer = when {
            isFistHold -> 0f
            else -> output.steer
        }

        val status = when {
            isFistHold -> "✊ Fist (Halt): Holding Position"
            currentState.isStopGestureActive && currentState.settings.stopGestureEnabled -> "✋ Stop Gesture: Backing Up ◀◀"
            currentState.activeGesture == RobotHandGesture.WAVE || currentState.isWaveDetected -> "👋 Wave Detected! *Meow* 🐾"
            currentState.activeGesture != RobotHandGesture.NONE -> "${currentState.activeGesture.icon} ${currentState.activeGesture.label}"
            else -> baseStatus
        }

        _uiState.value = currentState.copy(
            currentTarget = target,
            lockStatus = output.lockStatus,
            distanceCategory = output.distanceCategory,
            calculatedSpeed = calculatedSpeed,
            calculatedSteer = calculatedSteer,
            fps = fps,
            statusMessage = status
        )
    }

    /**
     * Computes the signed backward speed taking into account throttle inversion and backward polarity.
     */
    private fun calculateBackwardSpeed(settings: TrackerSettings): Float {
        val baseSpeed = settings.stopGestureBackwardSpeed.coerceIn(400f, 1500f)
        // Standard convention: negative speed moves backward.
        // If invertThrottle is active, positive speed moves backward.
        // invertBackwardDirection provides explicit user manual override if motor wiring is reversed.
        val isNegativePolarity = !(settings.invertThrottle xor settings.invertBackwardDirection)
        return if (isNegativePolarity) -baseSpeed else baseSpeed
    }

    /**
     * Triggered when MediaPipe Tasks classifies a gesture.
     */
    fun onGestureChanged(gesture: RobotHandGesture, confidence: Float) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            activeGesture = gesture,
            gestureConfidence = confidence
        )

        val s = currentState.settings

        when (gesture) {
            RobotHandGesture.WAVE -> {
                if (_uiState.value.isFistHoldActive) {
                    onFistHoldChanged(false)
                }
                onWaveDetected()
            }
            RobotHandGesture.OPEN_PALM_STOP -> {
                if (_uiState.value.isFistHoldActive) {
                    onFistHoldChanged(false)
                }
                onStopGestureChanged(true)
                cuteVoiceManager?.reactToGesture(
                    gesture = gesture,
                    enableSynth = s.synthSoundsEnabled,
                    enableVoice = s.cuteVoiceEnabled
                )
                if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
                    executeFelineInteraction(
                        eventName = "STOP_GESTURE",
                        details = "Human raised open palm stop gesture",
                        reflexSound = RobotSynthSound.SURPRISED_GASP,
                        gazeX = 0f,
                        gazeY = 0f
                    )
                }
            }
            RobotHandGesture.CLOSED_FIST -> {
                if (_uiState.value.isStopGestureActive) {
                    onStopGestureChanged(false)
                }
                onFistHoldChanged(true)
                cuteVoiceManager?.reactToGesture(
                    gesture = gesture,
                    enableSynth = s.synthSoundsEnabled,
                    enableVoice = s.cuteVoiceEnabled
                )
                if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
                    executeFelineInteraction(
                        eventName = "FIST_HOLD",
                        details = "Human held closed fist command to halt",
                        reflexSound = RobotSynthSound.LOCK_BEEP,
                        gazeX = 0f,
                        gazeY = 0f
                    )
                }
            }
            RobotHandGesture.NONE -> {
                if (_uiState.value.isStopGestureActive) {
                    onStopGestureChanged(false)
                }
                if (_uiState.value.isFistHoldActive) {
                    onFistHoldChanged(false)
                }
                cuteVoiceManager?.reactToGesture(
                    gesture = RobotHandGesture.NONE,
                    enableSynth = false,
                    enableVoice = false
                )
            }
            else -> {
                // Extended gestures (Pointing Up, Thumbs Up, Victory, Love, etc.)
                if (_uiState.value.isStopGestureActive) {
                    onStopGestureChanged(false)
                }
                if (_uiState.value.isFistHoldActive) {
                    onFistHoldChanged(false)
                }
                _uiState.value = _uiState.value.copy(
                    statusMessage = "${gesture.icon} ${gesture.label}"
                )
                cuteVoiceManager?.reactToGesture(
                    gesture = gesture,
                    enableSynth = s.synthSoundsEnabled,
                    enableVoice = s.cuteVoiceEnabled
                )
                if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
                    when (gesture) {
                        RobotHandGesture.VICTORY -> {
                            executeFelineInteraction(
                                eventName = "PEACE_LOVE",
                                details = "Human showed victory / peace sign",
                                reflexSound = RobotSynthSound.PROUD_FANFARE,
                                gazeX = 0f,
                                gazeY = -0.2f
                            )
                        }
                        RobotHandGesture.LOVE -> {
                            executeFelineInteraction(
                                eventName = "LOVE_YOU",
                                details = "Human made love gesture",
                                reflexSound = RobotSynthSound.LOVE_PURR,
                                gazeX = 0f,
                                gazeY = 0f
                            )
                        }
                        RobotHandGesture.THUMB_UP -> {
                            executeFelineInteraction(
                                eventName = "THUMBS_UP",
                                details = "Human gave thumbs up approval",
                                reflexSound = RobotSynthSound.PROUD_FANFARE,
                                gazeX = 0f,
                                gazeY = 0f
                            )
                        }
                        RobotHandGesture.THUMB_DOWN -> {
                            executeFelineInteraction(
                                eventName = "THUMBS_DOWN",
                                details = "Human gave thumbs down disapproval",
                                reflexSound = RobotSynthSound.POUT_WHIMPER,
                                gazeX = 0f,
                                gazeY = 0.2f
                            )
                        }
                        RobotHandGesture.POINTING_UP -> {
                            executeFelineInteraction(
                                eventName = "POINTING_UP",
                                details = "Human pointed finger up to the sky",
                                reflexSound = RobotSynthSound.CURIOUS_BEEP,
                                gazeX = 0f,
                                gazeY = -0.3f
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Triggered when Closed Fist gesture is detected or released.
     * Stops the robot and holds position steadily at zero speed/steer.
     */
    fun onFistHoldChanged(isActive: Boolean) {
        val currentState = _uiState.value
        pidController.clearErrors()

        if (isActive) {
            waveResetJob?.cancel()
            stopGestureActiveSinceMs = 0L
            _uiState.value = currentState.copy(
                isStopGestureActive = false,
                isFistHoldActive = true,
                isWaveDetected = false,
                calculatedSpeed = 0f,
                calculatedSteer = 0f,
                statusMessage = "✊ Fist (Halt): Holding Position"
            )
            repository.sendDriveCommand(0f, 0f)
            if (!currentState.isTrackingActive) {
                stopDriveLoop()
            }
        } else {
            _uiState.value = currentState.copy(
                isFistHoldActive = false,
                calculatedSpeed = 0f,
                calculatedSteer = 0f,
                statusMessage = if (currentState.isTrackingActive) "Tracking Active" else "Tracking Stopped (Standby)"
            )
            if (!currentState.isTrackingActive && !_uiState.value.isStopGestureActive) {
                stopDriveLoop()
            }
            repository.sendDriveCommand(0f, 0f)
        }
    }

    /**
     * Triggered when Open Palm Stop gesture is detected or toggled.
     * When enabled, drives the robot backward while keeping steering aligned.
     */
    fun onStopGestureChanged(isActive: Boolean) {
        val currentState = _uiState.value
        val now = System.currentTimeMillis()

        if (isActive) {
            waveResetJob?.cancel()
            stopGestureActiveSinceMs = now
            val backwardSpeed = if (currentState.settings.stopGestureEnabled) {
                calculateBackwardSpeed(currentState.settings)
            } else {
                0f
            }
            _uiState.value = currentState.copy(
                isStopGestureActive = true,
                isFistHoldActive = false,
                isWaveDetected = false,
                calculatedSpeed = if (currentState.settings.stopGestureEnabled) backwardSpeed else currentState.calculatedSpeed,
                statusMessage = if (currentState.settings.stopGestureEnabled) "✋ Stop Gesture: Backing Up ◀◀" else currentState.statusMessage
            )
            // If in standby, start drive loop briefly to execute backward motion
            if (!currentState.isTrackingActive && currentState.settings.stopGestureEnabled) {
                startStandbyReverseLoop()
            }
        } else {
            stopGestureActiveSinceMs = 0L
            val isFistHold = currentState.isFistHoldActive ||
                currentState.activeGesture == RobotHandGesture.CLOSED_FIST
            _uiState.value = currentState.copy(
                isStopGestureActive = false,
                calculatedSpeed = 0f,
                calculatedSteer = if (isFistHold) 0f else currentState.calculatedSteer,
                statusMessage = when {
                    isFistHold -> "✊ Fist (Halt): Holding Position"
                    currentState.isTrackingActive -> "Tracking Active"
                    else -> "Tracking Stopped (Standby)"
                }
            )
            if (!currentState.isTrackingActive && !isFistHold) {
                stopDriveLoop()
                repository.sendDriveCommand(0f, 0f)
            }
        }
    }

    fun toggleInvertBackwardDirection() {
        val updated = _uiState.value.settings.copy(
            invertBackwardDirection = !_uiState.value.settings.invertBackwardDirection
        )
        updateSettings(updated)
    }

    fun toggleStopGesture() {
        val updated = _uiState.value.settings.copy(
            stopGestureEnabled = !_uiState.value.settings.stopGestureEnabled
        )
        updateSettings(updated)
    }

    fun setStopGestureBackwardSpeed(speed: Float) {
        val updated = _uiState.value.settings.copy(
            stopGestureBackwardSpeed = speed.coerceIn(400f, 1500f)
        )
        updateSettings(updated)
    }

    private var lastSceneCommentaryMs: Long = 0L
    private var lastCommentedLabel: String = ""
    private var lastSameLabelCommentaryMs: Long = 0L

    /**
     * Triggered when ML Kit Image Labeler detects salient objects in the scene.
     * Evaluates confidence, filters out generic labels, debounces to avoid spamming,
     * and queries Gemma 4 to formulate spontaneous kitten commentary.
     */
    fun onSceneObjectsDetected(objects: List<DetectedSceneObject>) {
        _uiState.update { it.copy(detectedSceneObjects = objects) }

        val s = _uiState.value.settings
        if (!s.sceneCommentaryEnabled) return
        if (_uiState.value.isRobotSpeaking || _uiState.value.isThinkingAi || _uiState.value.isListeningVoice) return

        val best = objects.maxByOrNull { it.confidence } ?: return
        val now = System.currentTimeMillis()

        // Debounce: minimum 14 seconds between different scene commentaries
        if (now - lastSceneCommentaryMs < 14_000L) return

        // 30 seconds debounce before repeating commentary on the exact same object
        if (best.label.equals(lastCommentedLabel, ignoreCase = true) && (now - lastSameLabelCommentaryMs < 30_000L)) {
            return
        }

        lastSceneCommentaryMs = now
        lastCommentedLabel = best.label
        lastSameLabelCommentaryMs = now

        executeFelineInteraction(
            eventName = "SCENE_OBJECT: ${best.label}",
            details = best.label,
            reflexSound = RobotSynthSound.CURIOUS_BEEP,
            gazeX = 0f,
            gazeY = 0f
        )
    }

    /**
     * Triggered when ML Kit Pose or MediaPipe detects a hand wave gesture.
     * Plays cute kitten meow / synth chirp / cute voice greeting and triggers celebration animation.
     */
    fun onWaveDetected() {
        if (_uiState.value.isFistHoldActive || _uiState.value.activeGesture == RobotHandGesture.CLOSED_FIST) {
            return
        }
        val s = _uiState.value.settings
        if (!s.waveToMeowEnabled && !s.cuteVoiceEnabled && !s.synthSoundsEnabled) return

        val now = System.currentTimeMillis()
        if (s.waveToMeowEnabled) {
            soundEffectManager?.playCuteMeow(volume = 1.0f, isManual = false)
        }
        cuteVoiceManager?.reactToGesture(
            gesture = RobotHandGesture.WAVE,
            enableSynth = s.synthSoundsEnabled,
            enableVoice = s.cuteVoiceEnabled,
            isManual = false
        )

        _uiState.value = _uiState.value.copy(
            isWaveDetected = true,
            lastWaveDetectedTimeMs = now,
            statusMessage = "👋 Wave Detected! *Meow* 🐾"
        )

        if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
            executeFelineInteraction(
                eventName = "HUMAN_WAVE",
                details = "Human waved with open hand smiling at robot",
                reflexSound = RobotSynthSound.HAPPY_CHIRP,
                gazeX = 0f,
                gazeY = 0f
            )
            viewModelScope.launch {
                delay(2400L)
                if (_uiState.value.isCatFaceMode && !_uiState.value.isListeningVoice) {
                    startVoiceListening()
                }
            }
        }

        waveResetJob?.cancel()
        waveResetJob = viewModelScope.launch {
            delay(1600)
            if (_uiState.value.isWaveDetected) {
                _uiState.value = _uiState.value.copy(isWaveDetected = false)
            }
        }
    }

    fun toggleWaveToMeow() {
        val updated = _uiState.value.settings.copy(
            waveToMeowEnabled = !_uiState.value.settings.waveToMeowEnabled
        )
        updateSettings(updated)
    }

    fun triggerWaveMeowManual() {
        val now = System.currentTimeMillis()
        soundEffectManager?.playCuteMeow(volume = 1.0f, isManual = true)

        _uiState.value = _uiState.value.copy(
            isWaveDetected = true,
            lastWaveDetectedTimeMs = now,
            statusMessage = "👋 Test Wave! *Meow* 🐾"
        )

        waveResetJob?.cancel()
        waveResetJob = viewModelScope.launch {
            delay(1600)
            if (_uiState.value.isWaveDetected) {
                _uiState.value = _uiState.value.copy(isWaveDetected = false)
            }
        }
    }

    fun triggerCuteVoiceGreeting() {
        if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
            executeFelineInteraction(
                eventName = "GREETING",
                details = "Human tapped cat greeting button",
                reflexSound = RobotSynthSound.HAPPY_CHIRP,
                gazeX = 0f,
                gazeY = 0f
            )
            viewModelScope.launch {
                delay(2200L)
                if (_uiState.value.isCatFaceMode && !_uiState.value.isListeningVoice) {
                    startVoiceListening()
                }
            }
            return
        }
        val s = _uiState.value.settings
        cuteVoiceManager?.setVoiceParameters(pitch = s.voicePitch, rate = s.voiceRate)
        cuteVoiceManager?.playRandomCuteGreeting(
            enableSynth = s.synthSoundsEnabled,
            enableVoice = true
        )
    }

    fun triggerWakeUpGreeting() {
        val s = _uiState.value.settings
        if (_uiState.value.isCatFaceMode || !_uiState.value.catSliders.allowCannedPresets) {
            executeFelineInteraction(
                eventName = "WAKE_UP",
                details = "Human woke robot from sleep",
                reflexSound = RobotSynthSound.HAPPY_CHIRP,
                gazeX = 0f,
                gazeY = 0f
            )
        } else {
            cuteVoiceManager?.reactToWakeUp(
                enableSynth = s.synthSoundsEnabled,
                enableVoice = s.cuteVoiceEnabled
            )
        }
    }

    fun triggerSleepSnore() {
        val s = _uiState.value.settings
        cuteVoiceManager?.playSleepSnore(
            enableSynth = s.synthSoundsEnabled
        )
    }

    fun triggerSynthSound(sound: RobotSynthSound) {
        cuteVoiceManager?.synth?.playSynthSound(sound, isManual = true)
    }

    fun startTracking() {
        if (_uiState.value.isTrackingActive) return

        lastDetectionTimestamp = System.currentTimeMillis()
        pidController.reset()
        _uiState.value = _uiState.value.copy(
            isTrackingActive = true,
            statusMessage = if (_uiState.value.isFistHoldActive) "✊ Fist (Halt): Holding Position" else "Tracking Active"
        )
        val s = _uiState.value.settings
        if (s.synthSoundsEnabled) {
            cuteVoiceManager?.synth?.playSynthSound(RobotSynthSound.LOCK_BEEP, isManual = true)
        }
        if (s.cuteVoiceEnabled) {
            cuteVoiceManager?.speak("Locked on! Following you now!")
        }
        startDriveLoop()
    }

    fun stopTracking() {
        _uiState.value = _uiState.value.copy(
            isTrackingActive = false,
            calculatedSpeed = 0f,
            calculatedSteer = 0f,
            statusMessage = "Tracking Stopped (Standby)"
        )
        stopDriveLoop()
        repository.sendDriveCommand(0f, 0f)
    }

    fun emergencyStop(reason: String = "Emergency Stop Triggered") {
        stopDriveLoop()
        pidController.reset()
        _uiState.value = _uiState.value.copy(
            isTrackingActive = false,
            isFistHoldActive = false,
            isStopGestureActive = false,
            calculatedSpeed = 0f,
            calculatedSteer = 0f,
            statusMessage = reason
        )
        repository.sendDriveCommand(0f, 0f)
    }

    fun toggleTracking() {
        if (_uiState.value.isTrackingActive) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    fun toggleCameraFacing() {
        val newFacing = !_uiState.value.useFrontCamera
        _uiState.value = _uiState.value.copy(
            useFrontCamera = newFacing,
            currentTarget = null,
            lockStatus = TargetLockStatus.SEARCHING
        )
        analyzer.resetTracking()
        pidController.reset()
    }

    fun setTrackingMode(mode: TrackingMode) {
        _uiState.value = _uiState.value.copy(trackingMode = mode)
    }

    fun setCatFaceMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isCatFaceMode = enabled)
    }

    fun toggleCatFaceMode() {
        _uiState.value = _uiState.value.copy(isCatFaceMode = !_uiState.value.isCatFaceMode)
    }

    fun updateSettings(newSettings: TrackerSettings) {
        cuteVoiceManager?.setVoiceParameters(pitch = newSettings.voicePitch, rate = newSettings.voiceRate)
        try {
            analyzer.setTargetFps(newSettings.targetFps)
            analyzer.isGestureDetectionEnabled = true
            analyzer.isSceneCommentaryEnabled = newSettings.sceneCommentaryEnabled
        } catch (_: Throwable) {
            // Ignored in unit test environments without camera/ML Kit
        }
        _uiState.value = _uiState.value.copy(settings = newSettings)
    }

    fun setCameraPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
    }

    private fun startDriveLoop() {
        driveLoopJob?.cancel()
        driveLoopJob = viewModelScope.launch {
            while (isActive && _uiState.value.isTrackingActive) {
                val now = System.currentTimeMillis()
                val isFistHold = _uiState.value.isFistHoldActive ||
                    _uiState.value.activeGesture == RobotHandGesture.CLOSED_FIST

                if (isFistHold) {
                    pidController.clearErrors()
                    if (_uiState.value.calculatedSpeed != 0f ||
                        _uiState.value.calculatedSteer != 0f ||
                        _uiState.value.statusMessage != "✊ Fist (Halt): Holding Position"
                    ) {
                        _uiState.value = _uiState.value.copy(
                            calculatedSpeed = 0f,
                            calculatedSteer = 0f,
                            statusMessage = "✊ Fist (Halt): Holding Position"
                        )
                    }
                    repository.sendDriveCommand(0f, 0f)
                    delay(50)
                    continue
                }

                // Safety Watchdog: reverse max 3.5 seconds continuously to prevent backing into wall indefinitely
                if (_uiState.value.isStopGestureActive && stopGestureActiveSinceMs > 0L) {
                    if (now - stopGestureActiveSinceMs > 3500L) {
                        _uiState.value = _uiState.value.copy(
                            calculatedSpeed = 0f,
                            statusMessage = "⚠️ Reverse Safety Timeout (3.5s)"
                        )
                    }
                }

                // Watchdog: If no detections processed in last 600ms, compute zero output
                if (now - lastDetectionTimestamp > 600L) {
                    val timeoutOutput = pidController.computeDriveCommand(
                        target = null,
                        settings = _uiState.value.settings,
                        trackingMode = _uiState.value.trackingMode,
                        currentTimeMs = now
                    )
                    _uiState.value = _uiState.value.copy(
                        lockStatus = timeoutOutput.lockStatus,
                        calculatedSpeed = timeoutOutput.speed,
                        calculatedSteer = timeoutOutput.steer,
                        distanceCategory = DistanceCategory.UNKNOWN,
                        statusMessage = "Target Lost: Auto-stopped"
                    )
                }

                val speedToSend = _uiState.value.calculatedSpeed
                val steerToSend = _uiState.value.calculatedSteer

                repository.sendDriveCommand(speedToSend, steerToSend)
                delay(50) // 20 Hz loop
            }
        }
    }

    /**
     * Runs a dedicated reverse drive loop when Stop gesture is held while tracking is in standby.
     */
    private fun startStandbyReverseLoop() {
        driveLoopJob?.cancel()
        driveLoopJob = viewModelScope.launch {
            var ticks = 0
            while (isActive && !_uiState.value.isTrackingActive && _uiState.value.isStopGestureActive) {
                val now = System.currentTimeMillis()
                if (ticks >= 70 || (stopGestureActiveSinceMs > 0L && (now - stopGestureActiveSinceMs > 3500L))) {
                    _uiState.value = _uiState.value.copy(
                        calculatedSpeed = 0f,
                        statusMessage = "⚠️ Reverse Safety Timeout (3.5s)"
                    )
                    repository.sendDriveCommand(0f, 0f)
                    break
                }
                val speedToSend = _uiState.value.calculatedSpeed
                val steerToSend = _uiState.value.calculatedSteer
                repository.sendDriveCommand(speedToSend, steerToSend)
                ticks++
                delay(50)
            }
            if (!_uiState.value.isTrackingActive && !_uiState.value.isFistHoldActive) {
                repository.sendDriveCommand(0f, 0f)
            }
        }
    }

    private fun stopDriveLoop() {
        driveLoopJob?.cancel()
        driveLoopJob = null
    }

    // --- AI Cat Companion & Feline Personality Integration ---

    fun onPetLeftEar() {
        executeFelineInteraction(
            eventName = "PET_LEFT_EAR",
            details = "Human gently scratched left cat ear directly on screen",
            reflexSound = RobotSynthSound.LOVE_PURR,
            gazeX = -0.4f,
            gazeY = -0.3f
        )
    }

    fun onPetRightEar() {
        executeFelineInteraction(
            eventName = "PET_RIGHT_EAR",
            details = "Human gently scratched right cat ear directly on screen",
            reflexSound = RobotSynthSound.LOVE_PURR,
            gazeX = 0.4f,
            gazeY = -0.3f
        )
    }

    fun onBoopNose() {
        executeFelineInteraction(
            eventName = "NOSE_BOOP",
            details = "Human booped cat button nose directly on screen",
            reflexSound = RobotSynthSound.CAT_NOSE_SNEEZE,
            gazeX = 0f,
            gazeY = 0f
        )
    }

    fun onStrokeChin() {
        executeFelineInteraction(
            eventName = "PET_CHIN",
            details = "Human stroked cat chin affectionately",
            reflexSound = RobotSynthSound.LOVE_PURR,
            gazeX = 0f,
            gazeY = 0.3f
        )
    }

    fun toggleVoiceListening() {
        if (_uiState.value.isListeningVoice) {
            stopVoiceListening()
        } else {
            startVoiceListening()
        }
    }

    fun startVoiceListening() {
        val ctx = appContext ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            _uiState.update { it.copy(statusMessage = "Speech recognizer unavailable") }
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _uiState.update { it.copy(isListeningVoice = true) }
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            _uiState.update { it.copy(isListeningVoice = false) }
                        }
                        override fun onError(error: Int) {
                            _uiState.update { it.copy(isListeningVoice = false) }
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognized = matches?.firstOrNull()?.trim() ?: ""
                            _uiState.update { it.copy(isListeningVoice = false) }
                            if (recognized.isNotBlank()) {
                                submitCustomHumanSpeech(recognized)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim() ?: ""
                            if (partial.isNotBlank()) {
                                _uiState.update { it.copy(statusMessage = "Listening: \"$partial\"...") }
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
            _uiState.update { it.copy(isListeningVoice = true) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isListeningVoice = false) }
        }
    }

    fun stopVoiceListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _uiState.update { it.copy(isListeningVoice = false) }
    }

    fun submitCustomHumanSpeech(humanText: String) {
        if (humanText.isBlank()) return
        executeFelineInteraction(
            eventName = "HUMAN_SPOKE",
            details = "Human said to cat robot: '$humanText'",
            reflexSound = RobotSynthSound.CAT_MEOW_RISE,
            gazeX = 0f,
            gazeY = 0f
        )
    }

    fun executeFelineInteraction(
        eventName: String,
        details: String,
        reflexSound: RobotSynthSound,
        gazeX: Float,
        gazeY: Float
    ) {
        val now = System.currentTimeMillis()
        val isUrgent = eventName.contains("STOP") || eventName.contains("FIST") || eventName.contains("FALL")
        if (!isUrgent && (now - lastFelineInteractionMs < 1800L)) {
            return
        }
        lastFelineInteractionMs = now

        activeFelineReactionJob?.cancel()

        // 1. Instant zero-latency reflex audio & face reaction (0ms)
        cuteVoiceManager?.synth?.playSynthSound(reflexSound, isManual = true)

        // 2. Evolve internal Feline Drives
        val currentDrives = _uiState.value.felineDrives
        val nextDrives = personalityEngine?.calculateNextDrives(currentDrives, eventName) ?: currentDrives
        personalityEngine?.updateDrives(nextDrives)

        _uiState.update { current ->
            current.copy(
                isThinkingAi = true,
                felineDrives = nextDrives,
                statusMessage = "Gemma 4 thinking: $eventName...",
                currentTarget = current.currentTarget ?: DetectedTarget(normalizedX = gazeX, normalizedY = gazeY)
            )
        }

        // 3. Streaming inference with Gemma 4 or structured preset fallback
        activeFelineReactionJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val rawBuffer = StringBuilder()

            gemmaManager?.resetConversation()

            try {
                val flow = personalityEngine?.streamFelineReaction(eventName, details, nextDrives)
                flow?.collect { token ->
                    rawBuffer.append(token)
                    val currentText = rawBuffer.toString()
                    val speechPreview = extractStreamingSpeech(currentText)
                    if (speechPreview.isNotBlank()) {
                        _uiState.update { current ->
                            current.copy(
                                statusMessage = speechPreview,
                                aiSpeechSubtitle = speechPreview
                            )
                        }
                    }
                }
            } catch (err: Exception) {
                android.util.Log.w("TrackerViewModel", "Streaming token error: ${err.message}", err)
            }

            val elapsedMs = System.currentTimeMillis() - startTime
            val rawText = rawBuffer.toString()
            val finalDirective = personalityEngine?.parseFelineResponse(rawText, elapsedMs, eventName)
                ?: CatPersonalityDirective(
                    speech = "Purr! I'm here with you on my balancing wheels! Meow!",
                    vocalization = "HAPPY_CHIRP",
                    mood = FelineMood.HAPPY,
                    wheelAction = FelineWheelAction.WIGGLE_DANCE
                )

            _uiState.update { current ->
                current.copy(
                    isThinkingAi = false,
                    activeFelineDirective = finalDirective,
                    aiSpeechSubtitle = finalDirective.speech,
                    isRobotSpeaking = true,
                    statusMessage = finalDirective.speech
                )
            }

            // 4. Play vocalization synth sound & speak reply aloud
            val synthSound = runCatching {
                RobotSynthSound.valueOf(finalDirective.vocalization)
            }.getOrDefault(RobotSynthSound.HAPPY_CHIRP)
            cuteVoiceManager?.synth?.playSynthSound(synthSound, isManual = true)

            if (finalDirective.speech.isNotBlank()) {
                cuteVoiceManager?.speak(
                    text = finalDirective.speech,
                    pitchOverride = _uiState.value.catSliders.voicePitch,
                    rateOverride = _uiState.value.catSliders.voiceRate,
                    interrupt = true
                )
            }

            // 5. Execute safe stationary yaw-only wheel action
            executeFelineWheelAction(finalDirective.wheelAction)
        }
    }

    private fun extractStreamingSpeech(raw: String): String {
        val withoutThoughts = raw.replace(Regex("""(?s)<(?:thought|think)>.*?</(?:thought|think)>"""), "").trim()
        val cleaned = withoutThoughts.removePrefix("```json").removePrefix("```")
        val regex = Regex("""(?i)"speech"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)?"|(?i)"speech"\s*:\s*([^,}\n]+)""")
        val match = regex.find(cleaned)
        if (match != null) {
            val candidate = match.groupValues[1].ifEmpty { match.groupValues[2] }
            return candidate.replace("\\n", " ").replace("\\\"", "\"").replace("\\", "").trim()
        }
        // If raw output is direct plain text without JSON curly braces
        if (!cleaned.startsWith("{") && !cleaned.contains("\"speech\"")) {
            return cleaned.replace("\\n", " ").trim()
        }
        return ""
    }

    /**
     * Executes safe, stationary yaw-only wheel actions (e.g. happy wiggle dance)
     * strictly keeping speed = 0 to preserve balance stability on the TWIP robot.
     */
    private fun executeFelineWheelAction(action: FelineWheelAction) {
        if (action != FelineWheelAction.WIGGLE_DANCE) return
        felineWheelActionJob?.cancel()
        felineWheelActionJob = viewModelScope.launch {
            val currentTel = repository.currentTelemetry.value
            if (!currentTel.isBalancing || kotlin.math.abs(currentTel.pitch) > 12.0f) {
                return@launch
            }

            // Gentle yaw oscillations: alternate left and right steering with speed = 0
            val yawSequence = floatArrayOf(0.18f, -0.18f, 0.18f, -0.18f, 0.14f, -0.14f, 0f)
            val steerScale = _uiState.value.settings.maxSteer
            for (yaw in yawSequence) {
                val t = repository.currentTelemetry.value
                if (!t.isBalancing || kotlin.math.abs(t.pitch) > 12.0f || _uiState.value.isStopGestureActive || _uiState.value.isFistHoldActive) {
                    repository.sendDriveCommand(0f, 0f)
                    break
                }
                repository.sendDriveCommand(0f, yaw * steerScale)
                delay(140L)
            }
            repository.sendDriveCommand(0f, 0f)
        }
    }

    fun updateCatSliders(newSliders: CatPersonalitySliders) {
        personalityEngine?.updateSliders(newSliders)
        cuteVoiceManager?.allowCannedPresets = newSliders.allowCannedPresets
        _uiState.update { it.copy(catSliders = newSliders) }
        cuteVoiceManager?.setVoiceParameters(newSliders.voicePitch, newSliders.voiceRate)
    }

    fun toggleGemmaModel() {
        val gm = gemmaManager ?: return
        if (gm.isModelLoaded) {
            gm.close()
            _uiState.update { it.copy(isGemmaLoaded = false, activeBackend = "") }
            return
        }
        val modelFile = gm.findModelFile() ?: return
        viewModelScope.launch {
            gm.initModel(modelFile.absolutePath).onSuccess {
                _uiState.update { it.copy(isGemmaLoaded = true, activeBackend = gm.activeBackend) }
                cuteVoiceManager?.speak("Local Gemma 4 cat brain is now online! Meow!")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDriveLoop()
        waveResetJob?.cancel()
        activeFelineReactionJob?.cancel()
        felineWheelActionJob?.cancel()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        repository.sendDriveCommand(0f, 0f)
    }

    companion object {
        fun createWithContext(context: Context): TrackerViewModel {
            val appCtx = context.applicationContext
            val gemma = GemmaInferenceManager.getInstance(appCtx)
            return TrackerViewModel(
                soundEffectManager = SoundEffectManager.getInstance(context),
                cuteVoiceManager = CuteRobotVoiceManager.getInstance(context),
                appContext = appCtx,
                gemmaManager = gemma,
                personalityEngine = CatPersonalityEngine(gemma)
            )
        }
    }
}
