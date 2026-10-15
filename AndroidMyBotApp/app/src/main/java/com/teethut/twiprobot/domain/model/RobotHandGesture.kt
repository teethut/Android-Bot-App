package com.teethut.twiprobot.domain.model

enum class RobotHandGesture(
    val label: String,
    val icon: String,
    val description: String
) {
    NONE("None", "", "No gesture active"),
    OPEN_PALM_STOP("Stop (Open Palm)", "✋", "Hold open palm steady to drive robot backward safely"),
    WAVE("Waving", "👋", "Wave hand side-to-side to greet robot with a cute meow"),
    CLOSED_FIST("Fist (Halt)", "✊", "Hold closed fist"),
    POINTING_UP("Pointing Up", "☝️", "Pointing index finger upward"),
    THUMB_UP("Thumbs Up", "👍", "Thumbs up approval gesture"),
    THUMB_DOWN("Thumbs Down", "👎", "Thumbs down gesture"),
    VICTORY("Peace / Victory", "✌️", "Peace / Victory V-sign"),
    LOVE("Love", "🤟", "I Love You hand sign")
}

