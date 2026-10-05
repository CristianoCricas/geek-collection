// IndexedDB persistence: items, their photos (Blob) and app settings.
const DB_NAME = 'geek-collection';
const DB_VERSION = 2;

function open() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      const tx = req.transaction;
      let items;
      if (!db.objectStoreNames.contains('items')) {
        items = db.createObjectStore('items', { keyPath: 'id', autoIncrement: true });
        items.createIndex('category', 'category');
        items.createIndex('updatedAt', 'updatedAt');
      } else {
        items = tx.objectStore('items');
      }
      if (!items.indexNames.contains('syncId')) items.createIndex('syncId', 'syncId', { unique: false });
      if (!db.objectStoreNames.contains('images')) db.createObjectStore('images');
      if (!db.objectStoreNames.contains('settings')) db.createObjectStore('settings');
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

let dbPromise = null;
const db = () => (dbPromise ||= open());

function tx(storeNames, mode, fn) {
  return db().then((d) => new Promise((resolve, reject) => {
    const t = d.transaction(storeNames, mode);
    let result;
    t.oncomplete = () => resolve(result);
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error);
    const out = fn(t);
    if (out && typeof out.then === 'function') out.then((r) => { result = r; }, reject);
    else result = out;
  }));
}

const request = (r) => new Promise((resolve, reject) => {
  r.onsuccess = () => resolve(r.result);
  r.onerror = () => reject(r.error);
});

const isLive = (it) => !it.deletedAt;

export const Items = {
  /** Items visible in the library (tombstones excluded). */
  all: () => tx('items', 'readonly', (t) => request(t.objectStore('items').getAll())).then((list) => list.filter(isLive)),
  allIncludingDeleted: () => tx('items', 'readonly', (t) => request(t.objectStore('items').getAll())),
  get: (id) => tx('items', 'readonly', (t) => request(t.objectStore('items').get(Number(id)))).then((it) => (it && isLive(it) ? it : it || null)),
  /**
   * Saves an item. Every local write marks the record dirty so the next sync
   * pushes it; `fromSync` writes keep the server's flags.
   */
  async put(item, { fromSync = false } = {}) {
    const record = { ...item };
    if (record.id === null || record.id === undefined) delete record.id;
    if (!record.syncId) record.syncId = newSyncId();
    if (!fromSync) record.dirty = true;
    return tx('items', 'readwrite', (t) => request(t.objectStore('items').put(record)));
  },
  /** Soft delete: keeps a tombstone so the deletion reaches other devices. */
  remove: (id) => tx(['items', 'images'], 'readwrite', async (t) => {
    const store = t.objectStore('items');
    const it = await request(store.get(Number(id)));
    if (it) {
      const now = Date.now();
      store.put({ ...it, deletedAt: now, updatedAt: now, dirty: true, hasImage: false });
    }
    t.objectStore('images').delete(Number(id));
  }),
  hardRemove: (id) => tx(['items', 'images'], 'readwrite', (t) => {
    t.objectStore('items').delete(Number(id));
    t.objectStore('images').delete(Number(id));
  }),
  bySyncId: (syncId) => tx('items', 'readonly', (t) => request(t.objectStore('items').index('syncId').get(syncId))).then((it) => it || null),
  count: () => Items.all().then((l) => l.length),
};

function newSyncId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') return globalThis.crypto.randomUUID().replace(/-/g, '');
  let out = '';
  for (let i = 0; i < 32; i++) out += Math.floor(Math.random() * 16).toString(16);
  return out;
}

export const Images = {
  get: (id) => tx('images', 'readonly', (t) => request(t.objectStore('images').get(Number(id)))),
  put: (id, blob) => tx('images', 'readwrite', (t) => request(t.objectStore('images').put(blob, Number(id)))),
  remove: (id) => tx('images', 'readwrite', (t) => request(t.objectStore('images').delete(Number(id)))),
  all: () => tx('images', 'readonly', async (t) => {
    const store = t.objectStore('images');
    const [keys, values] = await Promise.all([request(store.getAllKeys()), request(store.getAll())]);
    return keys.map((k, i) => [k, values[i]]);
  }),
};

