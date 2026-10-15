package com.teethut.twiprobot.presentation.tracker.components

import com.teethut.twiprobot.domain.model.RobotHandGesture
import com.teethut.twiprobot.domain.model.TargetLockStatus

enum class CatMouthStyle {
    NEUTRAL_W,    // Cute :3 cat mouth
    OPEN_LAUGH,   // Joyful open smiling mouth with tongue
    SURPRISED_O,  // Small round surprised o mouth
    WAVY_SAD,     // Drooped sad wavy mouth when fallen
    CONFIDENT_SMIRK, // Playful proud smirk
    POUT_PUFFED   // Grumpy cute little wavy pout mouth
}

enum class CatEyeShape {
    NORMAL_ROUNDED, // Expressive anime eye with tracking iris & sparkles
    HAPPY_SQUINT,   // Joyful curved arc squint (^ ^)
    SURPRISED_WIDE, // Wide dilated round alert eyes (O O)
    PLAYFUL_WINK,   // One eye winking (>), other eye open
    HEART_EYES,     // Cute glowing pink heart eyes (♥ ♥)
    DIZZY_CROSS,    // Dizzy (X X) eyes when robot tilts or falls
    SLEEPY_CLOSED,  // Drooping curved sleepy relaxed eyes (u u)
    NERVOUS_WOBBLE, // Trembling alert pupils during balance wobble
    POUTY_HALF      // Half-lidded grumpy cute pout eyes
}

data class CatEmotionParameters(
    val eyeShape: CatEyeShape = CatEyeShape.NORMAL_ROUNDED,
    val mouthStyle: CatMouthStyle = CatMouthStyle.NEUTRAL_W,
    val eyeOpennessScale: Float = 1.0f,
    val pupilDilationScale: Float = 1.0f,
    val eyeSlantDeg: Float = 0f,
    val earDroopDeg: Float = 0f,
    val earFlattenBack: Boolean = false,
    val blushIntensity: Float = 1.0f,
    val hasStarSparkles: Boolean = false,
    val hasSweatDrop: Boolean = false,
    val sweatDropIntensity: Float = 0f,
    val isNapping: Boolean = false,
    val puffedCheeksScale: Float = 1.0f,
    val earTuftColorHex: Long = 0xFFFFFFFF,
    val statusBannerText: String? = null,
    val statusBannerColorHex: Long = 0xFFF472B6
)

object CatEmotionEngine {

