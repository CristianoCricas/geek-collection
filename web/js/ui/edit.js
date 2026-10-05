import { CATEGORIES, category, normalizeItem, PLATFORMS, platformByName, PROGRESS_STATUSES, statusLabel } from '../model.js';
import { Items, Images } from '../db.js';
import { prepareImage } from '../recognition/image.js';
import { googleLinks } from '../lookup/google.js';
import { appBar, candidateHtml, categoryChips, esc, openSheet, toast } from './components.js';

/** Draft handed over by the scan flow (lives only in memory). */
export const draft = { item: null, cover: null };

const CUSTOM = '__custom__';

function platformOptions(selected) {
  const known = platformByName(selected);
  const isCustom = selected && !known;
  let lastBrand = null;
  let html = '<option value="">— Selecione —</option>';
  for (const p of PLATFORMS) {
    if (p.brand !== lastBrand) {
      if (lastBrand) html += '</optgroup>';
      html += `<optgroup label="${esc(p.brand)}">`;
      lastBrand = p.brand;
    }
    html += `<option value="${esc(p.name)}" ${known && known.name === p.name ? 'selected' : ''}>${esc(p.name)}</option>`;
  }
  html += `</optgroup><option value="${CUSTOM}" ${isCustom ? 'selected' : ''}>Outra plataforma…</option>`;
  return html;
}

