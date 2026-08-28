package com.vmesspro.android.core

import com.vmesspro.android.data.local.NodeEntity

/**
 * Deterministic node ranking used for failover. A user-selected node remains first, while
 * fallback nodes are ordered by recent latency, observed failures and probe freshness.
 *
 * The selector deliberately uses only persisted measurements; it never fabricates a ping.
 */
object SmartNodeSelector {
    private const val UNKNOWN_LATENCY_MS = 8_000L
    private const val FAILED_PROBE_PENALTY_MS = 4_000L
    private const val FAILURE_PENALTY_MS = 2_500L
    private const val STALE_TEST_PENALTY_MS = 750L
    private const val STALE_AFTER_MS = 30L * 60L * 1000L

    fun order(
        nodes: List<NodeEntity>,
        preferredId: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<NodeEntity> {
        if (nodes.isEmpty()) return emptyList()

        val preferred = preferredId?.let { id -> nodes.firstOrNull { it.stableId == id } }
        val fallback = nodes
            .asSequence()
            .filterNot { it.stableId == preferred?.stableId }
            .sortedWith(
                compareBy<NodeEntity> { score(it, nowEpochMillis) }
                    .thenByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .toList()

        return if (preferred != null) listOf(preferred) + fallback else fallback
    }

    fun score(node: NodeEntity, nowEpochMillis: Long = System.currentTimeMillis()): Long {
        val latency = node.lastLatencyMs?.coerceIn(1L, 60_000L) ?: UNKNOWN_LATENCY_MS
        val failurePenalty = node.consecutiveFailures.coerceAtLeast(0).toLong() * FAILURE_PENALTY_MS
        val probePenalty = if (node.lastProbeSucceeded == false) FAILED_PROBE_PENALTY_MS else 0L
        val stalePenalty = when (val testedAt = node.lastTestedAt) {
            null -> STALE_TEST_PENALTY_MS
            else -> if (nowEpochMillis - testedAt > STALE_AFTER_MS) STALE_TEST_PENALTY_MS else 0L
        }
        return latency + failurePenalty + probePenalty + stalePenalty
    }
}
