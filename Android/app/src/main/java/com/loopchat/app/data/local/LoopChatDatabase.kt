package com.loopchat.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.loopchat.app.data.local.dao.ConversationDao
import com.loopchat.app.data.local.dao.MessageDao
import com.loopchat.app.data.local.dao.UserDao
import com.loopchat.app.data.local.entities.ConversationEntity
import com.loopchat.app.data.local.entities.MessageEntity
import com.loopchat.app.data.local.entities.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        com.loopchat.app.data.local.entities.GroupEntity::class,
        com.loopchat.app.data.local.entities.GroupMemberEntity::class
    ],
    version = 6, // Bumped from 5→6 to force schema re-check and fix integrity crash on devices with old DB
    exportSchema = false
)
abstract class LoopChatDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): com.loopchat.app.data.local.dao.GroupDao

    companion object {
        @Volatile
        private var INSTANCE: LoopChatDatabase? = null

        fun getDatabase(context: Context): LoopChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LoopChatDatabase::class.java,
                    "loopchat_database"
                )
                    // Wipe and rebuild DB for any version that doesn't have an explicit migration.
                    // Covers devices still running schema version 1-5 (the hash-mismatch crash).
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
