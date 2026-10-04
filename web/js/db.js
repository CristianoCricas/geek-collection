// IndexedDB persistence: items, their photos (Blob) and app settings.
const DB_NAME = 'geek-collection';
const DB_VERSION = 1;

function open() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('items')) {
        const items = db.createObjectStore('items', { keyPath: 'id', autoIncrement: true });
        items.createIndex('category', 'category');
        items.createIndex('updatedAt', 'updatedAt');
      }
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

export const Items = {
  all: () => tx('items', 'readonly', (t) => request(t.objectStore('items').getAll())),
  get: (id) => tx('items', 'readonly', (t) => request(t.objectStore('items').get(Number(id)))),
  async put(item) {
    const record = { ...item };
    if (record.id === null || record.id === undefined) delete record.id;
    return tx('items', 'readwrite', (t) => request(t.objectStore('items').put(record)));
  },
  remove: (id) => tx(['items', 'images'], 'readwrite', (t) => {
    t.objectStore('items').delete(Number(id));
    t.objectStore('images').delete(Number(id));
  }),
  count: () => tx('items', 'readonly', (t) => request(t.objectStore('items').count())),
};

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

const DEFAULT_SETTINGS = { googleApiKey: '', googleCx: '', wikiLang: 'pt', ocrLangs: 'por+eng' };

export const Settings = {
  async load() {
    const stored = await tx('settings', 'readonly', (t) => request(t.objectStore('settings').get('app')));
    return { ...DEFAULT_SETTINGS, ...(stored || {}) };
  },
  save: (settings) => tx('settings', 'readwrite', (t) => request(t.objectStore('settings').put(settings, 'app'))),
};

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
    const copy = { ...item };
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

function dataUrlToBlob(dataUrl) {
  const [head, b64] = dataUrl.split(',');
  const mime = (head.match(/data:([^;]+)/) || [])[1] || 'image/jpeg';
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}
