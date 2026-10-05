// Firebase Authentication + Cloud Firestore through their REST APIs.
// No SDK: the user only pastes the project id and the Web API key.
import { fetchJson, postJson, q } from '../lookup/http.js';

const AUTH = 'https://identitytoolkit.googleapis.com/v1/accounts';
const TOKEN = 'https://securetoken.googleapis.com/v1/token';

/** Friendly messages for the most common Firebase Auth error codes. */
export function authErrorMessage(message) {
  const code = String(message || '').split(' ')[0];
  const map = {
    EMAIL_EXISTS: 'Este e-mail já está cadastrado. Faça login.',
    EMAIL_NOT_FOUND: 'E-mail não encontrado. Crie uma conta.',
    INVALID_PASSWORD: 'Senha incorreta.',
    INVALID_LOGIN_CREDENTIALS: 'E-mail ou senha incorretos.',
    WEAK_PASSWORD: 'A senha precisa ter pelo menos 6 caracteres.',
    INVALID_EMAIL: 'E-mail inválido.',
    TOO_MANY_ATTEMPTS_TRY_LATER: 'Muitas tentativas. Tente mais tarde.',
    CONFIGURATION_NOT_FOUND: 'Ative o login por e-mail/senha no Firebase Authentication.',
    OPERATION_NOT_ALLOWED: 'Ative o login por e-mail/senha no Firebase Authentication.',
    API_KEY_INVALID: 'Chave da API do Firebase inválida.',
  };
  return map[code] || message || 'Falha na autenticação';
}

// ---------------------------------------------------------------- value codec

export function toValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toValue) } };
  if (typeof v === 'object') return { mapValue: { fields: toFields(v) } };
  return { stringValue: String(v) };
}

export function fromValue(v) {
  if (!v || 'nullValue' in v) return null;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('stringValue' in v) return v.stringValue;
  if ('timestampValue' in v) return v.timestampValue;
  if ('arrayValue' in v) return (v.arrayValue.values || []).map(fromValue);
  if ('mapValue' in v) return fromFields(v.mapValue.fields || {});
  return null;
}

export const toFields = (obj) => Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, toValue(v)]));
export const fromFields = (fields) => Object.fromEntries(Object.entries(fields || {}).map(([k, v]) => [k, fromValue(v)]));

/** Fields of a collection item that travel to the cloud. */
export const SYNC_FIELDS = [
  'syncId', 'title', 'category', 'platform', 'completionPercent', 'progressStatus', 'platinum', 'backlog',
  'description', 'creator', 'publisher', 'releaseYear', 'barcode', 'coverUrl', 'notes', 'favorite',
  'sourceUrl', 'sourceName', 'externalId', 'createdAt', 'updatedAt', 'deletedAt',
];

/** Builds the Firestore document body for an item (plus optional thumbnail). */
export function itemToDocumentFields(item, thumb = null) {
  const data = {};
  for (const f of SYNC_FIELDS) data[f] = item[f] === undefined ? null : item[f];
  data.deleted = Boolean(item.deletedAt);
  data.thumb = thumb || null;
  data.client = 'pwa';
  return toFields(data);
}

/** Converts a Firestore document into a plain item + metadata. */
export function documentToItem(doc) {
  const data = fromFields(doc.fields);
  const item = {};
  for (const f of SYNC_FIELDS) item[f] = data[f] ?? null;
  item.favorite = Boolean(item.favorite);
  item.platinum = Boolean(item.platinum);
  item.backlog = Boolean(item.backlog);
  item.deletedAt = data.deleted ? (item.deletedAt || item.updatedAt || Date.now()) : null;
  return { item, thumb: data.thumb || null, serverUpdatedAt: data.serverUpdatedAt || doc.updateTime || null };
}

// ---------------------------------------------------------------- client

export class FirebaseClient {
  /**
   * @param {() => {firebaseProjectId?: string, firebaseApiKey?: string}} settings
   * @param {{load: () => Promise<object|null>, save: (auth: object|null) => Promise<void>}} authStore
   * @param {{get?: typeof fetchJson, post?: typeof postJson}} http
   */
  constructor(settings, authStore, http = {}) {
    this.settings = settings;
    this.authStore = authStore;
    this.get = http.get || fetchJson;
    this.post = http.post || postJson;
    this.auth = null;
  }

