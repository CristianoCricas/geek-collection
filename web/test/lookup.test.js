import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GoogleSearch, candidateFromResult, cleanTitle } from '../js/lookup/google.js';
import { GoogleBooks } from '../js/lookup/books.js';
import { Wikipedia } from '../js/lookup/wikipedia.js';
import { LookupService } from '../js/lookup/service.js';

const fakeHttp = (routes) => {
  const calls = [];
  const http = async (url) => {
    calls.push(url);
    const key = Object.keys(routes).find((k) => url.includes(k));
    if (!key) throw new Error(`unexpected url ${url}`);
    const r = routes[key];
    if (r instanceof Error) throw r;
    return r;
  };
  http.calls = calls;
  return http;
};

test('cleanTitle strips store suffixes but keeps real subtitles', () => {
  assert.equal(cleanTitle('Catan - O Jogo | Amazon.com.br'), 'Catan - O Jogo');
  assert.equal(cleanTitle('God of War Ragnarök - PS5 - Mercado Livre'), 'God of War Ragnarök - PS5');
  assert.equal(cleanTitle('The Legend of Zelda: Breath of the Wild'), 'The Legend of Zelda: Breath of the Wild');
  assert.equal(cleanTitle('Dune - Wikipédia, a enciclopédia livre'), 'Dune');
});

test('google custom search results become candidates', async () => {
  const http = fakeHttp({
    'customsearch/v1': {
      items: [{
        title: 'Jogo Catan - Galápagos Jogos | Amazon.com.br',
        link: 'https://www.amazon.com.br/dp/B07',
        snippet: 'Catan é um jogo de tabuleiro para 3 a 4 jogadores lançado em 1995 ...',
        pagemap: {
          cse_image: [{ src: 'https://m.media-amazon.com/images/I/catan.jpg' }],
          metatags: [{ 'og:title': 'Catan - Galápagos Jogos', 'og:description': 'Jogo de tabuleiro de colonização.' }],
          product: [{ brand: 'Galápagos' }],
        },
      }],
    },
  });
  const google = new GoogleSearch(() => ({ googleApiKey: 'k', googleCx: 'c' }), http);
  assert.equal(google.isConfigured, true);
  const [c] = await google.candidates('catan jogo de tabuleiro');
  assert.equal(c.title, 'Catan - Galápagos Jogos');
  assert.equal(c.description, 'Jogo de tabuleiro de colonização.');
  assert.equal(c.coverUrl, 'https://m.media-amazon.com/images/I/catan.jpg');
  assert.equal(c.creator, 'Galápagos');
  assert.equal(c.releaseYear, 1995);
  assert.equal(c.sourceName, 'amazon.com.br');
  assert.match(http.calls[0], /key=k&cx=c&q=catan%20jogo%20de%20tabuleiro/);
  assert.doesNotMatch(http.calls[0], /searchType/);
});

test('google image search maps results', async () => {
  const http = fakeHttp({
    'searchType=image': { items: [{ title: 'Capa', link: 'https://img.example/catan.jpg', image: { thumbnailLink: 'https://t/x', contextLink: 'https://page', width: 800, height: 1000 } }] },
  });
  const google = new GoogleSearch(() => ({ googleApiKey: 'k', googleCx: 'c' }), http);
  const imgs = await google.images('catan capa');
  assert.deepEqual(imgs[0], { url: 'https://img.example/catan.jpg', thumbnail: 'https://t/x', pageUrl: 'https://page', title: 'Capa', width: 800, height: 1000 });
});

test('google unconfigured throws without calling the network', async () => {
  const http = fakeHttp({});
  const google = new GoogleSearch(() => ({}), http);
  assert.equal(google.isConfigured, false);
  await assert.rejects(() => google.candidates('x'), /não configurada/);
  assert.equal(http.calls.length, 0);
});

test('google books parses volumes by isbn', async () => {
  const http = fakeHttp({
    'books/v1/volumes': {
      items: [{ volumeInfo: {
        title: 'Fantastic Mr. Fox', authors: ['Roald Dahl'], publisher: 'Puffin', publishedDate: '1988-10-01',
        description: 'A <b>fox</b> story.', industryIdentifiers: [{ type: 'ISBN_10', identifier: '0140328726' }, { type: 'ISBN_13', identifier: '9780140328721' }],
        imageLinks: { thumbnail: 'http://books.google.com/books/content?id=1&edge=curl' }, infoLink: 'https://books.google.com/books?id=1', categories: ['Juvenile Fiction'],
      } }],
    },
  });
  const [c] = await new GoogleBooks(() => ({}), http).byIsbn('0140328726');
  assert.equal(c.title, 'Fantastic Mr. Fox');
  assert.equal(c.creator, 'Roald Dahl');
  assert.equal(c.publisher, 'Puffin');
  assert.equal(c.releaseYear, 1988);
  assert.equal(c.description, 'A fox story.');
  assert.equal(c.barcode, '9780140328721');
  assert.equal(c.coverUrl, 'https://books.google.com/books/content?id=1');
  assert.equal(c.category, 'BOOK');
  assert.match(http.calls[0], /q=isbn%3A9780140328721/);
  assert.doesNotMatch(http.calls[0], /key=/);
});

