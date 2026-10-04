// Google Books API: works without an API key (an optional key raises the quota).
import { fetchJson, q } from './http.js';
import { extractYear } from '../recognition/heuristics.js';
import { toIsbn13 } from '../recognition/heuristics.js';

const ENDPOINT = 'https://www.googleapis.com/books/v1/volumes';

export function candidateFromVolume(volume) {
  const info = volume.volumeInfo || {};
  if (!info.title) return null;
  const isbn13 = (info.industryIdentifiers || []).find((i) => i.type === 'ISBN_13')?.identifier
    || (info.industryIdentifiers || []).find((i) => i.type === 'ISBN_10')?.identifier;
  const thumb = info.imageLinks?.thumbnail || info.imageLinks?.smallThumbnail || null;
  const isComic = (info.categories || []).some((c) => /comic|manga|graphic/i.test(c));
  return {
    title: info.subtitle ? `${info.title}: ${info.subtitle}` : info.title,
    category: isComic ? 'COMIC' : 'BOOK',
    description: info.description ? String(info.description).replace(/<[^>]+>/g, '').trim() : null,
    creator: (info.authors || []).join(', ') || null,
    publisher: info.publisher || null,
    releaseYear: extractYear(info.publishedDate),
    coverUrl: thumb ? thumb.replace(/^http:/, 'https:').replace(/&edge=curl/, '') : null,
    barcode: isbn13 ? toIsbn13(isbn13) || isbn13 : null,
    sourceName: 'Google Books',
    sourceUrl: info.infoLink || info.canonicalVolumeLink || null,
    snippet: info.description ? info.description.slice(0, 200) : '',
    source: 'google-books',
  };
}

export class GoogleBooks {
  constructor(settings = () => ({}), http = fetchJson) {
    this.settings = settings;
    this.http = http;
  }

  async query(params) {
    const s = this.settings() || {};
    const url = `${ENDPOINT}?${q({ ...params, key: s.googleApiKey || undefined, printType: 'books' })}`;
    const json = await this.http(url);
    return (json.items || []).map(candidateFromVolume).filter(Boolean);
  }

  byIsbn(isbn) {
    const v = toIsbn13(isbn) || String(isbn).replace(/[^0-9X]/gi, '');
    return this.query({ q: `isbn:${v}`, maxResults: 5 });
  }

  byTitle(title, max = 6) {
    return this.query({ q: `intitle:${title}`, maxResults: max, orderBy: 'relevance' });
  }
}
