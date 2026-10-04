package com.cricas.geekcollection.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query(
        """
        SELECT * FROM items
        WHERE (:category IS NULL OR category = :category)
          AND (:favoritesOnly = 0 OR favorite = 1)
          AND (
            :query = '' OR
            title LIKE '%' || :query || '%' OR
            platform LIKE '%' || :query || '%' OR
            creator LIKE '%' || :query || '%' OR
            publisher LIKE '%' || :query || '%' OR
            notes LIKE '%' || :query || '%'
          )
        ORDER BY
          CASE WHEN :sort = 'title' THEN title END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'completion' THEN COALESCE(completionPercent, -1) END DESC,
          updatedAt DESC
        """
    )
    fun observe(query: String, category: String?, favoritesOnly: Boolean, sort: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: Long): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT COUNT(*) FROM items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE completionPercent >= 100")
    fun observeCompletedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("UPDATE items SET completionPercent = :percent, updatedAt = :now WHERE id = :id")
    suspend fun updateCompletion(id: Long, percent: Int, now: Long)

    @Query("UPDATE items SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean, now: Long)
}
