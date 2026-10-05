import { Settings } from '../db.js';
import { esc, toast } from './components.js';

/**
 * Login screen: the account is what enables cloud sync. The user can also
 * go on without an account (local-only library) and come back later.
 */
export async function renderLogin(root, { navigate, services, version, onSettingsChanged }) {
  const firebase = services.sync.firebase;
  const settings = await Settings.load();
  let mode = 'signin'; // signin | signup | reset
  let busy = false;
  let error = '';
  let info = '';
  let email = '';
  let password = '';
  let showConfig = !firebase.isConfigured;

  const render = () => {
    const configured = firebase.isConfigured;
    root.innerHTML = `<div class="view login">
      <main class="login-main">
        <div class="login-hero">
          <img src="icons/icon.svg" alt="" width="72" height="72">
          <h1>Geek Collection <span class="version-badge">v${esc(version)}</span></h1>
          <p class="hint">Entre para sincronizar sua biblioteca entre o celular, o PWA e o app Android.</p>
        </div>
        <form id="login-form" class="card" novalidate>
          <h2>${mode === 'signup' ? 'Criar conta' : mode === 'reset' ? 'Redefinir senha' : 'Entrar'}</h2>
          <div class="field">
            <label for="email">E-mail</label>
            <input class="input" id="email" type="email" autocomplete="email" inputmode="email" value="${esc(email)}" ${configured ? '' : 'disabled'} required>
          </div>
          ${mode !== 'reset' ? `
          <div class="field">
            <label for="password">Senha${mode === 'signup' ? ' (mínimo 6 caracteres)' : ''}</label>
            <input class="input" id="password" type="password" autocomplete="${mode === 'signup' ? 'new-password' : 'current-password'}" value="${esc(password)}" ${configured ? '' : 'disabled'} required>
          </div>` : '<p class="hint">Enviaremos um e-mail com o link para criar uma nova senha.</p>'}
          ${error ? `<p class="hint error">${esc(error)}</p>` : ''}
          ${info ? `<p class="hint" style="color:var(--accent)">${esc(info)}</p>` : ''}
          <button type="submit" class="btn block" ${configured && !busy ? '' : 'disabled'}>
            ${busy ? 'Aguarde…' : mode === 'signup' ? 'Criar conta' : mode === 'reset' ? 'Enviar e-mail' : 'Entrar'}
          </button>
          <div class="login-links">
            ${mode !== 'signin' ? '<a href="#" data-mode="signin">Já tenho conta</a>' : '<a href="#" data-mode="signup">Criar conta</a>'}
            ${mode !== 'reset' ? '<a href="#" data-mode="reset">Esqueci a senha</a>' : ''}
          </div>
        </form>

        ${!configured ? `<p class="hint error" style="text-align:center">A sincronização ainda não foi configurada neste app.</p>` : ''}
        <details class="card" ${showConfig ? 'open' : ''}>
          <summary class="hint" style="cursor:pointer">Projeto Firebase ${configured ? '(configurado)' : '(obrigatório para o login)'}</summary>
          <p class="hint">Quem publica o app define o projeto uma vez em <code>web/js/config.js</code>. Também dá para informar aqui, neste aparelho. Passo a passo no README e em Configurações.</p>
          <div class="field">
            <label for="firebaseProjectId">ID do projeto</label>
            <input class="input" id="firebaseProjectId" value="${esc(settings.firebaseProjectId)}" placeholder="${esc(firebase.config.projectId || 'ex.: geek-collection-1234a')}" spellcheck="false" autocomplete="off">
          </div>
          <div class="field">
            <label for="firebaseApiKey">Chave de API da Web</label>
            <input class="input" id="firebaseApiKey" value="${esc(settings.firebaseApiKey)}" spellcheck="false" autocomplete="off">
          </div>
          <button type="button" class="btn small secondary" data-action="save-config">Salvar projeto</button>
        </details>

        <button type="button" class="btn block outline" data-action="skip">Continuar sem conta</button>
        <p class="hint" style="text-align:center">Sem conta, a biblioteca fica só neste aparelho. Você pode entrar depois pelo ícone ☁️.</p>
      </main>
    </div>`;
    bind();
  };

  function readFields() {
    email = root.querySelector('#email')?.value.trim() || email;
    password = root.querySelector('#password')?.value ?? password;
  }

  function bind() {
    const view = root.querySelector('.view');
    view.querySelectorAll('[data-mode]').forEach((a) => a.addEventListener('click', (e) => {
      e.preventDefault();
      readFields();
      mode = a.dataset.mode;
      error = '';
      info = '';
      render();
      root.querySelector('#email')?.focus();
    }));

    root.querySelector('#login-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      readFields();
      if (!email) { error = 'Informe o e-mail.'; render(); return; }
      if (mode !== 'reset' && !password) { error = 'Informe a senha.'; render(); return; }
      busy = true; error = ''; info = '';
      render();
      try {
        if (mode === 'reset') {
          await firebase.sendPasswordReset(email);
          info = 'E-mail enviado. Verifique sua caixa de entrada e depois entre com a nova senha.';
          mode = 'signin';
        } else {
          if (mode === 'signup') await firebase.signUp(email, password);
          else await firebase.signIn(email, password);
          const next = { ...(await Settings.load()), skipLogin: false };
          await Settings.save(next);
          onSettingsChanged(next);
          toast(mode === 'signup' ? 'Conta criada. Sincronizando…' : 'Conectado. Sincronizando…');
          navigate('#/');
          services.sync.run({ silent: true }).catch(() => {});
          return;
        }
      } catch (err) {
        error = err.message || 'Falha na autenticação';
      } finally {
        busy = false;
      }
      render();
    });

    view.querySelector('[data-action="save-config"]').addEventListener('click', async () => {
      readFields();
      const next = {
        ...(await Settings.load()),
        firebaseProjectId: root.querySelector('#firebaseProjectId').value.trim(),
        firebaseApiKey: root.querySelector('#firebaseApiKey').value.trim(),
      };
      await Settings.save(next);
      onSettingsChanged(next);
      Object.assign(settings, next);
      showConfig = !firebase.isConfigured;
      error = ''; info = firebase.isConfigured ? 'Projeto salvo. Agora entre ou crie sua conta.' : '';
      render();
    });

    view.querySelector('[data-action="skip"]').addEventListener('click', async () => {
      const next = { ...(await Settings.load()), skipLogin: true };
      await Settings.save(next);
      onSettingsChanged(next);
      navigate('#/');
    });
  }

  render();
}
