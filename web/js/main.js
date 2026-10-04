// Entry point: wires services, the hash router and the service worker.
import { CATEGORIES } from './model.js';
import { Settings } from './db.js';
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

export const VERSION = '1.0.0';

// components.js reads the category list through this global to avoid a circular import.
window.__model = { CATEGORIES };

let settingsCache = { googleApiKey: '', googleCx: '', wikiLang: 'pt', ocrLangs: 'por+eng' };
const settings = () => settingsCache;

const services = {
  lookup: new LookupService({
    google: new GoogleSearch(settings),
    books: new GoogleBooks(settings),
    wikipedia: new Wikipedia(settings),
  }),
};

const app = document.getElementById('app');

function navigate(hash) {
  if (location.hash === hash) route();
  else location.hash = hash;
}

async function route() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, query = ''] = raw.split('?');
  const params = new URLSearchParams(query);
  const parts = path.split('/').filter(Boolean);
  const fresh = app.cloneNode(false);
  app.replaceWith(fresh);
  const root = document.getElementById('app');
  window.scrollTo(0, 0);

  try {
    if (parts.length === 0) return await renderLibrary(root);
    if (parts[0] === 'item' && parts[1]) return await renderDetail(root, Number(parts[1]), { navigate });
    if (parts[0] === 'edit') return await renderEdit(root, parts[1] ? Number(parts[1]) : null, { navigate, services, settings });
    if (parts[0] === 'scan') return await renderScan(root, { navigate, services, settings, shared: params.get('shared') === '1' });
    if (parts[0] === 'settings') {
      return await renderSettings(root, { navigate, version: VERSION, onSettingsChanged: (s) => { settingsCache = s; } });
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
  await route();
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
