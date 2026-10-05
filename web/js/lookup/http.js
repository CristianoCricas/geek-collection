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

export async function postJson(url, body, init = {}) {
  const isForm = typeof body === 'string';
  const res = await fetch(url, {
    method: 'POST',
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': isForm ? 'application/x-www-form-urlencoded' : 'application/json',
      ...(init.headers || {}),
    },
    body: isForm ? body : JSON.stringify(body),
  });
  if (!res.ok) {
    let detail = '';
    try {
      const data = await res.json();
      detail = data?.error?.message || (Array.isArray(data) && data[0]?.error?.message) || '';
    } catch { /* ignore */ }
    throw new HttpError(res.status, detail || `HTTP ${res.status}`, url);
  }
  return res.json();
}

export async function patchJson(url, body, init = {}) {
  return postJson(url, body, { ...init, method: 'PATCH' });
}

export const q = (params) =>
  Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
