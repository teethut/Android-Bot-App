package com.teethut.twiprobot.domain.repository

import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.PidSettings
import com.teethut.twiprobot.domain.model.RobotTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RobotRepository {
    val connectionState: StateFlow<ConnectionState>
    val telemetryFlow: Flow<RobotTelemetry>
    val currentTelemetry: StateFlow<RobotTelemetry>

    fun connect(host: String = "192.168.4.1", port: Int = 80)
    fun disconnect()
    fun sendDriveCommand(speed: Float, steer: Float)
    suspend fun fetchPidSettings(): Result<PidSettings>
    suspend fun updatePidSettings(settings: PidSettings): Result<Unit>
    suspend fun updateAngleOffset(offset: Float): Result<Unit> = Result.success(Unit)
}

