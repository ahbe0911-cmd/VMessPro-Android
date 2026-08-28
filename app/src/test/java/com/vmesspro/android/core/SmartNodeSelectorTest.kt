package com.vmesspro.android.core

import com.vmesspro.android.data.local.NodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNodeSelectorTest {
    private val now = 2_000_000_000_000L

    @Test
    fun `preferred node stays first while healthy low latency fallback wins next`() {
        val preferred = node("preferred", latency = 700, failures = 1)
        val fast = node("fast", latency = 80, failures = 0)
        val slow = node("slow", latency = 900, failures = 0)

        val ordered = SmartNodeSelector.order(listOf(slow, fast, preferred), preferred.stableId, now)

        assertEquals(listOf("preferred", "fast", "slow"), ordered.map { it.stableId })
    }

    @Test
    fun `repeated failures outweigh a superficially lower ping`() {
        val flaky = node("flaky", latency = 40, failures = 3)
        val stable = node("stable", latency = 250, failures = 0)

        assertTrue(SmartNodeSelector.score(stable, now) < SmartNodeSelector.score(flaky, now))
    }

    private fun node(id: String, latency: Long?, failures: Int): NodeEntity = NodeEntity(
        stableId = id,
        subscriptionId = null,
        name = id,
        countryCode = null,
        protocol = "VLESS",
        host = "$id.example.com",
        port = 443,
        encryptedConfig = "encrypted",
        lastLatencyMs = latency,
        lastProbeSucceeded = true,
        consecutiveFailures = failures,
        lastTestedAt = now,
        lastUsedAt = null,
        createdAt = now - 1000,
        updatedAt = now,
    )
}
