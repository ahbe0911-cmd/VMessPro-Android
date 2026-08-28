package com.vmesspro.android.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkImportParserTest {

    @Test
    fun `multi line QR payload imports every config and subscription`() {
        val payload = """
            vless://11111111-1111-4111-8111-111111111111@example.com:443?security=tls&sni=example.com#VLESS-One
            trojan://very-secret@example.net:443?security=tls&sni=example.net#Trojan-Two
            https://subscription.example.org/client
        """.trimIndent()

        val result = BulkImportParser.parse(payload)

        assertEquals(2, result.validServerCount)
        assertEquals(1, result.subscriptionUrls.size)
        assertEquals("https://subscription.example.org/client", result.subscriptionUrls.single())
        assertEquals(0, result.invalidCount)
        assertEquals(0, result.duplicateCount)
        assertTrue(result.profiles.map { it.name }.containsAll(listOf("VLESS-One", "Trojan-Two")))
    }

    @Test
    fun `multi line QR payload deduplicates repeated server entries`() {
        val line = "vless://22222222-2222-4222-8222-222222222222@example.com:443?security=tls&sni=example.com#Same"
        val payload = "$line\n$line"

        val result = BulkImportParser.parse(payload)

        assertEquals(1, result.validServerCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(0, result.invalidCount)
    }
}
