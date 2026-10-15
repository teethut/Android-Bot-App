package com.teethut.twiprobot.domain.model

data class DetectedSceneObject(
    val label: String,
    val confidence: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

