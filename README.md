# Geek Collection

Catálogo da sua biblioteca geek: jogos de tabuleiro, games, consoles, acessórios, livros,
HQs/mangás, action figures e colecionáveis em geral. Disponível em duas versões:

| Versão | Pasta | Como usar |
| --- | --- | --- |
| **PWA** (web, instalável, offline) | [`web/`](web/) | Abra <https://cristianocricas.github.io/geek-collection/> no celular e toque em *Adicionar à tela inicial* |
| **Android nativo** (Kotlin + Compose) | [`app/`](app/) + [`core/`](core/) | APK gerado pelo workflow *Android CI* |

## Versão PWA

Sem etapa de build: HTML, CSS e JavaScript puros (ES modules), publicados no GitHub Pages
pelo workflow `.github/workflows/pages.yml`. Funciona no Chrome/Edge/Safari do celular e
pode ser instalada como app.

> **Ativação única do GitHub Pages:** em *Settings → Pages → Build and deployment*, escolha
> *Source: GitHub Actions*. Depois rode o workflow *Deploy PWA to GitHub Pages* (aba Actions →
> *Run workflow*) ou faça qualquer push em `web/`. O token padrão do Actions não consegue
> criar o site sozinho, por isso o primeiro deploy falha até essa opção ser marcada.

- **Pesquisa no Google** como fonte principal de dados. O Google não oferece busca sem chave
  para aplicativos, então o app usa a [API Pesquisa Personalizada / Programmable Search
  Engine](https://developers.google.com/custom-search/v1/overview) (gratuita até 100
  consultas/dia): você cria o mecanismo e a chave uma vez e cola em *Configurações*.
  Com ela o app pesquisa na web (título, descrição, capa, ano) e no **Google Imagens**
  (escolher a capa). Livros e HQs usam também o **Google Books** (ISBN e título, sem chave).
  Sem chave configurada, livros continuam pelo Google Books e o restante cai na Wikipédia,
  além de links para abrir o Google em outra aba.
- **Adicionar por foto**: câmera ou galeria. No próprio aparelho, lê **códigos de barras**
  (`BarcodeDetector` nativo ou ZXing) e faz **OCR** (tesseract.js, português + inglês);
  deduz título, plataforma e categoria; depois pesquisa no Google e mostra os candidatos.
  No Android, também dá para **compartilhar uma foto da galeria** direto para o app
  (Web Share Target).
- Mesmas regras de cadastro do app nativo: plataforma para games/consoles/acessórios,
  percentual de conclusão para tabuleiro/games/livros/HQs, favoritos, busca, filtros,
  ordenação.
- Progresso além do percentual: status **em andamento / pausado / abandonado / finalizado**
  (para games, "história finalizada"), flag **platinado** (games; marca 100 % e finalizado) e
  flag **backlog** para jogos em geral (games e tabuleiro). A biblioteca filtra por todos eles.
- Dados em IndexedDB (fotos incluídas), **exportação/importação em JSON** para backup.
- **Sincronização na nuvem** (opcional) entre PWA e app Android, veja abaixo.
- Service worker: funciona offline, inclusive o OCR (motor e idiomas embutidos em `web/vendor/`).

Rodar localmente:

```bash
cd web
npm test                       # testes das heurísticas e dos parsers (Node 22)
npx http-server . -p 8080 -c-1 # abre http://localhost:8080
```

## Sincronização na nuvem

As duas versões sincronizam a biblioteca pelo **Firebase** (Authentication com e-mail/senha +
Cloud Firestore), acessado via REST, sem SDK e dentro do plano gratuito. A sincronização é
**incremental**: cada item tem `createdAt`, `updatedAt`, um `syncId` estável entre aparelhos
e uma flag `dirty` marcada em toda alteração local. Só os itens marcados sobem; só os
documentos com `serverUpdatedAt` (carimbo do servidor) posterior ao cursor da última
sincronização descem. Nunca há sync completo. Conflitos são resolvidos pelo `updatedAt`
mais recente; exclusões viram tombstones (`deletedAt`) e também são propagadas. A foto do
item viaja como miniatura (JPEG de até 400 px) dentro do documento.

Configuração, uma única vez:

