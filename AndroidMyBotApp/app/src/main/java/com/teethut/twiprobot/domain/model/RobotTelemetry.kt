package com.teethut.twiprobot.domain.model

data class RobotTelemetry(
    val timestampMs: Long = 0L,
    val state: String = "DISCONNECTED",
    val pitch: Float = 0.0f,
    val setpoint: Float = 0.0f,
    val offset: Float = 0.0f,
    val motorOut: Float = 0.0f,
    val speed: Float = 0.0f,
    val targetSpeed: Float = 0.0f,
    val steer: Float = 0.0f,
    val gForce: Float = 1.0f,
    val encLeft: Long = 0L,
    val encRight: Long = 0L,
    val freeHeapBytes: Long = 0L,
    val isBalancing: Boolean = false
)

