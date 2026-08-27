package com.vmesspro.android.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Boundary between app/domain code and the native VPN core.
 * The UI must never call libbox/sing-box directly.
 */
interface CoreAdapter {
    val state: StateFlow<ConnectionState>

    suspend fun connect(profileId: String)
    suspend fun disconnect()
    suspend fun probe(profileId: String): ProbeResult
}

data class ProbeResult(
    val tcpLatencyMs: Long?,
    val httpRttMs: Long?,
    val success: Boolean,
    val error: String? = null,
)
