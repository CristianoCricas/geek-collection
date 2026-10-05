import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FirebaseClient, documentToItem, itemToDocumentFields, fromFields, toFields, authErrorMessage } from '../js/sync/firebase.js';
import { runSync } from '../js/sync/engine.js';
import { normalizeItem } from '../js/model.js';

test('firestore value codec round-trips', () => {
  const src = { s: 'x', n: 3, f: 1.5, b: true, nil: null, arr: [1, 'a'], obj: { k: 'v' } };
  const back = fromFields(toFields(src));
  assert.deepEqual(back, src);
  assert.deepEqual(toFields({ n: 3 }), { n: { integerValue: '3' } });
});

test('item <-> document mapping', () => {
  const item = normalizeItem({ title: 'Zelda', category: 'VIDEO_GAME', platform: 'Nintendo Switch', completionPercent: 40, syncId: 'abc', createdAt: 1000, updatedAt: 2000, keepUpdatedAt: true });
  const fields = itemToDocumentFields(item, 'data:image/jpeg;base64,xx');
  assert.equal(fields.title.stringValue, 'Zelda');
  assert.equal(fields.completionPercent.integerValue, '40');
  assert.equal(fields.deleted.booleanValue, false);
  assert.equal(fields.thumb.stringValue, 'data:image/jpeg;base64,xx');
  assert.equal(fields.updatedAt.integerValue, '2000');
  const { item: back, thumb, serverUpdatedAt } = documentToItem({ fields: { ...fields, serverUpdatedAt: { timestampValue: '2026-01-01T00:00:00Z' } }, updateTime: 'ignored' });
  assert.equal(back.title, 'Zelda');
  assert.equal(back.platform, 'Nintendo Switch');
  assert.equal(back.completionPercent, 40);
  assert.equal(back.deletedAt, null);
  assert.equal(back.updatedAt, 2000);
  assert.equal(thumb, 'data:image/jpeg;base64,xx');
  assert.equal(serverUpdatedAt, '2026-01-01T00:00:00Z');
  // tombstone
  const dead = documentToItem({ fields: toFields({ syncId: 'd', title: 'X', deleted: true, updatedAt: 5 }) });
  assert.equal(dead.item.deletedAt, 5);
});

test('auth error messages are translated', () => {
  assert.equal(authErrorMessage('EMAIL_EXISTS'), 'Este e-mail já está cadastrado. Faça login.');
  assert.equal(authErrorMessage('WEAK_PASSWORD : Password should be at least 6 characters'), 'A senha precisa ter pelo menos 6 caracteres.');
  assert.equal(authErrorMessage('Weird'), 'Weird');
});

function fakeFirebase() {
  const calls = [];
  const stored = {};
  let auth = null;
  const http = {
    post: async (url, body, init) => {
      calls.push({ url, body, headers: init?.headers });
      if (url.includes('accounts:signInWithPassword')) return { localId: 'uid1', email: body.email, idToken: 'tok', refreshToken: 'ref', expiresIn: '3600' };
      if (url.includes('securetoken')) return { id_token: 'tok2', refresh_token: 'ref', user_id: 'uid1', expires_in: '3600' };
      if (url.endsWith(':commit')) {
        body.writes.forEach((w) => { stored[w.update.name] = { name: w.update.name, fields: { ...w.update.fields, serverUpdatedAt: { timestampValue: '2026-02-02T00:00:00Z' } } }; });
        return { writeResults: body.writes.map(() => ({ updateTime: '2026-02-02T00:00:00Z' })) };
      }
      if (url.endsWith(':runQuery')) return Object.values(stored).map((document) => ({ document }));
      throw new Error('unexpected ' + url);
    },
  };
  const client = new FirebaseClient(() => ({ firebaseProjectId: 'proj', firebaseApiKey: 'key' }), { load: async () => auth, save: async (a) => { auth = a; } }, http);
  return { client, calls, stored };
}

test('firebase client signs in, refreshes and commits with transforms', async () => {
  const { client, calls } = fakeFirebase();
  assert.equal(client.isConfigured, true);
  assert.equal(await client.isSignedIn(), false);
  const a = await client.signIn('a@b.c', 'secret');
  assert.equal(a.uid, 'uid1');
  assert.equal(await client.isSignedIn(), true);
  client.auth.expiresAt = 0; // force refresh
  const token = await client.ensureToken();
  assert.equal(token, 'tok2');
  assert.match(calls[1].body, /grant_type=refresh_token&refresh_token=ref/);
  const item = normalizeItem({ title: 'A', category: 'BOOK', syncId: 's1' });
  await client.commitItems([{ item, thumb: null }]);
  const commit = calls.at(-1);
  assert.equal(commit.headers.Authorization, 'Bearer tok2');
  assert.equal(commit.body.writes[0].update.name, 'projects/proj/databases/(default)/documents/users/uid1/items/s1');
  assert.deepEqual(commit.body.writes[0].updateTransforms, [{ fieldPath: 'serverUpdatedAt', setToServerValue: 'REQUEST_TIME' }]);
  const changed = await client.changedSince(null);
  assert.equal(changed.length, 1);
  assert.equal(changed[0].item.syncId, 's1');
  const query = calls.at(-1).body.structuredQuery;
  assert.equal(query.where, undefined);
  await client.changedSince('2026-01-01T00:00:00Z');
  assert.equal(calls.at(-1).body.structuredQuery.where.fieldFilter.value.timestampValue, '2026-01-01T00:00:00Z');
});