    /**
     * Resolves the target emotional parameters from robot state, telemetry, MediaPipe gestures,
     * and Gemma 4 feline AI personality directives.
     */
    fun resolveEmotion(
        isBalancing: Boolean,
        gesture: RobotHandGesture,
        isWaveTriggered: Boolean,
        isStopGestureActive: Boolean,
        lockStatus: TargetLockStatus,
        isFistHoldActive: Boolean = false,
        pitchAngle: Float = 0f,
        idleDurationMs: Long = 0L,
        felineDirective: com.teethut.twiprobot.domain.model.CatPersonalityDirective? = null
    ): CatEmotionParameters {
        if (!isBalancing) {
            // Robot tilted or fallen
            return CatEmotionParameters(
                eyeShape = CatEyeShape.DIZZY_CROSS,
                mouthStyle = CatMouthStyle.WAVY_SAD,
                eyeOpennessScale = 1.0f,
                earDroopDeg = 15f,
                blushIntensity = 0.3f,
                statusBannerText = "😵 DIZZY / TILTED",
                statusBannerColorHex = 0xFFEF4444
            )
        }

        // Severe pitch tilt / wobble recovery while balancing (> 16.0 deg tilt)
        if (kotlin.math.abs(pitchAngle) > 16.0f) {
            return CatEmotionParameters(
                eyeShape = CatEyeShape.NERVOUS_WOBBLE,
                mouthStyle = CatMouthStyle.SURPRISED_O,
                eyeOpennessScale = 1.15f,
                pupilDilationScale = 1.25f,
                earDroopDeg = 10f,
                earFlattenBack = true,
                hasSweatDrop = true,
                sweatDropIntensity = 1.0f,
                blushIntensity = 0.6f,
                statusBannerText = "💦 WHOA! WOBBLE!",
                statusBannerColorHex = 0xFF38BDF8
            )
        }

        if (isFistHoldActive || gesture == RobotHandGesture.CLOSED_FIST) {
            // Fist: focused attentive halt / holding position
            return CatEmotionParameters(
                eyeShape = CatEyeShape.NORMAL_ROUNDED,
                mouthStyle = CatMouthStyle.NEUTRAL_W,
                eyeOpennessScale = 0.95f,
                pupilDilationScale = 0.9f,
                blushIntensity = 0.7f,
                statusBannerText = "✊ HALT / FOCUSED",
                statusBannerColorHex = 0xFF94A3B8
            )
        }

        if (isStopGestureActive || gesture == RobotHandGesture.OPEN_PALM_STOP) {
            // Stop command: alert wide eyes, ears pinned back, backing up safely
            return CatEmotionParameters(
                eyeShape = CatEyeShape.SURPRISED_WIDE,
                mouthStyle = CatMouthStyle.SURPRISED_O,
                eyeOpennessScale = 1.25f,
                pupilDilationScale = 1.35f,
                earFlattenBack = true,
                earDroopDeg = 12f,
                blushIntensity = 0.5f,
                statusBannerText = "✋ BACKING UP ◀◀",
                statusBannerColorHex = 0xFFF59E0B
            )
        }

        if (isWaveTriggered || gesture == RobotHandGesture.WAVE) {
            // Waving greeting: joyful squint (^ ^), star sparkles, open smile with pink tongue
            return CatEmotionParameters(
                eyeShape = CatEyeShape.HAPPY_SQUINT,
                mouthStyle = CatMouthStyle.OPEN_LAUGH,
                hasStarSparkles = true,
                blushIntensity = 1.4f,
                statusBannerText = "👋 (=^・ω・^=) *MEOW!*",
                statusBannerColorHex = 0xFFF472B6
            )
        }

        return when (gesture) {
            RobotHandGesture.VICTORY -> {
                // Peace / Victory: playful wink, proud happy smile
                CatEmotionParameters(
                    eyeShape = CatEyeShape.PLAYFUL_WINK,
                    mouthStyle = CatMouthStyle.CONFIDENT_SMIRK,
                    hasStarSparkles = true,
                    blushIntensity = 1.3f,
                    statusBannerText = "✌️ PEACE & VICTORY! ✨",
                    statusBannerColorHex = 0xFF38BDF8
                )
            }
            RobotHandGesture.LOVE -> {
                // Love sign: glowing heart eyes (♥ ♥), flustered blush
                CatEmotionParameters(
                    eyeShape = CatEyeShape.HEART_EYES,
                    mouthStyle = CatMouthStyle.NEUTRAL_W,
                    blushIntensity = 1.6f,
                    statusBannerText = "🤟 LOVE YOU! 💖",
                    statusBannerColorHex = 0xFFEC4899
                )
            }
            RobotHandGesture.THUMB_UP -> {
                // Thumbs Up: proud smirk, star sparkles
                CatEmotionParameters(
                    eyeShape = CatEyeShape.NORMAL_ROUNDED,
                    mouthStyle = CatMouthStyle.CONFIDENT_SMIRK,
                    hasStarSparkles = true,
                    blushIntensity = 1.2f,
                    statusBannerText = "👍 GREAT JOB! ⭐",
                    statusBannerColorHex = 0xFF10B981
                )
            }
            RobotHandGesture.THUMB_DOWN -> {
                // Thumbs Down: grumpy pout with puffed blushing cheeks
                CatEmotionParameters(
                    eyeShape = CatEyeShape.POUTY_HALF,
                    mouthStyle = CatMouthStyle.POUT_PUFFED,
                    puffedCheeksScale = 1.45f,
                    blushIntensity = 1.35f,
                    earDroopDeg = 6f,
                    statusBannerText = "😤 *HMPH!*",
                    statusBannerColorHex = 0xFFF43F5E
                )
            }
            RobotHandGesture.POINTING_UP -> {
                // Pointing up: curious upward slant
                CatEmotionParameters(
                    eyeShape = CatEyeShape.NORMAL_ROUNDED,
                    mouthStyle = CatMouthStyle.NEUTRAL_W,
                    eyeSlantDeg = -6f,
                    pupilDilationScale = 1.15f,
                    statusBannerText = "☝️ LOOK UP! 🚀",
                    statusBannerColorHex = 0xFF818CF8
                )
            }
            else -> {
                if (felineDirective != null) {
                    val eyeShape = when (felineDirective.mood) {
                        com.teethut.twiprobot.domain.model.FelineMood.HAPPY -> CatEyeShape.HAPPY_SQUINT
                        com.teethut.twiprobot.domain.model.FelineMood.AFFECTIONATE -> CatEyeShape.HEART_EYES
                        com.teethut.twiprobot.domain.model.FelineMood.CURIOUS -> CatEyeShape.NORMAL_ROUNDED
                        com.teethut.twiprobot.domain.model.FelineMood.SASSY -> CatEyeShape.PLAYFUL_WINK
                        com.teethut.twiprobot.domain.model.FelineMood.STARTLED -> CatEyeShape.SURPRISED_WIDE
                        com.teethut.twiprobot.domain.model.FelineMood.SLEEPY -> CatEyeShape.SLEEPY_CLOSED
                    }
                    val mouthStyle = when (felineDirective.mood) {
                        com.teethut.twiprobot.domain.model.FelineMood.HAPPY -> CatMouthStyle.OPEN_LAUGH
                        com.teethut.twiprobot.domain.model.FelineMood.SASSY -> CatMouthStyle.CONFIDENT_SMIRK
                        com.teethut.twiprobot.domain.model.FelineMood.STARTLED -> CatMouthStyle.SURPRISED_O
                        else -> CatMouthStyle.NEUTRAL_W
                    }
                    CatEmotionParameters(
                        eyeShape = eyeShape,
                        mouthStyle = mouthStyle,
                        pupilDilationScale = (felineDirective.pupilDilation * 1.5f).coerceIn(0.6f, 1.8f),
                        earDroopDeg = felineDirective.earDroop * 15f,
                        blushIntensity = (felineDirective.blushAlpha * 2.2f).coerceIn(0.4f, 2.0f),
                        hasStarSparkles = felineDirective.mood == com.teethut.twiprobot.domain.model.FelineMood.HAPPY ||
                            felineDirective.mood == com.teethut.twiprobot.domain.model.FelineMood.AFFECTIONATE,
                        hasSweatDrop = felineDirective.mood == com.teethut.twiprobot.domain.model.FelineMood.STARTLED,
                        sweatDropIntensity = if (felineDirective.mood == com.teethut.twiprobot.domain.model.FelineMood.STARTLED) 0.8f else 0f,
                        isNapping = felineDirective.mood == com.teethut.twiprobot.domain.model.FelineMood.SLEEPY,
                        statusBannerText = felineDirective.speech.ifBlank { null },
                        statusBannerColorHex = felineDirective.mood.badgeColor
                    )
                } else if (idleDurationMs >= 15000L && lockStatus != TargetLockStatus.LOCKED) {
                    CatEmotionParameters(
                        eyeShape = CatEyeShape.SLEEPY_CLOSED,
                        mouthStyle = CatMouthStyle.NEUTRAL_W,
                        eyeOpennessScale = 0.25f,
                        isNapping = true,
                        blushIntensity = 0.55f,
                        earDroopDeg = 8f,
                        statusBannerText = "💤 NAPPING... zZz",
                        statusBannerColorHex = 0xFF94A3B8
                    )
                } else if (lockStatus == TargetLockStatus.LOST) {
                    // Check if target was lost during active tracking follow
                    CatEmotionParameters(
                        eyeShape = CatEyeShape.POUTY_HALF,
                        mouthStyle = CatMouthStyle.POUT_PUFFED,
                        puffedCheeksScale = 1.3f,
                        blushIntensity = 1.1f,
                        statusBannerText = "🥺 Where did you go?",
                        statusBannerColorHex = 0xFFF59E0B
                    )
                } else {
                    // Default Happy Idle: tracking person
                    CatEmotionParameters(
                        eyeShape = CatEyeShape.NORMAL_ROUNDED,
                        mouthStyle = CatMouthStyle.NEUTRAL_W,
                        eyeOpennessScale = if (lockStatus == TargetLockStatus.LOCKED) 1.05f else 1.0f,
                        blushIntensity = if (lockStatus == TargetLockStatus.LOCKED) 1.15f else 0.85f,
                        statusBannerText = null
                    )
                }
            }
        }
    }
}

