package com.vmesspro.android.domain.config

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigParserTest {
    @Test
    fun parsesVmessJson() {
        val json = """{"v":"2","ps":"Frankfurt","add":"example.com","port":"443","id":"11111111-1111-4111-8111-111111111111","aid":"0","scy":"auto","net":"ws","host":"cdn.example.com","path":"/ws","tls":"tls","sni":"example.com"}"""
        val uri = "vmess://" + Base64.getEncoder().withoutPadding().encodeToString(json.toByteArray())
        val result = ConfigParser.parse(uri)
        assertTrue(result is ParseResult.Success)
        val profile = (result as ParseResult.Success).profile
        assertEquals(ProxyProtocol.VMESS, profile.protocol)
        assertEquals("example.com", profile.server)
        assertEquals(443, profile.port)
        assertEquals("ws", profile.transport.type)
        assertEquals("tls", profile.security.type)
    }

    @Test
    fun parsesVlessReality() {
        val uri = "vless://11111111-1111-4111-8111-111111111111@example.com:443?security=reality&sni=www.example.com&fp=chrome&pbk=publicKey&sid=abcd&type=tcp&flow=xtls-rprx-vision#Reality"
        val result = ConfigParser.parse(uri)
        assertTrue(result is ParseResult.Success)
        val profile = (result as ParseResult.Success).profile
        assertEquals(ProxyProtocol.VLESS, profile.protocol)
        assertEquals("reality", profile.security.type)
        assertEquals("publicKey", profile.security.realityPublicKey)
        assertEquals("xtls-rprx-vision", profile.security.flow)
    }

    @Test
    fun stableIdIgnoresDisplayName() {
        val first = ConfigParser.parse("vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com#One") as ParseResult.Success
        val second = ConfigParser.parse("vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com#Two") as ParseResult.Success
        assertEquals(first.profile.stableId, second.profile.stableId)
    }

    @Test
    fun bulkImportDoesNotStopOnBrokenEntry() {
        val input = """
            vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com#One
            invalid-config
            vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com#Duplicate
        """.trimIndent()
        val preview = BulkImportParser.parse(input)
        assertEquals(1, preview.validServerCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(1, preview.invalidCount)
    }
}
