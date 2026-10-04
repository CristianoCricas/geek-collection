# Geek Collection

Aplicativo Android para catalogar sua biblioteca geek: jogos de tabuleiro, games, consoles,
acessórios, livros, HQs/mangás, action figures e colecionáveis em geral.

## Funcionalidades

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

### Fontes de dados

| Tipo | Fonte | Chave? |
| --- | --- | --- |
| Livros / HQs | [Open Library](https://openlibrary.org/developers/api) (ISBN e título) | não |
| Jogos de tabuleiro | [BoardGameGeek XML API 2](https://boardgamegeek.com/wiki/page/BGG_XML_API2) | não |
| Games | [RAWG](https://rawg.io/apidocs) | sim, gratuita (opcional) |
| Qualquer código de barras | [UPCitemdb](https://www.upcitemdb.com/api/explorer) (endpoint trial) | não |
| Consoles, acessórios, figures, fallback | Wikipédia (pt/en/es) | não |

Sem a chave da RAWG, os games caem no fallback da Wikipédia. A chave é informada em
*Configurações* dentro do app.

## Arquitetura

```
core/   módulo Kotlin puro (JVM) — modelos, heurísticas de reconhecimento e clientes das APIs
app/    módulo Android — Jetpack Compose, Room, ML Kit, Coil, OkHttp
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

## Build

```bash
./gradlew :core:test            # testes do núcleo (não precisa de SDK Android)
./gradlew :app:assembleDebug    # APK em app/build/outputs/apk/debug/
```

O workflow em `.github/workflows/android.yml` roda testes, lint e publica os APKs
(debug e release assinado com a chave de debug) como artefatos de cada push.

## Licença

Projeto pessoal de Cristiano Cricas. Defina a licença que preferir.