function memoryStore(initial = []) {
  const items = new Map(initial.map((i) => [i.syncId, { ...i }]));
  let cursor = null;
  const log = [];
  return {
    items, log,
    dirtyItems: async () => [...items.values()].filter((i) => i.dirty !== false),
    bySyncId: async (id) => items.get(id) || null,
    applyRemote: async (remote, thumb) => { items.set(remote.syncId, { ...remote, dirty: false, thumb }); log.push(['apply', remote.syncId]); },
    removeLocal: async (id) => { items.delete(id); log.push(['remove', id]); },
    markClean: async (ids) => { ids.forEach((id) => { const it = items.get(id); if (it) it.dirty = false; }); log.push(['clean', ...ids]); },
    thumbFor: async (it) => (it.hasImage ? 'thumb-' + it.syncId : null),
    loadCursor: async () => cursor,
    saveCursor: async (c) => { cursor = c; log.push(['cursor', c]); },
    get cursor() { return cursor; },
  };
}

test('engine pushes dirty items only, pulls remote changes, honours last-write-wins', async () => {
  const { client, calls, stored } = fakeFirebase();
  await client.signIn('a@b.c', 'secret');
  // Pre-existing remote doc from another device, newer than the local copy.
  stored['projects/proj/databases/(default)/documents/users/uid1/items/r1'] = {
    name: 'r1', fields: { ...itemToDocumentFields(normalizeItem({ title: 'Remote newer', category: 'BOOK', syncId: 'r1', updatedAt: 5000, keepUpdatedAt: true })), serverUpdatedAt: { timestampValue: '2026-01-05T00:00:00Z' } },
  };
  stored['.../r2'] = {
    name: 'r2', fields: { ...itemToDocumentFields(normalizeItem({ title: 'Remote older', category: 'BOOK', syncId: 'r2', updatedAt: 10, keepUpdatedAt: true })), serverUpdatedAt: { timestampValue: '2026-01-03T00:00:00Z' } },
  };
  stored['.../r3'] = {
    name: 'r3', fields: { ...itemToDocumentFields({ ...normalizeItem({ title: 'Gone', category: 'BOOK', syncId: 'r3', updatedAt: 9000, keepUpdatedAt: true }), deletedAt: 9000 }), serverUpdatedAt: { timestampValue: '2026-01-04T00:00:00Z' } },
  };
  const store = memoryStore([
    { syncId: 'l1', title: 'Local dirty', dirty: true, updatedAt: 100, hasImage: true },
    { syncId: 'l2', title: 'Local clean', dirty: false, updatedAt: 100 },
    { syncId: 'r1', title: 'Local stale', dirty: true, updatedAt: 1000 },
    { syncId: 'r2', title: 'Local newer dirty', dirty: true, updatedAt: 20 },
    { syncId: 'r3', title: 'To be removed', dirty: false, updatedAt: 1 },
  ]);
  const statuses = [];
  const summary = await runSync({ firebase: client, store, onStatus: (s) => statuses.push(s) });

  // pull runs first; r1 was overwritten by the newer remote copy, so only l1 and r2 are pushed
  const commit = calls.find((c) => c.url.endsWith(':commit'));
  assert.deepEqual(commit.body.writes.map((w) => w.update.name.split('/').pop()).sort(), ['l1', 'r2']);
  assert.equal(commit.body.writes.find((w) => w.update.name.endsWith('/l1')).update.fields.thumb.stringValue, 'thumb-l1');
  assert.equal(summary.pushed, 2);
  assert.equal(summary.pulled, 1);
  // pull: remote r1 is newer than local (even though local was dirty) → applied; r2 local wins; r3 tombstone removes local
  assert.equal(store.items.get('r1').title, 'Remote newer');
  assert.equal(store.items.get('r2').title, 'Local newer dirty');
  assert.equal(store.items.has('r3'), false);
  assert.equal(summary.removed, 1);
  assert.equal(store.items.get('l1').dirty, false);
  // cursor advanced to the newest serverUpdatedAt seen
  // pull happens before push, so the cursor is the newest server stamp seen while pulling
  assert.equal(store.cursor, '2026-01-05T00:00:00Z');
  assert.ok(statuses.some((s) => /Enviando/.test(s)));
});

test('engine requires a session', async () => {
  const { client } = fakeFirebase();
  await assert.rejects(() => runSync({ firebase: client, store: memoryStore() }), /Faça login/);
});
