// Pure heuristics that turn raw recognition output (barcodes, OCR lines,
// search snippets) into structured hints. No DOM, no network.
import { PLATFORMS } from '../model.js';

// ---------------------------------------------------------------- barcodes

export function normalizeBarcode(raw) {
  return String(raw || '').replace(/[^0-9Xx]/g, '').toUpperCase();
}

function ean13CheckDigit(first12) {
  let sum = 0;
  for (let i = 0; i < 12; i++) {
    const d = first12.charCodeAt(i) - 48;
    sum += i % 2 === 0 ? d : d * 3;
  }
  return String((10 - (sum % 10)) % 10);
}

function isValidEan13(v) {
  return /^\d{13}$/.test(v) && ean13CheckDigit(v.slice(0, 12)) === v[12];
}

function isValidIsbn10(v) {
  if (!/^\d{9}[\dX]$/.test(v)) return false;
  let sum = 0;
  for (let i = 0; i < 10; i++) {
    const c = v[i];
    const d = c === 'X' ? 10 : c.charCodeAt(0) - 48;
    sum += d * (10 - i);
  }
  return sum % 11 === 0;
}

export function isIsbn(raw) {
  const v = normalizeBarcode(raw);
  if (v.length === 10) return isValidIsbn10(v);
  if (v.length === 13) return (v.startsWith('978') || v.startsWith('979')) && isValidEan13(v);
  return false;
}

/** Returns the ISBN-13 form of a valid ISBN-10/13, or null. */
export function toIsbn13(raw) {
  const v = normalizeBarcode(raw);
  if (v.length === 13 && isIsbn(v)) return v;
  if (v.length === 10 && isValidIsbn10(v)) {
    const base = '978' + v.slice(0, 9);
    return base + ean13CheckDigit(base);
  }
  return null;
}

/** @returns {'ISBN'|'EAN_UPC'|'UNKNOWN'} */
export function classifyBarcode(raw) {
  const v = normalizeBarcode(raw);
  if (isIsbn(v)) return 'ISBN';
  if (/^\d{8,14}$/.test(v)) return 'EAN_UPC';
  return 'UNKNOWN';
}

// ---------------------------------------------------------------- platforms

const platformCandidates = PLATFORMS.flatMap((p) => [p.name, ...p.aliases].map((alias) => ({ platform: p, alias })))
  .sort((a, b) => b.alias.length - a.alias.length);

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

export function detectPlatform(lines) {
  const text = (Array.isArray(lines) ? lines.join(' ') : String(lines || '')).toLowerCase();
  if (!text.trim()) return null;
  for (const { platform, alias } of platformCandidates) {
    const a = alias.toLowerCase();
    if (a.length <= 3) {
      if (new RegExp(`(?<![a-z0-9])${escapeRe(a)}(?![a-z0-9])`).test(text)) return platform.name;
    } else if (text.includes(a)) {
      return platform.name;
    }
  }
  return null;
}

// ---------------------------------------------------------------- title guess

const BOILERPLATE = [
  'pegi', 'esrb', 'classificação', 'classificacao', 'indicativa', 'anos', 'years',
  'only on', 'exclusivo', 'edition', 'edição', 'edicao', 'blu-ray', 'dvd', 'disc', 'disco',
  'playstation', 'xbox', 'nintendo', 'switch', 'ps4', 'ps5', 'wii', 'sega', 'pc dvd',
  'jogadores', 'players', 'idade', 'ages', 'contém', 'contains', 'made in', 'fabricado',
  'www.', '.com', 'http', 'isbn', 'barcode', 'código', 'codigo', 'bestseller', 'best seller',
  'autor', 'author', 'editora', 'publisher', 'tradução', 'volume', 'vol.',
];

function scoreLine(line) {
  const letters = (line.match(/\p{L}/gu) || []).length;
  const digits = (line.match(/\d/g) || []).length;
  const total = line.length;
  if (letters < 3) return 0;
  if (letters / total < 0.5) return 0;
  const lower = line.toLowerCase();
  if (BOILERPLATE.some((b) => lower.includes(b))) return 0;
  if (digits > letters) return 0;
  let score = Math.min(letters, 30);
  const upper = (line.match(/\p{Lu}/gu) || []).length;
  if (upper / letters > 0.7) score += 6;
  if (total > 45) score -= (total - 45) * 0.5;
  const words = line.split(' ');
  if (words.length <= 7 && words.every((w) => /^\p{Lu}/u.test(w) || w.length <= 3)) score += 4;
  return score;
}

