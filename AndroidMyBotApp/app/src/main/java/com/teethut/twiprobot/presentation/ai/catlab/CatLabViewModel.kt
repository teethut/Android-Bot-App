package com.teethut.twiprobot.presentation.ai.catlab

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
import com.teethut.twiprobot.data.sound.CuteRobotVoiceManager
import com.teethut.twiprobot.data.sound.RobotSynthSound
import com.teethut.twiprobot.domain.model.CatPersonalityDirective
import com.teethut.twiprobot.domain.model.CatPersonalitySliders
import com.teethut.twiprobot.domain.model.DetectedTarget
import com.teethut.twiprobot.domain.model.DistanceCategory
import com.teethut.twiprobot.domain.model.FelineDrives
import com.teethut.twiprobot.domain.model.FelineMood
import com.teethut.twiprobot.domain.model.FelineWheelAction
import com.teethut.twiprobot.domain.model.RobotHandGesture
import com.teethut.twiprobot.domain.model.TargetLockStatus
import com.teethut.twiprobot.domain.model.VisionTrackerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class CatLabUiState(
    val trackerState: VisionTrackerState = VisionTrackerState(
        isCatFaceMode = true,
        statusMessage = "Cat AI Lab Sandbox: Ready"
    ),
    val currentDirective: CatPersonalityDirective = CatPersonalityDirective(
        speech = "Purr! Cat AI Lab ready for interaction!",
        vocalization = "HAPPY_CHIRP",
        mood = FelineMood.HAPPY,
        wheelAction = FelineWheelAction.HOLD_STILL
    ),
    val drives: FelineDrives = FelineDrives(),
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val isListeningVoice: Boolean = false,
    val voiceSttError: String? = null,
    val simulatedPitch: Float = 0f,
    val simulatedBalancing: Boolean = true,
    val isModelLoaded: Boolean = false,
    val activeBackend: String = "GPU",
    val sliders: CatPersonalitySliders = CatPersonalitySliders(),
    val eventLog: List<CatPersonalityDirective> = emptyList(),
    val useNeuralVoice: Boolean = true,
    val isNeuralVoiceReady: Boolean = false,
    val neuralVoiceStatus: String = "Initializing...",
    val selectedNeuralProfile: com.teethut.twiprobot.data.sound.NeuralVoiceProfile = com.teethut.twiprobot.data.sound.NeuralVoiceProfile.KRISTIN_HD,
    val isLoadingGemma: Boolean = false,
    val gemmaLoadError: String? = null
)

