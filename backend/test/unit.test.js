import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createApp } from '../src/app.js';
import { validateProduct, validateCategory, positiveId, productFilters, scanInput } from '../src/catalog.js';
import { bearerToken } from '../src/auth/service.js';

const token = 'u'.repeat(43);
const valid = { internal_code: 'UNIT-1', common_name: 'Planta', category_id: 1, price_cents: 12500 };

test('scan codes preserve case and leading zeroes but reject malformed/ambiguous request shapes', () => {
  assert.equal(scanInput({ code: '  001-Abc  ' }), '001-Abc');
  for (const input of [{ code: 123 }, { code: 'a' }, { code: 'x'.repeat(129) }, { code: 'ab\n' }, { code: 'ab', branch_id: 1 }, { code: '\ud800a' }]) assert.throws(() => scanInput(input));
});

test('product filters bound search and require canonical integer category IDs', () => {
  assert.deepEqual(productFilters(new URLSearchParams()), { search: '', categoryId: null });
  assert.deepEqual(productFilters(new URLSearchParams({ search: '  Rosa  ', category_id: '12' })), { search: 'Rosa', categoryId: 12 });
  for (const category_id of ['', 'uuid', '0', '01', '1e2', '4294967296']) {
    assert.throws(() => productFilters(new URLSearchParams({ category_id })));
  }
  for (const search of ['x'.repeat(81), 'a\u0000b', 'a\u0085b']) {
    assert.throws(() => productFilters(new URLSearchParams({ search })));
  }
});

test('rejects invalid money, IDs, unknown keys, and empty patches', () => {
  for (const price_cents of [-1, 0.5, '100', Number.MAX_SAFE_INTEGER + 1, null]) {
    assert.throws(() => validateProduct({ ...valid, price_cents }));
  }
  for (const id of ['uuid', '1 OR 1=1', '01', '1e2', 0, 4294967296]) assert.throws(() => positiveId(id));
  assert.throws(() => validateProduct({ ...valid, role: 'OWNER' }));
  assert.throws(() => validateProduct({}, true));
  assert.throws(() => validateProduct({ ...valid, category_id: true }));
  assert.throws(() => validateCategory({ name: 'A' }));
  assert.deepEqual(validateProduct(valid), valid);
});

test('catalog details distinguish missing, nullable money and invalid category patches', () => {
  assert.deepEqual(validateProduct({ wholesale_price_cents: null, watering_advice: ' Regar ' }, true), { wholesale_price_cents: null, watering_advice: 'Regar' });
  for (const wholesale_price_cents of [-1, 0.1, '10', Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => validateProduct({ wholesale_price_cents }, true));
  }
  for (const input of [{ watering_advice: null }, { light_type: 'x'.repeat(161) }, { recommended_climate: 'x'.repeat(161) }]) assert.throws(() => validateProduct(input, true));
  for (const input of [{}, { is_active: 1 }, { name: null }, { id: 1 }]) assert.throws(() => validateCategory(input, true));
  assert.deepEqual(validateCategory({ is_active: false }, true), { is_active: false });
});

test('HTTP authorization precedes SQL and error responses do not leak details', async t => {
  let calls = 0;
  const db = { execute: async () => { calls++; throw new Error('sensitive database diagnostic'); } };
  const auth = { withAccess: async (request, _required, action) => {
    bearerToken(request);
    return action(db, {});
  } };
  const server = createApp({ db, auth }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}`;
  for (const path of ['/api/v1/products', '/api/v1/categories']) {
    const response = await fetch(url + path, { method: 'POST' });
    assert.equal(response.status, 401);
  }
  assert.equal(calls, 0);
  const all = await fetch(url + '/api/v1/products?status=all');
  assert.equal(all.status, 401);
  const browser = await fetch(url + '/api/v1/products', { headers: { Origin: 'https://example.invalid' } });
  assert.equal(browser.status, 403);
  const invalid = await fetch(url + '/api/v1/products', {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: '{',
  });
  assert.equal(invalid.status, 400);
  const oversized = await fetch(url + '/api/v1/products', {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ description: 'x'.repeat(17000) }),
  });
  assert.equal(oversized.status, 413);
  const contentType = await fetch(url + '/api/v1/products', {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: 'text',
  });
  assert.equal(contentType.status, 415);
  assert.equal(calls, 0);
  const health = await fetch(url + '/health');
  assert.equal(health.status, 503);
  assert.deepEqual(await health.json(), { error: 'DATABASE_UNAVAILABLE' });
});

test('writes require a session and cannot fall back to the old shared token', async t => {
  const server = createApp({ db: { execute: () => assert.fail('SQL must not execute') } }).listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => server.close(resolve)));
  const response = await fetch(`http://127.0.0.1:${server.address().port}/api/v1/products/1`, { method: 'DELETE' });
  assert.equal(response.status, 401);
  const legacy = await fetch(`http://127.0.0.1:${server.address().port}/api/v1/products/1`, {
    method: 'DELETE', headers: { Authorization: 'Bearer old-local-token' },
  });
  assert.equal(legacy.status, 401);
});
