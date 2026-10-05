package com.cricas.geekcollection.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ItemEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        /** v2: progress status, platinum and backlog flags. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN progressStatus TEXT NOT NULL DEFAULT 'IN_PROGRESS'")
                db.execSQL("ALTER TABLE items ADD COLUMN platinum INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN backlog INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: cloud sync bookkeeping (stable id, dirty flag, tombstones). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE items ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE items ADD COLUMN deletedAt INTEGER")
                db.execSQL("UPDATE items SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_items_syncId ON items (syncId)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "geek_collection.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
