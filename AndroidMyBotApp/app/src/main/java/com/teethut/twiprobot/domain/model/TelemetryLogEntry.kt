package com.teethut.twiprobot.domain.model

data class TelemetryLogEntry(
    val timestampMs: Long,
    val state: String,
    val pitch: Float,
    val setpoint: Float,
    val offset: Float,
    val gForce: Float = 1.0f,
    val motorOut: Float,
    val speed: Float,
    val targetSpeed: Float,
    val steer: Float,
    val encLeft: Long,
    val encRight: Long,
    val freeHeapBytes: Long
) {
    fun toCsvRow(): String {
        return "$timestampMs,$state,$pitch,$setpoint,$offset,$gForce,$motorOut,$speed,$targetSpeed,$steer,$encLeft,$encRight,$freeHeapBytes"
    }

    companion object {
        const val CSV_HEADER = "timestamp_ms,state,pitch_deg,setpoint_deg,offset_deg,g_force,motor_out,speed_est,target_speed,steer_offset,enc_left,enc_right,heap_bytes"

        fun fromTelemetry(t: RobotTelemetry): TelemetryLogEntry {
            return TelemetryLogEntry(
                timestampMs = t.timestampMs,
                state = t.state,
                pitch = t.pitch,
                setpoint = t.setpoint,
                offset = t.offset,
                gForce = t.gForce,
                motorOut = t.motorOut,
                speed = t.speed,
                targetSpeed = t.targetSpeed,
                steer = t.steer,
                encLeft = t.encLeft,
                encRight = t.encRight,
                freeHeapBytes = t.freeHeapBytes
            )
        }
    }
}

