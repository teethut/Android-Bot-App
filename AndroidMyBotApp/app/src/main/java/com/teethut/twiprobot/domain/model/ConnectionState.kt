package com.teethut.twiprobot.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val host: String, val port: Int) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

