import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyBarcode, detectPlatform, extractYear, guessTitle, isIsbn, rankTitles, suggestCategory, toIsbn13,
} from '../js/recognition/heuristics.js';
import { normalizeItem, normalizePlatform, category, progressSummary, statusLabel } from '../js/model.js';

test('barcode classification', () => {
  assert.equal(isIsbn('9780140328721'), true);
  assert.equal(classifyBarcode('978-0-14-032872-1'), 'ISBN');
  assert.equal(toIsbn13('0140328726'), '9780140328721');
  assert.equal(toIsbn13('0-306-40615-2'), '9780306406157');
  assert.equal(isIsbn('9780140328722'), false);
  assert.equal(toIsbn13('0140328722'), null);
  assert.equal(classifyBarcode('7898357410015'), 'EAN_UPC');
  assert.equal(classifyBarcode('045496590420'), 'EAN_UPC');
  assert.equal(classifyBarcode('https://example.com'), 'UNKNOWN');
});

test('platform detection prefers specific aliases and whole words', () => {
  assert.equal(detectPlatform(['ONLY ON PLAYSTATION 5', 'PEGI 18']), 'PlayStation 5');
  assert.equal(detectPlatform(['Nintendo Switch', 'Zelda']), 'Nintendo Switch');
  assert.equal(detectPlatform(['XBOX SERIES X']), 'Xbox Series X|S');
  assert.equal(detectPlatform(['Nintendo', 'DS']), 'Nintendo DS');
  assert.equal(detectPlatform(['Nice words']), null);
  assert.equal(detectPlatform(['Catan', '3-4 jogadores']), null);
});

test('title guesser skips boilerplate', () => {
  const lines = ['PEGI 12', 'THE LEGEND OF ZELDA', 'Breath of the Wild', 'Nintendo Switch', '045496590420', 'www.nintendo.com'];
  assert.equal(guessTitle(lines), 'THE LEGEND OF ZELDA');
  assert.deepEqual(rankTitles(lines), ['THE LEGEND OF ZELDA', 'Breath of the Wild']);
  assert.equal(guessTitle(['123456', 'PEGI 3', 'ab']), null);
});

test('category suggestion', () => {
  assert.equal(suggestCategory({ barcodes: ['9780140328721'], textLines: ['Boneco'] }), 'BOOK');
  assert.equal(suggestCategory({ textLines: ['PlayStation 4', 'God of War'] }), 'VIDEO_GAME');
  assert.equal(suggestCategory({ textLines: ['Xbox One', 'Controle sem fio'] }), 'ACCESSORY');
  assert.equal(suggestCategory({ textLines: ['CATAN', '3-4 jogadores', '75 minutos'] }), 'BOARD_GAME');
  assert.equal(suggestCategory({ textLines: ['Iron Man'], snippets: ['Action Figure Iron Man Marvel Legends 15 cm'] }), 'ACTION_FIGURE');
  assert.equal(suggestCategory({ textLines: ['Berserk Vol. 1'], snippets: ['Mangá Berserk volume 1 - Panini Comics'] }), 'COMIC');
  assert.equal(suggestCategory({}), null);
});

test('year extraction', () => {
  assert.equal(extractYear('Lançado em 18 de maio de 2015'), 2015);
  assert.equal(extractYear('2017-03-03'), 2017);
  assert.equal(extractYear('sem ano'), null);
});

test('item normalization follows category rules', () => {
  const game = normalizeItem({ title: '  Zelda ', category: 'VIDEO_GAME', platform: ' Nintendo Switch ', completionPercent: '150', releaseYear: '2017' });
  assert.equal(game.title, 'Zelda');
  assert.equal(game.platform, 'Nintendo Switch');
  assert.equal(game.completionPercent, 100);
  assert.equal(game.releaseYear, 2017);
  const figure = normalizeItem({ title: 'Iron Man', category: 'ACTION_FIGURE', platform: 'PS5', completionPercent: 40 });
  assert.equal(figure.platform, null);
  assert.equal(figure.completionPercent, null);
  const book = normalizeItem({ title: 'Dune', category: 'BOOK' });
  assert.equal(book.completionPercent, 0);
  assert.equal(category('NOPE').id, 'OTHER');
  assert.equal(normalizePlatform('ps5'), 'PlayStation 5');
  assert.equal(normalizePlatform('Genesis'), 'Mega Drive');
});

test('progress status, platinum and backlog follow category rules', () => {
  const game = normalizeItem({ title: 'Elden Ring', category: 'VIDEO_GAME', completionPercent: 40, progressStatus: 'PAUSED', backlog: true });
  assert.equal(game.progressStatus, 'PAUSED');
  assert.equal(game.backlog, true);
  assert.equal(game.platinum, false);
  assert.equal(progressSummary(game), '⏸️ Pausado · 40%');

  const plat = normalizeItem({ title: 'Bloodborne', category: 'VIDEO_GAME', completionPercent: 70, platinum: true });
  assert.equal(plat.platinum, true);
  assert.equal(plat.completionPercent, 100);
  assert.equal(plat.progressStatus, 'FINISHED');
  assert.equal(progressSummary(plat), '🏆 Platinado');

  const finished = normalizeItem({ title: 'GoW', category: 'VIDEO_GAME', completionPercent: 80, progressStatus: 'FINISHED' });
  assert.equal(progressSummary(finished), '🏁 História finalizada · 80%');
  assert.equal(statusLabel('FINISHED', 'BOOK'), 'Finalizado');

  const board = normalizeItem({ title: 'Catan', category: 'BOARD_GAME', backlog: true, platinum: true, progressStatus: 'ABANDONED' });
  assert.equal(board.backlog, true);
  assert.equal(board.platinum, false, 'platinum is only for video games');
  assert.equal(board.progressStatus, 'ABANDONED');

  const book = normalizeItem({ title: 'Dune', category: 'BOOK', backlog: true, progressStatus: 'BOGUS' });
  assert.equal(book.backlog, false, 'backlog is only for games');
  assert.equal(book.progressStatus, 'IN_PROGRESS');
  assert.equal(progressSummary(book), '0% concluído');

  const figure = normalizeItem({ title: 'Iron Man', category: 'ACTION_FIGURE', progressStatus: 'PAUSED', backlog: true, platinum: true });
  assert.equal(figure.progressStatus, null);
  assert.equal(figure.backlog, false);
  assert.equal(figure.platinum, false);
  assert.equal(progressSummary(figure), '');
});
