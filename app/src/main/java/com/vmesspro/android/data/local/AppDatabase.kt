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
    // Version 2 intentionally forces legacy v1 installations to reopen through Room's
    // destructive fallback. Several development APKs used version 1 while the schema was
    // still evolving, which can otherwise produce an identity-hash crash on real devices.
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun connectionHistoryDao(): ConnectionHistoryDao
}