export async function renderEdit(root, id, { navigate, services }) {
  let item;
  let coverBlob = null;
  let coverChanged = false;
  let removeImage = false;

  if (id) {
    item = await Items.get(id);
    if (!item) { navigate('#/'); return; }
  } else if (draft.item) {
    item = { ...draft.item };
    coverBlob = draft.cover;
    coverChanged = Boolean(coverBlob);
    draft.item = null;
    draft.cover = null;
  } else {
    item = normalizeItem({ title: '', category: 'VIDEO_GAME' });
  }

  const render = () => {
    const cat = category(item.category);
    root.innerHTML = `<div class="view">
      ${appBar({
        title: id ? 'Editar item' : 'Novo item',
        back: id ? `#/item/${id}` : '#/',
        actions: '<button class="btn small" data-action="save">✓ Salvar</button>',
      })}
      <main>
        <form id="form" novalidate>
          <div class="item-card" style="cursor:default;align-items:flex-start;margin:8px 0 16px">
            <div class="cover md" id="cover-box"><span>${cat.icon}</span></div>
            <div class="btn-group" style="flex:1">
              <label class="btn small outline">📷 Tirar foto<input class="file-input" type="file" accept="image/*" capture="environment" data-file></label>
              <label class="btn small outline">🖼️ Escolher da galeria<input class="file-input" type="file" accept="image/*" data-file></label>
              <button type="button" class="btn small outline" data-action="cover-search" ${services.lookup.google.isConfigured ? '' : 'disabled title="Configure a pesquisa Google"'}>🔎 Buscar capa no Google</button>
              <button type="button" class="btn small danger" data-action="remove-photo" ${(item.hasImage && !removeImage) || coverBlob || item.coverUrl ? '' : 'hidden'}>Remover imagem</button>
            </div>
          </div>

          <div class="field">
            <label for="title">Título</label>
            <input class="input" id="title" name="title" value="${esc(item.title)}" autocomplete="off" required>
            <p class="hint error" id="title-error" hidden>Informe um título</p>
          </div>
          <button type="button" class="btn block" data-action="search">🌐 Buscar dados no Google</button>
          <p class="hint" style="margin:6px 0 16px">Preenche descrição, capa, ano e autor a partir do título${services.lookup.google.isConfigured ? '' : '. Sem a chave da API, usa Google Books (livros) e Wikipédia'}.</p>

          <div class="field">
            <label>Categoria</label>
            ${categoryChips(item.category)}
          </div>

          <div class="field" ${cat.platform ? '' : 'hidden'}>
            <label for="platform">Plataforma</label>
            <select class="input" id="platform" name="platformSelect">${platformOptions(item.platform)}</select>
            <input class="input" id="platform-custom" name="platformCustom" placeholder="Digite o nome da plataforma" value="${esc(item.platform && !platformByName(item.platform) ? item.platform : '')}" ${item.platform && !platformByName(item.platform) ? '' : 'hidden'}>
          </div>

          <div class="field" ${cat.completion ? '' : 'hidden'}>
            <label for="completion">Percentual de conclusão: <strong id="completion-value">${item.completionPercent ?? 0}%</strong></label>
            <input type="range" id="completion" name="completionPercent" min="0" max="100" step="5" value="${item.completionPercent ?? 0}">
          </div>
          <div class="field" ${cat.completion ? '' : 'hidden'}>
            <label for="status">Status</label>
            <select class="input" id="status" name="progressStatus">${PROGRESS_STATUSES.map((s) => `<option value="${s.id}" ${(item.progressStatus || 'IN_PROGRESS') === s.id ? 'selected' : ''}>${s.icon} ${esc(statusLabel(s.id, cat.id))}</option>`).join('')}</select>
          </div>
          <div class="field" ${cat.platinum ? '' : 'hidden'}>
            <label class="toggle"><input type="checkbox" name="platinum" ${item.platinum ? 'checked' : ''}> <span>🏆 Platinado<small>Todos os troféus/conquistas. Marca 100% e história finalizada.</small></span></label>
          </div>
          <div class="field" ${cat.backlog ? '' : 'hidden'}>
            <label class="toggle"><input type="checkbox" name="backlog" ${item.backlog ? 'checked' : ''}> <span>📥 Backlog<small>Ainda não comecei a jogar.</small></span></label>
          </div>

          <div class="field">
            <label for="creator">${esc(cat.creatorLabel)}</label>
            <input class="input" id="creator" name="creator" value="${esc(item.creator || '')}">
          </div>
          <div class="field">
            <label for="publisher">Editora / distribuidora</label>
            <input class="input" id="publisher" name="publisher" value="${esc(item.publisher || '')}">
          </div>
          <div class="row">
            <div class="field">
              <label for="year">Ano de lançamento</label>
              <input class="input" id="year" name="releaseYear" inputmode="numeric" maxlength="4" value="${esc(item.releaseYear || '')}">
            </div>
            <div class="field" style="flex:1.4">
              <label for="barcode">Código de barras</label>
              <input class="input" id="barcode" name="barcode" inputmode="numeric" value="${esc(item.barcode || '')}">
            </div>
          </div>
          <div class="field">
            <label for="coverUrl">URL da capa (opcional)</label>
            <input class="input" id="coverUrl" name="coverUrl" inputmode="url" value="${esc(item.coverUrl || '')}" placeholder="https://…">
          </div>
          <div class="field">
            <label for="description">Descrição</label>
            <textarea class="input" id="description" name="description">${esc(item.description || '')}</textarea>
          </div>
          <div class="field">
            <label for="notes">Observações</label>
            <textarea class="input" id="notes" name="notes" style="min-height:60px">${esc(item.notes || '')}</textarea>
          </div>
          <button type="submit" class="btn block">✓ Salvar</button>
        </form>
      </main></div>`;

    showCover();
    bind();
  };

  async function showCover() {
    const box = root.querySelector('#cover-box');
    if (coverBlob) {
      box.innerHTML = `<img src="${URL.createObjectURL(coverBlob)}" alt="">`;
    } else if (item.hasImage && item.id && !removeImage) {
      const blob = await Images.get(item.id);
      if (blob) box.innerHTML = `<img src="${URL.createObjectURL(blob)}" alt="">`;
    } else if (item.coverUrl) {
      box.innerHTML = `<img src="${esc(item.coverUrl)}" alt="" referrerpolicy="no-referrer" onerror="this.remove()">`;
    }
  }

  /** Reads the form fields back into the item object. */
  function collect() {
    const f = root.querySelector('#form');
    const sel = f.platformSelect.value;
    const platform = sel === CUSTOM ? f.platformCustom.value : sel;
    item = {
      ...item,
      title: f.title.value,
      platform,
      completionPercent: Number(f.completionPercent.value),
      progressStatus: f.progressStatus.value,
      platinum: f.platinum.checked,
      backlog: f.backlog.checked,
      creator: f.creator.value,
      publisher: f.publisher.value,
      releaseYear: f.releaseYear.value,
      barcode: f.barcode.value,
      coverUrl: f.coverUrl.value,
      description: f.description.value,
      notes: f.notes.value,
    };
  }

  function bind() {
    const form = root.querySelector('#form');
    const view = root.querySelector('.view');

    root.querySelector('[data-chips]').addEventListener('click', (e) => {
      const chip = e.target.closest('.chip');
      if (!chip) return;
      collect();
      item.category = chip.dataset.value;
      render();
    });

    form.platformSelect.addEventListener('change', () => {
      const custom = root.querySelector('#platform-custom');
      custom.hidden = form.platformSelect.value !== CUSTOM;
      if (!custom.hidden) custom.focus();
    });

    form.completionPercent.addEventListener('input', () => {
      root.querySelector('#completion-value').textContent = `${form.completionPercent.value}%`;
    });

    form.coverUrl.addEventListener('change', () => { collect(); if (!coverBlob) showCover(); });

    root.querySelectorAll('[data-file]').forEach((input) => input.addEventListener('change', async () => {
      const file = input.files && input.files[0];
      if (!file) return;
      try {
        const { cover } = await prepareImage(file);
        coverBlob = cover;
        coverChanged = true;
        removeImage = false;
        collect();
        render();
      } catch (err) {
        toast(err.message || 'Não foi possível ler a imagem');
      }
    }));

    form.addEventListener('submit', (e) => { e.preventDefault(); save(); });

    view.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-action]');
      if (!btn) return;
      const action = btn.dataset.action;
      if (action === 'save') { e.preventDefault(); save(); }
      if (action === 'remove-photo') {
        collect();
        coverBlob = null;
        coverChanged = true;
        removeImage = true;
        item.coverUrl = null;
        render();
      }
      if (action === 'search') searchOnline();
      if (action === 'cover-search') searchCover();
    });
  }

  async function save() {
    collect();
    const normalized = normalizeItem({ ...item, hasImage: coverBlob ? true : (item.hasImage && !removeImage) });
    if (!normalized.title) {
      root.querySelector('#title').classList.add('error');
      root.querySelector('#title-error').hidden = false;
      root.querySelector('#title').focus();
      return;
    }
    const savedId = await Items.put(normalized);
    if (coverChanged) {
      if (coverBlob) await Images.put(savedId, coverBlob);
      else await Images.remove(savedId);
    }
    toast('Item salvo');
    services.sync && services.sync.schedule();
    navigate(`#/item/${savedId}`);
  }

  async function searchOnline() {
    collect();
    if (!item.title.trim()) {
      root.querySelector('#title').classList.add('error');
      root.querySelector('#title-error').hidden = false;
      return;
    }
    const sheet = openSheet('<h2>Resultados encontrados</h2><div class="center"><div class="spinner"></div>Buscando dados…</div>');
    try {
      const outcome = await services.lookup.searchByTitle(item.title, item.category, item.platform);
      renderCandidates(sheet, outcome, (c) => { applyCandidate(c); sheet.close(); });
    } catch (err) {
      sheet.el.innerHTML = `<h2>Resultados encontrados</h2><p class="hint error">${esc(err.message)}</p>`;
    }
  }

  function renderCandidates(sheet, outcome, onPick) {
    const list = outcome.candidates;
    sheet.el.innerHTML = `
      <h2>Resultados encontrados</h2>
      ${list.length ? list.map(candidateHtml).join('') : '<p class="hint">Nenhum resultado online.</p>'}
      ${outcome.errors.length ? `<p class="hint error">Algumas fontes falharam: ${esc(outcome.errors.join('; '))}</p>` : ''}
      ${!outcome.googleConfigured ? `<p class="hint">Para pesquisar no Google direto do app, <a href="#/settings">configure a chave da API</a>. Ou abra <a href="${esc(googleLinks.web(item.title))}" target="_blank" rel="noopener">o Google</a> em outra aba.</p>` : ''}`;
    sheet.el.querySelectorAll('[data-candidate]').forEach((el) => {
      el.addEventListener('click', () => onPick(list[Number(el.dataset.candidate)]));
    });
  }

  function applyCandidate(c) {
    item = {
      ...item,
      title: c.title || item.title,
      category: c.category || item.category,
      platform: c.platform || item.platform,
      description: c.description || item.description,
      creator: c.creator || item.creator,
      publisher: c.publisher || item.publisher,
      releaseYear: c.releaseYear || item.releaseYear,
      barcode: c.barcode || item.barcode,
      coverUrl: c.coverUrl || item.coverUrl,
      sourceName: c.sourceName || item.sourceName,
      sourceUrl: c.sourceUrl || item.sourceUrl,
    };
    render();
    toast('Dados preenchidos. Revise e salve.');
  }

  async function searchCover() {
    collect();
    if (!item.title.trim()) { toast('Informe um título primeiro'); return; }
    const sheet = openSheet('<h2>Capas no Google Imagens</h2><div class="center"><div class="spinner"></div>Buscando imagens…</div>');
    try {
      const images = await services.lookup.coverImages(item.title, item.category, item.platform);
      sheet.el.innerHTML = `
        <h2>Capas no Google Imagens</h2>
        ${images.length ? `<div class="image-grid">${images.map((im, i) => `<button type="button" data-img="${i}" title="${esc(im.title)}"><img src="${esc(im.thumbnail)}" alt="${esc(im.title)}" referrerpolicy="no-referrer" loading="lazy"></button>`).join('')}</div>` : '<p class="hint">Nenhuma imagem encontrada.</p>'}
        <p class="hint">Toque em uma imagem para usá-la como capa.</p>`;
      sheet.el.querySelectorAll('[data-img]').forEach((b) => b.addEventListener('click', () => {
        item.coverUrl = images[Number(b.dataset.img)].url;
        sheet.close();
        render();
      }));
    } catch (err) {
      sheet.el.innerHTML = `<h2>Capas no Google Imagens</h2><p class="hint error">${esc(err.message)}</p>`;
    }
  }

  render();
}

export { CATEGORIES };
