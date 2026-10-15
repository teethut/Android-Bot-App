package com.teethut.twiprobot.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.teethut.twiprobot.domain.model.RobotHandGesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Manages the robot's cute BMO / Anime Cat-Bot speaking voice using on-device Android TextToSpeech,
 * coordinated with procedural synth chirps & purrs.
 */
class CuteRobotVoiceManager(
    context: Context,
    val synth: ProceduralRobotSynth = ProceduralRobotSynth()
) {
    private val appContext = context.applicationContext
    val neuralVoice: NeuralVoiceManager = NeuralVoiceManager.getInstance(context)
    var useNeuralVoice: Boolean = true
    var allowCannedPresets: Boolean = true

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val isPlatformTtsSpeaking = MutableStateFlow(false)
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var voicePitch: Float = DEFAULT_CUTE_PITCH
        private set
    var voiceRate: Float = DEFAULT_CUTE_RATE
        private set

    private var lastGestureSpeechMs: Long = 0L
    private var lastSpokenGesture: RobotHandGesture = RobotHandGesture.NONE
    private var lastFallSpeechMs: Long = 0L

    init {
        initTts()
        neuralVoice.setVoiceParameters(voicePitch, voiceRate)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            kotlinx.coroutines.flow.combine(
                neuralVoice.isSpeaking,
                isPlatformTtsSpeaking
            ) { neural, platform ->
                neural || platform
            }.collect { combinedSpeaking ->
                _isSpeaking.value = combinedSpeaking
            }
        }
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val result = engine.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }

                    // Try to pick a clear, higher-timbre offline voice if multiple voices exist
                    try {
                        val preferredVoice = engine.voices?.firstOrNull { v ->
                            v.locale.language == Locale.ENGLISH.language &&
                                !v.isNetworkConnectionRequired &&
                                (v.name.contains("female", ignoreCase = true) ||
                                    v.name.contains("sfg", ignoreCase = true) ||
                                    v.name.contains("tpf", ignoreCase = true) ||
                                    v.name.contains("iob", ignoreCase = true))
                        }
                        if (preferredVoice != null) {
                            engine.voice = preferredVoice
                        }
                    } catch (_: Exception) {
                    }

                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(audioAttrs)
                    engine.setPitch(voicePitch)
                    engine.setSpeechRate(voiceRate)

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isPlatformTtsSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            isPlatformTtsSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            isPlatformTtsSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            isPlatformTtsSpeaking.value = false
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            isPlatformTtsSpeaking.value = false
                        }
                    })

                    isTtsReady = true
                    Log.d("CuteRobotVoice", "Cute Robot TTS initialized (pitch=$voicePitch, rate=$voiceRate)")
                } else {
                    Log.w("CuteRobotVoice", "TTS initialization failed with status=$status")
                }
            }
        } catch (e: Exception) {
            Log.e("CuteRobotVoice", "Failed to initialize TextToSpeech: ${e.message}", e)
        }
    }

    fun setVoiceParameters(pitch: Float = voicePitch, rate: Float = voiceRate) {
        voicePitch = pitch.coerceIn(0.8f, 2.2f)
        voiceRate = rate.coerceIn(0.7f, 1.8f)
        tts?.setPitch(voicePitch)
        tts?.setSpeechRate(voiceRate)
        neuralVoice.setVoiceParameters(voicePitch, voiceRate)
    }

    /**
     * Speaks any text aloud in the robot's cute voice.
     */
    fun speak(
        text: String,
        pitchOverride: Float? = null,
        rateOverride: Float? = null,
        interrupt: Boolean = true
    ): Boolean {
        val cleaned = cleanTextForSpeech(text)
        if (cleaned.isBlank()) return false

        // 1. Try On-Device Neural Voice (Kokoro Studio / Piper VITS) first if enabled and ready
        if (useNeuralVoice && neuralVoice.isModelReady.value) {
            val pitch = (pitchOverride ?: voicePitch).coerceIn(0.7f, 2.0f)
            val speed = (rateOverride ?: voiceRate).coerceIn(0.7f, 1.8f)
            val neuralSuccess = neuralVoice.speak(
                cleaned,
                pitch = pitch,
                speed = speed,
                interrupt = interrupt
            )
            if (neuralSuccess) return true
        }

        // 2. Direct fallback to platform Android TextToSpeech
        val engine = tts
        if (engine == null || !isTtsReady) {
            Log.w("CuteRobotVoice", "TTS not ready yet; playing happy synth chirp fallback")
            synth.playSynthSound(RobotSynthSound.HAPPY_CHIRP, isManual = true)
            return false
        }

        val targetPitch = (pitchOverride ?: voicePitch).coerceIn(0.8f, 2.2f)
        val targetRate = (rateOverride ?: voiceRate).coerceIn(0.7f, 1.8f)
        engine.setPitch(targetPitch)
        engine.setSpeechRate(targetRate)

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "twip_cute_${UUID.randomUUID()}"
        val res = engine.speak(cleaned, queueMode, params, utteranceId)
        return res == TextToSpeech.SUCCESS
    }

    fun stopSpeaking() {
        neuralVoice.stopSpeaking()
        isPlatformTtsSpeaking.value = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        } finally {
            _isSpeaking.value = false
        }
    }

    /**
     * Reacts to a detected hand gesture with expressive procedural synth sounds and/or cute voice lines.
     */
    fun reactToGesture(
        gesture: RobotHandGesture,
        enableSynth: Boolean,
        enableVoice: Boolean,
        isManual: Boolean = false
    ) {
        if (gesture == RobotHandGesture.NONE) {
            lastSpokenGesture = RobotHandGesture.NONE
            return
        }

        val now = System.currentTimeMillis()
        if (!isManual) {
            // Avoid repeating the same gesture reaction rapidly
            val minCooldown = if (gesture == lastSpokenGesture) 4200L else 1800L
            if (now - lastGestureSpeechMs < minCooldown) return
        }
        lastGestureSpeechMs = now
        lastSpokenGesture = gesture

        if (enableSynth) {
            val synthSound = when (gesture) {
                RobotHandGesture.WAVE -> RobotSynthSound.HAPPY_CHIRP
                RobotHandGesture.LOVE -> RobotSynthSound.LOVE_PURR
                RobotHandGesture.VICTORY -> RobotSynthSound.PROUD_FANFARE
                RobotHandGesture.THUMB_UP -> RobotSynthSound.PROUD_FANFARE
                RobotHandGesture.POINTING_UP -> RobotSynthSound.CURIOUS_BEEP
                RobotHandGesture.OPEN_PALM_STOP -> RobotSynthSound.SURPRISED_GASP
                RobotHandGesture.CLOSED_FIST -> RobotSynthSound.LOCK_BEEP
                RobotHandGesture.THUMB_DOWN -> RobotSynthSound.POUT_WHIMPER
                RobotHandGesture.NONE -> null
            }
            synthSound?.let { synth.playSynthSound(it, isManual = isManual) }
        }

        if (enableVoice && allowCannedPresets) {
            val phrase = getCutePhraseForGesture(gesture)
            if (phrase != null) {
                val isUrgentCommand = gesture == RobotHandGesture.OPEN_PALM_STOP || gesture == RobotHandGesture.CLOSED_FIST
                // Do not cut off ongoing speech for non-urgent gestures
                if (!isUrgentCommand && isSpeaking.value) {
                    Log.d("CuteRobotVoice", "Robot is already speaking, skipping non-urgent gesture voice line: $phrase")
                } else {
                    speak(phrase, interrupt = isUrgentCommand)
                }
            }
        }
    }

    private var lastWobbleSpeechMs: Long = 0L
    private var lastWobbleSynthMs: Long = 0L
    private var lastPoutSpeechMs: Long = 0L
    private var lastWakeUpSpeechMs: Long = 0L
    private var lastSleepSnoreMs: Long = 0L

    /**
     * Reacts when the robot experiences sudden pitch tilt or nervous wobble.
     */
    fun reactToWobble(enableSynth: Boolean, enableVoice: Boolean) {
        val now = System.currentTimeMillis()

        // 1. Synth sound effect (moderate cooldown: 5s)
        if (enableSynth && (now - lastWobbleSynthMs >= 5000L)) {
            lastWobbleSynthMs = now
            synth.playSynthSound(RobotSynthSound.WOBBLE_WARBLE, isManual = true)
        }

        // 2. Voice phrase (generous cooldown: 15s so robot never nags repeatedly)
        if (enableVoice && allowCannedPresets && (now - lastWobbleSpeechMs >= 15000L)) {
            if (!isSpeaking.value) {
                lastWobbleSpeechMs = now
                val wobbleLines = listOf(
                    "Whoa, that was close!",
                    "Eep! Slippery wheels!",
                    "Holding on tight!"
                )
                speak(wobbleLines.random(), pitchOverride = (voicePitch + 0.15f).coerceAtMost(2.1f), interrupt = false)
            }
        }
    }

    /**
     * Reacts when target is lost or robot is pouting.
     */
    fun reactToPout(enableSynth: Boolean, enableVoice: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastPoutSpeechMs < 4500L) return
        lastPoutSpeechMs = now

        if (enableSynth) {
            synth.playSynthSound(RobotSynthSound.POUT_WHIMPER, isManual = true)
        }
        if (enableVoice && allowCannedPresets) {
            if (!isSpeaking.value) {
                val poutLines = listOf(
                    "Hmph! Where did you go?",
                    "Aww, don't leave me behind!",
                    "Pout! Come back, friend!"
                )
                speak(poutLines.random(), pitchOverride = (voicePitch - 0.05f).coerceAtLeast(1.0f), interrupt = false)
            }
        }
    }

    /**
     * Plays a wake-up reaction when waking from sleepy napping mode.
     */
    fun reactToWakeUp(enableSynth: Boolean, enableVoice: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastWakeUpSpeechMs < 5000L) return
        lastWakeUpSpeechMs = now

        if (enableSynth) {
            synth.playSynthSound(RobotSynthSound.HAPPY_CHIRP, isManual = true)
        }
        if (enableVoice && allowCannedPresets) {
            val wakeLines = listOf(
                "Yawn! Huh? I am awake, I am awake!",
                "Yawn! Good morning! Ready to roll!",
                "Yay! You woke me up!"
            )
            speak(wakeLines.random(), interrupt = true)
        }
    }

    /**
     * Plays gentle kitten sleep purr for napping idle mode.
     */
    fun playSleepSnore(enableSynth: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastSleepSnoreMs < 6000L) return
        lastSleepSnoreMs = now

        if (enableSynth) {
            synth.playSynthSound(RobotSynthSound.SLEEP_SNORE_PURR, volume = 0.6f, isManual = true)
        }
    }

    /**
     * Reacts when the robot tips over / loses balance.
     */
    fun reactToFall(enableSynth: Boolean, enableVoice: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastFallSpeechMs < 5000L) return
        lastFallSpeechMs = now

        if (enableSynth) {
            synth.playSynthSound(RobotSynthSound.DIZZY_WARBLE, isManual = true)
        }
        if (enableVoice && allowCannedPresets) {
            val fallLines = listOf(
                "Oopsie! I fell over! Help me up please?",
                "Whoa, dizzy! My wheels slipped!",
                "Eep! I tipped over! Pick me up please!"
            )
            speak(fallLines.random(), pitchOverride = (voicePitch + 0.1f).coerceAtMost(2.0f))
        }
    }

    /**
     * Plays a random cute greeting line + happy chirp (for test button or tap interaction).
     */
    fun playRandomCuteGreeting(enableSynth: Boolean = true, enableVoice: Boolean = true) {
        if (enableSynth) {
            synth.playSynthSound(RobotSynthSound.HAPPY_CHIRP, isManual = true)
        }
        if (enableVoice && allowCannedPresets) {
            val greetings = listOf(
                "Hi there! I am Twippy, your balancing robot buddy!",
                "Yay! Let's go for a roll together! Meow!",
                "All systems green and feeling super cute today!",
                "Beep boop! Wave at me or show me a peace sign!",
                "Purr! I love balancing on my two wheels!"
            )
            speak(greetings.random(), interrupt = true)
        }
    }

    fun release() {
        neuralVoice.release()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isTtsReady = false
            _isSpeaking.value = false
        }
    }

    companion object {
        const val DEFAULT_CUTE_PITCH = 1.35f
        const val DEFAULT_CUTE_RATE = 1.08f

        @Volatile
        private var instance: CuteRobotVoiceManager? = null

        fun getInstance(context: Context): CuteRobotVoiceManager {
            return instance ?: synchronized(this) {
                instance ?: CuteRobotVoiceManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Returns a cute voice phrase for a given [RobotHandGesture].
         */
        fun getCutePhraseForGesture(gesture: RobotHandGesture): String? {
            return when (gesture) {
                RobotHandGesture.WAVE -> listOf(
                    "Hi there! Meow!",
                    "Yay! Hello friend!",
                    "Hiya! Ready to roll!"
                ).random()
                RobotHandGesture.LOVE -> listOf(
                    "Aww, I love you too!",
                    "Purr! You are my favorite human!",
                    "Yay, heart eyes for you!"
                ).random()
                RobotHandGesture.VICTORY -> listOf(
                    "Peace and victory! Yay!",
                    "Victory pose! We are awesome!",
                    "Yay! Peace sign!"
                ).random()
                RobotHandGesture.THUMB_UP -> listOf(
                    "Awesome job! Systems green!",
                    "Thumbs up! Let's go!",
                    "Yay, thank you!"
                ).random()
                RobotHandGesture.POINTING_UP -> listOf(
                    "Ooh, what is up there?",
                    "Looking up! To the stars!",
                    "Hmm, what are we pointing at?"
                ).random()
                RobotHandGesture.OPEN_PALM_STOP -> listOf(
                    "Eep! Backing up safely!",
                    "Whoa! Stopping and reversing!",
                    "Okay, giving you space!"
                ).random()
                RobotHandGesture.CLOSED_FIST -> listOf(
                    "Holding position! Focused!",
                    "Standing by!"
                ).random()
                RobotHandGesture.THUMB_DOWN -> listOf(
                    "Aww, did I do something wrong?",
                    "Oopsie, I will try harder!"
                ).random()
                RobotHandGesture.NONE -> null
            }
        }

        /**
         * Sanitizes markdown, symbols, and emojis so Android TTS speaks cleanly and naturally.
         */
        fun cleanTextForSpeech(rawText: String): String {
            val stripped = rawText
                // Remove code blocks
                .replace(Regex("""```[\s\S]*?```"""), "")
                // Remove inline code backticks
                .replace("`", "")
                // Remove markdown bold/italic/headers
                .replace(Regex("""[*_#~]+"""), "")
                // Replace arrows and symbols
                .replace("→", " to ")
                .replace("◀◀", "")
                // Remove kaomoji like (=^・ω・^=)
                .replace(Regex("""\(=[^)]+=\)"""), "meow")
                // Strip CJK, Hangul, Cyrillic, Thai, Arabic, and non-Latin scripts
                .replace(Regex("""[\p{InCJK_UNIFIED_IDEOGRAPHS}\p{InHIRAGANA}\p{InKATAKANA}\p{InHANGUL_SYLLABLES}\p{InCJK_SYMBOLS_AND_PUNCTUATION}\p{InCYRILLIC}\p{InARABIC}\p{InTHAI}]"""), "")
                // Keep only English letters, digits, standard punctuation, and whitespace
                .replace(Regex("""[^a-zA-Z0-9\s.,!?'\-]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            // If there are fewer than 2 alphabet letters, discard it so TTS doesn't speak bare symbols/numbers
            val letterCount = stripped.count { it in 'a'..'z' || it in 'A'..'Z' }
            return if (letterCount < 2) "" else stripped
        }
    }
}
