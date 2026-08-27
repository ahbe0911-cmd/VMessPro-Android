package com.vmesspro.android.domain.config

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface ParseResult {
    data class Success(val profile: ProxyProfile) : ParseResult
    data class Failure(val reason: String) : ParseResult
}

object ConfigParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): ParseResult {
        val value = raw.trim()
        return try {
            when {
                value.startsWith("vmess://", ignoreCase = true) -> parseVmess(value)
                value.startsWith("vless://", ignoreCase = true) -> parseVless(value)
                value.startsWith("trojan://", ignoreCase = true) -> parseTrojan(value)
                else -> ParseResult.Failure("پروتکل کانفیگ پشتیبانی نمی‌شود")
            }
        } catch (_: Exception) {
            ParseResult.Failure("کانفیگ معتبر نیست")
        }
    }

    private fun parseVmess(raw: String): ParseResult {
        val encoded = raw.substringAfter("vmess://").substringBefore('#').trim()
        val decoded = decodeBase64(encoded) ?: return ParseResult.Failure("VMess Base64 معتبر نیست")
        val obj = runCatching { json.parseToJsonElement(decoded) as JsonObject }.getOrNull()
            ?: return ParseResult.Failure("ساختار VMess معتبر نیست")

        val host = obj.string("add")?.trim().orEmpty()
        val port = obj.string("port")?.toIntOrNull() ?: obj["port"]?.jsonPrimitive?.intOrNull
        val uuid = obj.string("id")?.trim().orEmpty()
        if (host.isBlank() || port == null || port !in 1..65535 || !isUuid(uuid)) {
            return ParseResult.Failure("آدرس، پورت یا UUID در VMess معتبر نیست")
        }

        val network = obj.string("net")?.ifBlank { "tcp" } ?: "tcp"
        val tls = obj.string("tls")?.lowercase().orEmpty()
        val securityType = when {
            tls == "tls" -> "tls"
            tls == "reality" -> "reality"
            else -> "none"
        }
        val temporary = ProxyProfile(
            stableId = "",
            name = obj.string("ps")?.ifBlank { host } ?: host,
            protocol = ProxyProtocol.VMESS,
            server = host,
            port = port,
            credential = Credential.Vmess(
                uuid = uuid,
                alterId = obj.string("aid")?.toIntOrNull() ?: 0,
                cipher = obj.string("scy")?.ifBlank { "auto" } ?: "auto",
            ),
            transport = TransportSettings(
                type = network,
                headerType = obj.string("type"),
                host = obj.string("host"),
                path = obj.string("path"),
                serviceName = obj.string("serviceName"),
            ),
            security = SecuritySettings(
                type = securityType,
                serverName = obj.string("sni"),
                fingerprint = obj.string("fp"),
                alpn = splitCsv(obj.string("alpn")),
                flow = obj.string("flow"),
                realityPublicKey = obj.string("pbk"),
                realityShortId = obj.string("sid"),
            ),
            rawUri = raw,
        )
        return ParseResult.Success(temporary.copy(stableId = StableNodeId.from(temporary)))
    }

    private fun parseVless(raw: String): ParseResult = parseStandardUri(raw, ProxyProtocol.VLESS)

    private fun parseTrojan(raw: String): ParseResult = parseStandardUri(raw, ProxyProtocol.TROJAN)

    private fun parseStandardUri(raw: String, protocol: ProxyProtocol): ParseResult {
        val uri = URI(raw)
        val host = uri.host?.trim().orEmpty()
        val port = uri.port
        val userInfo = uri.rawUserInfo?.let(::decodePercent)?.trim().orEmpty()
        if (host.isBlank() || port !in 1..65535 || userInfo.isBlank()) {
            return ParseResult.Failure("آدرس، پورت یا اطلاعات احراز هویت معتبر نیست")
        }
        if (protocol == ProxyProtocol.VLESS && !isUuid(userInfo)) {
            return ParseResult.Failure("UUID در VLESS معتبر نیست")
        }

        val query = parseQuery(uri.rawQuery)
        val type = query["type"]?.ifBlank { "tcp" } ?: "tcp"
        val security = query["security"]?.lowercase()?.ifBlank { "none" } ?: "none"
        val credential = when (protocol) {
            ProxyProtocol.VLESS -> Credential.Vless(userInfo)
            ProxyProtocol.TROJAN -> Credential.Trojan(userInfo)
            ProxyProtocol.VMESS -> error("VMess uses JSON parser")
        }
        val temporary = ProxyProfile(
            stableId = "",
            name = uri.rawFragment?.let(::decodePercent)?.ifBlank { host } ?: host,
            protocol = protocol,
            server = host,
            port = port,
            credential = credential,
            transport = TransportSettings(
                type = type,
                headerType = query["headerType"],
                host = query["host"],
                path = query["path"],
                serviceName = query["serviceName"] ?: query["service_name"],
            ),
            security = SecuritySettings(
                type = security,
                serverName = query["sni"] ?: query["serverName"],
                fingerprint = query["fp"] ?: query["fingerprint"],
                alpn = splitCsv(query["alpn"]),
                flow = query["flow"],
                realityPublicKey = query["pbk"] ?: query["publicKey"],
                realityShortId = query["sid"] ?: query["shortId"],
            ),
            rawUri = raw,
        )

        if (security == "reality" && temporary.security.realityPublicKey.isNullOrBlank()) {
            return ParseResult.Failure("Public Key برای Reality وجود ندارد")
        }
        return ParseResult.Success(temporary.copy(stableId = StableNodeId.from(temporary)))
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "").trim()
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter('=', "")
            decodePercent(key) to decodePercent(value)
        }.toMap()
    }

    private fun decodePercent(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun splitCsv(value: String?): List<String> = value
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    internal fun decodeBase64(value: String): String? {
        val normalized = value.filterNot(Char::isWhitespace)
        if (normalized.isEmpty()) return null
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = runCatching { Base64.getDecoder().decode(padded) }.getOrElse {
            runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull() ?: return null
        }
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
    }
}
