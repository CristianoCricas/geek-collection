// Domain model shared by the UI, the recognition heuristics and the lookup
// services. Pure JavaScript, no DOM access, so it is unit tested with Node.

/** @typedef {'BOARD_GAME'|'VIDEO_GAME'|'CONSOLE'|'ACCESSORY'|'BOOK'|'COMIC'|'ACTION_FIGURE'|'COLLECTIBLE'|'OTHER'} CategoryId */

export const CATEGORIES = Object.freeze([
  { id: 'BOARD_GAME', label: 'Jogo de tabuleiro', icon: '🎲', platform: false, completion: true, creatorLabel: 'Designer', searchHint: 'jogo de tabuleiro' },
  { id: 'VIDEO_GAME', label: 'Game', icon: '🎮', platform: true, completion: true, creatorLabel: 'Desenvolvedora', searchHint: 'jogo' },
  { id: 'CONSOLE', label: 'Console', icon: '🕹️', platform: true, completion: false, creatorLabel: 'Fabricante', searchHint: 'console' },
  { id: 'ACCESSORY', label: 'Acessório', icon: '🎧', platform: true, completion: false, creatorLabel: 'Marca', searchHint: 'acessório' },
  { id: 'BOOK', label: 'Livro', icon: '📚', platform: false, completion: true, creatorLabel: 'Autor', searchHint: 'livro' },
  { id: 'COMIC', label: 'HQ / Mangá', icon: '💥', platform: false, completion: true, creatorLabel: 'Autor', searchHint: 'HQ mangá' },
  { id: 'ACTION_FIGURE', label: 'Action figure', icon: '🤖', platform: false, completion: false, creatorLabel: 'Marca', searchHint: 'action figure' },
  { id: 'COLLECTIBLE', label: 'Colecionável', icon: '⭐', platform: false, completion: false, creatorLabel: 'Marca', searchHint: 'colecionável' },
  { id: 'OTHER', label: 'Outro', icon: '📦', platform: false, completion: false, creatorLabel: 'Marca / autor', searchHint: '' },
]);

const byId = new Map(CATEGORIES.map((c) => [c.id, c]));

/** @param {string|undefined|null} id */
export function category(id) {
  return byId.get(id) || byId.get('OTHER');
}

