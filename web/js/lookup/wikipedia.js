// Key-less fallback used when the Google search engine is not configured.
import { fetchJson, q } from './http.js';
import { extractYear } from '../recognition/heuristics.js';

export class Wikipedia {
  constructor(settings = () => ({}), http = fetchJson) {
    this.settings = settings;
    this.http = http;
  }

  async candidates(query, limit = 5) {
    const lang = (this.settings() || {}).wikiLang || 'pt';
    const url = `https://${lang}.wikipedia.org/w/api.php?${q({
      action: 'query', format: 'json', origin: '*', generator: 'search',
      gsrsearch: query, gsrlimit: limit, prop: 'pageimages|extracts|info', inprop: 'url',
      exintro: 1, explaintext: 1, exsentences: 3, exlimit: limit, piprop: 'thumbnail', pithumbsize: 600,
    })}`;
    const json = await this.http(url);
    const pages = Object.values(json.query?.pages || {});
    return pages
      .sort((a, b) => (a.index ?? 1e9) - (b.index ?? 1e9))
      .map((p) => ({
        title: p.title,
        category: null,
        description: p.extract || null,
        creator: null,
        publisher: null,
        releaseYear: extractYear(p.extract),
        coverUrl: p.thumbnail?.source || null,
        sourceName: `${lang}.wikipedia.org`,
        sourceUrl: p.fullurl || `https://${lang}.wikipedia.org/?curid=${p.pageid}`,
        snippet: (p.extract || '').slice(0, 200),
        source: 'wikipedia',
      }));
  }
}
