/* Service worker: offline app shell, runtime cache for the vendored
 * recognition engines, and the Web Share Target handler. */
const VERSION = '0.1.0';
const SHELL_CACHE = `geek-shell-${VERSION}`;
const RUNTIME_CACHE = `geek-runtime-${VERSION}`;
const SHARE_CACHE = 'geek-share';

const SHELL = [
  './', './index.html', './manifest.webmanifest', './css/app.css',
  './js/main.js', './js/db.js', './js/model.js', './js/version.js',
  './js/sync/firebase.js', './js/sync/engine.js',
  './js/recognition/heuristics.js', './js/recognition/image.js', './js/recognition/barcode.js',
  './js/recognition/ocr.js', './js/recognition/recognizer.js',
  './js/lookup/http.js', './js/lookup/google.js', './js/lookup/books.js', './js/lookup/wikipedia.js', './js/lookup/service.js',
  './js/ui/components.js', './js/ui/library.js', './js/ui/detail.js', './js/ui/edit.js', './js/ui/scan.js', './js/ui/settings.js',
  './icons/icon.svg', './icons/icon-192.png', './icons/icon-512.png', './icons/icon-maskable-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL_CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(
      keys.filter((k) => k !== SHELL_CACHE && k !== RUNTIME_CACHE && k !== SHARE_CACHE).map((k) => caches.delete(k)),
    )).then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Web Share Target: stash the shared image, then open the scan screen.
  if (event.request.method === 'POST' && url.pathname.endsWith('/share-target')) {
    event.respondWith((async () => {
      try {
        const form = await event.request.formData();
        const file = form.get('image');
        const cache = await caches.open(SHARE_CACHE);
        if (file && file.size) {
          await cache.put('shared-image', new Response(file, { headers: { 'Content-Type': file.type || 'image/jpeg' } }));
        }
        const text = [form.get('title'), form.get('text'), form.get('url')].filter(Boolean).join(' ');
        await cache.put('shared-text', new Response(text));
      } catch (e) {
        console.warn('share-target', e);
      }
      return Response.redirect('./#/scan?shared=1', 303);
    })());
    return;
  }

  if (event.request.method !== 'GET') return;

  // Never cache API calls.
  if (url.origin !== self.location.origin) return;

  // Vendored engines and language data: cache-first, populated on demand.
  if (url.pathname.includes('/vendor/')) {
    event.respondWith(cacheFirst(RUNTIME_CACHE, event.request));
    return;
  }

  // App shell: cache-first with background refresh; navigations fall back to index.
  event.respondWith((async () => {
    const cache = await caches.open(SHELL_CACHE);
    const cached = await cache.match(event.request, { ignoreSearch: true });
    const network = fetch(event.request).then((res) => {
      if (res.ok) cache.put(event.request, res.clone());
      return res;
    }).catch(() => null);
    if (cached) {
      network.catch(() => {});
      return cached;
    }
    const res = await network;
    if (res) return res;
    if (event.request.mode === 'navigate') return cache.match('./index.html');
    return new Response('Offline', { status: 503 });
  })());
});

async function cacheFirst(cacheName, request) {
  const cache = await caches.open(cacheName);
  const hit = await cache.match(request);
  if (hit) return hit;
  const res = await fetch(request);
  if (res.ok) cache.put(request, res.clone());
  return res;
}
