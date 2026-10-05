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
        WHERE deletedAt IS NULL
          AND (:category IS NULL OR category = :category)
          AND (:favoritesOnly = 0 OR favorite = 1)
          AND (
            :progress IS NULL OR
            (:progress = 'BACKLOG' AND backlog = 1) OR
            (:progress = 'PLATINUM' AND platinum = 1) OR
            (:progress NOT IN ('BACKLOG', 'PLATINUM') AND progressStatus = :progress AND backlog = 0 AND completionPercent IS NOT NULL)
          )
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
    fun observe(query: String, category: String?, favoritesOnly: Boolean, progress: String?, sort: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: Long): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items WHERE deletedAt IS NULL AND completionPercent >= 100")
    fun observeCompletedCount(): Flow<Int>

    // ---- cloud sync
    @Query("SELECT * FROM items WHERE dirty = 1")
    suspend fun getDirty(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ItemEntity?

    @Query("UPDATE items SET dirty = 0 WHERE syncId IN (:syncIds)")
    suspend fun markClean(syncIds: List<String>)

    @Query("UPDATE items SET deletedAt = :now, updatedAt = :now, dirty = 1, localImagePath = NULL WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM items WHERE syncId = :syncId")
    suspend fun hardDeleteBySyncId(syncId: String)

    @Query("DELETE FROM items WHERE syncId IN (:syncIds) AND deletedAt IS NOT NULL")
    suspend fun purgeTombstones(syncIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("UPDATE items SET completionPercent = :percent, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun updateCompletion(id: Long, percent: Int, now: Long)

    @Query("UPDATE items SET favorite = :favorite, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE items SET progressStatus = :status, platinum = :platinum, completionPercent = :percent, backlog = :backlog, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, platinum: Boolean, percent: Int?, backlog: Boolean, now: Long)
}