1. Crie um projeto em <https://console.firebase.google.com/>.
2. Em *Authentication → Sign-in method*, ative **E-mail/senha**.
3. Em *Firestore Database*, crie o banco (modo produção) e cole nas *Regras*:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```

4. Em *Configurações do projeto → Geral*, copie o **ID do projeto** e a **chave de API da
   Web** e cole em *Configurações* no app (PWA e Android). Crie a conta em um aparelho e
   entre com o mesmo e-mail/senha nos outros.

Layout dos dados: `users/{uid}/items/{syncId}`, com os campos do item mais `deleted`,
`thumb`, `client` e `serverUpdatedAt`.

## Versão do app

A versão fica no arquivo [`VERSION`](VERSION) (atualmente **0.1.0**, beta) e é lida pelo
Gradle (`versionName`/`versionCode`), pelo PWA (`web/js/version.js`) e pelo service worker
(nome do cache). O workflow [`version.yml`](.github/workflows/version.yml) atualiza a versão
automaticamente a cada push no branch padrão, seguindo a regra:

| Dígito | Quando muda | Como |
| --- | --- | --- |
| **1º (MASTER)** | decisão manual | Actions → *Version* → *Run workflow* com `bump = major` |
| **2º** | melhoria ou recurso novo | commit com prefixo `feat:` / `feature:` / `melhoria:` / `recurso:` (ou `[minor]` na mensagem) |
| **3º** | correção de bug ou chore | qualquer outro commit (`fix:`, `chore:`, `docs:`, `refactor:`…) |

O workflow analisa os commits desde a última tag `vX.Y.Z`, roda
`node scripts/bump-version.mjs <tipo>`, cria o commit `chore(release): bump version…`, a tag
e dispara os builds do Android e do PWA com a nova versão. Para ajustar na mão:
`node scripts/bump-version.mjs patch|minor|major`.

## Versão Android

### Funcionalidades

- **Cadastro por categoria** com campos que mudam conforme o tipo do item:
  - *Games, consoles e acessórios*: campo de **plataforma** (lista curada de Nintendo, Sony,
    Microsoft, Sega, Atari, PC etc., ou nome livre).
  - *Jogos de tabuleiro, games, livros e HQs*: **percentual de conclusão** (0 a 100 %), com
    barra de progresso na lista e ajuste rápido na tela de detalhes, mais o status
    (em andamento, pausado, abandonado, história finalizada), a flag **platinado** (games)
    e a flag **backlog** (games e tabuleiro).
- **Adicionar por foto (reconhecimento de imagem)**: fotografe a capa, a caixa ou o código de
  barras. O app roda, no próprio aparelho, os modelos do ML Kit para:
  - ler **códigos de barras** (EAN/UPC/ISBN);
  - reconhecer **texto** (OCR) e deduzir o título e a plataforma;
  - **rotular a imagem** para sugerir a categoria (livro, brinquedo, console…).

  Em seguida busca os dados na internet e mostra os candidatos para você escolher. Nenhuma
  foto sai do aparelho; só o texto reconhecido é enviado nas buscas.
- **Busca online a partir do título** direto no formulário, para preencher descrição, capa,
  ano, autor/desenvolvedora e editora.
- Biblioteca com busca, filtro por categoria, favoritos, ordenação e estatísticas.
- Foto própria do item (câmera ou galeria) ou capa obtida online.
- Dados 100 % locais (Room/SQLite). Sem conta, sem backend próprio.

#### Fontes de dados

| Tipo | Fonte | Chave? |
| --- | --- | --- |
| Livros / HQs | [Open Library](https://openlibrary.org/developers/api) (ISBN e título) | não |
| Jogos de tabuleiro | [BoardGameGeek XML API 2](https://boardgamegeek.com/wiki/page/BGG_XML_API2) | não |
| Games | [RAWG](https://rawg.io/apidocs) | sim, gratuita (opcional) |
| Qualquer código de barras | [UPCitemdb](https://www.upcitemdb.com/api/explorer) (endpoint trial) | não |
| Consoles, acessórios, figures, fallback | Wikipédia (pt/en/es) | não |

Sem a chave da RAWG, os games caem no fallback da Wikipédia. A chave é informada em
*Configurações* dentro do app.

### Arquitetura

```
core/   módulo Kotlin puro (JVM) — modelos, heurísticas de reconhecimento e clientes das APIs
app/    módulo Android — Jetpack Compose, Room, ML Kit, Coil, OkHttp
web/    PWA — js/model.js, js/recognition/ (heurísticas, barcode, OCR), js/lookup/ (Google, Google Books, Wikipédia), js/ui/
```

- **core** não depende do Android, por isso é coberto por testes unitários rápidos
  (`./gradlew :core:test`). Contém:
  - `model/` — `CollectionItem`, `ItemCategory` (define quais categorias têm plataforma e
    conclusão) e a lista de `Platforms`.
  - `recognition/` — `BarcodeClassifier` (ISBN x EAN), `TitleGuesser` (escolhe o título entre
    as linhas do OCR), `PlatformDetector` e `CategorySuggester`.
  - `lookup/` — um `LookupProvider` por fonte e o `LookupService`, que decide quais fontes
    consultar a partir do que foi reconhecido e agrega os resultados.
- **core** também contém `sync/`: codec do Firestore, cliente REST do Firebase e o
  `SyncEngine` (pull incremental, resolução de conflitos, push dos itens alterados), com testes.
- **app** segue MVVM com `ViewModel` + `StateFlow`, injeção manual via `AppContainer`
  e navegação com Navigation Compose. `sync/RoomSyncStore` liga o motor ao Room e
  `sync/SyncManager` cuida do sync automático (abertura do app e após alterações).

Requisitos: Android 8.0 (API 26) ou superior. Kotlin 2.0, AGP 8.7, Compose BOM 2024.12.

### Build

```bash
./gradlew :core:test            # testes do núcleo (não precisa de SDK Android)
./gradlew :app:assembleDebug    # APK em app/build/outputs/apk/debug/
```

O workflow em `.github/workflows/android.yml` roda testes, lint e publica os APKs
(debug e release assinado com a chave de debug) como artefatos de cada push.

## Licença

Projeto pessoal de Cristiano Cricas. Defina a licença que preferir.
