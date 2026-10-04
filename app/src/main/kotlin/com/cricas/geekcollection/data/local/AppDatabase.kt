package com.cricas.geekcollection.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "geek_collection.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
