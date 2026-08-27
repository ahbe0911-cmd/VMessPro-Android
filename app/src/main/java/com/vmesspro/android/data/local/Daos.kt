package com.vmesspro.android.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE stableId = :id LIMIT 1")
    suspend fun getById(id: String): NodeEntity?

    @Query("SELECT * FROM nodes WHERE subscriptionId = :subscriptionId")
    suspend fun getBySubscription(subscriptionId: String): List<NodeEntity>

    @Upsert
    suspend fun upsert(nodes: List<NodeEntity>)

    @Query("DELETE FROM nodes WHERE subscriptionId = :subscriptionId AND stableId NOT IN (:keepIds)")
    suspend fun deleteMissingFromSubscription(subscriptionId: String, keepIds: List<String>)

    @Query("DELETE FROM nodes WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: String)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionEntity?

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)
}

@Dao
interface FavoriteDao {
    @Query("SELECT nodeId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE nodeId = :nodeId")
    suspend fun remove(nodeId: String)
}

@Dao
interface ConnectionHistoryDao {
    @Query("SELECT * FROM connection_history ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ConnectionHistoryEntity>>

    @Insert
    suspend fun insert(history: ConnectionHistoryEntity): Long

    @Query("UPDATE connection_history SET endedAt = :endedAt, downloadedBytes = :downloadedBytes, uploadedBytes = :uploadedBytes, status = :status, failureReason = :failureReason WHERE id = :id")
    suspend fun finish(
        id: Long,
        endedAt: Long,
        downloadedBytes: Long,
        uploadedBytes: Long,
        status: String,
        failureReason: String?,
    )
}
