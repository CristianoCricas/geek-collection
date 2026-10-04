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
- Dados em IndexedDB (fotos incluídas), **exportação/importação em JSON** para backup.
- Service worker: funciona offline, inclusive o OCR (motor e idiomas embutidos em `web/vendor/`).

Rodar localmente:

```bash
cd web
npm test                       # testes das heurísticas e dos parsers (Node 22)
npx http-server . -p 8080 -c-1 # abre http://localhost:8080
```

## Versão Android

### Funcionalidades

- **Cadastro por categoria** com campos que mudam conforme o tipo do item:
  - *Games, consoles e acessórios*: campo de **plataforma** (lista curada de Nintendo, Sony,
    Microsoft, Sega, Atari, PC etc., ou nome livre).
  - *Jogos de tabuleiro, games, livros e HQs*: **percentual de conclusão** (0 a 100 %), com
    barra de progresso na lista e ajuste rápido na tela de detalhes.
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
- **app** segue MVVM com `ViewModel` + `StateFlow`, injeção manual via `AppContainer`
  e navegação com Navigation Compose.

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
