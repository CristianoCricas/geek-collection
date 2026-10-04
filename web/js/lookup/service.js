// Orchestrates recognition hints + online sources into a list of candidates.
// Google first (Programmable Search + Google Books); Wikipedia only when the
// Google search engine is not configured.
import { category as categoryOf } from '../model.js';
import {
  classifyBarcode, detectPlatform, normalizeBarcode, rankTitles, suggestCategory, toIsbn13,
} from '../recognition/heuristics.js';

const dedupe = (list) => {
  const seen = new Set();
  return list.filter((c) => {
    const key = (c.sourceUrl || `${c.source}:${c.title}`).toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

async function settle(tasks) {
  const results = await Promise.allSettled(tasks.map((t) => t.run()));
  const found = [];
  const errors = [];
  results.forEach((r, i) => {
    if (r.status === 'fulfilled') found.push(...r.value);
    else errors.push(`${tasks[i].name}: ${r.reason?.message || r.reason}`);
  });
  return { found, errors };
}

export class LookupService {
  /**
   * @param {{google: import('./google.js').GoogleSearch, books: import('./books.js').GoogleBooks, wikipedia: import('./wikipedia.js').Wikipedia}} deps
   */
  constructor({ google, books, wikipedia }) {
    this.google = google;
    this.books = books;
    this.wikipedia = wikipedia;
  }

  /** Query text sent to Google for a title + category pair. */
  buildQuery(title, categoryId, platform) {
    const cat = categoryOf(categoryId);
    const parts = [title];
    if (platform && cat.platform) parts.push(platform);
    if (categoryId && cat.searchHint) parts.push(cat.searchHint);
    return parts.join(' ').trim();
  }

  /**
   * Full pipeline for a photo: barcode → Google Books / Google; OCR title →
   * Google (or Wikipedia fallback). Returns candidates plus the hints.
   */
  async lookup(recognition, categoryHint = null) {
    const titles = rankTitles(recognition.textLines || []);
    const barcode = (recognition.barcodes || []).map(normalizeBarcode).find((b) => classifyBarcode(b) !== 'UNKNOWN') || null;
    let category = categoryHint || suggestCategory(recognition);
    const platform = detectPlatform(recognition.textLines || []);

    const tasks = [];
    if (barcode && classifyBarcode(barcode) === 'ISBN') {
      tasks.push({ name: 'Google Books', run: () => this.books.byIsbn(barcode) });
    } else if (barcode && this.google.isConfigured) {
      tasks.push({ name: 'Google', run: () => this.google.candidates(`"${barcode}"`, 5) });
    }
    const title = titles[0];
    if (title) {
      const isBook = category === 'BOOK' || category === 'COMIC';
      if (isBook) tasks.push({ name: 'Google Books', run: () => this.books.byTitle(title, 5) });
      if (this.google.isConfigured) {
        tasks.push({ name: 'Google', run: () => this.google.candidates(this.buildQuery(title, category, platform), 6) });
      } else if (!isBook) {
        tasks.push({ name: 'Wikipédia', run: () => this.wikipedia.candidates(title, 5) });
      }
    }

    const { found, errors } = await settle(tasks);
    if (!category) {
      category = suggestCategory({ ...recognition, snippets: found.map((c) => `${c.title} ${c.snippet || ''}`) });
    }
    const candidates = dedupe(found).map((c) => this.applyHints(c, category, platform, barcode));
    return {
      candidates,
      errors,
      suggestedCategory: category,
      suggestedPlatform: platform,
      titleGuess: title || null,
      barcode,
      googleConfigured: this.google.isConfigured,
    };
  }

  /** Manual search typed in the form. */
  async searchByTitle(title, categoryId, platform = null) {
    const isBook = categoryId === 'BOOK' || categoryId === 'COMIC';
    const tasks = [];
    if (isBook) tasks.push({ name: 'Google Books', run: () => this.books.byTitle(title, 6) });
    if (this.google.isConfigured) {
      tasks.push({ name: 'Google', run: () => this.google.candidates(this.buildQuery(title, categoryId, platform), 8) });
    } else if (!isBook) {
      tasks.push({ name: 'Wikipédia', run: () => this.wikipedia.candidates(title, 6) });
    }
    const { found, errors } = await settle(tasks);
    return {
      candidates: dedupe(found).map((c) => this.applyHints(c, categoryId, platform, null)),
      errors,
      suggestedCategory: categoryId,
      suggestedPlatform: platform,
      titleGuess: title,
      barcode: null,
      googleConfigured: this.google.isConfigured,
    };
  }

  async searchByBarcode(barcode, categoryId = null) {
    const v = normalizeBarcode(barcode);
    const kind = classifyBarcode(v);
    const tasks = [];
    if (kind === 'ISBN') tasks.push({ name: 'Google Books', run: () => this.books.byIsbn(v) });
    else if (kind === 'EAN_UPC' && this.google.isConfigured) tasks.push({ name: 'Google', run: () => this.google.candidates(`"${v}"`, 6) });
    const { found, errors } = await settle(tasks);
    const category = categoryId || (kind === 'ISBN' ? 'BOOK' : suggestCategory({ snippets: found.map((c) => `${c.title} ${c.snippet || ''}`) }));
    return {
      candidates: dedupe(found).map((c) => this.applyHints(c, category, null, v)),
      errors,
      suggestedCategory: category,
      suggestedPlatform: null,
      titleGuess: null,
      barcode: kind === 'ISBN' ? toIsbn13(v) : v,
      googleConfigured: this.google.isConfigured,
    };
  }

  /** Cover images for a title (Google Images through the API). */
  async coverImages(title, categoryId, platform) {
    if (!this.google.isConfigured) return [];
    const cat = categoryOf(categoryId);
    const query = [title, platform && cat.platform ? platform : '', cat.searchHint, 'capa'].filter(Boolean).join(' ');
    return this.google.images(query, 8);
  }

  applyHints(candidate, category, platform, barcode) {
    const cat = categoryOf(candidate.category || category);
    const detected = detectPlatform([candidate.title, candidate.snippet || '']);
    return {
      ...candidate,
      category: candidate.category || category || null,
      platform: cat.platform ? (detected || platform || null) : null,
      barcode: candidate.barcode || barcode || null,
    };
  }
}