const DEFAULT_SETTINGS = { googleApiKey: '', googleCx: '', wikiLang: 'pt', ocrLangs: 'por+eng', firebaseProjectId: '', firebaseApiKey: '', autoSync: true };

export const Settings = {
  async load() {
    const stored = await tx('settings', 'readonly', (t) => request(t.objectStore('settings').get('app')));
    return { ...DEFAULT_SETTINGS, ...(stored || {}) };
  },
  save: (settings) => tx('settings', 'readwrite', (t) => request(t.objectStore('settings').put(settings, 'app'))),
  /** Generic key/value slots (auth session, sync cursor, last sync time). */
  getValue: (key) => tx('settings', 'readonly', (t) => request(t.objectStore('settings').get(key))).then((v) => (v === undefined ? null : v)),
  setValue: (key, value) => tx('settings', 'readwrite', (t) => (value === null || value === undefined
    ? request(t.objectStore('settings').delete(key))
    : request(t.objectStore('settings').put(value, key)))),
};

/** Local side of the cloud sync (see js/sync/engine.js for the contract). */
export function createSyncStore({ makeThumb, thumbToBlob, normalize }) {
  return {
    dirtyItems: async () => (await Items.allIncludingDeleted()).filter((it) => it.dirty !== false || !it.syncId),
    bySyncId: (syncId) => Items.bySyncId(syncId),
    async applyRemote(remote, thumb) {
      const local = await Items.bySyncId(remote.syncId);
      const merged = normalize({ ...(local || {}), ...remote, id: local ? local.id : null, dirty: false, keepUpdatedAt: true, hasImage: local ? local.hasImage : false });
      merged.updatedAt = remote.updatedAt || merged.updatedAt;
      merged.createdAt = remote.createdAt || merged.createdAt;
      const id = await Items.put(merged, { fromSync: true });
      if (thumb && !(local && local.hasImage)) {
        await Images.put(id, thumbToBlob(thumb));
        await Items.put({ ...merged, id, hasImage: true }, { fromSync: true });
      }
    },
    async removeLocal(syncId) {
      const local = await Items.bySyncId(syncId);
      if (local) await Items.hardRemove(local.id);
    },
    async markClean(syncIds) {
      for (const syncId of syncIds) {
        const it = await Items.bySyncId(syncId);
        if (!it) continue;
        if (it.deletedAt) await Items.hardRemove(it.id); // tombstone delivered, drop it
        else await Items.put({ ...it, dirty: false }, { fromSync: true });
      }
    },
    async thumbFor(item) {
      if (!item.hasImage || !item.id) return null;
      const blob = await Images.get(item.id);
      return blob ? makeThumb(blob) : null;
    },
    loadCursor: () => Settings.getValue('syncCursor'),
    saveCursor: (cursor) => Settings.setValue('syncCursor', cursor),
  };
}

/** Serialises the whole library (items + images as data URLs) for backup. */
export async function exportLibrary() {
  const [items, images] = await Promise.all([Items.all(), Images.all()]);
  const encoded = {};
  for (const [id, blob] of images) encoded[id] = await blobToDataUrl(blob);
  return { app: 'geek-collection', version: 1, exportedAt: new Date().toISOString(), items, images: encoded };
}

export async function importLibrary(data, { replace = false } = {}) {
  if (!data || data.app !== 'geek-collection' || !Array.isArray(data.items)) throw new Error('Arquivo de backup inválido');
  if (replace) {
    await tx(['items', 'images'], 'readwrite', (t) => { t.objectStore('items').clear(); t.objectStore('images').clear(); });
  }
  let count = 0;
  for (const item of data.items) {
    const oldId = item.id;
    const copy = { ...item, dirty: true };
    if (copy.deletedAt) continue;
    if (!replace) delete copy.id;
    const newId = await Items.put(copy);
    const img = data.images && data.images[oldId];
    if (img) await Images.put(newId, dataUrlToBlob(img));
    count++;
  }
  return count;
}

function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = () => reject(r.error);
    r.readAsDataURL(blob);
  });
}

export function dataUrlToBlob(dataUrl) {
  const [head, b64] = dataUrl.split(',');
  const mime = (head.match(/data:([^;]+)/) || [])[1] || 'image/jpeg';
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}
