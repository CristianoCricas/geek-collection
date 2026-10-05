// Small rendering helpers: HTML escaping, covers, progress bars, toasts, sheets.
import { category, completionOf, progressSummary, PROGRESS_STATUSES, statusLabel } from '../model.js';
import { Images } from '../db.js';

export const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

export const html = (strings, ...values) => strings.reduce((out, s, i) => out + s + (i < values.length ? values[i] : ''), '');

let toastTimer = null;
export function toast(message, ms = 2600) {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, ms);
}

const objectUrls = new Map();
/** Object URL for an item's stored photo, cached per id and invalidated on change. */
export async function imageUrl(id, version) {
  const key = `${id}:${version || ''}`;
  if (objectUrls.has(key)) return objectUrls.get(key);
  const blob = await Images.get(id);
  if (!blob) return null;
  const url = URL.createObjectURL(blob);
  objectUrls.set(key, url);
  return url;
}

/** Renders the cover box; local photo is filled in asynchronously. */
export function coverHtml(item, size = 'sm', { id = '' } = {}) {
  const cat = category(item.category);
  const remote = item.coverUrl ? `<img src="${esc(item.coverUrl)}" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.remove()">` : '';
  return `<div class="cover ${size}" data-cover="${item.hasImage && item.id ? item.id : ''}" data-version="${item.updatedAt || ''}" ${id ? `id="${id}"` : ''}>${remote || `<span aria-hidden="true">${cat.icon}</span>`}</div>`;
}

/** Call after inserting coverHtml() into the DOM to load local photos. */
export async function hydrateCovers(root) {
  const boxes = root.querySelectorAll('[data-cover]:not([data-cover=""])');
  for (const box of boxes) {
    const url = await imageUrl(Number(box.dataset.cover), box.dataset.version);
    if (url) box.innerHTML = `<img src="${url}" alt="">`;
  }
}

export function progressHtml(item, { label = true } = {}) {
  const pct = completionOf(item);
  const status = item.progressStatus || 'IN_PROGRESS';
  const cls = item.platinum ? 'platinum' : status === 'FINISHED' || pct >= 100 ? 'done' : status === 'PAUSED' ? 'paused' : status === 'ABANDONED' ? 'abandoned' : '';
  return `
    <div class="progress ${cls}" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"><span style="width:${pct}%"></span></div>
    ${label ? `<div class="progress-label">${esc(progressSummary(item))}</div>` : ''}`;
}

export function backlogTag(item) {
  return item.backlog ? '<span class="tag backlog">📥 Backlog</span>' : '';
}

/** Chips for the progress status (uses the category-specific label). */
export function statusChips(selected, categoryId, { name = 'status', allLabel = null } = {}) {
  return `
    <div class="chips" data-chips="${name}">
      ${allLabel ? `<button type="button" class="chip ${selected ? '' : 'selected'}" data-value="">${esc(allLabel)}</button>` : ''}
      ${PROGRESS_STATUSES.map((s) => `<button type="button" class="chip ${selected === s.id ? 'selected' : ''}" data-value="${s.id}">${s.icon} ${esc(statusLabel(s.id, categoryId))}</button>`).join('')}
    </div>`;
}

export function appBar({ title, subtitle = '', back = null, actions = '' }) {
  return `
    <header class="appbar">
      ${back ? `<button class="icon-btn" data-nav="${esc(back)}" aria-label="Voltar">←</button>` : ''}
      <h1>${esc(title)}${subtitle ? `<small>${esc(subtitle)}</small>` : ''}</h1>
      ${actions}
    </header>`;
}

export function categoryChips(selected, { allLabel = null, name = 'category' } = {}) {
  const { CATEGORIES } = window.__model;
  return `
    <div class="chips ${allLabel ? '' : 'wrap'}" data-chips="${name}">
      ${allLabel ? `<button type="button" class="chip ${selected ? '' : 'selected'}" data-value="">${esc(allLabel)}</button>` : ''}
      ${CATEGORIES.map((c) => `<button type="button" class="chip ${selected === c.id ? 'selected' : ''}" data-value="${c.id}">${c.icon} ${esc(c.label)}</button>`).join('')}
    </div>`;
}

export function confirmDialog(message) {
  return Promise.resolve(window.confirm(message));
}

/** Simple bottom sheet; returns a close() function. */
export function openSheet(contentHtml, { onClose } = {}) {
  const backdrop = document.createElement('div');
  backdrop.className = 'sheet-backdrop';
  backdrop.innerHTML = `<div class="sheet" role="dialog" aria-modal="true">${contentHtml}</div>`;
  const close = () => { backdrop.remove(); onClose && onClose(); };
  backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
  document.body.appendChild(backdrop);
  return { close, el: backdrop.firstElementChild };
}

export function candidateHtml(c, index) {
  const cat = category(c.category);
  const meta = [c.category ? cat.label : null, c.platform, c.releaseYear, c.creator].filter(Boolean).join(' · ');
  return `
    <div class="candidate" data-candidate="${index}" role="button" tabindex="0">
      <div class="cover">${c.coverUrl ? `<img src="${esc(c.coverUrl)}" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.remove()">` : `<span>${cat.icon}</span>`}</div>
      <div class="item-body">
        <p class="item-title">${esc(c.title)}</p>
        ${meta ? `<p class="item-sub">${esc(meta)}</p>` : ''}
        ${c.description ? `<p class="item-sub" style="white-space:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical">${esc(c.description)}</p>` : ''}
        <span class="source">${esc(c.sourceName || c.source)}</span>
      </div>
    </div>`;
}

export const formatDate = (ts) => new Date(ts).toLocaleDateString('pt-BR');