  get config() {
    const s = this.settings() || {};
    return { projectId: (s.firebaseProjectId || '').trim(), apiKey: (s.firebaseApiKey || '').trim() };
  }

  get isConfigured() {
    const c = this.config;
    return Boolean(c.projectId && c.apiKey);
  }

  async loadAuth() {
    if (!this.auth) this.auth = await this.authStore.load();
    return this.auth;
  }

  async isSignedIn() {
    const a = await this.loadAuth();
    return Boolean(a && a.refreshToken);
  }

  async signOut() {
    this.auth = null;
    await this.authStore.save(null);
  }

  async signUp(email, password) {
    return this.credentialRequest('signUp', email, password);
  }

  async signIn(email, password) {
    return this.credentialRequest('signInWithPassword', email, password);
  }

  async credentialRequest(method, email, password) {
    const { apiKey } = this.config;
    if (!apiKey) throw new Error('Configure o projeto Firebase primeiro');
    let data;
    try {
      data = await this.post(`${AUTH}:${method}?key=${encodeURIComponent(apiKey)}`, { email, password, returnSecureToken: true });
    } catch (e) {
      throw new Error(authErrorMessage(e.message));
    }
    this.auth = {
      uid: data.localId,
      email: data.email || email,
      idToken: data.idToken,
      refreshToken: data.refreshToken,
      expiresAt: Date.now() + (Number(data.expiresIn || 3600) - 60) * 1000,
    };
    await this.authStore.save(this.auth);
    return this.auth;
  }

  /** Returns a valid ID token, refreshing it when needed. */
  async ensureToken() {
    const a = await this.loadAuth();
    if (!a || !a.refreshToken) throw new Error('Faça login para sincronizar');
    if (a.idToken && a.expiresAt && Date.now() < a.expiresAt) return a.idToken;
    const { apiKey } = this.config;
    let data;
    try {
      data = await this.post(`${TOKEN}?key=${encodeURIComponent(apiKey)}`, q({ grant_type: 'refresh_token', refresh_token: a.refreshToken }));
    } catch (e) {
      if (e.status === 400 || e.status === 401) {
        await this.signOut();
        throw new Error('Sessão expirada. Faça login novamente.');
      }
      throw e;
    }
    this.auth = {
      ...a,
      uid: data.user_id || a.uid,
      idToken: data.id_token,
      refreshToken: data.refresh_token || a.refreshToken,
      expiresAt: Date.now() + (Number(data.expires_in || 3600) - 60) * 1000,
    };
    await this.authStore.save(this.auth);
    return this.auth.idToken;
  }

  get documentsRoot() {
    return `projects/${this.config.projectId}/databases/(default)/documents`;
  }

  get baseUrl() {
    return `https://firestore.googleapis.com/v1/${this.documentsRoot}`;
  }

  docName(syncId) {
    return `${this.documentsRoot}/users/${this.auth.uid}/items/${syncId}`;
  }

  async authHeaders() {
    const token = await this.ensureToken();
    return { Authorization: `Bearer ${token}` };
  }

  /** Upserts documents in one commit; the server stamps serverUpdatedAt. */
  async commitItems(entries) {
    if (!entries.length) return [];
    const headers = await this.authHeaders();
    const writes = entries.map(({ item, thumb }) => ({
      update: { name: this.docName(item.syncId), fields: itemToDocumentFields(item, thumb) },
      updateTransforms: [{ fieldPath: 'serverUpdatedAt', setToServerValue: 'REQUEST_TIME' }],
    }));
    const res = await this.post(`${this.baseUrl}:commit`, { writes }, { headers });
    return res.writeResults || [];
  }

  /** Documents changed on the server after the cursor, oldest first. */
  async changedSince(cursor, limit = 300) {
    const headers = await this.authHeaders();
    const where = cursor
      ? { fieldFilter: { field: { fieldPath: 'serverUpdatedAt' }, op: 'GREATER_THAN', value: { timestampValue: cursor } } }
      : undefined;
    const body = {
      structuredQuery: {
        from: [{ collectionId: 'items' }],
        ...(where ? { where } : {}),
        orderBy: [{ field: { fieldPath: 'serverUpdatedAt' }, direction: 'ASCENDING' }],
        limit,
      },
    };
    const rows = await this.post(`${this.baseUrl}/users/${this.auth.uid}:runQuery`, body, { headers });
    return (Array.isArray(rows) ? rows : []).filter((r) => r.document).map((r) => documentToItem(r.document));
  }
}
