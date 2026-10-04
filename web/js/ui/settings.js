import { Settings, exportLibrary, importLibrary, Items } from '../db.js';
import { appBar, esc, toast } from './components.js';

const WIKI = [['pt', 'Português'], ['en', 'English'], ['es', 'Español']];
const OCR = [['por+eng', 'Português + Inglês'], ['por', 'Português'], ['eng', 'Inglês']];

export async function renderSettings(root, { navigate, onSettingsChanged, version }) {
  const s = await Settings.load();
  const count = await Items.count();

  root.innerHTML = `
    ${appBar({ title: 'Configurações', back: '#/' })}
    <main>
      <form id="form">
        <div class="card">
          <h3>Pesquisa no Google</h3>
          <p class="hint">O app pesquisa no Google pela <strong>API Pesquisa Personalizada (Programmable Search Engine)</strong>, a forma oficial de fazer buscas Google a partir de um aplicativo. É gratuita até 100 consultas por dia. Configure uma vez:</p>
          <ol class="steps hint">
            <li>Acesse <a href="https://programmablesearchengine.google.com/controlpanel/create" target="_blank" rel="noopener">programmablesearchengine.google.com</a>, crie um mecanismo com <em>Pesquisar em toda a web</em> e ative a <em>Pesquisa de imagens</em>. Copie o <strong>ID do mecanismo (cx)</strong>.</li>
            <li>Em <a href="https://developers.google.com/custom-search/v1/introduction" target="_blank" rel="noopener">developers.google.com/custom-search</a>, clique em <em>Get a key</em> para gerar a <strong>chave da API</strong>.</li>
          </ol>
          <div class="field">
            <label for="googleCx">ID do mecanismo de pesquisa (cx)</label>
            <input class="input" id="googleCx" name="googleCx" value="${esc(s.googleCx)}" autocomplete="off" spellcheck="false">
          </div>
          <div class="field">
            <label for="googleApiKey">Chave da API do Google</label>
            <input class="input" id="googleApiKey" name="googleApiKey" value="${esc(s.googleApiKey)}" autocomplete="off" spellcheck="false">
          </div>
          <p class="hint">A chave fica salva apenas neste dispositivo. O Google Books (livros, por ISBN) funciona mesmo sem chave.</p>
        </div>

        <div class="card">
          <h3>Idioma</h3>
          <div class="field">
            <label for="ocrLangs">Idiomas do OCR (leitura de texto na foto)</label>
            <select class="input" id="ocrLangs" name="ocrLangs">${OCR.map(([v, l]) => `<option value="${v}" ${s.ocrLangs === v ? 'selected' : ''}>${l}</option>`).join('')}</select>
          </div>
          <div class="field">
            <label for="wikiLang">Wikipédia (usada quando a pesquisa Google não está configurada)</label>
            <select class="input" id="wikiLang" name="wikiLang">${WIKI.map(([v, l]) => `<option value="${v}" ${s.wikiLang === v ? 'selected' : ''}>${l}</option>`).join('')}</select>
          </div>
        </div>

        <button type="submit" class="btn block">Salvar</button>
      </form>

      <div class="card" style="margin-top:16px">
        <h3>Backup</h3>
        <p class="hint">Seus dados ficam só neste dispositivo (${count} itens). Exporte um arquivo JSON com itens e fotos para guardar ou levar para outro aparelho.</p>
        <div class="btn-group horizontal">
          <button class="btn small secondary" data-action="export">⬇️ Exportar</button>
          <label class="btn small secondary">⬆️ Importar<input class="file-input" type="file" accept="application/json,.json" id="import"></label>
        </div>
      </div>

      <div class="card">
        <h3>Fontes de dados</h3>
        <p class="hint">Pesquisa web e imagens: Google (Programmable Search). Livros e HQs: Google Books. Fallback sem chave: Wikipédia. O reconhecimento (código de barras e OCR) roda no aparelho; nenhuma foto é enviada para a internet, apenas o texto reconhecido vai na pesquisa.</p>
      </div>
      <div class="card">
        <h3>Sobre</h3>
        <p class="hint">Geek Collection PWA ${esc(version)} · <a href="https://github.com/CristianoCricas/geek-collection" target="_blank" rel="noopener">github.com/CristianoCricas/geek-collection</a></p>
      </div>
    </main>`;

  root.querySelector('#form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    const next = {
      ...s,
      googleCx: f.googleCx.value.trim(),
      googleApiKey: f.googleApiKey.value.trim(),
      ocrLangs: f.ocrLangs.value,
      wikiLang: f.wikiLang.value,
    };
    await Settings.save(next);
    onSettingsChanged(next);
    toast('Configurações salvas');
    navigate('#/');
  });

  root.querySelector('[data-action="export"]').addEventListener('click', async () => {
    const data = await exportLibrary();
    const blob = new Blob([JSON.stringify(data)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `geek-collection-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  });

  root.querySelector('#import').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      const data = JSON.parse(await file.text());
      const replace = window.confirm('Substituir a biblioteca atual pelos dados do arquivo? (Cancelar = adicionar aos itens existentes)');
      const n = await importLibrary(data, { replace });
      toast(`${n} itens importados`);
      navigate('#/');
    } catch (err) {
      toast(err.message || 'Falha ao importar');
    }
  });
}
