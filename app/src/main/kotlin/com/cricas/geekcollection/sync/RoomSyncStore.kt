package com.cricas.geekcollection.sync

import android.util.Base64
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.sync.SyncStore
import com.cricas.geekcollection.data.ImageStore
import com.cricas.geekcollection.data.local.ItemDao
import com.cricas.geekcollection.data.local.ItemEntity
import com.cricas.geekcollection.data.settings.SettingsRepository

/** [SyncStore] backed by Room, the local picture store and SharedPreferences. */
class RoomSyncStore(
    private val dao: ItemDao,
    private val images: ImageStore,
    private val settings: SettingsRepository,
) : SyncStore {

    override suspend fun dirtyItems(): List<CollectionItem> = dao.getDirty().map(ItemEntity::toDomain)

    override suspend fun bySyncId(syncId: String): CollectionItem? = dao.getBySyncId(syncId)?.toDomain()

    override suspend fun applyRemote(item: CollectionItem, thumb: String?) {
        val local = dao.getBySyncId(item.syncId)?.toDomain()
        var merged = item.copy(
            id = local?.id ?: 0L,
            localImagePath = local?.localImagePath,
            dirty = false,
        ).normalized().copy(updatedAt = item.updatedAt, createdAt = item.createdAt, dirty = false)
        if (merged.localImagePath == null && thumb != null) {
            decodeDataUrl(thumb)?.let { bytes -> merged = merged.copy(localImagePath = images.saveBytes(bytes)) }
        }
        if (local == null) dao.insert(ItemEntity.fromDomain(merged)) else dao.update(ItemEntity.fromDomain(merged))
    }

    override suspend fun removeLocal(syncId: String) {
        dao.getBySyncId(syncId)?.localImagePath?.let { images.delete(it) }
        dao.hardDeleteBySyncId(syncId)
    }

    override suspend fun markClean(syncIds: List<String>) {
        if (syncIds.isEmpty()) return
        dao.purgeTombstones(syncIds)
        dao.markClean(syncIds)
    }

    override suspend fun thumbFor(item: CollectionItem): String? =
        item.localImagePath?.let { images.thumbnailDataUrl(it) }

    override suspend fun loadCursor(): String? = settings.syncCursor

    override suspend fun saveCursor(cursor: String) {
        settings.syncCursor = cursor
    }

    private fun decodeDataUrl(dataUrl: String): ByteArray? {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return null
        return runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
    }
}
