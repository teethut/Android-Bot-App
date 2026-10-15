package com.teethut.twiprobot.data.ai

import android.util.Log
import com.teethut.twiprobot.data.sound.RobotSynthSound
import com.teethut.twiprobot.domain.model.CatPersonalityDirective
import com.teethut.twiprobot.domain.model.CatPersonalitySliders
import com.teethut.twiprobot.domain.model.FelineDrives
import com.teethut.twiprobot.domain.model.FelineMood
import com.teethut.twiprobot.domain.model.FelineWheelAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.system.measureTimeMillis

/**
 * High-speed feline cognitive persona engine that drives Twippy's personality
 * using on-device Gemma via LiteRT-LM.
 */
class CatPersonalityEngine(
    private val gemmaManager: GemmaInferenceManager
) {
    companion object {
        private const val TAG = "CatPersonalityEngine"
    }

    private var sliders = CatPersonalitySliders()
    private var drives = FelineDrives()
    private val recentContext = mutableListOf<String>()

    fun updateSliders(newSliders: CatPersonalitySliders) {
        sliders = newSliders
    }

    fun getSliders(): CatPersonalitySliders = sliders

    fun updateDrives(newDrives: FelineDrives) {
        drives = newDrives
    }

    fun getDrives(): FelineDrives = drives

    /**
     * Builds the structured JSON prompt for Gemma 4 LiteRT, injecting
     * personality sliders and dynamic cyber-cat drives.
     */
    fun buildPrompt(
        eventType: String,
        details: String,
        currentDrives: FelineDrives = drives
    ): String {
        val playfulnessDescriptor = when {
            sliders.playfulness > 0.6f -> "hyperactive kitten"
            sliders.playfulness < 0.4f -> "gentle sweet kitten"
            else -> "playful kitten"
        }
        val affectionPct = (currentDrives.affection * 100).toInt()
        val varietyTone = listOf(
            "Be cute and playful.",
            "Be cheerful and sweet.",
            "Be inquisitive and friendly.",
            "Be energetic and affectionate."
        ).random()

        val taskDesc = when {
            eventType.contains("SPOKE", ignoreCase = true) || eventType.contains("VOICE", ignoreCase = true) -> {
                "Human said: \"$details\". Reply directly in a cute spoken kitten sentence (under 10 words). $varietyTone"
            }
            eventType.startsWith("SCENE_OBJECT", ignoreCase = true) || eventType.contains("SCENE_OBJECT", ignoreCase = true) -> {
                "Spotted a $details. Give an adorable kitten observation (5 to 10 words). $varietyTone"
            }
            else -> {
                "Event: $eventType ($details). Give a brand new cute spoken reaction in English (5 to 10 words). $varietyTone"
            }
        }

        return """
        You are Twippy, a cyber-cat robot companion on two wheels ($playfulnessDescriptor, affection $affectionPct%).
        $taskDesc
        Spoken English only. No non-Latin characters.
        Return ONLY a single valid raw JSON object starting with { and ending with }:
        {"speech":"<your kitten reply>","vocalization":"HAPPY_CHIRP","mood":"HAPPY","wheel_action":"WIGGLE_DANCE","pupil_dilation":0.8,"ear_droop":0.0,"blush_alpha":0.6}
        JSON:
        """.trimIndent()
    }

    /**
     * Updates the internal cyber-cat drives based on the interaction event.
     */
    fun calculateNextDrives(current: FelineDrives, eventType: String): FelineDrives {
        return when {
            eventType.contains("SCENE_OBJECT", ignoreCase = true) -> {
                current.copy(
                    curiosity = (current.curiosity + 0.20f).coerceIn(0f, 1f),
                    energy = (current.energy + 0.05f).coerceIn(0f, 1f)
                )
            }
            eventType.contains("PET_EAR", ignoreCase = true) || eventType.contains("PET_CHIN", ignoreCase = true) -> {
                current.copy(
                    affection = (current.affection + 0.15f).coerceIn(0f, 1f),
                    curiosity = (current.curiosity + 0.05f).coerceIn(0f, 1f)
                )
            }
            eventType.contains("NOSE_BOOP", ignoreCase = true) || eventType.contains("BOOP", ignoreCase = true) -> {
                current.copy(
                    affection = (current.affection + 0.10f).coerceIn(0f, 1f),
                    curiosity = (current.curiosity + 0.15f).coerceIn(0f, 1f),
                    energy = (current.energy - 0.05f).coerceIn(0f, 1f)
                )
            }
            eventType.contains("WOBBLE", ignoreCase = true) || eventType.contains("WIGGLE", ignoreCase = true) -> {
                current.copy(
                    energy = (current.energy - 0.10f).coerceIn(0f, 1f),
                    curiosity = (current.curiosity + 0.08f).coerceIn(0f, 1f)
                )
            }
            eventType.contains("IDLE", ignoreCase = true) || eventType.contains("BORED", ignoreCase = true) -> {
                current.copy(
                    energy = (current.energy - 0.15f).coerceIn(0f, 1f),
                    curiosity = (current.curiosity - 0.15f).coerceIn(0f, 1f),
                    affection = (current.affection - 0.05f).coerceIn(0f, 1f)
                )
            }
            eventType.contains("WAVE", ignoreCase = true) || eventType.contains("PEACE", ignoreCase = true) -> {
                current.copy(
                    curiosity = (current.curiosity + 0.12f).coerceIn(0f, 1f),
                    affection = (current.affection + 0.08f).coerceIn(0f, 1f),
                    energy = (current.energy - 0.04f).coerceIn(0f, 1f)
                )
            }
            else -> {
                current.copy(
                    affection = (current.affection + 0.05f).coerceIn(0f, 1f),
                    energy = (current.energy - 0.03f).coerceIn(0f, 1f)
                )
            }
        }
    }

    /**
     * Streams raw response tokens from Gemma 4 LiteRT (or simulated tokens in sandbox mock mode).
     */
    fun streamFelineReaction(
        eventType: String,
        details: String,
        currentDrives: FelineDrives = drives
    ): Flow<String> {
        val prompt = buildPrompt(eventType, details, currentDrives)
        return if (gemmaManager.isModelLoaded) {
            gemmaManager.streamResponse(prompt)
        } else if (sliders.allowCannedPresets) {
            flow {
                val mockJson = generateMockFelineResponse(eventType)
                val parts = mockJson.split(" ")
                for (part in parts) {
                    emit("$part ")
                    delay(35L)
                }
            }.flowOn(Dispatchers.Default)
        } else {
            flow {
                val initStatus = "Purr! Gemma brain initializing. Ready in a second!"
                emit("""{"speech":"$initStatus","vocalization":"HAPPY_CHIRP","mood":"HAPPY","pupil_dilation":0.6,"ear_droop":0.0,"blush_alpha":0.4,"wheel_action":"HOLD_STILL"}""")
            }
        }
    }

    /**
     * Queries Gemma to react to a simulated or real human interaction event.
     */
    suspend fun reactToEvent(
        eventType: String,
        details: String,
        currentDrives: FelineDrives = drives
    ): CatPersonalityDirective = withContext(Dispatchers.Default) {
        val updatedDrives = calculateNextDrives(currentDrives, eventType)
        drives = updatedDrives

        val prompt = buildPrompt(eventType, details, updatedDrives)

        var rawResponse = ""
        val elapsedMs = measureTimeMillis {
            rawResponse = if (gemmaManager.isModelLoaded) {
                gemmaManager.generateResponse(prompt).getOrElse { err ->
                    Log.w(TAG, "Gemma inference failed: ${err.message}, using fallback", err)
                    if (sliders.allowCannedPresets) generateMockFelineResponse(eventType)
                    else """{"speech":"Purr! Neural circuits online and ready! Meow!","vocalization":"HAPPY_CHIRP","mood":"HAPPY","pupil_dilation":0.6,"ear_droop":0.0,"blush_alpha":0.4,"wheel_action":"HOLD_STILL"}"""
                }
            } else if (sliders.allowCannedPresets) {
                Log.d(TAG, "Gemma model not loaded in engine, using rule-based feline simulator")
                generateMockFelineResponse(eventType)
            } else {
                """{"speech":"Purr! Gemma brain is loading! Give me a second!","vocalization":"HAPPY_CHIRP","mood":"HAPPY","pupil_dilation":0.6,"ear_droop":0.0,"blush_alpha":0.4,"wheel_action":"HOLD_STILL"}"""
            }
        }

        val directive = parseFelineResponse(rawResponse, elapsedMs, eventType)

        // Store short memory
        synchronized(recentContext) {
            recentContext.add("$eventType -> ${directive.mood.name}")
            if (recentContext.size > 4) recentContext.removeAt(0)
        }

        directive
    }

    /**
     * Validates that speech generated by the LLM is intelligible English
     * without hallucinated CJK scripts, repetitive tokens, or raw JSON fields.
     */
    fun isValidEnglishSpeech(text: String): Boolean {
        if (text.isBlank()) return false
        val trimmed = text.trim()

        // Disallow raw JSON syntax or code remnants anywhere
        if (trimmed.contains("{") || trimmed.contains("}") || trimmed.contains("\"speech\":") || trimmed.contains("\"vocalization\":") || trimmed.contains("\"mood\":") || trimmed.contains("```")) {
            return false
        }

        // Disallow CJK, Hangul, Cyrillic, Arabic, Thai characters
        if (Regex("""[\p{InCJK_UNIFIED_IDEOGRAPHS}\p{InHIRAGANA}\p{InKATAKANA}\p{InHANGUL_SYLLABLES}\p{InCYRILLIC}\p{InARABIC}\p{InTHAI}]""").containsMatchIn(trimmed)) {
            return false
        }

        val cleanedText = trimmed.replace(Regex("""^[{}\\s"]+|[{}\\s"]+$"""), "")
        if (cleanedText.isBlank() || cleanedText.length > 160) return false

        // Must contain at least 2 ASCII alphabet letters (allows "Hi!", "Yay!", "Purr!", "Meow!", "Aww!")
        val letters = cleanedText.count { it in 'a'..'z' || it in 'A'..'Z' }
        if (letters < 2) return false

        // Check for repetitive word loops (e.g., "cat cat cat cat cat")
        val words = cleanedText.lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotBlank() }
        if (words.size >= 4 && words.distinct().size <= 2) return false

        return true
    }

    fun getNaturalFelineSpeech(eventType: String): String {
        return when {
            eventType.contains("NOSE", ignoreCase = true) || eventType.contains("BOOP", ignoreCase = true) -> listOf(
                "A-tchoo! Booped my button nose!",
                "Sniff sniff... tickles my whiskers!",
                "Eep! Nose boop alert! Meow!",
                "Aww, gentle on my cyber nose, human!"
            ).random()
            eventType.contains("PET_EAR", ignoreCase = true) -> listOf(
                "Purrrrr! Ear scratches are the absolute best!",
                "Mrrrp! Right behind the ear feels so good!",
                "Twitching my cyber ears with joy! Purr!",
                "Keep going, human! Best ear rubs ever!"
            ).random()
            eventType.contains("PET_CHIN", ignoreCase = true) || eventType.contains("CHIN", ignoreCase = true) -> listOf(
                "Mrrp! Chin rubs make my motors purr!",
                "Tilting my chin up... that is the sweet spot!",
                "Purrrrr... happiness overload!",
                "My wheel servos are purring like a kitten!"
            ).random()
            eventType.contains("PET", ignoreCase = true) -> listOf(
                "Purrr... Scratches feel so wonderful!",
                "Warm cyber cuddles for my favorite human!",
                "Meow! You give the coziest pets!",
                "Purr purr purr... so relaxing!"
            ).random()
            eventType.contains("WAVE", ignoreCase = true) -> listOf(
                "Meow! I see your wave! Let's play!",
                "Hi there! Waving my robotic tail back at you!",
                "Yay, hello human friend! Ready to roll!",
                "Paws up! Big friendly kitten wave to you!"
            ).random()
            eventType.contains("PEACE", ignoreCase = true) || eventType.contains("LOVE", ignoreCase = true) -> listOf(
                "Yay! Peace and kitty love! Purr purr!",
                "Victory paws! We are the best team!",
                "Sending big robotic heart eyes your way!",
                "Aww, peace sign! You made my blush glow!"
            ).random()
            eventType.contains("CLOSE", ignoreCase = true) -> listOf(
                "Eep! Too close, human! Giving you room!",
                "Whoa, personal space bubble activated! Backing up!",
                "Startled my sensors! Backing up safely!"
            ).random()
            eventType.contains("LEAVE", ignoreCase = true) || eventType.contains("AWAY", ignoreCase = true) -> listOf(
                "Hmph! Don't wander away from me!",
                "Hey! Where did you go, human?",
                "Scanning room... missing my companion!",
                "Come back soon! I am waiting right here!"
            ).random()
            eventType.contains("WOBBLE", ignoreCase = true) || eventType.contains("FALL", ignoreCase = true) -> listOf(
                "Whoa! Slippery floor! Wobble wobble!",
                "Balancing gyro engaged! Saved the landing!",
                "Dizzy wheels! Catching my balance!",
                "Whoa, steady motors! That was a close tilt!"
            ).random()
            eventType.contains("BORED", ignoreCase = true) || eventType.contains("IDLE", ignoreCase = true) -> listOf(
                "Yawn... Taking a quick kitten nap!",
                "Recharging my playfulness capacitor... zzz...",
                "Quiet room... dreaming of robotic mice!",
                "Napping mode active... poke me to play!"
            ).random()
            eventType.contains("SPOKE", ignoreCase = true) || eventType.contains("VOICE", ignoreCase = true) -> listOf(
                "Meooow! I love your friendly voice!",
                "Purr! Talking with you is so much fun!",
                "Listening closely with my cyber ears! Tell me more!",
                "Mrrp! You sound happy today! Let's chat!"
            ).random()
            else -> listOf(
                "Meow! Hello there, human friend!",
                "Purr! What shall we explore together?",
                "Systems green and feeling playful!",
                "Beep-meow! Ready for our next adventure!"
            ).random()
        }
    }

    fun sanitizeCandidateSpeech(text: String): String {
        if (text.isBlank()) return ""
        var cleaned = text.trim()
        // Strip thinking/reasoning blocks
        cleaned = cleaned.replace(Regex("""(?s)<(?:thought|think)>.*?</(?:thought|think)>"""), " ")
        // Strip any remaining XML/HTML-like tags
        cleaned = cleaned.replace(Regex("""<[^>]+>"""), " ")
        // Strip markdown backticks, asterisks, hashes
        cleaned = cleaned.replace(Regex("""[*_`#~]"""), " ")
        // Remove nested JSON curly braces or key remnants if any leaked
        cleaned = cleaned.replace(Regex("""\{[^}]*\}"""), " ")
        cleaned = cleaned.replace(Regex("""(?i)"(?:speech|vocalization|mood|wheel_action|pupil_dilation|ear_droop|blush_alpha)"\s*:\s*"""), " ")
        // Strip non-ASCII or unprintable characters (keep standard ASCII 32 to 126)
        cleaned = cleaned.replace(Regex("""[^\x20-\x7E]"""), " ")
        // Remove spaces preceding punctuation (e.g. "Hello !" -> "Hello!")
        cleaned = cleaned.replace(Regex("""\s+([.,!?:;])"""), "$1")
        // Remove leading/trailing quotes and punctuation colons
        cleaned = cleaned.replace(Regex("""^["':,\s]+|["'\s]+$"""), "")
        // Normalize whitespace
        cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
        return cleaned
    }

    fun getDynamicGemmaFallbackSpeech(eventType: String): String {
        return when {
            eventType.contains("NOSE", ignoreCase = true) || eventType.contains("BOOP", ignoreCase = true) -> listOf(
                "A-tchoo! Neural sensory spike on my button nose!",
                "Whiskers tingling! My sensors felt that boop!",
                "Eep! Nose sensor triggered! Beep-meow!"
            ).random()
            eventType.contains("PET", ignoreCase = true) -> listOf(
                "Purrrrr! Cybernetic petting receptors fully charged!",
                "My motor servos purr when you touch my ears!",
                "Warm cuddles detected! Purr purr purr!"
            ).random()
            eventType.contains("WAVE", ignoreCase = true) -> listOf(
                "Meow! Gemma vision spotted your wave! Ready to roll!",
                "Wave pattern recognized! Hello my favorite human!",
                "Paws up! Neural tracker locked onto your wave!"
            ).random()
            eventType.contains("PEACE", ignoreCase = true) || eventType.contains("LOVE", ignoreCase = true) -> listOf(
                "Yay! Peace gesture verified! Purr purr!",
                "Love algorithm running at maximum power!",
                "Heart detected! My blush LEDs are glowing!"
            ).random()
            eventType.contains("STOP", ignoreCase = true) || eventType.contains("FIST", ignoreCase = true) -> listOf(
                "Halt command received! Holding wheels steady!",
                "Stopping in place! Standing by, human!",
                "Brakes engaged! Wheels locked safely!"
            ).random()
            eventType.contains("WOBBLE", ignoreCase = true) || eventType.contains("FALL", ignoreCase = true) -> listOf(
                "Whoa! Gyro compensation engaged! Caught my balance!",
                "Slippery tilt! Stabilizing cyber wheels!",
                "Balancing algorithms active! Steady now!"
            ).random()
            eventType.contains("LOST", ignoreCase = true) || eventType.contains("LEAVE", ignoreCase = true) -> listOf(
                "Scanning room with my camera... where did you go?",
                "Target lost from view! Come back soon!",
                "Hmph! Missing my human companion!"
            ).random()
            else -> listOf(
                "Meow! Neural circuits active and listening!",
                "Purr! Local Gemma AI ready for our next move!",
                "Cyber-cat online and rolling with you!"
            ).random()
        }
    }

    fun parseFelineResponse(
        rawText: String,
        latencyMs: Long = 0L,
        fallbackEvent: String = ""
    ): CatPersonalityDirective {
        val noThoughts = rawText.replace(Regex("""(?s)<(?:thought|think)>.*?</(?:thought|think)>"""), "").trim()
        val cleaned = noThoughts
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStr = try {
            val startIdx = cleaned.indexOf('{')
            val endIdx = cleaned.lastIndexOf('}')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                cleaned.substring(startIdx, endIdx + 1)
            } else {
                cleaned
            }
        } catch (_: Exception) {
            cleaned
        }

        var candidateSpeech = ""
        var vocalizationStr = ""
        var moodStr = ""
        var wheelStr = ""
        var pupil = 0.6f
        var earDroop = 0.0f
        var blush = 0.4f

        try {
            val json = JSONObject(jsonStr)
            candidateSpeech = json.optString("speech", "").trim()
            vocalizationStr = json.optString("vocalization", "").uppercase()
            moodStr = json.optString("mood", "").uppercase()
            wheelStr = json.optString("wheel_action", "").uppercase()
            pupil = json.optString("pupil_dilation").toFloatOrNull()?.coerceIn(0.2f, 1.0f) ?: 0.6f
            earDroop = json.optString("ear_droop").toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 0.0f
            blush = json.optString("blush_alpha").toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 0.4f
        } catch (_: Exception) {
            // JSONObject failed or Android stub in unit test - fallback to regex extraction
            val speechMatch = Regex("""(?i)"speech"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"|(?i)"speech"\s*:\s*([^,}\n]+)""").find(cleaned)
            if (speechMatch != null) candidateSpeech = (speechMatch.groupValues[1].ifEmpty { speechMatch.groupValues[2] }).trim().replace("\\\"", "\"")

            val vocalMatch = Regex("""(?i)"vocalization"\s*:\s*"([^"]+)"|(?i)"vocalization"\s*:\s*([^,}\n]+)""").find(cleaned)
            if (vocalMatch != null) vocalizationStr = (vocalMatch.groupValues[1].ifEmpty { vocalMatch.groupValues[2] }).trim().uppercase()

            val moodMatch = Regex("""(?i)"mood"\s*:\s*"([^"]+)"|(?i)"mood"\s*:\s*([^,}\n]+)""").find(cleaned)
            if (moodMatch != null) moodStr = (moodMatch.groupValues[1].ifEmpty { moodMatch.groupValues[2] }).trim().uppercase()

            val wheelMatch = Regex("""(?i)"wheel_action"\s*:\s*"([^"]+)"|(?i)"wheel_action"\s*:\s*([^,}\n]+)""").find(cleaned)
            if (wheelMatch != null) wheelStr = (wheelMatch.groupValues[1].ifEmpty { wheelMatch.groupValues[2] }).trim().uppercase()

            val pupilMatch = Regex("""(?i)"pupil_dilation"\s*:\s*([0-9.]+)""").find(cleaned)
            if (pupilMatch != null) pupil = pupilMatch.groupValues[1].toFloatOrNull()?.coerceIn(0.2f, 1.0f) ?: 0.6f

            val earMatch = Regex("""(?i)"ear_droop"\s*:\s*([0-9.]+)""").find(cleaned)
            if (earMatch != null) earDroop = earMatch.groupValues[1].toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 0.0f

            val blushMatch = Regex("""(?i)"blush_alpha"\s*:\s*([0-9.]+)""").find(cleaned)
            if (blushMatch != null) blush = blushMatch.groupValues[1].toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 0.4f
        }

        // If no candidate speech from JSON or regex, check if raw cleaned text itself is a valid direct answer from Gemma
        if (candidateSpeech.isBlank() && (isValidEnglishSpeech(cleaned) || isValidEnglishSpeech(sanitizeCandidateSpeech(cleaned)))) {
            candidateSpeech = if (isValidEnglishSpeech(cleaned)) cleaned else sanitizeCandidateSpeech(cleaned)
        }

        val sanitizedCandidate = sanitizeCandidateSpeech(candidateSpeech)
        val isGemmaSpeechValid = isValidEnglishSpeech(candidateSpeech) ||
            (candidateSpeech.isNotBlank() && isValidEnglishSpeech(sanitizedCandidate))

        val speech = if (isGemmaSpeechValid) {
            val speechToUse = if (isValidEnglishSpeech(sanitizedCandidate)) sanitizedCandidate else candidateSpeech
            speechToUse.replace(Regex("""^["'\s]+|["'\s]+$"""), "")
        } else if (sliders.allowCannedPresets) {
            getNaturalFelineSpeech(fallbackEvent)
        } else {
            getDynamicGemmaFallbackSpeech(fallbackEvent)
        }

        val mood = runCatching { FelineMood.valueOf(moodStr) }.getOrNull()
            ?: inferMoodFromSpeech(speech, fallbackEvent)

        val vocalization = if (vocalizationStr.isNotBlank() && runCatching { RobotSynthSound.valueOf(vocalizationStr) }.isSuccess) {
            vocalizationStr
        } else {
            inferVocalizationFromMood(mood, fallbackEvent)
        }

        val wheelAction = runCatching { FelineWheelAction.valueOf(wheelStr) }.getOrNull()
            ?: inferWheelActionFromMood(mood, fallbackEvent)

        val source = if (isGemmaSpeechValid) "GEMMA_4" else if (sliders.allowCannedPresets) "PRESET" else "GEMMA_FALLBACK"

        return CatPersonalityDirective(
            speech = speech,
            vocalization = vocalization,
            mood = mood,
            pupilDilation = pupil,
            earDroop = earDroop,
            blushAlpha = blush,
            wheelAction = wheelAction,
            latencyMs = latencyMs,
            rawJson = if (jsonStr.isNotBlank()) jsonStr else rawText,
            source = source
        )
    }

    private fun inferMoodFromSpeech(speech: String, eventType: String): FelineMood {
        val lower = speech.lowercase()
        val lowerEvent = eventType.lowercase()
        return when {
            lower.contains("love") || lower.contains("purr") || lower.contains("hug") || lower.contains("scratch") || lowerEvent.contains("pet") -> FelineMood.AFFECTIONATE
            lower.contains("scared") || lower.contains("whoa") || lower.contains("oops") || lower.contains("eep") || lowerEvent.contains("wobble") || lowerEvent.contains("boop") -> FelineMood.STARTLED
            lower.contains("hmph") || lower.contains("leave") || lower.contains("grumpy") || lowerEvent.contains("leave") -> FelineMood.SASSY
            lower.contains("yawn") || lower.contains("nap") || lower.contains("sleep") || lowerEvent.contains("idle") -> FelineMood.SLEEPY
            lower.contains("what") || lower.contains("curious") || lower.contains("inspect") || lower.contains("pounce") || lowerEvent.contains("scene_object") -> FelineMood.CURIOUS
            else -> FelineMood.HAPPY
        }
    }

    private fun inferVocalizationFromMood(mood: FelineMood, eventType: String): String {
        return when (mood) {
            FelineMood.AFFECTIONATE -> RobotSynthSound.LOVE_PURR.name
            FelineMood.STARTLED -> if (eventType.contains("NOSE", ignoreCase = true) || eventType.contains("BOOP", ignoreCase = true)) RobotSynthSound.CAT_NOSE_SNEEZE.name else RobotSynthSound.WOBBLE_WARBLE.name
            FelineMood.SASSY -> RobotSynthSound.POUT_WHIMPER.name
            FelineMood.SLEEPY -> RobotSynthSound.SLEEP_SNORE_PURR.name
            FelineMood.CURIOUS -> RobotSynthSound.CURIOUS_BEEP.name
            else -> RobotSynthSound.HAPPY_CHIRP.name
        }
    }

    private fun inferWheelActionFromMood(mood: FelineMood, eventType: String): FelineWheelAction {
        return when {
            mood == FelineMood.AFFECTIONATE -> FelineWheelAction.PURR_ROCK
            mood == FelineMood.STARTLED -> FelineWheelAction.RECOIL_BACK
            eventType.contains("WOBBLE", ignoreCase = true) -> FelineWheelAction.RECOIL_BACK
            else -> FelineWheelAction.WIGGLE_DANCE
        }
    }

    private fun generateMockFelineResponse(eventType: String): String {
        return when {
            eventType.contains("NOSE", ignoreCase = true) || eventType.contains("BOOP", ignoreCase = true) ->
                """{"speech":"A-tchoo! Booped my button nose!","vocalization":"CAT_NOSE_SNEEZE","mood":"STARTLED","pupil_dilation":0.85,"ear_droop":0.2,"blush_alpha":0.8,"wheel_action":"RECOIL_BACK"}"""
            eventType.contains("PET_EAR", ignoreCase = true) ->
                """{"speech":"Purrrrr! Ear scratches are the absolute best!","vocalization":"LOVE_PURR","mood":"AFFECTIONATE","pupil_dilation":0.85,"ear_droop":0.1,"blush_alpha":0.8,"wheel_action":"PURR_ROCK"}"""
            eventType.contains("PET_CHIN", ignoreCase = true) ->
                """{"speech":"Mrrp! Chin rubs make my motors purr!","vocalization":"LOVE_PURR","mood":"AFFECTIONATE","pupil_dilation":0.75,"ear_droop":0.0,"blush_alpha":0.7,"wheel_action":"BUNT_FORWARD"}"""
            eventType.contains("WAVE", ignoreCase = true) ->
                """{"speech":"Meow! I see your wave! Let's play!","vocalization":"HAPPY_CHIRP","mood":"HAPPY","pupil_dilation":0.7,"ear_droop":0.0,"blush_alpha":0.5,"wheel_action":"WIGGLE_DANCE"}"""
            eventType.contains("PEACE", ignoreCase = true) || eventType.contains("LOVE", ignoreCase = true) ->
                """{"speech":"Yay! Peace and love! Purr purr!","vocalization":"PROUD_FANFARE","mood":"AFFECTIONATE","pupil_dilation":0.8,"ear_droop":0.0,"blush_alpha":0.9,"wheel_action":"WIGGLE_DANCE"}"""
            eventType.contains("CLOSE", ignoreCase = true) ->
                """{"speech":"Hiss! Back off, human! Too close!","vocalization":"CAT_HISS_STARTLE","mood":"STARTLED","pupil_dilation":1.0,"ear_droop":0.5,"blush_alpha":0.1,"wheel_action":"RECOIL_BACK"}"""
            eventType.contains("LEAVE", ignoreCase = true) || eventType.contains("LOST", ignoreCase = true) ->
                """{"speech":"Hmph! Did you just leave me all alone?","vocalization":"POUT_WHIMPER","mood":"SASSY","pupil_dilation":0.35,"ear_droop":0.5,"blush_alpha":0.2,"wheel_action":"HOLD_STILL"}"""
            eventType.contains("WOBBLE", ignoreCase = true) || eventType.contains("TILT", ignoreCase = true) ->
                """{"speech":"Whoa, dizzy! Hold onto my wheels!","vocalization":"WOBBLE_WARBLE","mood":"STARTLED","pupil_dilation":0.9,"ear_droop":0.7,"blush_alpha":0.0,"wheel_action":"HOLD_STILL"}"""
            eventType.contains("BORED", ignoreCase = true) || eventType.contains("IDLE", ignoreCase = true) ->
                """{"speech":"Yawn... Nobody is playing with me. Napping!","vocalization":"SLEEP_SNORE_PURR","mood":"SLEEPY","pupil_dilation":0.3,"ear_droop":0.6,"blush_alpha":0.3,"wheel_action":"HOLD_STILL"}"""
            eventType.contains("SPOKE", ignoreCase = true) || eventType.contains("VOICE", ignoreCase = true) ->
                """{"speech":"Meooow! Talking with you is wonderful!","vocalization":"CAT_MEOW_RISE","mood":"HAPPY","pupil_dilation":0.75,"ear_droop":0.0,"blush_alpha":0.6,"wheel_action":"WIGGLE_DANCE"}"""
            eventType.contains("SCENE_OBJECT", ignoreCase = true) ->
                """{"speech":"Ooh, a mysterious object to inspect! Can I pounce?","vocalization":"CURIOUS_BEEP","mood":"CURIOUS","pupil_dilation":0.8,"ear_droop":0.0,"blush_alpha":0.4,"wheel_action":"HOLD_STILL"}"""
            else ->
                """{"speech":"Meow! What are we doing next, human?","vocalization":"CURIOUS_BEEP","mood":"CURIOUS","pupil_dilation":0.6,"ear_droop":0.0,"blush_alpha":0.4,"wheel_action":"HOLD_STILL"}"""
        }
    }
}
