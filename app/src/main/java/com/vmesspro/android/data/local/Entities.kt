package com.vmesspro.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nodes",
    indices = [
        Index(value = ["subscriptionId"]),
        Index(value = ["countryCode"]),
        Index(value = ["protocol"]),
        Index(value = ["lastUsedAt"]),
    ],
)
data class NodeEntity(
    @PrimaryKey val stableId: String,
    val subscriptionId: String?,
    val name: String,
    val countryCode: String?,
    val protocol: String,
    val host: String,
    val port: Int,
    /** Encrypted normalized configuration. Encryption is performed before persistence. */
    val encryptedConfig: String,
    val lastLatencyMs: Long?,
    val lastProbeSucceeded: Boolean?,
    val consecutiveFailures: Int = 0,
    val lastTestedAt: Long?,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Subscription URL encrypted before persistence. */
    val encryptedUrl: String,
    val enabled: Boolean = true,
    val autoRefresh: Boolean = true,
    val etag: String?,
    val lastModified: String?,
    val usedBytes: Long?,
    val totalBytes: Long?,
    val expiresAt: Long?,
    val lastRefreshAt: Long?,
    val lastRefreshError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val nodeId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "connection_history",
    indices = [Index(value = ["nodeId"]), Index(value = ["startedAt"])],
)
data class ConnectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nodeId: String?,
    val nodeDisplayName: String,
    val countryCode: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val downloadedBytes: Long = 0,
    val uploadedBytes: Long = 0,
    val status: String,
    val failureReason: String?,
)
