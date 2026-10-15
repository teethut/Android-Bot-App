package com.teethut.twiprobot.presentation.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teethut.twiprobot.data.ai.GemmaInferenceManager
import com.teethut.twiprobot.data.repository.RobotRepositoryImpl
import com.teethut.twiprobot.data.sound.CuteRobotVoiceManager
import com.teethut.twiprobot.data.sound.RobotSynthSound
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    ROBOT_GEMMA,
    SYSTEM
}

data class AssistantUiState(
    val isModelLoaded: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val activeBackend: String = "",
    val modelPath: String = "/data/local/tmp/llm/gemma-4-e4b.task",
    val statusMessage: String = "Model not loaded. Push or import .task file to initialize.",
    val messages: List<ChatMessage> = emptyList(),
    val cutePersonaEnabled: Boolean = true,
    val autoSpeakReplies: Boolean = true,
    val isSpeaking: Boolean = false
)

class RobotAssistantViewModel(
    context: Context,
    private val robotRepository: RobotRepository = RobotRepositoryImpl.instance,
    private val gemmaManager: GemmaInferenceManager = GemmaInferenceManager.getInstance(context.applicationContext),
    private val voiceManager: CuteRobotVoiceManager = CuteRobotVoiceManager.getInstance(context.applicationContext)
) : ViewModel() {

    private val appContext: Context = context.applicationContext
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()
    val telemetry: StateFlow<RobotTelemetry> = robotRepository.currentTelemetry

    init {
        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isSpeaking = speaking)
            }
        }

        // Auto-discover model if already pushed to phone
        val appExtDir = appContext.getExternalFilesDir(null)
        val candidates = buildList {
            if (appExtDir != null) {
                add(File(appExtDir, "gemma-4-E4B-it-gpu.litertlm"))
                add(File(appExtDir, "gemma-4-E4B-it.litertlm"))
                add(File(appExtDir, "gemma-4-E2B-it-gpu.litertlm"))
                add(File(appExtDir, "gemma-4-E4B-it-web.task"))
                add(File(appExtDir, "gemma-4-e4b.task"))
            }
            add(File(appContext.filesDir, "gemma-4-E4B-it-gpu.litertlm"))
            add(File("/sdcard/Download/gemma-4-E4B-it-gpu.litertlm"))
            add(File("/sdcard/Download/gemma-4-E4B-it.litertlm"))
            add(File("/sdcard/Download/gemma-4-E4B-it-web.task"))
            add(File("/data/local/tmp/llm/gemma-4-E4B-it-gpu.litertlm"))
        }

        val discovered = candidates.firstOrNull { it.exists() && it.length() > 100L * 1024 * 1024 }
            ?: candidates.firstOrNull { it.exists() }

        if (discovered != null) {
            val sizeMb = discovered.length() / (1024 * 1024)
            _uiState.value = _uiState.value.copy(
                modelPath = discovered.absolutePath,
                statusMessage = "Found model: ${discovered.name} ($sizeMb MB). Tap Load to activate."
            )
        } else if (appExtDir != null) {
            // Default recommended path with exact matching filename
            _uiState.value = _uiState.value.copy(
                modelPath = File(appExtDir, "gemma-4-E4B-it-gpu.litertlm").absolutePath
            )
        }
    }

    fun toggleCutePersona() {
        _uiState.value = _uiState.value.copy(
            cutePersonaEnabled = !_uiState.value.cutePersonaEnabled
        )
    }

    fun toggleAutoSpeak() {
        val next = !_uiState.value.autoSpeakReplies
        _uiState.value = _uiState.value.copy(autoSpeakReplies = next)
        if (!next) {
            voiceManager.stopSpeaking()
        }
    }

    fun speakMessage(text: String) {
        voiceManager.synth.playSynthSound(RobotSynthSound.HAPPY_CHIRP, isManual = true)
        voiceManager.speak(text, interrupt = true)
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun playCuteSampleVoice() {
        voiceManager.playRandomCuteGreeting(enableSynth = true, enableVoice = true)
    }

    fun importModelFromUri(uri: Uri, fileName: String = "gemma-4-E4B-it-gpu.litertlm") {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isLoadingModel = true,
                statusMessage = "Importing model into app storage (this may take a few seconds)..."
            )
            runCatching {
                val targetDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
                val targetFile = File(targetDir, fileName)
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not open stream for selected file")
                targetFile
            }.onSuccess { importedFile ->
                _uiState.value = _uiState.value.copy(
                    modelPath = importedFile.absolutePath,
                    statusMessage = "Imported to app storage! Initializing Gemma 4..."
                )
                loadModel(importedFile.absolutePath)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    statusMessage = "Import failed: ${err.localizedMessage ?: err.message}"
                )
            }
        }
    }

    fun updateModelPath(path: String) {
        _uiState.value = _uiState.value.copy(modelPath = path)
    }

    fun loadModel(customPath: String? = null) {
        val targetPath = customPath ?: _uiState.value.modelPath
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingModel = true,
                statusMessage = "Loading Gemma 4 on-device model..."
            )

            val file = File(targetPath)
            if (!file.exists()) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    isModelLoaded = false,
                    statusMessage = "File not found: ${file.absolutePath}. Please push via adb or place in internal storage."
                )
                return@launch
            }

            val sizeMb = file.length() / (1024 * 1024)
            if (file.length() < 100L * 1024 * 1024) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    isModelLoaded = false,
                    statusMessage = "Invalid model file: size is only ${file.length()} bytes (${sizeMb} MB). Expected ~2.6 GB. The file may be an HTML error page or Git LFS text pointer."
                )
                return@launch
            }

            val result = gemmaManager.initModel(file.absolutePath)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    isModelLoaded = true,
                    activeBackend = gemmaManager.activeBackend,
                    statusMessage = "Gemma 4 successfully loaded on ${gemmaManager.activeBackend}! (${sizeMb} MB)"
                )
                if (_uiState.value.autoSpeakReplies) {
                    voiceManager.synth.playSynthSound(RobotSynthSound.PROUD_FANFARE, isManual = true)
                    voiceManager.speak("Yay! My Gemma 4 brain is online and ready to help!")
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoadingModel = false,
                    isModelLoaded = false,
                    activeBackend = "",
                    statusMessage = "Failed to load model (${sizeMb} MB): ${error.localizedMessage ?: error.message}"
                )
            }
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || !_uiState.value.isModelLoaded) return

        val userMsg = ChatMessage(MessageSender.USER, trimmed)
        val currentList = _uiState.value.messages + userMsg

        _uiState.value = _uiState.value.copy(
            messages = currentList,
            isGenerating = true
        )

        // Reset conversation before each enriched turn to prevent telemetry context bloat in LiteRT KV cache
        gemmaManager.resetConversation()

        // Enrich prompt with hardware environment, persona, and live robot telemetry
        val t = telemetry.value
        val isCute = _uiState.value.cutePersonaEnabled
        val enrichedPrompt = buildString {
            appendLine("Environment Context:")
            appendLine("- Model: Google Gemma 4 E4B running on-device on Samsung Galaxy S24 Ultra")
            appendLine("- Execution Engine: Google LiteRT LM accelerated by ${gemmaManager.activeBackend}")
            appendLine("- TWIP2 Telemetry: Balancing=${t.isBalancing}, State=${t.state}, Pitch=${t.pitch}°, Setpoint=${t.setpoint}°, Speed=${t.speed}, MotorOut=${t.motorOut}")
            if (isCute) {
                appendLine("- Voice Style: Respond as Twippy, an adorable, cheerful BMO/cat-robot buddy! Keep your reply concise (2 to 3 short sentences) so it sounds super cute when spoken aloud, while still giving accurate technical advice.")
            }
            appendLine()
            appendLine("User Question: $trimmed")
        }

        viewModelScope.launch {
            val responseResult = gemmaManager.generateResponse(enrichedPrompt)
            val rawReply = responseResult.getOrElse { err ->
                "Inference error: ${err.message}"
            }

            // Sanitize any stray LaTeX formatting to clean natural text
            val replyText = rawReply
                .replace(Regex("""\$\\text\{([^}]+)\}\$""")) { it.groupValues[1] }
                .replace(Regex("""\$\\checkmark\$"""), "✓")
                .replace(Regex("""\$\\rightarrow\$"""), "→")
                .replace(Regex("""\$\{\s*"""), "")
                .replace(Regex("""\s*\}\$"""), "")
                .replace(Regex("""\$\\text\{\?\}\\?\s*"""), "")
                .trim()

            val botMsg = ChatMessage(MessageSender.ROBOT_GEMMA, replyText)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botMsg,
                isGenerating = false
            )

            if (_uiState.value.autoSpeakReplies) {
                voiceManager.synth.playSynthSound(RobotSynthSound.HAPPY_CHIRP, isManual = true)
                voiceManager.speak(replyText, interrupt = true)
            }
        }
    }

    fun clearChat() {
        voiceManager.stopSpeaking()
        gemmaManager.resetConversation()
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        gemmaManager.close()
    }
}

