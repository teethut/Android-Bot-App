package com.teethut.twiprobot.domain.model

data class PidSettings(
    // Inner Loop (Balance Angle)
    val kp1: Float = 40.0f,
    val ki1: Float = 0.1f,
    val kd1: Float = 0.0f,

    // Outer Loop (Speed Control)
    val kp2: Float = -0.0015f,
    val ki2: Float = 0.0f,
    val kd2: Float = 0.00002f,

    // System Settings
    val angleOffset: Float = 6.0f,
    val maxAccel: Float = 40000.0f,
    val maxStepsPerSec: Float = 20000.0f,

    // Steering Tuning
    val maxSteer: Float = 2500.0f,
    val steerAccel: Float = 8000.0f
)

