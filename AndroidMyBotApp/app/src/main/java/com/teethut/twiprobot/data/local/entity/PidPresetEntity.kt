package com.teethut.twiprobot.data.local.entity

import com.teethut.twiprobot.domain.model.PidSettings

data class PidPresetEntity(
    val id: Long = 0L,
    val name: String,
    val surfaceType: String = "Custom",
    val kp1: Float,
    val ki1: Float,
    val kd1: Float,
    val kp2: Float,
    val ki2: Float,
    val kd2: Float,
    val angleOffset: Float = 6.0f,
    val maxAccel: Float = 40000.0f,
    val maxStepsPerSec: Float = 20000.0f,
    val maxSteer: Float = 2500.0f,
    val steerAccel: Float = 8000.0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toPidSettings(): PidSettings {
        return PidSettings(
            kp1 = kp1, ki1 = ki1, kd1 = kd1,
            kp2 = kp2, ki2 = ki2, kd2 = kd2,
            angleOffset = angleOffset,
            maxAccel = maxAccel,
            maxStepsPerSec = maxStepsPerSec,
            maxSteer = maxSteer,
            steerAccel = steerAccel
        )
    }

    companion object {
        fun fromPidSettings(name: String, surfaceType: String, settings: PidSettings, id: Long = 0L): PidPresetEntity {
            return PidPresetEntity(
                id = id,
                name = name,
                surfaceType = surfaceType,
                kp1 = settings.kp1,
                ki1 = settings.ki1,
                kd1 = settings.kd1,
                kp2 = settings.kp2,
                ki2 = settings.ki2,
                kd2 = settings.kd2,
                angleOffset = settings.angleOffset,
                maxAccel = settings.maxAccel,
                maxStepsPerSec = settings.maxStepsPerSec,
                maxSteer = settings.maxSteer,
                steerAccel = settings.steerAccel
            )
        }
    }
}

