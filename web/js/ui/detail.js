import { category } from '../model.js';
import { Items } from '../db.js';
import { appBar, coverHtml, esc, formatDate, hydrateCovers, progressHtml, toast } from './components.js';
import { googleLinks } from '../lookup/google.js';

export async function renderDetail(root, id, { navigate }) {
  const item = await Items.get(id);
  if (!item) {
    root.innerHTML = `${appBar({ title: 'Item', back: '#/' })}<main><p class="empty">Item não encontrado.</p></main>`;
    return;
  }
  const cat = category(item.category);
  const kv = (label, value) => (value ? `<dt>${esc(label)}</dt><dd>${esc(value)}</dd>` : '');

  root.innerHTML = `<div class="view">
    ${appBar({
      title: cat.label,
      back: '#/',
      actions: `
        <button class="icon-btn ${item.favorite ? 'active' : ''}" data-action="favorite" aria-label="${item.favorite ? 'Remover dos favoritos' : 'Favoritar'}">${item.favorite ? '♥' : '♡'}</button>
        <button class="icon-btn" data-action="delete" aria-label="Excluir">🗑️</button>`,
    })}
    <main>
      <div class="item-card" style="align-items:flex-start;cursor:default">
        ${coverHtml(item, 'lg')}
        <div class="item-body">
          <h2 style="margin:0 0 8px;font-size:1.3rem">${esc(item.title)}</h2>
          <span class="tag">${cat.icon} ${esc(cat.label)}</span>
          <dl class="kv">
            ${kv('Plataforma', item.platform)}
            ${kv('Ano de lançamento', item.releaseYear)}
          </dl>
        </div>
      </div>

      ${cat.completion ? `
        <div class="card" style="margin-top:16px">
          <h3>Progresso</h3>
          ${progressHtml(item)}
          <input type="range" id="completion" min="0" max="100" step="5" value="${item.completionPercent ?? 0}" aria-label="Percentual de conclusão">
          ${(item.completionPercent ?? 0) < 100 ? '<button class="btn small secondary" data-action="complete">✓ Marcar como concluído</button>' : ''}
        </div>` : ''}

      <hr class="divider">
      <dl class="kv">
        ${kv(cat.creatorLabel, item.creator)}
        ${kv('Editora / distribuidora', item.publisher)}
        ${kv('Código de barras', item.barcode)}
      </dl>
      ${item.description ? `<h3 style="margin:16px 0 4px;font-size:1rem">Descrição</h3><p style="margin:0;white-space:pre-wrap">${esc(item.description)}</p>` : ''}
      ${item.notes ? `<h3 style="margin:16px 0 4px;font-size:1rem">Observações</h3><p style="margin:0;white-space:pre-wrap">${esc(item.notes)}</p>` : ''}

      <p class="meta" style="margin-top:20px">Adicionado em ${formatDate(item.createdAt)}${item.sourceName ? ` · Dados de ${esc(item.sourceName)}` : ''}</p>
      <div class="btn-group horizontal" style="margin-top:8px">
        ${item.sourceUrl ? `<a class="btn small outline" href="${esc(item.sourceUrl)}" target="_blank" rel="noopener">Abrir fonte</a>` : ''}
        <a class="btn small outline" href="${esc(googleLinks.web([item.title, item.platform].filter(Boolean).join(' ')))}" target="_blank" rel="noopener">Pesquisar no Google</a>
      </div>
    </main>
    <div class="fab-wrap"><button class="fab" data-nav="#/edit/${item.id}">✏️ Editar</button></div></div>`;

  hydrateCovers(root);
  const view = root.querySelector('.view');

  const range = root.querySelector('#completion');
  if (range) {
    range.addEventListener('input', () => {
      const pct = Number(range.value);
      const bar = root.querySelector('.progress');
      bar.querySelector('span').style.width = `${pct}%`;
      bar.classList.toggle('done', pct >= 100);
      root.querySelector('.progress-label').textContent = pct >= 100 ? 'Concluído' : `${pct}% concluído`;
    });
    range.addEventListener('change', async () => {
      await Items.put({ ...item, completionPercent: Number(range.value), updatedAt: Date.now() });
      renderDetail(root, id, { navigate });
    });
  }

  view.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    const action = btn.dataset.action;
    if (action === 'favorite') {
      await Items.put({ ...item, favorite: !item.favorite, updatedAt: Date.now() });
      renderDetail(root, id, { navigate });
    } else if (action === 'complete') {
      await Items.put({ ...item, completionPercent: 100, updatedAt: Date.now() });
      renderDetail(root, id, { navigate });
    } else if (action === 'delete') {
      if (window.confirm(`Excluir "${item.title}" da biblioteca? Esta ação não pode ser desfeita.`)) {
        await Items.remove(id);
        toast('Item excluído');
        navigate('#/');
      }
    }
  });
}