/** Candidate titles ordered from most to least likely. */
export function rankTitles(lines) {
  const seen = new Set();
  return (lines || [])
    .map((l) => String(l).trim().replace(/\s+/g, ' '))
    .filter((l) => l.length >= 3)
    .map((l) => ({ l, s: scoreLine(l) }))
    .filter((x) => x.s > 0)
    .sort((a, b) => b.s - a.s)
    .map((x) => x.l)
    .filter((l) => {
      const k = l.toLowerCase();
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    })
    .slice(0, 5);
}

export function guessTitle(lines) {
  return rankTitles(lines)[0] || null;
}

// ---------------------------------------------------------------- category

const KEYWORDS = [
  { cat: 'BOARD_GAME', words: ['jogo de tabuleiro', 'board game', 'boardgame', 'tabuleiro', 'card game', 'jogo de cartas', 'jogadores', 'players', 'galápagos', 'devir', 'grok', 'ludopedia', 'boardgamegeek'] },
  { cat: 'COMIC', words: ['mangá', 'manga', 'hq', 'quadrinhos', 'comic', 'graphic novel', 'panini comics', 'jbc', 'newpop'] },
  { cat: 'BOOK', words: ['livro', 'book', 'isbn', 'editora', 'páginas', 'pages', 'romance', 'capa dura', 'capa comum', 'paperback', 'hardcover', 'autor', 'author', 'edição'] },
  { cat: 'ACTION_FIGURE', words: ['action figure', 'figura de ação', 'figure', 'boneco', 'estátua', 'statue', 'funko', 'nendoroid', 'figma', 'hot toys', 's.h.figuarts', 'marvel legends', 'bandai'] },
  { cat: 'CONSOLE', words: ['console', 'videogame console', 'portátil', 'handheld'] },
  { cat: 'ACCESSORY', words: ['controle', 'controller', 'gamepad', 'joystick', 'headset', 'fone', 'cabo', 'cable', 'acessório', 'accessory', 'carregador', 'charger', 'memory card', 'volante'] },
  { cat: 'VIDEO_GAME', words: ['video game', 'videogame', 'jogo', 'game', 'gameplay', 'steam', 'mídia física', 'lacrado', 'dlc', 'rpg', 'fps'] },
  { cat: 'COLLECTIBLE', words: ['colecionável', 'collectible', 'pelúcia', 'plush', 'caneca', 'mug', 'pôster', 'poster', 'chaveiro', 'keychain', 'miniatura', 'réplica'] },
];

/**
 * Suggests a category from barcodes, OCR lines and (optionally) web snippets.
 * Snippets weigh less than the item's own text.
 */
export function suggestCategory({ barcodes = [], textLines = [], snippets = [] } = {}) {
  if (barcodes.some((b) => classifyBarcode(b) === 'ISBN')) return 'BOOK';
  const scores = new Map();
  const bump = (cat, n) => scores.set(cat, (scores.get(cat) || 0) + n);

  const own = textLines.join(' ').toLowerCase();
  const web = snippets.join(' ').toLowerCase();
  for (const { cat, words } of KEYWORDS) {
    for (const w of words) {
      const re = new RegExp(`(?<![\\p{L}\\p{N}])${escapeRe(w)}(?![\\p{L}\\p{N}])`, 'u');
      if (re.test(own)) bump(cat, w.length > 4 ? 2 : 1);
      if (re.test(web)) bump(cat, w.length > 4 ? 1 : 0.5);
    }
  }
  // A platform name on the box or in the snippets points at the gaming
  // world; accessory/console words decide which kind of gaming item it is.
  const ownPlatform = detectPlatform(textLines);
  const webPlatform = detectPlatform(snippets);
  if (ownPlatform || webPlatform) {
    const weight = ownPlatform ? 2 : 1;
    const acc = (scores.get('ACCESSORY') || 0) > 0;
    const con = (scores.get('CONSOLE') || 0) > 0;
    bump(acc ? 'ACCESSORY' : con ? 'CONSOLE' : 'VIDEO_GAME', weight);
  }
  if (!scores.size) return null;
  return [...scores.entries()].sort((a, b) => b[1] - a[1])[0][0];
}

/** Extracts a plausible release year from free text. */
export function extractYear(text) {
  const m = String(text || '').match(/\b(1[89]\d{2}|20\d{2})\b/);
  return m ? Number(m[1]) : null;
}
