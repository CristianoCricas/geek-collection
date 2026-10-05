import { Settings, exportLibrary, importLibrary, Items } from '../db.js';
import { appBar, esc, formatDate, toast } from './components.js';

const WIKI = [['pt', 'Português'], ['en', 'English'], ['es', 'Español']];
const OCR = [['por+eng', 'Português + Inglês'], ['por', 'Português'], ['eng', 'Inglês']];

export async function renderSettings(root, { navigate, services, onSettingsChanged, version }) {
  const s = await Settings.load();
  const count = await Items.count();
  const sync = services && services.sync;
  const auth = sync ? await sync.firebase.loadAuth() : null;
  const lastSync = sync ? await sync.lastSyncAt() : null;
  const signedIn = Boolean(auth && auth.refreshToken);

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

        <div class="card" id="sync-card">
          <h3>☁️ Sincronização na nuvem</h3>
          <p class="hint">Mantém a biblioteca igual em todos os seus aparelhos (PWA e app Android) usando o <strong>Firebase</strong> do Google, no plano gratuito. Só o que foi criado ou alterado desde a última sincronização é enviado ou recebido.</p>
          <details ${s.firebaseProjectId ? '' : 'open'}>
            <summary class="hint" style="cursor:pointer">Como configurar (uma vez)</summary>
            <ol class="steps hint">
              <li>Em <a href="https://console.firebase.google.com/" target="_blank" rel="noopener">console.firebase.google.com</a>, crie um projeto.</li>
              <li>Em <em>Authentication → Sign-in method</em>, ative <strong>E-mail/senha</strong>.</li>
              <li>Em <em>Firestore Database</em>, crie o banco (modo produção) e, na aba <em>Regras</em>, cole:
                <pre style="white-space:pre-wrap;font-size:0.75rem"><code>rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null &amp;&amp; request.auth.uid == uid;
    }
  }
}</code></pre></li>
              <li>Em <em>Configurações do projeto → Geral</em>, copie o <strong>ID do projeto</strong> e a <strong>Chave de API da Web</strong> e cole abaixo.</li>
            </ol>
          </details>
          <div class="field">
            <label for="firebaseProjectId">ID do projeto Firebase</label>
            <input class="input" id="firebaseProjectId" name="firebaseProjectId" value="${esc(s.firebaseProjectId)}" autocomplete="off" spellcheck="false" placeholder="ex.: geek-collection-1234a">
          </div>
          <div class="field">
            <label for="firebaseApiKey">Chave de API da Web</label>
            <input class="input" id="firebaseApiKey" name="firebaseApiKey" value="${esc(s.firebaseApiKey)}" autocomplete="off" spellcheck="false">
          </div>
          <label class="toggle"><input type="checkbox" name="autoSync" ${s.autoSync === false ? '' : 'checked'}> <span>Sincronizar automaticamente<small>Ao abrir o app, ao voltar a ficar online e alguns segundos após cada alteração.</small></span></label>
          <hr class="divider">
          ${signedIn ? `
            <p>Conectado como <strong>${esc(auth.email || auth.uid)}</strong>${lastSync ? `<br><span class="hint">Última sincronização: ${esc(new Date(lastSync).toLocaleString('pt-BR'))}</span>` : ''}</p>
            <div class="btn-group horizontal">
              <button type="button" class="btn small" data-action="sync-now">☁️ Sincronizar agora</button>
              <button type="button" class="btn small outline" data-action="sign-out">Sair</button>
            </div>
            <p class="hint" id="sync-status"></p>
          ` : `
            <div class="field">
              <label for="syncEmail">E-mail</label>
              <input class="input" id="syncEmail" type="email" autocomplete="email" ${s.firebaseApiKey ? '' : 'disabled'}>
            </div>
            <div class="field">
              <label for="syncPassword">Senha (mínimo 6 caracteres)</label>
              <input class="input" id="syncPassword" type="password" autocomplete="current-password" ${s.firebaseApiKey ? '' : 'disabled'}>
            </div>
            <div class="btn-group horizontal">
              <button type="button" class="btn small" data-action="sign-in" ${s.firebaseApiKey ? '' : 'disabled'}>Entrar</button>
              <button type="button" class="btn small outline" data-action="sign-up" ${s.firebaseApiKey ? '' : 'disabled'}>Criar conta</button>
            </div>
            <p class="hint">${s.firebaseApiKey ? 'Use o mesmo e-mail e senha em todos os aparelhos.' : 'Preencha e salve o projeto Firebase para habilitar o login.'}</p>
            <p class="hint error" id="sync-error"></p>
          `}
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
      firebaseProjectId: f.firebaseProjectId.value.trim(),
      firebaseApiKey: f.firebaseApiKey.value.trim(),
      autoSync: f.autoSync.checked,
    };
    await Settings.save(next);
    onSettingsChanged(next);
    toast('Configurações salvas');
    navigate('#/');
  });

  const saveConfig = async () => {
    const f = root.querySelector('#form');
    const next = { ...s, firebaseProjectId: f.firebaseProjectId.value.trim(), firebaseApiKey: f.firebaseApiKey.value.trim(), autoSync: f.autoSync.checked };
    await Settings.save(next);
    onSettingsChanged(next);
    return next;
  };
  const showError = (msg) => { const el = root.querySelector('#sync-error'); if (el) el.textContent = msg; };
  const credential = async (method) => {
    const next = await saveConfig();
    if (!next.firebaseProjectId || !next.firebaseApiKey) { showError('Informe o ID do projeto e a chave de API.'); return; }
    const email = root.querySelector('#syncEmail').value.trim();
    const password = root.querySelector('#syncPassword').value;
    if (!email || !password) { showError('Informe e-mail e senha.'); return; }
    showError('');
    try {
      await sync.firebase[method](email, password);
      toast(method === 'signUp' ? 'Conta criada' : 'Conectado');
      await renderSettings(root, { navigate, services, onSettingsChanged, version });
      sync.run({ onStatus: (m) => { const el = root.querySelector('#sync-status'); if (el) el.textContent = m; } }).catch(() => {});
    } catch (err) {
      showError(err.message || 'Falha na autenticação');
    }
  };
  root.querySelector('[data-action="sign-in"]')?.addEventListener('click', () => credential('signIn'));
  root.querySelector('[data-action="sign-up"]')?.addEventListener('click', () => credential('signUp'));
  root.querySelector('[data-action="sign-out"]')?.addEventListener('click', async () => {
    await sync.firebase.signOut();
    await Settings.setValue('syncCursor', null);
    toast('Desconectado');
    renderSettings(root, { navigate, services, onSettingsChanged, version });
  });
  root.querySelector('[data-action="sync-now"]')?.addEventListener('click', async () => {
    await saveConfig();
    const status = root.querySelector('#sync-status');
    try {
      await sync.run({ onStatus: (m) => { status.textContent = m; } });
      renderSettings(root, { navigate, services, onSettingsChanged, version });
    } catch (err) {
      status.textContent = err.message || 'Falha ao sincronizar';
    }
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
