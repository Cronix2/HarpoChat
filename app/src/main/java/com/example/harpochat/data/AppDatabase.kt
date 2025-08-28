package com.example.harpochat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [ThreadEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun build(ctx: Context, factory: SupportFactory): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "harpo_encrypted.db")
                .openHelperFactory(factory)  // <-- SQLCipher
                .fallbackToDestructiveMigration()
                .build()
    }
}