class CatLabViewModel(
    private val context: Context,
    private val gemmaManager: GemmaInferenceManager = GemmaInferenceManager.getInstance(context.applicationContext),
    private val voiceManager: CuteRobotVoiceManager = CuteRobotVoiceManager.getInstance(context.applicationContext),
    private val personalityEngine: CatPersonalityEngine = CatPersonalityEngine(gemmaManager)
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CatLabUiState(
            isModelLoaded = gemmaManager.isModelLoaded,
            activeBackend = gemmaManager.activeBackend,
            drives = personalityEngine.getDrives(),
            isNeuralVoiceReady = voiceManager.neuralVoice.isModelReady.value,
            neuralVoiceStatus = voiceManager.neuralVoice.statusMessage.value,
            selectedNeuralProfile = voiceManager.neuralVoice.currentProfile.value
        )
    )
    val uiState: StateFlow<CatLabUiState> = _uiState.asStateFlow()

    private var activeReactionJob: Job? = null
    private var tiltRecoveryJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                _uiState.update { current ->
                    current.copy(
                        isSpeaking = speaking,
                        trackerState = current.trackerState.copy(isRobotSpeaking = speaking)
                    )
                }
            }
        }
        viewModelScope.launch {
            voiceManager.neuralVoice.isModelReady.collect { ready ->
                _uiState.update { current ->
                    current.copy(isNeuralVoiceReady = ready)
                }
            }
        }
        viewModelScope.launch {
            voiceManager.neuralVoice.statusMessage.collect { status ->
                _uiState.update { current ->
                    current.copy(neuralVoiceStatus = status)
                }
            }
        }
        viewModelScope.launch {
            voiceManager.neuralVoice.currentProfile.collect { profile ->
                _uiState.update { current ->
                    current.copy(selectedNeuralProfile = profile)
                }
            }
        }
        // Auto-initialize local Gemma 4 if candidate weights file exists
        if (!gemmaManager.isModelLoaded) {
            val candidate = gemmaManager.findModelFile()
            if (candidate != null && candidate.exists()) {
                toggleGemmaModel()
            }
        }
    }

    fun selectNeuralProfile(profile: com.teethut.twiprobot.data.sound.NeuralVoiceProfile) {
        voiceManager.neuralVoice.selectProfile(profile)
    }

    fun testNeuralVoiceGreeting() {
        voiceManager.speak("Hi there! I am your cute balancing cat robot buddy! Meow!", interrupt = true)
    }

    fun toggleNeuralVoice(enable: Boolean) {
        voiceManager.useNeuralVoice = enable
        _uiState.update { it.copy(useNeuralVoice = enable) }
    }

    fun refreshModelStatus() {
        _uiState.update {
            it.copy(
                isModelLoaded = gemmaManager.isModelLoaded,
                activeBackend = gemmaManager.activeBackend
            )
        }
    }

    fun toggleGemmaModel() {
        if (gemmaManager.isModelLoaded) {
            gemmaManager.close()
            _uiState.update {
                it.copy(
                    isModelLoaded = false,
                    activeBackend = "",
                    isLoadingGemma = false,
                    gemmaLoadError = null
                )
            }
            return
        }

        val modelFile = gemmaManager.findModelFile()
        if (modelFile == null || !modelFile.exists()) {
            _uiState.update {
                it.copy(
                    gemmaLoadError = "Gemma 4 model file not found in /sdcard/Download/"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGemma = true, gemmaLoadError = null) }
            val result = gemmaManager.initModel(modelFile.absolutePath)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoadingGemma = false,
                        isModelLoaded = true,
                        activeBackend = gemmaManager.activeBackend,
                        gemmaLoadError = null
                    )
                }
                voiceManager.speak("Yay! Local Gemma 4 brain is now online! Meow!")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingGemma = false,
                        isModelLoaded = false,
                        gemmaLoadError = err.message ?: "Failed to load Gemma 4"
                    )
                }
            }
        }
    }

    fun updateSliders(newSliders: CatPersonalitySliders) {
        personalityEngine.updateSliders(newSliders)
        voiceManager.allowCannedPresets = newSliders.allowCannedPresets
        _uiState.update { it.copy(sliders = newSliders) }
        voiceManager.setVoiceParameters(newSliders.voicePitch, newSliders.voiceRate)
    }

    fun setVoicePitch(pitch: Float) {
        val updated = _uiState.value.sliders.copy(voicePitch = pitch)
        updateSliders(updated)
    }

    // Direct Petting Gestures (Canvas Touch & Hotspots)
    fun onPetLeftEar() {
        executeInteraction(
            eventName = "PET_LEFT_EAR",
            details = "Human gently scratched left cat ear directly on screen",
            reflexSound = RobotSynthSound.LOVE_PURR,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = -0.4f,
            gazeY = -0.3f
        )
    }

    fun onPetRightEar() {
        executeInteraction(
            eventName = "PET_RIGHT_EAR",
            details = "Human gently scratched right cat ear directly on screen",
            reflexSound = RobotSynthSound.LOVE_PURR,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0.4f,
            gazeY = -0.3f
        )
    }

    fun onBoopNose() {
        executeInteraction(
            eventName = "NOSE_BOOP",
            details = "Human booped cat button nose directly on screen",
            reflexSound = RobotSynthSound.CAT_NOSE_SNEEZE,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0f,
            gazeY = 0f
        )
    }

    fun onStrokeChin() {
        executeInteraction(
            eventName = "PET_CHIN",
            details = "Human stroked cat chin affectionately",
            reflexSound = RobotSynthSound.LOVE_PURR,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0f,
            gazeY = 0.3f
        )
    }

    fun triggerPetEarEvent() = onPetRightEar()
    fun triggerPetChinEvent() = onStrokeChin()

    fun triggerWaveEvent() {
        executeInteraction(
            eventName = "HUMAN_WAVE",
            details = "Human waved with open hand smiling at 1.5m",
            reflexSound = RobotSynthSound.HAPPY_CHIRP,
            mockGesture = RobotHandGesture.WAVE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0f,
            gazeY = 0f
        )
    }

    fun triggerPeaceLoveEvent() {
        executeInteraction(
            eventName = "PEACE_LOVE",
            details = "Human made victory / peace love sign",
            reflexSound = RobotSynthSound.PROUD_FANFARE,
            mockGesture = RobotHandGesture.VICTORY,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0f,
            gazeY = -0.2f
        )
    }

    fun triggerApproachCloseEvent() {
        executeInteraction(
            eventName = "HUMAN_TOO_CLOSE",
            details = "Human suddenly lunged closer than 0.4m",
            reflexSound = RobotSynthSound.CAT_HISS_STARTLE,
            mockGesture = RobotHandGesture.OPEN_PALM_STOP,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.TOO_CLOSE,
            gazeX = 0f,
            gazeY = -0.1f
        )
    }

    fun triggerHumanDisappearedEvent() {
        executeInteraction(
            eventName = "HUMAN_LEFT",
            details = "Human walked out of frame, target lost",
            reflexSound = RobotSynthSound.POUT_WHIMPER,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOST,
            mockDistance = DistanceCategory.UNKNOWN,
            gazeX = 0.5f,
            gazeY = 0f
        )
    }

    fun triggerWobbleTiltEvent() {
        tiltRecoveryJob?.cancel()
        _uiState.update { it.copy(simulatedPitch = 16.0f) }
        tiltRecoveryJob = viewModelScope.launch {
            delay(1200L)
            _uiState.update { it.copy(simulatedPitch = 0f) }
        }

        executeInteraction(
            eventName = "ROBOT_WOBBLE",
            details = "Chassis experienced sudden pitch tilt angle 16.0 degrees",
            reflexSound = RobotSynthSound.WOBBLE_WARBLE,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0f,
            gazeY = 0.2f
        )
    }

    fun triggerBoredomEvent() {
        executeInteraction(
            eventName = "BORED_IDLE",
            details = "Human has been inactive for 20 seconds, robot napping",
            reflexSound = RobotSynthSound.SLEEP_SNORE_PURR,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.SEARCHING,
            mockDistance = DistanceCategory.UNKNOWN,
            gazeX = 0f,
            gazeY = 0.4f
        )
    }

    fun submitCustomHumanSpeech(humanText: String) {
        if (humanText.isBlank()) return
        executeInteraction(
            eventName = "HUMAN_SPOKE",
            details = "Human said to cat robot: '$humanText'",
            reflexSound = RobotSynthSound.CAT_MEOW_RISE,
            mockGesture = RobotHandGesture.NONE,
            mockLock = TargetLockStatus.LOCKED,
            mockDistance = DistanceCategory.OPTIMAL,
            gazeX = 0f,
            gazeY = 0f
        )
    }

    // Voice Input (STT) Management
    fun toggleVoiceListening() {
        if (_uiState.value.isListeningVoice) {
            stopVoiceListening()
        } else {
            startVoiceListening()
        }
    }

    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _uiState.update { it.copy(voiceSttError = "Speech recognizer unavailable") }
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _uiState.update { it.copy(isListeningVoice = true, voiceSttError = null) }
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            _uiState.update { it.copy(isListeningVoice = false) }
                        }
                        override fun onError(error: Int) {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                                else -> "Voice error ($error)"
                            }
                            _uiState.update { it.copy(isListeningVoice = false, voiceSttError = msg) }
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
                                _uiState.update { current ->
                                    current.copy(trackerState = current.trackerState.copy(statusMessage = "Listening: \"$partial\"..."))
                                }
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
            _uiState.update { it.copy(isListeningVoice = true, voiceSttError = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isListeningVoice = false, voiceSttError = e.message) }
        }
    }

    private fun stopVoiceListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _uiState.update { it.copy(isListeningVoice = false) }
    }

    private fun executeInteraction(
        eventName: String,
        details: String,
        reflexSound: RobotSynthSound,
        mockGesture: RobotHandGesture,
        mockLock: TargetLockStatus,
        mockDistance: DistanceCategory,
        gazeX: Float,
        gazeY: Float
    ) {
        activeReactionJob?.cancel()

        // 1. Instant zero-latency reflex audio & face reaction (0ms)
        voiceManager.synth.playSynthSound(reflexSound, isManual = true)

        // 2. Evolve internal Cyber-Cat Drives
        val currentDrives = _uiState.value.drives
        val nextDrives = personalityEngine.calculateNextDrives(currentDrives, eventName)
        personalityEngine.updateDrives(nextDrives)

        _uiState.update { current ->
            current.copy(
                isThinking = true,
                drives = nextDrives,
                trackerState = current.trackerState.copy(
                    activeGesture = mockGesture,
                    lockStatus = mockLock,
                    distanceCategory = mockDistance,
                    isWaveDetected = mockGesture == RobotHandGesture.WAVE,
                    currentTarget = DetectedTarget(
                        normalizedX = gazeX,
                        normalizedY = gazeY
                    ),
                    statusMessage = "Gemma 4 thinking: $eventName..."
                )
            )
        }

        // 3. Streaming Gemma 4 inference & structured cognitive reaction
        activeReactionJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val rawBuffer = StringBuilder()

            android.util.Log.d("CatAI", "=== Interaction started: $eventName ($details) ===")
            // Reset conversation context so that prior turns do not corrupt the model's KV cache
            gemmaManager.resetConversation()

            try {
                personalityEngine.streamFelineReaction(eventName, details, nextDrives).collect { token ->
                    rawBuffer.append(token)
                    val currentText = rawBuffer.toString()
                    val speechPreview = extractStreamingSpeech(currentText)
                    if (speechPreview.isNotBlank()) {
                        _uiState.update { current ->
                            current.copy(
                                trackerState = current.trackerState.copy(
                                    statusMessage = speechPreview
                                ),
                                currentDirective = current.currentDirective.copy(
                                    speech = speechPreview
                                )
                            )
                        }
                    }
                }
            } catch (err: Exception) {
                android.util.Log.w("CatAI", "Streaming token error: ${err.message}", err)
            }

            val elapsedMs = System.currentTimeMillis() - startTime
            val rawText = rawBuffer.toString()
            android.util.Log.d("CatAI", "Raw Gemma output ($elapsedMs ms): '$rawText'")

            val finalDirective = personalityEngine.parseFelineResponse(rawText, elapsedMs, eventName)
            android.util.Log.d("CatAI", "Parsed directive speech: '${finalDirective.speech}', mood: ${finalDirective.mood}")

            _uiState.update { current ->
                val newLog = listOf(finalDirective) + current.eventLog.take(15)
                current.copy(
                    isThinking = false,
                    currentDirective = finalDirective,
                    eventLog = newLog,
                    trackerState = current.trackerState.copy(
                        isRobotSpeaking = true,
                        statusMessage = finalDirective.speech
                    )
                )
            }

            // 4. Play vocalization synth sound & speak reply aloud once Gemma finishes
            val synthSound = runCatching {
                RobotSynthSound.valueOf(finalDirective.vocalization)
            }.getOrDefault(RobotSynthSound.HAPPY_CHIRP)

            voiceManager.synth.playSynthSound(synthSound, isManual = true)
            voiceManager.speak(
                text = finalDirective.speech,
                pitchOverride = _uiState.value.sliders.voicePitch,
                rateOverride = _uiState.value.sliders.voiceRate,
                interrupt = true
            )
        }
    }

    private fun isSpeechCompleteInJson(raw: String): Boolean {
        val key = "\"speech\":"
        val idx = raw.indexOf(key)
        if (idx != -1) {
            val startQuote = raw.indexOf('"', idx + key.length)
            if (startQuote != -1) {
                val nextQuote = raw.indexOf('"', startQuote + 1)
                return nextQuote != -1 && (nextQuote - startQuote) > 2
            }
        }
        return false
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

    override fun onCleared() {
        super.onCleared()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }
}
