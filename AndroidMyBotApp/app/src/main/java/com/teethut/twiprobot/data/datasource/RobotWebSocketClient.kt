package com.teethut.twiprobot.data.datasource

import android.util.Log
import com.teethut.twiprobot.domain.model.ConnectionState
import com.teethut.twiprobot.domain.model.RobotTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class RobotWebSocketClient(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    private val tag = "RobotWebSocketClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket keep-alive
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var currentHost: String = "192.168.4.1"
    private var currentPort: Int = 80
    private var autoReconnectEnabled = false
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetryFlow = MutableSharedFlow<RobotTelemetry>(replay = 1, extraBufferCapacity = 64)
    val telemetryFlow: SharedFlow<RobotTelemetry> = _telemetryFlow.asSharedFlow()

    fun connect(host: String = "192.168.4.1", port: Int = 80) {
        currentHost = host
        currentPort = port
        autoReconnectEnabled = true
        reconnectJob?.cancel()

        initiateConnection()
    }

    private fun initiateConnection() {
        if (_connectionState.value is ConnectionState.Connected) {
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val url = "ws://$currentHost:$currentPort/ws"
        Log.d(tag, "Connecting to WebSocket at $url")

        val request = Request.Builder()
            .url(url)
            .build()

        activeWebSocket?.close(1000, "Reconnecting")
        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket Opened: $url")
                _connectionState.value = ConnectionState.Connected(currentHost, currentPort)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseTelemetry(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket Closed: $reason")
                _connectionState.value = ConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket Failure: ${t.message}")
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failure")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!autoReconnectEnabled) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2000)
            if (isActive && autoReconnectEnabled && _connectionState.value !is ConnectionState.Connected) {
                Log.d(tag, "Attempting auto-reconnect...")
                initiateConnection()
            }
        }
    }

    fun disconnect() {
        autoReconnectEnabled = false
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "User disconnected")
        activeWebSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun sendDriveCommand(speed: Float, steer: Float): Boolean {
        val socket = activeWebSocket ?: return false
        val sSpeed = speed.roundToInt()
        val sSteer = steer.roundToInt()
        val command = "V:$sSpeed,S:$sSteer"
        return socket.send(command)
    }

    private fun parseTelemetry(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            if (json.optString("type") != "telemetry") return

            val stateStr = json.optString("state", "UNKNOWN")
            val pitch = json.optDouble("pitch", 0.0).toFloat()
            val telemetry = RobotTelemetry(
                timestampMs = json.optLong("ms", System.currentTimeMillis()),
                state = stateStr,
                pitch = pitch,
                setpoint = json.optDouble("setpoint", 0.0).toFloat(),
                offset = json.optDouble("offset", 0.0).toFloat(),
                motorOut = json.optDouble("motorOut", 0.0).toFloat(),
                speed = json.optDouble("speed", 0.0).toFloat(),
                targetSpeed = json.optDouble("targetSpeed", 0.0).toFloat(),
                steer = json.optDouble("steer", 0.0).toFloat(),
                gForce = json.optDouble("gForce", 1.0).toFloat(),
                encLeft = json.optLong("encL", 0L),
                encRight = json.optLong("encR", 0L),
                freeHeapBytes = json.optLong("heap", 0L),
                isBalancing = stateStr == "BALANCING"
            )

            _telemetryFlow.tryEmit(telemetry)
        } catch (e: Exception) {
            Log.e(tag, "Error parsing telemetry JSON: ${e.message}")
        }
    }
}

