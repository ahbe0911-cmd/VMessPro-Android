package com.vmesspro.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NodeEntity::class,
        SubscriptionEntity::class,
        FavoriteEntity::class,
        ConnectionHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun connectionHistoryDao(): ConnectionHistoryDao
}
