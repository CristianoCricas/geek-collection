package com.cricas.geekcollection.core.sync

import com.cricas.geekcollection.core.model.CollectionItem

/** Local persistence operations the sync needs (implemented with Room on Android). */
interface SyncStore {
    /** Items (including tombstones) changed locally since the last push. */
    suspend fun dirtyItems(): List<CollectionItem>
    suspend fun bySyncId(syncId: String): CollectionItem?
    /** Insert/overwrite from the cloud without marking dirty; [thumb] is a data URL or null. */
    suspend fun applyRemote(item: CollectionItem, thumb: String?)
    /** A tombstone arrived: remove the local row and its picture for good. */
    suspend fun removeLocal(syncId: String)
    /** Pushed successfully: clear dirty; tombstones can be purged. */
    suspend fun markClean(syncIds: List<String>)
    /** Small JPEG data URL of the item's picture, or null. */
    suspend fun thumbFor(item: CollectionItem): String?
    suspend fun loadCursor(): String?
    suspend fun saveCursor(cursor: String)
}

data class SyncSummary(val pushed: Int = 0, val pulled: Int = 0, val removed: Int = 0, val skipped: Int = 0) {
    val changedLocally: Boolean get() = pulled > 0 || removed > 0
}

/**
 * Incremental two-way sync: pull documents changed on the server since the
 * cursor (server timestamp), resolve conflicts by newest `updatedAt`, then
 * push whatever is still dirty. Only deltas travel, never the whole library.
 */
class SyncEngine(
    private val firebase: FirebaseClient,
    private val store: SyncStore,
    private val batchSize: Int = 100,
    private val pageSize: Int = 300,
) {
    suspend fun sync(onStatus: (String) -> Unit = {}): SyncSummary {
        firebase.ensureToken()
        var pushed = 0
        var pulled = 0
        var removed = 0
        var skipped = 0

        // ---- pull first so a stale local edit never clobbers a newer remote one.
        var cursor = store.loadCursor()
        for (page in 0 until 50) {
            onStatus(if (pulled > 0) "Recebendo… $pulled" else "Verificando alterações…")
            val docs = firebase.changedSince(cursor, pageSize)
            for (remote in docs) {
                val local = store.bySyncId(remote.item.syncId)
                val localWins = local != null && local.dirty && local.updatedAt >= remote.item.updatedAt
                when {
                    remote.item.isDeleted -> if (local != null && !localWins) { store.removeLocal(remote.item.syncId); removed++ } else skipped++
                    localWins || (local != null && !local.dirty && local.updatedAt == remote.item.updatedAt) -> skipped++
                    else -> { store.applyRemote(remote.item, remote.thumb); pulled++ }
                }
                val stamp = remote.serverUpdatedAt
                if (stamp != null && (cursor == null || stamp > cursor)) cursor = stamp
            }
            cursor?.let { store.saveCursor(it) }
            if (docs.size < pageSize) break
        }

        // ---- push
        val dirty = store.dirtyItems()
        dirty.chunked(batchSize).forEach { chunk ->
            onStatus("Enviando ${pushed + chunk.size} de ${dirty.size}…")
            val entries = chunk.map { item -> item to (if (item.isDeleted) null else store.thumbFor(item)) }
            firebase.commitItems(entries)
            store.markClean(chunk.map { it.syncId })
            pushed += chunk.size
        }
        return SyncSummary(pushed, pulled, removed, skipped)
    }
}
