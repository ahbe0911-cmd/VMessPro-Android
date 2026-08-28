package com.vmesspro.android.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Boundary between app/domain code and the native VPN core.
 * The UI must never call libbox/sing-box directly.
 */
interface CoreAdapter {
    val state: StateFlow<ConnectionState>
    val telemetry: StateFlow<VpnTelemetry>

    suspend fun connect(profileId: String)
    suspend fun disconnect()
    suspend fun probe(profileId: String): ProbeResult
}

data class VpnTelemetry(
    val uploadBytesPerSecond: Long = 0,
    val downloadBytesPerSecond: Long = 0,
    val uploadedBytesTotal: Long = 0,
    val downloadedBytesTotal: Long = 0,
    val activeConnectionsIn: Int = 0,
    val activeConnectionsOut: Int = 0,
    val trafficAvailable: Boolean = false,
)

data class ProbeResult(
    val tcpLatencyMs: Long?,
    val httpRttMs: Long?,
    val success: Boolean,
    val error: String? = null,
)
