import { category, normalizeItem } from '../model.js';
import { prepareImage } from '../recognition/image.js';
import { recognizeImage } from '../recognition/recognizer.js';
import { googleLinks } from '../lookup/google.js';
import { appBar, candidateHtml, categoryChips, esc, toast } from './components.js';
import { draft } from './edit.js';

const state = { categoryHint: '' };

export async function renderScan(root, { navigate, services, settings, shared = false }) {
  let step = 'idle';
  let status = '';
  let previewUrl = null;
  let cover = null;
  let recognition = null;
  let outcome = null;
  let error = null;

  const render = () => {
    root.innerHTML = `<div class="view">
      ${appBar({ title: 'Adicionar por foto', back: '#/' })}
      <main>${step === 'idle' ? idleHtml() : step === 'working' ? workingHtml() : step === 'error' ? errorHtml() : resultsHtml()}</main></div>`;
    bind();
  };

  const idleHtml = () => `
    <div class="center" style="padding-top:16px">
      <div style="font-size:3.5rem">🔍</div>
      <h2 style="margin:8px 0">Reconhecimento de imagem</h2>
      <p class="hint" style="font-size:0.95rem">Fotografe a capa, a caixa ou o código de barras. O app lê códigos e textos no próprio aparelho e depois pesquisa os dados no Google.</p>
    </div>
    <div class="field">
      <label>Dica de categoria (opcional)</label>
      ${categoryChips(state.categoryHint, { allLabel: 'Detectar automaticamente' })}
    </div>
    <div class="btn-group">
      <label class="btn block">📷 Tirar foto<input class="file-input" type="file" accept="image/*" capture="environment" data-file></label>
      <label class="btn block secondary">🖼️ Usar foto da galeria<input class="file-input" type="file" accept="image/*" data-file></label>
    </div>
    ${services.lookup.google.isConfigured ? '' : `<p class="hint" style="margin-top:20px;text-align:center">Para pesquisar no Google direto do app, <a href="#/settings">configure a chave gratuita da API</a>. Sem ela, livros usam o Google Books e o restante usa a Wikipédia.</p>`}
    <p class="hint" style="text-align:center">Dica: no Android, você também pode compartilhar uma foto da galeria com o Geek Collection.</p>`;

  const workingHtml = () => `
    ${previewUrl ? `<img class="photo-preview" src="${previewUrl}" alt="">` : ''}
    <div class="center"><div class="spinner"></div><p>${esc(status || 'Analisando a imagem…')}</p></div>`;

  const errorHtml = () => `
    <div class="center"><p class="hint error">${esc(error)}</p><button class="btn" data-action="reset">Tentar novamente</button></div>`;

  const resultsHtml = () => {
    const cat = category(outcome.suggestedCategory || state.categoryHint);
    const lines = recognition.textLines || [];
    const query = outcome.titleGuess || outcome.barcode || '';
    return `
      <div class="card item-card" style="cursor:default;align-items:flex-start">
        <div class="cover md">${previewUrl ? `<img src="${previewUrl}" alt="">` : `<span>${cat.icon}</span>`}</div>
        <div class="item-body">
          <h3>O que foi reconhecido</h3>
          ${outcome.barcode ? `<p class="item-sub">Código de barras: ${esc(outcome.barcode)}</p>` : ''}
          ${outcome.suggestedCategory ? `<p class="item-sub">Categoria sugerida: ${esc(cat.label)}</p>` : ''}
          ${outcome.suggestedPlatform ? `<p class="item-sub">Plataforma: ${esc(outcome.suggestedPlatform)}</p>` : ''}
          ${lines.length ? `<p class="item-sub" style="white-space:normal">Texto: ${esc(lines.slice(0, 5).join(' / '))}</p>` : ''}
          ${!lines.length && !outcome.barcode ? '<p class="item-sub" style="white-space:normal">Nada reconhecido. Tente uma foto mais nítida da capa ou do código de barras.</p>' : ''}
        </div>
      </div>
      ${outcome.candidates.length ? `<div class="card"><h3>Resultados encontrados</h3>${outcome.candidates.map(candidateHtml).join('')}</div>`
        : (lines.length || outcome.barcode) ? '<p class="hint">Nenhum resultado online. Você pode preencher o cadastro manualmente com o que foi reconhecido.</p>' : ''}
      ${outcome.errors.length ? `<p class="hint error">Algumas fontes falharam: ${esc(outcome.errors.join('; '))}</p>` : ''}
      ${!outcome.googleConfigured ? `<p class="hint">Pesquisa Google não configurada. <a href="#/settings">Configurar</a>${query ? ` ou <a href="${esc(googleLinks.web(query))}" target="_blank" rel="noopener">abrir o Google em outra aba</a>` : ''}.</p>` : ''}
      <div class="btn-group" style="margin-top:8px">
        <button class="btn block" data-action="manual">Preencher manualmente</button>
        <div class="btn-group horizontal">
          <button class="btn outline" data-action="retry" ${lines.length || outcome.barcode ? '' : 'disabled'}>Buscar de novo</button>
          <button class="btn outline" data-action="reset">📷 Nova foto</button>
        </div>
      </div>`;
  };

  function bind() {
    root.querySelector('[data-chips]')?.addEventListener('click', (e) => {
      const chip = e.target.closest('.chip');
      if (!chip) return;
      state.categoryHint = chip.dataset.value === state.categoryHint ? '' : chip.dataset.value;
      render();
    });
    root.querySelectorAll('[data-file]').forEach((input) => input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (file) analyze(file);
    }));
    root.querySelectorAll('[data-candidate]').forEach((el) => el.addEventListener('click', () => pick(outcome.candidates[Number(el.dataset.candidate)])));
    root.querySelector('.view').addEventListener('click', (e) => {
      const btn = e.target.closest('[data-action]');
      if (!btn) return;
      if (btn.dataset.action === 'reset') { step = 'idle'; render(); }
      if (btn.dataset.action === 'retry') search();
      if (btn.dataset.action === 'manual') proceedManually();
    });
  }

  async function analyze(file) {
    step = 'working';
    status = 'Preparando a imagem…';
    render();
    try {
      const prepared = await prepareImage(file);
      cover = prepared.cover;
      previewUrl = URL.createObjectURL(cover);
      render();
      recognition = await recognizeImage(prepared.analysis, {
        langs: settings().ocrLangs || 'por+eng',
        onStatus: (s) => { status = s; const p = root.querySelector('.center p'); if (p) p.textContent = s; },
      });
      await search();
    } catch (err) {
      console.error(err);
      error = `Falha ao analisar a imagem: ${err.message || err}`;
      step = 'error';
      render();
    }
  }

  async function search() {
    step = 'working';
    status = 'Pesquisando no Google…';
    render();
    try {
      outcome = await services.lookup.lookup(recognition, state.categoryHint || null);
    } catch (err) {
      outcome = { candidates: [], errors: [String(err.message || err)], suggestedCategory: state.categoryHint || null, suggestedPlatform: null, titleGuess: null, barcode: null, googleConfigured: services.lookup.google.isConfigured };
    }
    step = 'results';
    render();
  }

  function recognizedItem() {
    const cat = outcome.suggestedCategory || state.categoryHint || 'OTHER';
    return normalizeItem({
      title: outcome.titleGuess || '',
      category: cat,
      platform: outcome.suggestedPlatform,
      barcode: outcome.barcode,
      hasImage: Boolean(cover),
    });
  }

  function pick(c) {
    const base = recognizedItem();
    draft.item = normalizeItem({
      ...base,
      title: c.title || base.title,
      category: c.category || base.category,
      platform: c.platform || base.platform,
      description: c.description,
      creator: c.creator,
      publisher: c.publisher,
      releaseYear: c.releaseYear,
      barcode: c.barcode || base.barcode,
      coverUrl: c.coverUrl,
      sourceName: c.sourceName,
      sourceUrl: c.sourceUrl,
    });
    draft.cover = cover;
    navigate('#/edit');
  }

  function proceedManually() {
    draft.item = recognizedItem();
    draft.cover = cover;
    navigate('#/edit');
  }

  render();

  if (shared) {
    try {
      const cache = await caches.open('geek-share');
      const res = await cache.match('shared-image');
      if (res) {
        const blob = await res.blob();
        await cache.delete('shared-image');
        analyze(new File([blob], 'shared.jpg', { type: blob.type || 'image/jpeg' }));
      } else {
        toast('Nenhuma imagem compartilhada encontrada');
      }
    } catch (err) {
      toast('Não foi possível ler a imagem compartilhada');
    }
  }
}
