package com.teethut.twiprobot.data.repository

import com.teethut.twiprobot.data.datasource.RobotRestApi
import com.teethut.twiprobot.data.datasource.RobotWebSocketClient
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.PidSettings
import com.teethut.twiprobot.domain.model.RobotTelemetry
import com.teethut.twiprobot.domain.repository.RobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RobotRepositoryImpl(
    private val webSocketClient: RobotWebSocketClient = RobotWebSocketClient(),
    private val restApi: RobotRestApi = RobotRestApi(),
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : RobotRepository {

    private var activeHost: String = "192.168.4.1"
    private var activePort: Int = 80

    override val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState

    private val _currentTelemetry = MutableStateFlow(RobotTelemetry())
    override val currentTelemetry: StateFlow<RobotTelemetry> = _currentTelemetry.asStateFlow()

    override val telemetryFlow: Flow<RobotTelemetry> = webSocketClient.telemetryFlow

    init {
        appScope.launch {
            webSocketClient.telemetryFlow.collect { telemetry ->
                _currentTelemetry.value = telemetry
            }
        }
        // Auto-connect to robot on app launch so telemetry and commands work immediately
        connect(activeHost, activePort)
    }

    override fun connect(host: String, port: Int) {
        activeHost = host
        activePort = port
        webSocketClient.connect(host, port)
    }

    override fun disconnect() {
        webSocketClient.disconnect()
    }

    override fun sendDriveCommand(speed: Float, steer: Float) {
        webSocketClient.sendDriveCommand(speed, steer)
    }

    private var cachedPidSettings: PidSettings? = null

    override suspend fun fetchPidSettings(): Result<PidSettings> {
        val res = restApi.getPidSettings(activeHost, activePort)
        res.onSuccess { cachedPidSettings = it }
        return res
    }

    override suspend fun updatePidSettings(settings: PidSettings): Result<Unit> {
        val res = restApi.setPidSettings(settings, activeHost, activePort)
        if (res.isSuccess) {
            cachedPidSettings = settings
        }
        return res
    }

    override suspend fun updateAngleOffset(offset: Float): Result<Unit> {
        val current = cachedPidSettings ?: fetchPidSettings().getOrNull() ?: PidSettings()
        val updated = current.copy(angleOffset = offset)
        return updatePidSettings(updated)
    }

    companion object {
        // App-wide singleton instance for easy injection/sharing across ViewModels
        val instance: RobotRepository by lazy {
            RobotRepositoryImpl()
        }
    }
}

