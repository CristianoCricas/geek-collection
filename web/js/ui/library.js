import { CATEGORIES, category, completionOf } from '../model.js';
import { Items } from '../db.js';
import { appBar, coverHtml, esc, hydrateCovers, progressHtml, categoryChips } from './components.js';

const state = { query: '', category: '', favorites: false, sort: 'recent' };

const SORTS = { recent: 'Recentes', title: 'Título', completion: 'Conclusão' };

function filterItems(items) {
  const q = state.query.trim().toLowerCase();
  let list = items.filter((it) =>
    (!state.category || it.category === state.category)
    && (!state.favorites || it.favorite)
    && (!q || [it.title, it.platform, it.creator, it.publisher, it.notes, category(it.category).label]
      .some((v) => v && String(v).toLowerCase().includes(q))));
  list = list.sort((a, b) => {
    if (state.sort === 'title') return a.title.localeCompare(b.title, 'pt-BR', { sensitivity: 'base' });
    if (state.sort === 'completion') return (b.completionPercent ?? -1) - (a.completionPercent ?? -1) || b.updatedAt - a.updatedAt;
    return b.updatedAt - a.updatedAt;
  });
  return list;
}

function itemCard(it) {
  const cat = category(it.category);
  const sub = [cat.label, it.platform, it.releaseYear].filter(Boolean).join(' · ');
  return `
    <article class="card item-card" data-nav="#/item/${it.id}">
      ${coverHtml(it, 'sm')}
      <div class="item-body">
        <p class="item-title">${esc(it.title)}${it.favorite ? '<span class="fav" aria-label="Favorito">♥</span>' : ''}</p>
        <p class="item-sub">${esc(sub)}</p>
        ${it.creator ? `<p class="item-sub">${esc(it.creator)}</p>` : ''}
        ${cat.completion ? progressHtml(it) : ''}
      </div>
    </article>`;
}

export async function renderLibrary(root) {
  const items = await Items.all();
  const list = filterItems(items);
  const completed = items.filter((i) => completionOf(i) >= 100 && category(i.category).completion).length;

  root.innerHTML = `<div class="view">
    ${appBar({
      title: 'Minha biblioteca',
      subtitle: items.length ? `${items.length} itens · ${completed} concluídos` : '',
      actions: `
        <button class="icon-btn ${state.favorites ? 'active' : ''}" data-action="favorites" aria-label="Somente favoritos" title="Favoritos">${state.favorites ? '♥' : '♡'}</button>
        <button class="icon-btn" data-action="sort" aria-label="Ordenar (${SORTS[state.sort]})" title="Ordenar: ${SORTS[state.sort]}">⇅</button>
        <button class="icon-btn" data-nav="#/settings" aria-label="Configurações" title="Configurações">⚙️</button>`,
    })}
    <main>
      <div class="search">
        <span class="lead" aria-hidden="true">🔍</span>
        <input class="input" type="search" id="q" placeholder="Buscar por título, plataforma ou autor" value="${esc(state.query)}" autocomplete="off">
        ${state.query ? '<button class="icon-btn clear" data-action="clear" aria-label="Limpar">✕</button>' : ''}
      </div>
      ${categoryChips(state.category, { allLabel: 'Todos' })}
      ${items.length === 0 ? `
        <div class="empty">
          <div class="big">🎮📚🎲</div>
          <h2>Sua biblioteca está vazia</h2>
          <p>Adicione jogos, livros, consoles e colecionáveis manualmente ou a partir de uma foto do item.</p>
        </div>` : list.length === 0 ? '<p class="empty">Nenhum item encontrado para esta busca.</p>' : list.map(itemCard).join('')}
    </main>
    <div class="fab-wrap">
      <button class="fab small" data-nav="#/edit">✏️ Cadastrar manualmente</button>
      <button class="fab" data-nav="#/scan">📷 Adicionar por foto</button>
    </div></div>`;

  hydrateCovers(root);
  const view = root.querySelector('.view');

  const q = root.querySelector('#q');
  let timer;
  q.addEventListener('input', () => {
    state.query = q.value;
    clearTimeout(timer);
    timer = setTimeout(() => {
      const pos = q.selectionStart;
      renderLibrary(root).then(() => {
        const nq = root.querySelector('#q');
        nq.focus();
        nq.setSelectionRange(pos, pos);
      });
    }, 180);
  });
  root.querySelector('[data-chips]').addEventListener('click', (e) => {
    const chip = e.target.closest('.chip');
    if (!chip) return;
    state.category = chip.dataset.value === state.category ? '' : chip.dataset.value;
    renderLibrary(root);
  });
  view.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    if (btn.dataset.action === 'favorites') state.favorites = !state.favorites;
    if (btn.dataset.action === 'clear') state.query = '';
    if (btn.dataset.action === 'sort') {
      const keys = Object.keys(SORTS);
      state.sort = keys[(keys.indexOf(state.sort) + 1) % keys.length];
    }
    renderLibrary(root);
  });
}

export { CATEGORIES };
