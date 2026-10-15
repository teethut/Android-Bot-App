package com.teethut.twiprobot.domain.model

enum class FelineMood(val label: String, val badgeColor: Long) {
    HAPPY("Happy / Content", 0xFF10B981),
    CURIOUS("Curious / Inquisitive", 0xFF06B6D4),
    AFFECTIONATE("Loving / Purring", 0xFFEC4899),
    SASSY("Sassy / Aloof", 0xFFF59E0B),
    STARTLED("Startled / Spooked", 0xFFEF4444),
    SLEEPY("Sleepy / Napping", 0xFF8B5CF6)
}

enum class FelineWheelAction(val label: String, val description: String) {
    HOLD_STILL("Hold Still", "Maintains stationary balance"),
    BUNT_FORWARD("Bunt / Head-Rub", "Gentle forward creep like nudging a leg"),
    RECOIL_BACK("Spooked Recoil", "Quick step back from sudden movement"),
    WIGGLE_DANCE("Happy Wiggle", "Playful alternating yaw oscillation"),
    PURR_ROCK("Purr Rocking", "Slow rhythmic pitch rocking like breathing")
}

data class CatPersonalityDirective(
    val speech: String = "",
    val vocalization: String = "HAPPY_CHIRP", // Maps to RobotSynthSound
    val mood: FelineMood = FelineMood.HAPPY,
    val pupilDilation: Float = 0.6f, // 0.2f (slits/focused) to 1.0f (huge chibi pupils)
    val earDroop: Float = 0.0f,      // 0.0f (perked) to 1.0f (drooped/timid)
    val blushAlpha: Float = 0.4f,    // 0.0f to 1.0f
    val wheelAction: FelineWheelAction = FelineWheelAction.HOLD_STILL,
    val latencyMs: Long = 0L,
    val rawJson: String = "",
    val source: String = "PRESET" // "GEMMA_4" or "PRESET"
) {
    val isGenerated: Boolean get() = source.startsWith("GEMMA")
}

data class CatPersonalitySliders(
    val playfulness: Float = 0.8f,   // 0.0 to 1.0
    val sassiness: Float = 0.3f,     // 0.0 to 1.0
    val voicePitch: Float = 1.35f,   // 0.8 to 2.0 (1.35x = sweet youthful kitten)
    val voiceRate: Float = 1.08f,    // 0.8 to 1.6
    val allowCannedPresets: Boolean = true // when false: Gemma-Only Mode (disable canned presets)
)

