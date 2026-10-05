# Geek Collection — convenções do projeto

Instruções para quem trabalha neste repositório (incluindo o Claude Code).

## Commits e push

- **Agrupar antes do push.** Cada push ao branch padrão dispara o workflow *Version*, que
  cria uma versão nova. Por isso, uma entrega (recurso, correção ou ajuste) deve chegar ao
  remoto em **um único commit**: acumule as alterações localmente, valide (testes, smoke
  test, CI quando aplicável) e só então faça um commit e um push. Ajustes feitos logo
  depois, na mesma entrega, entram com `git commit --amend` (ou squash) **antes** de
  empurrar; nunca como uma sequência de pushes pequenos.
- **Prefixo da mensagem define o bump** (ver tabela no README):
  - `feat:` / `feature:` / `melhoria:` / `recurso:` → 2º dígito (melhoria ou recurso novo);
  - `fix:`, `chore:`, `refactor:`, `perf:`, `test:`, `ci:` etc. → 3º dígito;
  - `docs:` → sem bump (apenas documentação);
  - `chore(release):` é reservado ao próprio workflow e a ajustes manuais de versão.
  - O 1º dígito (MASTER) só muda manualmente: Actions → *Version* → *Run workflow* → `major`.
- Nunca editar `VERSION`, `web/js/version.js`, `web/sw.js` (constante `VERSION`) ou a
  versão em `web/package.json` à mão fora de `scripts/bump-version.mjs`.
- Tags `vX.Y.Z` são criadas pelo workflow. O proxy de algumas sessões bloqueia push de
  tags; não é necessário criá-las localmente.

## Validação antes do push

- `cd web && npm test` (heurísticas, parsers, sync do PWA).
- Testes do core Android (`./gradlew :core:test`; sem SDK Android, copiar `core/` para um
  build isolado só com o plugin Kotlin JVM).
- Mudanças de interface do PWA: rodar um smoke test no Chromium headless (Playwright) e
  olhar as capturas, inclusive em 360 px de largura.
- O módulo `app/` (Android) só compila no GitHub Actions; antes de empurrar, pedir uma
  revisão de compilação do diff e corrigir o que for apontado.

## Firebase (sync e login)

- Os valores padrão do projeto ficam em `web/js/config.js` e
  `app/src/main/res/values/firebase.xml` (vazios no repositório; a chave de API web é pública
  por desenho, a segurança vem das regras do Firestore). Não colocar chaves privadas.
- A tela de login (`web/js/ui/login.js`, `ui/login/LoginScreen.kt`) é a única porta de
  entrada da conta; Configurações só mostra status, "Sair" e o projeto.

## Idioma

Interface, README e mensagens ao usuário em português do Brasil; identificadores de código
e mensagens de commit em inglês.
