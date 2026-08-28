package com.vmesspro.android.core

import com.vmesspro.android.domain.config.Credential
import com.vmesspro.android.domain.config.ProxyProfile
import com.vmesspro.android.domain.config.ProxyProtocol
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SingBoxConfigBuilder {
    fun build(profile: ProxyProfile, customDns: String?): String {
        val root = buildJsonObject {
            put("log", buildJsonObject {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", buildDns(customDns))
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("address", buildJsonArray {
                        add("172.19.0.1/30")
                        add("fdfe:dcba:9876::1/126")
                    })
                    put("mtu", 1500)
                    put("auto_route", true)
                    put("strict_route", true)
                    put("stack", "system")
                })
            })
            put("outbounds", buildJsonArray {
                add(buildProxyOutbound(profile))
                add(buildJsonObject {
                    put("type", "direct")
                    put("tag", "direct")
                })
            })
            put("route", buildJsonObject {
                put("final", "proxy")
            })
        }
        return root.toString()
    }

    private fun buildDns(customDns: String?): JsonObject = buildJsonObject {
        val normalized = customDns?.trim().orEmpty()
        put("servers", buildJsonArray {
            add(buildJsonObject {
                put("type", "local")
                put("tag", "local")
            })
            if (normalized.isNotBlank()) {
                parseCustomDns(normalized)?.let(::add)
            }
        })
        put("final", if (normalized.isNotBlank() && parseCustomDns(normalized) != null) "custom" else "local")
        put("strategy", "prefer_ipv4")
        put("cache_capacity", 4096)
    }

    private fun parseCustomDns(value: String): JsonObject? {
        if (value.startsWith("https://", ignoreCase = true)) {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            return buildJsonObject {
                put("type", "https")
                put("tag", "custom")
                put("server", host)
                if (uri.port > 0) put("server_port", uri.port)
                if (!uri.path.isNullOrBlank() && uri.path != "/") put("path", uri.path)
                if (!isIpLiteral(host)) put("domain_resolver", "local")
                put("tls", buildJsonObject {
                    put("enabled", true)
                    if (!isIpLiteral(host)) put("server_name", host)
                })
            }
        }

        val host = value.substringBefore(':').trim()
        val port = value.substringAfter(':', "53").toIntOrNull() ?: 53
        if (host.isBlank() || port !in 1..65535) return null
        return buildJsonObject {
            put("type", "udp")
            put("tag", "custom")
            put("server", host)
            put("server_port", port)
            if (!isIpLiteral(host)) put("domain_resolver", "local")
        }
    }

    private fun buildProxyOutbound(profile: ProxyProfile): JsonObject = buildJsonObject {
        put("tag", "proxy")
        put("server", profile.server)
        put("server_port", profile.port)

        when (profile.protocol) {
            ProxyProtocol.VMESS -> {
                val credential = profile.credential as Credential.Vmess
                put("type", "vmess")
                put("uuid", credential.uuid)
                put("security", credential.cipher.ifBlank { "auto" })
                put("alter_id", credential.alterId)
            }
            ProxyProtocol.VLESS -> {
                val credential = profile.credential as Credential.Vless
                put("type", "vless")
                put("uuid", credential.uuid)
                profile.security.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
                put("packet_encoding", "xudp")
            }
            ProxyProtocol.TROJAN -> {
                val credential = profile.credential as Credential.Trojan
                put("type", "trojan")
                put("password", credential.password)
            }
        }

        buildTls(profile)?.let { put("tls", it) }
        buildTransport(profile)?.let { put("transport", it) }
    }

    private fun buildTls(profile: ProxyProfile): JsonObject? {
        val security = profile.security
        val enabled = security.type.equals("tls", true) ||
            security.type.equals("reality", true) ||
            profile.protocol == ProxyProtocol.TROJAN
        if (!enabled) return null

        return buildJsonObject {
            put("enabled", true)
            val serverName = security.serverName?.takeIf { it.isNotBlank() }
                ?: profile.server.takeUnless(::isIpLiteral)
            serverName?.let { put("server_name", it) }
            if (security.alpn.isNotEmpty()) {
                put("alpn", JsonArray(security.alpn.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
            security.fingerprint?.takeIf { it.isNotBlank() }?.let { fingerprint ->
                put("utls", buildJsonObject {
                    put("enabled", true)
                    put("fingerprint", fingerprint)
                })
            }
            if (security.type.equals("reality", true)) {
                val publicKey = requireNotNull(security.realityPublicKey?.takeIf { it.isNotBlank() }) {
                    "Reality public key is missing"
                }
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", publicKey)
                    security.realityShortId?.takeIf { it.isNotBlank() }?.let { put("short_id", it) }
                })
            }
        }
    }

    private fun buildTransport(profile: ProxyProfile): JsonObject? {
        val transport = profile.transport
        var type = transport.type.lowercase().ifBlank { "tcp" }
        if (type == "h2") type = "http"
        if (type == "tcp" && transport.headerType.equals("http", true)) type = "http"
        if (type == "tcp" || type == "none") return null

        require(type in setOf("ws", "http", "grpc", "quic", "httpupgrade")) {
            "Transport '$type' is not supported by the pinned sing-box core"
        }
        return buildJsonObject {
            put("type", type)
            when (type) {
                "ws" -> {
                    transport.path?.takeIf { it.isNotBlank() }?.let { put("path", it) }
                    transport.host?.takeIf { it.isNotBlank() }?.let { host ->
                        put("headers", buildJsonObject { put("Host", host) })
                    }
                }
                "http" -> {
                    transport.path?.takeIf { it.isNotBlank() }?.let { put("path", it) }
                    transport.host?.takeIf { it.isNotBlank() }?.let { host ->
                        put("host", buildJsonArray { add(host) })
                    }
                }
                "grpc" -> transport.serviceName?.takeIf { it.isNotBlank() }?.let { put("service_name", it) }
                "httpupgrade" -> {
                    transport.host?.takeIf { it.isNotBlank() }?.let { put("host", it) }
                    transport.path?.takeIf { it.isNotBlank() }?.let { put("path", it) }
                }
            }
        }
    }

    private fun isIpLiteral(value: String): Boolean {
        val v = value.trim().removePrefix("[").removeSuffix("]")
        if (':' in v) return true
        val parts = v.split('.')
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }
}
