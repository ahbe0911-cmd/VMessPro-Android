package com.vmesspro.android.core

import com.vmesspro.android.domain.config.Credential
import com.vmesspro.android.domain.config.ProxyProfile
import com.vmesspro.android.domain.config.ProxyProtocol
import com.vmesspro.android.domain.config.SecuritySettings
import com.vmesspro.android.domain.config.TransportSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun vlessRealityProducesRealityAndGrpcFields() {
        val profile = ProxyProfile(
            stableId = "node",
            name = "Reality",
            protocol = ProxyProtocol.VLESS,
            server = "example.com",
            port = 443,
            credential = Credential.Vless("11111111-1111-4111-8111-111111111111"),
            transport = TransportSettings(type = "grpc", serviceName = "edge"),
            security = SecuritySettings(
                type = "reality",
                serverName = "www.microsoft.com",
                fingerprint = "chrome",
                flow = "xtls-rprx-vision",
                realityPublicKey = "public-key",
                realityShortId = "abcd",
            ),
            rawUri = "vless://test",
        )

        val root = json.parseToJsonElement(SingBoxConfigBuilder.build(profile, null)).jsonObject
        val outbound = root["outbounds"]!!.jsonArray.first().jsonObject
        assertEquals("vless", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("xtls-rprx-vision", outbound["flow"]!!.jsonPrimitive.content)
        assertEquals("grpc", outbound["transport"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val tls = outbound["tls"]!!.jsonObject
        assertTrue(tls["reality"]!!.jsonObject["enabled"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("public-key", tls["reality"]!!.jsonObject["public_key"]!!.jsonPrimitive.content)
    }

    @Test
    fun vmessWebSocketProducesHostHeader() {
        val profile = ProxyProfile(
            stableId = "node",
            name = "VMess",
            protocol = ProxyProtocol.VMESS,
            server = "1.2.3.4",
            port = 443,
            credential = Credential.Vmess(
                uuid = "22222222-2222-4222-8222-222222222222",
                alterId = 0,
                cipher = "auto",
            ),
            transport = TransportSettings(type = "ws", host = "cdn.example.com", path = "/ws"),
            security = SecuritySettings(type = "tls", serverName = "cdn.example.com"),
            rawUri = "vmess://test",
        )

        val root = json.parseToJsonElement(SingBoxConfigBuilder.build(profile, "1.1.1.1")).jsonObject
        val outbound = root["outbounds"]!!.jsonArray.first().jsonObject
        assertEquals("vmess", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("cdn.example.com", outbound["transport"]!!.jsonObject["headers"]!!.jsonObject["Host"]!!.jsonPrimitive.content)
        assertEquals("custom", root["dns"]!!.jsonObject["final"]!!.jsonPrimitive.content)
    }
}
