package com.vmesspro.android.domain.config

import java.security.MessageDigest

object StableNodeId {
    fun from(profile: ProxyProfile): String {
        val canonical = buildString {
            append(profile.protocol.name).append('|')
            append(profile.server.lowercase()).append('|')
            append(profile.port).append('|')
            when (val credential = profile.credential) {
                is Credential.Vmess -> append(credential.uuid.lowercase()).append('|').append(credential.alterId).append('|').append(credential.cipher)
                is Credential.Vless -> append(credential.uuid.lowercase())
                is Credential.Trojan -> append(credential.password)
            }
            append('|').append(profile.transport.type.lowercase())
            append('|').append(profile.transport.headerType.orEmpty().lowercase())
            append('|').append(profile.transport.host.orEmpty().lowercase())
            append('|').append(profile.transport.path.orEmpty())
            append('|').append(profile.transport.serviceName.orEmpty())
            append('|').append(profile.security.type.lowercase())
            append('|').append(profile.security.serverName.orEmpty().lowercase())
            append('|').append(profile.security.fingerprint.orEmpty().lowercase())
            append('|').append(profile.security.alpn.joinToString(",") { it.lowercase() })
            append('|').append(profile.security.flow.orEmpty().lowercase())
            append('|').append(profile.security.realityPublicKey.orEmpty())
            append('|').append(profile.security.realityShortId.orEmpty())
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
