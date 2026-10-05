// Entry point: wires services, the hash router and the service worker.
import { CATEGORIES, normalizeItem } from './model.js';
import { Settings, createSyncStore, dataUrlToBlob } from './db.js';
import { makeThumb } from './recognition/image.js';
import { FirebaseClient } from './sync/firebase.js';
import { runSync } from './sync/engine.js';
import { VERSION } from './version.js';
import { DEFAULT_FIREBASE } from './config.js';
import { renderLogin } from './ui/login.js';
import { GoogleSearch } from './lookup/google.js';
import { GoogleBooks } from './lookup/books.js';
import { Wikipedia } from './lookup/wikipedia.js';
import { LookupService } from './lookup/service.js';
import { renderLibrary } from './ui/library.js';
import { renderDetail } from './ui/detail.js';
import { renderEdit } from './ui/edit.js';
import { renderScan } from './ui/scan.js';
import { renderSettings } from './ui/settings.js';
import { toast } from './ui/components.js';

// components.js reads the category list through this global to avoid a circular import.
window.__model = { CATEGORIES };

let settingsCache = { googleApiKey: '', googleCx: '', wikiLang: 'pt', ocrLangs: 'por+eng', firebaseProjectId: '', firebaseApiKey: '', autoSync: true, skipLogin: false };
/** Settings with the deployment's default Firebase project applied when nothing was typed. */
const settings = () => ({
  ...settingsCache,
  firebaseProjectId: settingsCache.firebaseProjectId || DEFAULT_FIREBASE.projectId,
  firebaseApiKey: settingsCache.firebaseApiKey || DEFAULT_FIREBASE.apiKey,
});

const firebase = new FirebaseClient(settings, {
  load: () => Settings.getValue('firebaseAuth'),
  save: (auth) => Settings.setValue('firebaseAuth', auth),
});
const syncStore = createSyncStore({ makeThumb, thumbToBlob: dataUrlToBlob, normalize: normalizeItem });

/** Cloud sync facade used by the screens. */
const sync = {
  firebase,
  running: false,
  listeners: new Set(),
  onChange(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); },
  async canSync() { return firebase.isConfigured && (await firebase.isSignedIn()); },
  async lastSyncAt() { return Settings.getValue('lastSyncAt'); },
  /** Runs a sync; resolves with the summary or throws. Concurrent calls are coalesced. */
  async run({ silent = false, onStatus } = {}) {
    if (this.running) return null;
    if (!(await this.canSync())) return null;
    if (!navigator.onLine) { if (!silent) toast('Sem conexão. A sincronização será feita quando houver internet.'); return null; }
    this.running = true;
    this.listeners.forEach((fn) => fn({ running: true }));
    try {
      const summary = await runSync({ firebase, store: syncStore, onStatus: onStatus || (() => {}) });
      await Settings.setValue('lastSyncAt', Date.now());
      if (!silent) {
        const parts = [];
        if (summary.pushed) parts.push(`${summary.pushed} enviado(s)`);
        if (summary.pulled) parts.push(`${summary.pulled} recebido(s)`);
        if (summary.removed) parts.push(`${summary.removed} removido(s)`);
        toast(parts.length ? `Sincronizado: ${parts.join(', ')}` : 'Tudo sincronizado');
      }
      this.listeners.forEach((fn) => fn({ running: false, summary }));
      return summary;
    } catch (err) {
      console.warn('sync', err);
      if (!silent) toast(`Falha ao sincronizar: ${err.message || err}`);
      this.listeners.forEach((fn) => fn({ running: false, error: err }));
      throw err;
    } finally {
      this.running = false;
    }
  },
  /** Debounced background sync after local edits. */
  schedule(delay = 4000) {
    clearTimeout(this._timer);
    this._timer = setTimeout(() => { if (settingsCache.autoSync) this.run({ silent: true }).catch(() => {}); }, delay);
  },
};

const services = {
  lookup: new LookupService({
    google: new GoogleSearch(settings),
    books: new GoogleBooks(settings),
    wikipedia: new Wikipedia(settings),
  }),
  sync,
};

function navigate(hash) {
  if (location.hash === hash) route();
  else location.hash = hash;
}

async function route() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, query = ''] = raw.split('?');
  const params = new URLSearchParams(query);
  const parts = path.split('/').filter(Boolean);
  // Give every screen a fresh root element so listeners from the previous
  // screen (including its sync listener) are dropped with it.
  const current = document.getElementById('app');
  const fresh = current.cloneNode(false);
  current.replaceWith(fresh);
  const root = fresh;
  window.scrollTo(0, 0);

  try {
    if (parts.length === 0) return await renderLibrary(root, { services });
    if (parts[0] === 'item' && parts[1]) return await renderDetail(root, Number(parts[1]), { navigate, services });
    if (parts[0] === 'edit') return await renderEdit(root, parts[1] ? Number(parts[1]) : null, { navigate, services, settings });
    if (parts[0] === 'scan') return await renderScan(root, { navigate, services, settings, shared: params.get('shared') === '1' });
    if (parts[0] === 'settings') {
      return await renderSettings(root, { navigate, services, version: VERSION, onSettingsChanged: (s) => { settingsCache = s; } });
    }
    if (parts[0] === 'login') {
      return await renderLogin(root, { navigate, services, version: VERSION, onSettingsChanged: (s) => { settingsCache = s; } });
    }
    navigate('#/');
  } catch (err) {
    console.error(err);
    root.innerHTML = `<main><p class="empty">Algo deu errado: ${String(err.message || err)}</p><p class="center"><a class="btn" href="#/">Voltar ao início</a></p></main>`;
  }
}

// Global delegation for data-nav attributes (cards, buttons).
document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-nav]');
  if (el && !e.target.closest('a,button:not([data-nav]),input,label')) {
    e.preventDefault();
    navigate(el.dataset.nav);
  } else if (el && el.matches('button')) {
    e.preventDefault();
    navigate(el.dataset.nav);
  }
});

window.addEventListener('hashchange', route);

async function start() {
  settingsCache = await Settings.load();
  // First screen: the login page, unless the user is signed in or chose to go on without an account.
  const signedIn = await firebase.isSignedIn();
  const hash = location.hash.replace(/^#/, '') || '/';
  if (!signedIn && !settingsCache.skipLogin && hash === '/') {
    location.hash = '#/login';
  }
  await route();
  // Sync on launch and whenever the app comes back online.
  if (settingsCache.autoSync) sync.run({ silent: true }).catch(() => {});
  window.addEventListener('online', () => { if (settingsCache.autoSync) sync.run({ silent: true }).catch(() => {}); });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && settingsCache.autoSync) sync.run({ silent: true }).catch(() => {});
  });
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    try {
      const reg = await navigator.serviceWorker.register('./sw.js');
      reg.addEventListener('updatefound', () => {
        const sw = reg.installing;
        sw && sw.addEventListener('statechange', () => {
          if (sw.state === 'installed' && navigator.serviceWorker.controller) toast('Nova versão disponível. Recarregue o app.');
        });
      });
    } catch (err) {
      console.warn('Service worker não registrado', err);
    }
  }
}

start();