test('wikipedia fallback orders by index', async () => {
  const http = fakeHttp({
    'wikipedia.org': { query: { pages: {
      2: { pageid: 2, index: 2, title: 'Mega Drive', extract: 'Console lançado em 1988.' },
      1: { pageid: 1, index: 1, title: 'Sega Genesis', extract: 'Console da Sega.', thumbnail: { source: 'https://up/g.jpg' }, fullurl: 'https://pt.wikipedia.org/wiki/Sega_Genesis' },
    } } },
  });
  const list = await new Wikipedia(() => ({ wikiLang: 'pt' }), http).candidates('mega drive');
  assert.deepEqual(list.map((c) => c.title), ['Sega Genesis', 'Mega Drive']);
  assert.equal(list[0].coverUrl, 'https://up/g.jpg');
  assert.equal(list[1].releaseYear, 1988);
  assert.match(http.calls[0], /^https:\/\/pt\.wikipedia\.org/);
});

function service({ configured = true, routes = {} } = {}) {
  const http = fakeHttp(routes);
  const settings = () => (configured ? { googleApiKey: 'k', googleCx: 'c' } : {});
  const svc = new LookupService({
    google: new GoogleSearch(settings, http),
    books: new GoogleBooks(settings, http),
    wikipedia: new Wikipedia(settings, http),
  });
  return { svc, http };
}

test('lookup: ISBN goes to Google Books and suggests BOOK', async () => {
  const { svc, http } = service({ routes: {
    'books/v1/volumes': { items: [{ volumeInfo: { title: 'Dune', authors: ['Frank Herbert'], industryIdentifiers: [{ type: 'ISBN_13', identifier: '9780441172719' }] } }] },
    'customsearch/v1': { items: [] },
  } });
  const out = await svc.lookup({ barcodes: ['9780441172719'], textLines: ['DUNE', 'Frank Herbert'] });
  assert.equal(out.suggestedCategory, 'BOOK');
  assert.equal(out.barcode, '9780441172719');
  assert.equal(out.candidates[0].title, 'Dune');
  assert.ok(http.calls.some((u) => u.includes('isbn%3A9780441172719')));
  assert.ok(http.calls.some((u) => u.includes('intitle%3A')));
  // Google web search still runs for books when configured, with the category hint appended.
  assert.ok(http.calls.some((u) => u.includes('customsearch') && u.includes('livro')));
});

test('lookup: game box uses platform in the Google query and applies hints', async () => {
  const { svc, http } = service({ routes: {
    'customsearch/v1': { items: [
      { title: 'God of War Ragnarök - PS4 - Mercado Livre', link: 'https://ml.com/x', snippet: 'Jogo God of War Ragnarök para PlayStation 4, mídia física.' },
      { title: 'God of War Ragnarök - PS4 - Mercado Livre', link: 'https://ml.com/x', snippet: 'dup' },
    ] },
  } });
  const out = await svc.lookup({ barcodes: [], textLines: ['GOD OF WAR', 'RAGNARÖK', 'Only on PlayStation 4', 'PEGI 18'] });
  assert.equal(out.suggestedCategory, 'VIDEO_GAME');
  assert.equal(out.suggestedPlatform, 'PlayStation 4');
  assert.equal(out.titleGuess, 'GOD OF WAR');
  assert.equal(out.candidates.length, 1);
  assert.equal(out.candidates[0].platform, 'PlayStation 4');
  assert.equal(out.candidates[0].category, 'VIDEO_GAME');
  assert.match(http.calls[0], /q=GOD%20OF%20WAR%20PlayStation%204%20jogo/);
});

test('lookup: without Google key falls back to Wikipedia and category comes from snippets', async () => {
  const { svc, http } = service({ configured: false, routes: {
    'wikipedia.org': { query: { pages: { 1: { pageid: 1, index: 1, title: 'Iron Man (Marvel Legends)', extract: 'Action figure da linha Marvel Legends da Hasbro.' } } } },
  } });
  const out = await svc.lookup({ barcodes: [], textLines: ['IRON MAN', 'Hasbro'] });
  assert.equal(out.googleConfigured, false);
  assert.equal(out.candidates[0].sourceName, 'pt.wikipedia.org');
  assert.equal(out.suggestedCategory, 'ACTION_FIGURE');
  assert.ok(http.calls.every((u) => u.includes('wikipedia.org')));
});

test('lookup: EAN barcode searches Google with the quoted number', async () => {
  const { svc, http } = service({ routes: {
    'customsearch/v1': { items: [{ title: 'Controle DualSense PS5 - Sony', link: 'https://sony.com/ds', snippet: 'Controle sem fio DualSense para PlayStation 5' }] },
  } });
  const out = await svc.searchByBarcode('711719399209');
  assert.equal(out.barcode, '711719399209');
  assert.equal(out.suggestedCategory, 'ACCESSORY');
  assert.equal(out.candidates[0].platform, 'PlayStation 5');
  assert.match(http.calls[0], /q=%22711719399209%22/);
});

test('lookup: provider failure is reported, not thrown', async () => {
  const { svc } = service({ routes: { 'customsearch/v1': new Error('quota'), 'books/v1/volumes': { items: [] } } });
  const out = await svc.searchByTitle('Catan', 'BOARD_GAME');
  assert.deepEqual(out.candidates, []);
  assert.deepEqual(out.errors, ['Google: quota']);
});
