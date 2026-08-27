package com.vmesspro.android.core

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Preparing : ConnectionState
    data object Connecting : ConnectionState
    data object Verifying : ConnectionState
    data class Connected(val sinceEpochMillis: Long) : ConnectionState
    data object Reconnecting : ConnectionState
    data class Error(val reason: String) : ConnectionState
}
