// Google Programmable Search (Custom Search JSON API). This is the official
// way to run Google searches from a web app: the user creates a free search
// engine + API key once (100 queries/day free) and pastes both in Settings.
import { fetchJson, q } from './http.js';
import { extractYear } from '../recognition/heuristics.js';

const ENDPOINT = 'https://www.googleapis.com/customsearch/v1';

/** Site-name suffixes Google appends to page titles: "Catan - Amazon.com.br". */
export function cleanTitle(title) {
  if (!title) return '';
  let t = String(title).replace(/\s+/g, ' ').trim();
  // Drop trailing " - Site", " | Site", " – Site" when the remainder is still a reasonable title.
  for (let i = 0; i < 2; i++) {
    const m = t.match(/^(.{3,}?)\s+[|\-–—:]\s+([^|\-–—]{2,40})$/);
    if (!m) break;
    const tail = m[2].toLowerCase();
    const looksLikeSite = /amazon|mercado ?livre|magazine|americanas|submarino|kabum|shopee|aliexpress|wikipedia|wikipédia|youtube|steam|nuuvem|loja|store|shop|\.com|\.br|ludopedia|boardgamegeek|goodreads|skoob|saraiva|cultura|fnac|ebay|olx|site oficial|oficial|nintendo|playstation|xbox|wiki|fandom|google|blog|review|análise|comprar|preço|promoção/.test(tail);
    if (!looksLikeSite) break;
    t = m[1].trim();
  }
  return t;
}

export function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return null;
  }
}

/** Converts one Custom Search result into a lookup candidate. */
export function candidateFromResult(item) {
  const meta = item.pagemap?.metatags?.[0] || {};
  const title = cleanTitle(meta['og:title'] || item.title);
  if (!title) return null;
  const description = meta['og:description'] || meta.description || item.snippet || null;
  const image = item.pagemap?.cse_image?.[0]?.src || meta['og:image'] || item.pagemap?.cse_thumbnail?.[0]?.src || null;
  const product = item.pagemap?.product?.[0] || {};
  const book = item.pagemap?.book?.[0] || {};
  return {
    title,
    category: null,
    description: description ? String(description).replace(/\s+/g, ' ').trim() : null,
    creator: book.author || product.brand || meta['book:author'] || null,
    publisher: null,
    releaseYear: extractYear(meta['book:release_date'] || product.releasedate || '') || extractYear(`${item.title} ${item.snippet || ''}`),
    coverUrl: image && /^https?:\/\//.test(image) ? image : null,
    sourceName: hostOf(item.link) || 'google',
    sourceUrl: item.link || null,
    snippet: item.snippet || '',
    source: 'google',
  };
}

export class GoogleSearch {
  /**
   * @param {() => {googleApiKey?: string, googleCx?: string}} settings
   * @param {typeof fetchJson} http
   */
  constructor(settings, http = fetchJson) {
    this.settings = settings;
    this.http = http;
  }

  get isConfigured() {
    const s = this.settings() || {};
    return Boolean(s.googleApiKey && s.googleCx);
  }

  async search(query, { num = 6, imageSearch = false } = {}) {
    const s = this.settings() || {};
    if (!s.googleApiKey || !s.googleCx) throw new Error('Pesquisa Google não configurada');
    const url = `${ENDPOINT}?${q({
      key: s.googleApiKey,
      cx: s.googleCx,
      q: query,
      num: Math.min(10, num),
      hl: 'pt-BR',
      gl: 'br',
      safe: 'active',
      searchType: imageSearch ? 'image' : undefined,
      imgSize: imageSearch ? 'large' : undefined,
    })}`;
    const json = await this.http(url);
    return json.items || [];
  }

  /** Web results converted into candidates. */
  async candidates(query, num = 6) {
    const items = await this.search(query, { num });
    return items.map(candidateFromResult).filter(Boolean);
  }

  /** Image results: [{url, thumbnail, pageUrl, title}] */
  async images(query, num = 8) {
    const items = await this.search(query, { num, imageSearch: true });
    return items
      .filter((i) => i.link && /^https?:\/\//.test(i.link))
      .map((i) => ({
        url: i.link,
        thumbnail: i.image?.thumbnailLink || i.link,
        pageUrl: i.image?.contextLink || null,
        title: cleanTitle(i.title),
        width: i.image?.width,
        height: i.image?.height,
      }));
  }
}

/** URLs for opening Google manually when the API is not configured. */
export const googleLinks = {
  web: (query) => `https://www.google.com/search?${q({ q: query, hl: 'pt-BR' })}`,
  images: (query) => `https://www.google.com/search?${q({ q: query, tbm: 'isch', hl: 'pt-BR' })}`,
  lens: 'https://lens.google.com/',
};
