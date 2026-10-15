package com.teethut.twiprobot.domain.model

import android.graphics.Rect

enum class TrackingMode(val label: String, val description: String) {
    PAN_ONLY("Pan Only (Turret)", "Rotates on the spot to face you without driving"),
    FOLLOW("Full Follow", "Steers to face you and drives to maintain distance")
}

enum class TargetLockStatus {
    SEARCHING,
    LOCKED,
    LOST
}

enum class DistanceCategory(val label: String) {
    TOO_CLOSE("Too Close"),
    OPTIMAL("Optimal (~1.5m)"),
    TOO_FAR("Too Far"),
    UNKNOWN("--")
}

data class DetectedTarget(
    val trackingId: Int? = null,
    /** Normalized X in [-1.0, 1.0] where 0 is center, -1 is far left, +1 is far right */
    val normalizedX: Float = 0f,
    /** Normalized Y in [-1.0, 1.0] where 0 is center */
    val normalizedY: Float = 0f,
    /** Normalized width fraction of the frame [0.0, 1.0] */
    val normalizedWidth: Float = 0f,
    /** Normalized height fraction of the frame [0.0, 1.0] */
    val normalizedHeight: Float = 0f,
    val rawBoundingBox: Rect = Rect(),
    val timestampMs: Long = System.currentTimeMillis()
)

enum class TrackerResolution(val label: String, val width: Int, val height: Int) {
    RES_LOW("360p (Battery Saver)", 480, 360),
    RES_MEDIUM("480p (Balanced)", 640, 480),
    RES_HIGH("720p (High Res)", 1280, 720)
}

data class TrackerSettings(
    /** Target face height relative to frame height (e.g. 0.18 corresponds to ~1.5m away) */
    val targetDistanceFraction: Float = 0.18f,
    val maxSpeed: Float = 2000f,
    val maxSteer: Float = 1500f,
    val invertSteer: Boolean = false,
    val invertThrottle: Boolean = false,
    val allowReverse: Boolean = false,
    val targetFps: Int = 15, // 10, 15, 20, 30 fps
    val resolution: TrackerResolution = TrackerResolution.RES_MEDIUM,
    val deadbandX: Float = 0.06f,
    val deadbandDist: Float = 0.04f,
    val kpSteer: Float = 1.0f,
    val kpSpeed: Float = 1.0f,
    val waveToMeowEnabled: Boolean = true,
    val stopGestureEnabled: Boolean = true,
    val stopGestureBackwardSpeed: Float = 1200f,
    val invertBackwardDirection: Boolean = false,
    val cuteVoiceEnabled: Boolean = true,
    val synthSoundsEnabled: Boolean = true,
    val voicePitch: Float = 1.48f,
    val voiceRate: Float = 1.12f,
    val wobbleDetectionEnabled: Boolean = true,
    val wobblePitchThreshold: Float = 16.0f, // degrees of tilt before considered a wobble (normal balance is ±1..10 deg)
    val sceneCommentaryEnabled: Boolean = true
)

data class VisionTrackerState(
    val isTrackingActive: Boolean = false,
    val isCatFaceMode: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.FOLLOW,
    val lockStatus: TargetLockStatus = TargetLockStatus.SEARCHING,
    val currentTarget: DetectedTarget? = null,
    val distanceCategory: DistanceCategory = DistanceCategory.UNKNOWN,
    val calculatedSpeed: Float = 0f,
    val calculatedSteer: Float = 0f,
    val fps: Int = 0,
    val useFrontCamera: Boolean = true,
    val settings: TrackerSettings = TrackerSettings(),
    val hasCameraPermission: Boolean = false,
    val isWaveDetected: Boolean = false,
    val lastWaveDetectedTimeMs: Long = 0L,
    val isStopGestureActive: Boolean = false,
    val isFistHoldActive: Boolean = false,
    val activeGesture: RobotHandGesture = RobotHandGesture.NONE,
    val gestureConfidence: Float = 0f,
    val isRobotSpeaking: Boolean = false,
    val currentAngleOffset: Float = 6.0f,
    val isUpdatingOffset: Boolean = false,
    val offsetUpdateMessage: String? = null,
    val isLandscapeLocked: Boolean = true,
    val statusMessage: String = "Ready to Track",
    val activeFelineDirective: CatPersonalityDirective? = null,
    val felineDrives: FelineDrives = FelineDrives(),
    val catSliders: CatPersonalitySliders = CatPersonalitySliders(),
    val isThinkingAi: Boolean = false,
    val isListeningVoice: Boolean = false,
    val aiSpeechSubtitle: String? = null,
    val isGemmaLoaded: Boolean = false,
    val activeBackend: String = "",
    val detectedSceneObjects: List<DetectedSceneObject> = emptyList()
)