export const PLATFORMS = Object.freeze([
  // Nintendo
  { name: 'NES', brand: 'Nintendo', aliases: ['Nintendo Entertainment System', 'Nintendinho', 'Famicom'] },
  { name: 'Super Nintendo', brand: 'Nintendo', aliases: ['SNES', 'Super NES', 'Super Famicom'] },
  { name: 'Nintendo 64', brand: 'Nintendo', aliases: ['N64'] },
  { name: 'GameCube', brand: 'Nintendo', aliases: ['Nintendo GameCube', 'NGC'] },
  { name: 'Wii', brand: 'Nintendo', aliases: ['Nintendo Wii'] },
  { name: 'Wii U', brand: 'Nintendo', aliases: ['Nintendo Wii U'] },
  { name: 'Nintendo Switch', brand: 'Nintendo', aliases: ['Switch', 'NSW'] },
  { name: 'Nintendo Switch 2', brand: 'Nintendo', aliases: ['Switch 2'] },
  { name: 'Game Boy', brand: 'Nintendo', aliases: ['Gameboy', 'GB'] },
  { name: 'Game Boy Color', brand: 'Nintendo', aliases: ['GBC'] },
  { name: 'Game Boy Advance', brand: 'Nintendo', aliases: ['GBA'] },
  { name: 'Nintendo DS', brand: 'Nintendo', aliases: ['NDS', 'DS'] },
  { name: 'Nintendo 3DS', brand: 'Nintendo', aliases: ['3DS'] },
  // Sony
  { name: 'PlayStation', brand: 'Sony', aliases: ['PS1', 'PSX', 'PSOne', 'PlayStation 1'] },
  { name: 'PlayStation 2', brand: 'Sony', aliases: ['PS2'] },
  { name: 'PlayStation 3', brand: 'Sony', aliases: ['PS3'] },
  { name: 'PlayStation 4', brand: 'Sony', aliases: ['PS4'] },
  { name: 'PlayStation 5', brand: 'Sony', aliases: ['PS5'] },
  { name: 'PSP', brand: 'Sony', aliases: ['PlayStation Portable'] },
  { name: 'PS Vita', brand: 'Sony', aliases: ['PlayStation Vita', 'Vita'] },
  // Microsoft
  { name: 'Xbox', brand: 'Microsoft', aliases: ['Xbox Classic'] },
  { name: 'Xbox 360', brand: 'Microsoft', aliases: ['X360'] },
  { name: 'Xbox One', brand: 'Microsoft', aliases: ['XONE', 'XB1'] },
  { name: 'Xbox Series X|S', brand: 'Microsoft', aliases: ['Xbox Series X', 'Xbox Series S', 'Series X', 'Series S', 'XSX'] },
  // Sega
  { name: 'Master System', brand: 'Sega', aliases: ['Sega Master System', 'SMS'] },
  { name: 'Mega Drive', brand: 'Sega', aliases: ['Sega Genesis', 'Genesis', 'Sega Mega Drive'] },
  { name: 'Sega Saturn', brand: 'Sega', aliases: ['Saturn'] },
  { name: 'Dreamcast', brand: 'Sega', aliases: ['Sega Dreamcast'] },
  { name: 'Game Gear', brand: 'Sega', aliases: ['Sega Game Gear'] },
  // Others
  { name: 'Atari 2600', brand: 'Atari', aliases: ['Atari'] },
  { name: 'Neo Geo', brand: 'SNK', aliases: ['NeoGeo'] },
  { name: 'PC', brand: 'PC', aliases: ['Windows', 'Steam', 'Computador'] },
  { name: 'Mobile', brand: 'Mobile', aliases: ['Android', 'iOS', 'Celular'] },
  { name: 'Arcade', brand: 'Arcade', aliases: ['Fliperama'] },
]);

export function platformByName(name) {
  if (!name) return null;
  const lower = name.trim().toLowerCase();
  return PLATFORMS.find((p) => p.name.toLowerCase() === lower) || null;
}

/** Maps any spelling (alias or name) onto the curated platform name, or returns the input. */
export function normalizePlatform(name) {
  if (!name) return null;
  const lower = name.trim().toLowerCase();
  const hit = PLATFORMS.find((p) => p.name.toLowerCase() === lower || p.aliases.some((a) => a.toLowerCase() === lower));
  return hit ? hit.name : name.trim();
}

const clean = (v) => {
  if (v === undefined || v === null) return null;
  const s = String(v).trim();
  return s.length ? s : null;
};

/**
 * Builds a well-formed item: trims strings, drops fields that do not apply to
 * the category and clamps the completion percentage.
 */
export function normalizeItem(input) {
  const cat = category(input.category);
  const now = Date.now();
  const year = Number.parseInt(input.releaseYear, 10);
  let completion = null;
  if (cat.completion) {
    const n = Number.parseInt(input.completionPercent, 10);
    completion = Number.isFinite(n) ? Math.min(100, Math.max(0, n)) : 0;
  }
  return {
    id: input.id || null,
    title: clean(input.title) || '',
    category: cat.id,
    platform: cat.platform ? clean(input.platform) : null,
    completionPercent: completion,
    description: clean(input.description),
    creator: clean(input.creator),
    publisher: clean(input.publisher),
    releaseYear: Number.isFinite(year) && year > 1800 && year < 2200 ? year : null,
    barcode: clean(input.barcode),
    coverUrl: clean(input.coverUrl),
    hasImage: Boolean(input.hasImage),
    notes: clean(input.notes),
    favorite: Boolean(input.favorite),
    sourceUrl: clean(input.sourceUrl),
    sourceName: clean(input.sourceName),
    createdAt: input.createdAt || now,
    updatedAt: now,
  };
}

export function completionOf(item) {
  return Math.min(100, Math.max(0, item.completionPercent ?? 0));
}
