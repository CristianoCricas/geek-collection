package com.cricas.geekcollection.data

import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.ProgressStatus
import com.cricas.geekcollection.data.local.ItemDao
import com.cricas.geekcollection.data.local.ItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortOrder(val key: String) { RECENT("recent"), TITLE("title"), COMPLETION("completion") }

/** Library filter on progress: a status, or one of the flags. */
enum class ProgressFilter(val key: String) {
    BACKLOG("BACKLOG"),
    IN_PROGRESS(ProgressStatus.IN_PROGRESS.name),
    PAUSED(ProgressStatus.PAUSED.name),
    ABANDONED(ProgressStatus.ABANDONED.name),
    FINISHED(ProgressStatus.FINISHED.name),
    PLATINUM("PLATINUM"),
}

class ItemRepository(private val dao: ItemDao, private val images: ImageStore) {

    fun observeItems(
        query: String,
        category: ItemCategory?,
        favoritesOnly: Boolean,
        progress: ProgressFilter?,
        sort: SortOrder,
    ): Flow<List<CollectionItem>> =
        dao.observe(query.trim(), category?.name, favoritesOnly, progress?.key, sort.key)
            .map { list -> list.map(ItemEntity::toDomain) }

    fun observeItem(id: Long): Flow<CollectionItem?> = dao.observeById(id).map { it?.toDomain() }

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeCompletedCount(): Flow<Int> = dao.observeCompletedCount()

    suspend fun getItem(id: Long): CollectionItem? = dao.getById(id)?.toDomain()

    /** Inserts or updates, returning the item id. */
    suspend fun save(item: CollectionItem): Long {
        val now = System.currentTimeMillis()
        val normalized = item.normalized()
        return if (normalized.id == 0L) {
            dao.insert(ItemEntity.fromDomain(normalized.copy(createdAt = now, updatedAt = now)))
        } else {
            val previousImage = dao.getById(normalized.id)?.localImagePath
            if (previousImage != null && previousImage != normalized.localImagePath) {
                images.delete(previousImage)
            }
            dao.update(ItemEntity.fromDomain(normalized.copy(updatedAt = now)))
            normalized.id
        }
    }

    suspend fun delete(item: CollectionItem) {
        dao.delete(ItemEntity.fromDomain(item))
        item.localImagePath?.let { images.delete(it) }
    }

    suspend fun setCompletion(id: Long, percent: Int) {
        dao.updateCompletion(id, percent.coerceIn(0, 100), System.currentTimeMillis())
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.updateFavorite(id, favorite, System.currentTimeMillis())
    }

    /** Applies a progress change through [CollectionItem.normalized] so category rules hold. */
    suspend fun updateProgress(item: CollectionItem, transform: CollectionItem.() -> CollectionItem) {
        val next = item.transform().normalized()
        dao.updateProgress(
            id = item.id,
            status = next.progressStatus.name,
            platinum = next.platinum,
            percent = next.completionPercent,
            backlog = next.backlog,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun setStatus(item: CollectionItem, status: ProgressStatus) = updateProgress(item) {
        copy(progressStatus = status, platinum = if (status == ProgressStatus.FINISHED) platinum else false)
    }

    suspend fun setPlatinum(item: CollectionItem, platinum: Boolean) = updateProgress(item) {
        copy(platinum = platinum, backlog = if (platinum) false else backlog)
    }

    suspend fun setBacklog(item: CollectionItem, backlog: Boolean) = updateProgress(item) {
        if (backlog) copy(backlog = true, progressStatus = ProgressStatus.IN_PROGRESS, platinum = false) else copy(backlog = false)
    }

    suspend fun markCompleted(item: CollectionItem) = updateProgress(item) {
        copy(completionPercent = 100, progressStatus = ProgressStatus.FINISHED, backlog = false)
    }
}
