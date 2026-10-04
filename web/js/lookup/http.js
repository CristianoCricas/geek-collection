// Tiny fetch wrapper shared by the lookup providers. Injected into providers
// so unit tests can replace it with canned responses.

export class HttpError extends Error {
  constructor(status, message, url) {
    super(message || `HTTP ${status}`);
    this.name = 'HttpError';
    this.status = status;
    this.url = url;
  }
}

export async function fetchJson(url, init) {
  const res = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init && init.headers) } });
  if (!res.ok) {
    let detail = '';
    try {
      const body = await res.json();
      detail = body?.error?.message || body?.error_description || '';
    } catch { /* ignore */ }
    throw new HttpError(res.status, detail || `HTTP ${res.status}`, url);
  }
  return res.json();
}

export const q = (params) =>
  Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
