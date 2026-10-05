// Incremental, two-way sync. Only items changed locally since the last push
// (dirty flag set on every write) go up; only documents changed on the
// server since the last pull (server timestamp cursor) come down.
// Conflicts resolve by the newest updatedAt (last write wins).

/**
 * @typedef {object} SyncStore
 * @property {() => Promise<object[]>} dirtyItems
 * @property {(syncId: string) => Promise<object|null>} bySyncId
 * @property {(item: object, thumb: string|null) => Promise<void>} applyRemote  upsert without marking dirty
 * @property {(syncId: string) => Promise<void>} removeLocal                  hard delete (tombstone arrived)
 * @property {(syncIds: string[]) => Promise<void>} markClean
 * @property {(item: object) => Promise<string|null>} thumbFor                base64 JPEG thumbnail or null
 * @property {() => Promise<string|null>} loadCursor
 * @property {(cursor: string) => Promise<void>} saveCursor
 */

const BATCH = 100;

export async function runSync({ firebase, store, onStatus = () => {} }) {
  const summary = { pushed: 0, pulled: 0, removed: 0, skipped: 0 };
  await firebase.ensureToken();

  // ---- pull first, so a stale local edit never overwrites a newer remote one.
  let cursor = await store.loadCursor();
  for (let page = 0; page < 50; page++) {
    onStatus(summary.pulled ? `Recebendo… ${summary.pulled}` : 'Verificando alterações…');
    const docs = await firebase.changedSince(cursor, 300);
    for (const { item: remote, thumb, serverUpdatedAt } of docs) {
      if (!remote.syncId) continue;
      const local = await store.bySyncId(remote.syncId);
      const localWins = local && local.dirty !== false && (local.updatedAt || 0) >= (remote.updatedAt || 0);
      if (remote.deletedAt) {
        if (local && !localWins) { await store.removeLocal(remote.syncId); summary.removed++; } else summary.skipped++;
      } else if (localWins || (local && local.dirty === false && local.updatedAt === remote.updatedAt)) {
        summary.skipped++;
      } else {
        await store.applyRemote(remote, thumb);
        summary.pulled++;
      }
      if (serverUpdatedAt && (!cursor || serverUpdatedAt > cursor)) cursor = serverUpdatedAt;
    }
    if (cursor) await store.saveCursor(cursor);
    if (docs.length < 300) break;
  }

  // ---- push what is still dirty (new or locally newer).
  const dirty = await store.dirtyItems();
  for (let i = 0; i < dirty.length; i += BATCH) {
    const chunk = dirty.slice(i, i + BATCH);
    onStatus(`Enviando ${Math.min(i + chunk.length, dirty.length)} de ${dirty.length}…`);
    const entries = [];
    for (const item of chunk) entries.push({ item, thumb: item.deletedAt ? null : await store.thumbFor(item) });
    await firebase.commitItems(entries);
    await store.markClean(chunk.map((it) => it.syncId));
    summary.pushed += chunk.length;
  }
  return summary;
}
