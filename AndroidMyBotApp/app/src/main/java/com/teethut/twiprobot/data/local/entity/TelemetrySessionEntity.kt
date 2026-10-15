package com.teethut.twiprobot.data.local.entity

data class TelemetrySessionEntity(
    val id: Long = 0L,
    val sessionName: String,
    val startedAtMs: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val sampleCount: Int,
    val maxSpeed: Float = 0f,
    val csvData: String
)

