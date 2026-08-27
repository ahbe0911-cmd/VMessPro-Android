package com.vmesspro.android.domain.config

data class ProxyProfile(
    val stableId: String,
    val name: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val credential: Credential,
    val transport: TransportSettings,
    val security: SecuritySettings,
    val rawUri: String,
)

enum class ProxyProtocol { VMESS, VLESS, TROJAN }

sealed interface Credential {
    data class Vmess(
        val uuid: String,
        val alterId: Int,
        val cipher: String,
    ) : Credential

    data class Vless(val uuid: String) : Credential

    data class Trojan(val password: String) : Credential
}

data class TransportSettings(
    val type: String = "tcp",
    val headerType: String? = null,
    val host: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
)

data class SecuritySettings(
    val type: String = "none",
    val serverName: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val flow: String? = null,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
)
