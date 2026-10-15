package com.teethut.twiprobot.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class NeuralVoiceProfile(val label: String, val dirName: String, val sampleRate: Int, val description: String) {
    KRISTIN_HD("Kristin HD (Real-Time)", "vits-piper-en_US-kristin-medium", 22050, "Piper Medium VITS (⚡ ~300ms instant, cute & bright)"),
    KOKORO_STUDIO("Kokoro Studio (Ultra Natural)", "kokoro-int8-en-v0_19", 24000, "Kokoro 82M StyleTTS2 (Broadcast studio quality, 🐢 ~10s compute delay)")
}

/**
 * On-Device Neural Voice Manager powered by Sherpa-ONNX (Piper VITS & Kokoro 82M).
 * Synthesizes high-fidelity, natural neural character speech directly on-device (offline)
 * with low latency on arm64-v8a hardware.
 */
class NeuralVoiceManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ttsMutex = Mutex()
    private var offlineTts: OfflineTts? = null
    private var currentAudioTrack: AudioTrack? = null
    private var currentPlaybackJob: Job? = null

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initializing neural engine...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _currentProfile = MutableStateFlow(NeuralVoiceProfile.KRISTIN_HD)
    val currentProfile: StateFlow<NeuralVoiceProfile> = _currentProfile.asStateFlow()

    var defaultPitch: Float = 1.35f
        private set
    var defaultSpeed: Float = 1.08f
        private set

    private var activeSpeakerId: Int = 0
    private var isKokoroActive: Boolean = false

    init {
        scope.launch {
            initTts()
        }
    }

    fun selectProfile(profile: NeuralVoiceProfile) {
        if (_currentProfile.value == profile && _isModelReady.value) return
        _isModelReady.value = false
        _currentProfile.value = profile
        scope.launch {
            initTts(profile)
        }
    }

    /**
     * Initializes the requested profile (default Kokoro Studio with Kristin HD fallback).
     */
    suspend fun initTts(profile: NeuralVoiceProfile = _currentProfile.value): Boolean = ttsMutex.withLock {
        withContext(Dispatchers.IO) {
            _isModelReady.value = false
            stopSpeaking()
            var targetProfile = profile
            var modelDir = findDirectoryForProfile(targetProfile)

        // If Kokoro Studio is requested but files aren't found, try Kristin HD
        if (modelDir == null && targetProfile == NeuralVoiceProfile.KOKORO_STUDIO) {
            Log.w(TAG, "Kokoro Studio directory not found, attempting fallback to Kristin HD")
            val fallbackDir = findDirectoryForProfile(NeuralVoiceProfile.KRISTIN_HD)
            if (fallbackDir != null) {
                targetProfile = NeuralVoiceProfile.KRISTIN_HD
                modelDir = fallbackDir
            }
        }

        if (modelDir == null) {
            _statusMessage.value = "${targetProfile.label} not found on device."
            _isModelReady.value = false
            Log.w(TAG, "Model directory for ${targetProfile.dirName} not found.")
            return@withContext false
        }
        _currentProfile.value = targetProfile

        try {
            val tokensFile = File(modelDir, "tokens.txt")
            val dataDir = File(modelDir, "espeak-ng-data")

            when (targetProfile) {
                NeuralVoiceProfile.KOKORO_STUDIO -> {
                    val kokoroModel = if (File(modelDir, "model.int8.onnx").exists()) File(modelDir, "model.int8.onnx") else File(modelDir, "model.onnx")
                    val voicesFile = File(modelDir, "voices.bin")

                    if (kokoroModel.exists() && voicesFile.exists() && tokensFile.exists() && dataDir.exists()) {
                        Log.i(TAG, "Loading Sherpa-ONNX Kokoro Studio Neural TTS from: ${kokoroModel.absolutePath}")

                        val kokoroConfig = OfflineTtsKokoroModelConfig(
                            model = kokoroModel.absolutePath,
                            voices = voicesFile.absolutePath,
                            tokens = tokensFile.absolutePath,
                            dataDir = dataDir.absolutePath,
                            lexicon = "",
                            lang = "en-us",
                            dictDir = "",
                            lengthScale = 1.0f
                        )

                        val modelConfig = OfflineTtsModelConfig(
                            kokoro = kokoroConfig,
                            numThreads = 4,
                            debug = false,
                            provider = "cpu"
                        )

                        val ttsConfig = OfflineTtsConfig(
                            model = modelConfig,
                            ruleFsts = "",
                            ruleFars = "",
                            maxNumSentences = 1,
                            silenceScale = 0.2f
                        )

                        offlineTts?.release()
                        offlineTts = OfflineTts(assetManager = null, config = ttsConfig)
                        isKokoroActive = true
                        activeSpeakerId = 0 // af_bella: warm, expressive human voice
                        _isModelReady.value = true
                        _statusMessage.value = "Neural Voice (Kokoro Studio): Ready"
                        Log.i(TAG, "Sherpa-ONNX Kokoro Studio Neural Voice initialized successfully (sampleRate=${offlineTts?.sampleRate()}, speakers=${offlineTts?.numSpeakers()})")
                        return@withContext true
                    }
                }
                NeuralVoiceProfile.KRISTIN_HD -> {
                    val piperModel = when {
                        File(modelDir, "en_US-kristin-medium.onnx").exists() -> File(modelDir, "en_US-kristin-medium.onnx")
                        File(modelDir, "en_US-amy-low.onnx").exists() -> File(modelDir, "en_US-amy-low.onnx")
                        else -> null
                    }
                    if (piperModel != null && piperModel.exists() && tokensFile.exists() && dataDir.exists()) {
                        val voiceLabel = if (piperModel.name.contains("kristin")) "Kristin HD (22kHz)" else "Amy (16kHz)"
                        Log.i(TAG, "Loading Sherpa-ONNX VITS ($voiceLabel) from: ${piperModel.absolutePath}")

                        val vitsConfig = OfflineTtsVitsModelConfig(
                            model = piperModel.absolutePath,
                            lexicon = "",
                            tokens = tokensFile.absolutePath,
                            dataDir = dataDir.absolutePath,
                            dictDir = "",
                            noiseScale = 0.667f,
                            noiseScaleW = 0.8f,
                            lengthScale = 0.90f
                        )

                        val modelConfig = OfflineTtsModelConfig(
                            vits = vitsConfig,
                            numThreads = 4,
                            debug = false,
                            provider = "cpu"
                        )

                        val ttsConfig = OfflineTtsConfig(
                            model = modelConfig,
                            ruleFsts = "",
                            ruleFars = "",
                            maxNumSentences = 1,
                            silenceScale = 0.2f
                        )

                        offlineTts?.release()
                        offlineTts = OfflineTts(assetManager = null, config = ttsConfig)
                        isKokoroActive = false
                        activeSpeakerId = 0
                        _isModelReady.value = true
                        _statusMessage.value = "Neural Voice ($voiceLabel): Ready"
                        Log.i(TAG, "Sherpa-ONNX VITS Neural Voice initialized successfully (sampleRate=${offlineTts?.sampleRate()})")
                        return@withContext true
                    }
                }
            }

            _statusMessage.value = "Incomplete neural model files in $modelDir"
            _isModelReady.value = false
            Log.w(TAG, "Incomplete model files in $modelDir")
            return@withContext false
        } catch (e: Throwable) {
            _statusMessage.value = "Neural init error: ${e.message}"
            _isModelReady.value = false
            Log.e(TAG, "Failed to initialize Sherpa-ONNX: ${e.message}", e)
            false
        }
    }
}

    fun setSpeakerId(sid: Int) {
        activeSpeakerId = sid
        Log.d(TAG, "Set active speaker ID: $sid")
    }

    fun setVoiceParameters(pitch: Float, speed: Float) {
        defaultPitch = pitch.coerceIn(0.7f, 2.0f)
        defaultSpeed = speed.coerceIn(0.7f, 1.8f)
        Log.d(TAG, "Updated NeuralVoice parameters: defaultPitch=$defaultPitch, defaultSpeed=$defaultSpeed")
    }

    /**
     * Synthesizes and plays text aloud using on-device Neural TTS with pitch & speed control.
     */
    fun speak(
        text: String,
        pitch: Float = defaultPitch,
        speed: Float = defaultSpeed,
        interrupt: Boolean = true
    ): Boolean {
        if (!_isModelReady.value) {
            return false
        }

        val cleaned = CuteRobotVoiceManager.cleanTextForSpeech(text)
        if (cleaned.isBlank()) return false

        if (interrupt) {
            stopSpeaking()
        }

        currentPlaybackJob = scope.launch {
            try {
                _isSpeaking.value = true
                val startTime = System.currentTimeMillis()

                val audio: GeneratedAudio? = ttsMutex.withLock {
                    val tts = offlineTts
                    if (tts == null || !_isModelReady.value) null
                    else withContext(Dispatchers.Default) {
                        tts.generate(cleaned, sid = activeSpeakerId, speed = 1.0f)
                    }
                }

                if (audio == null) {
                    _isSpeaking.value = false
                    return@launch
                }

                val latencyMs = System.currentTimeMillis() - startTime
                Log.d(TAG, "Generated neural audio in ${latencyMs}ms (${audio.samples.size} samples, ${audio.sampleRate}Hz, sid=$activeSpeakerId, targetPitch=$pitch, targetSpeed=$speed)")

                if (audio.samples.isNotEmpty()) {
                    playSamples(audio.samples, audio.sampleRate, pitch = pitch, speed = speed)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error generating or playing neural speech: ${e.message}", e)
            } finally {
                _isSpeaking.value = false
            }
        }
        return true
    }

    private suspend fun playSamples(
        samples: FloatArray,
        sampleRate: Int,
        pitch: Float = 1.35f,
        speed: Float = 1.08f
    ) = withContext(Dispatchers.IO) {
        val trackToRelease: AudioTrack?
        try {
            // 1. Convert float samples (-1.0f..1.0f) to 16-bit PCM (ShortArray) for 100% universal Android HAL compatibility & punchy volume
            val shortSamples = ShortArray(samples.size) { i ->
                (samples[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, 4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            trackToRelease = track
            currentAudioTrack = track
            track.setVolume(1.0f)

            // 2. Apply pitch and speed scaling via PlaybackParams
            val targetPitch = pitch.coerceIn(0.6f, 2.0f)
            val targetSpeed = speed.coerceIn(0.6f, 2.0f)
            try {
                val playbackParams = android.media.PlaybackParams().apply {
                    this.pitch = targetPitch
                    this.speed = targetSpeed
                }
                track.playbackParams = playbackParams
                Log.d(TAG, "Applied AudioTrack PlaybackParams: pitch=$targetPitch, speed=$targetSpeed")
            } catch (e: Throwable) {
                Log.w(TAG, "Could not set PlaybackParams on AudioTrack: ${e.message}")
            }

            track.play()

            val effectiveSpeed = if (targetSpeed > 0.1f) targetSpeed else 1.0f
            val durationMs = ((shortSamples.size.toDouble() / (sampleRate * effectiveSpeed)) * 1000).toLong()
            Log.d(TAG, "Starting audio playback: ${shortSamples.size} frames (~${durationMs}ms) at ${sampleRate}Hz (pitch=$targetPitch, speed=$targetSpeed)")

            // 3. Stream audio in chunks
            var offset = 0
            val chunkSize = 2048
            while (offset < shortSamples.size && isActive && currentAudioTrack == track) {
                val toWrite = minOf(chunkSize, shortSamples.size - offset)
                val written = track.write(shortSamples, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }

            // 4. Wait until the hardware playback head has actually played all frames through the DAC/speaker
            val waitStartTime = System.currentTimeMillis()
            val maxWaitMs = durationMs + 800L
            while (isActive &&
                currentAudioTrack == track &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < shortSamples.size &&
                (System.currentTimeMillis() - waitStartTime) < maxWaitMs
            ) {
                kotlinx.coroutines.delay(20L)
            }

            // Small DAC buffer drain safety delay so the hardware speaker DAC doesn't clip the final syllable
            kotlinx.coroutines.delay(120L)

            Log.d(TAG, "Audio playback completed smoothly (played=${track.playbackHeadPosition}/${shortSamples.size} frames)")

            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        } catch (e: Throwable) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}", e)
        } finally {
            currentAudioTrack = null
        }
    }

    fun stopSpeaking() {
        try {
            currentPlaybackJob?.cancel()
            currentAudioTrack?.let { track ->
                currentAudioTrack = null
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                    }
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Throwable) {
        } finally {
            currentAudioTrack = null
            _isSpeaking.value = false
        }
    }

    fun release() {
        stopSpeaking()
        scope.launch {
            ttsMutex.withLock {
                try {
                    offlineTts?.release()
                } catch (_: Throwable) {
                } finally {
                    offlineTts = null
                    _isModelReady.value = false
                }
            }
        }
    }

    private fun findDirectoryForProfile(profile: NeuralVoiceProfile): File? {
        val candidates = listOf(
            File(appContext.getExternalFilesDir("models"), profile.dirName),
            File("/sdcard/Android/data/${appContext.packageName}/files/models/${profile.dirName}"),
            File(appContext.filesDir, "models/${profile.dirName}"),
            File("/sdcard/TWIP/models/${profile.dirName}")
        )

        for (candidate in candidates) {
            if (candidate.exists()) {
                val hasModel = when (profile) {
                    NeuralVoiceProfile.KRISTIN_HD -> File(candidate, "en_US-kristin-medium.onnx").exists() || File(candidate, "en_US-amy-low.onnx").exists()
                    NeuralVoiceProfile.KOKORO_STUDIO -> File(candidate, "model.int8.onnx").exists() || File(candidate, "model.onnx").exists()
                }
                if (hasModel) return candidate
            }
        }
        return null
    }

    companion object {
        private const val TAG = "NeuralVoiceManager"

        @Volatile
        private var instance: NeuralVoiceManager? = null

        fun getInstance(context: Context): NeuralVoiceManager {
            return instance ?: synchronized(this) {
                instance ?: NeuralVoiceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
