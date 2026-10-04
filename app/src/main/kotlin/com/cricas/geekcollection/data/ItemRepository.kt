package com.cricas.geekcollection.data

import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.data.local.ItemDao
import com.cricas.geekcollection.data.local.ItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortOrder(val key: String) { RECENT("recent"), TITLE("title"), COMPLETION("completion") }

class ItemRepository(private val dao: ItemDao, private val images: ImageStore) {

    fun observeItems(
        query: String,
        category: ItemCategory?,
        favoritesOnly: Boolean,
        sort: SortOrder,
    ): Flow<List<CollectionItem>> =
        dao.observe(query.trim(), category?.name, favoritesOnly, sort.key)
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
}
