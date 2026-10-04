import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createApp } from '../src/app.js';
import { browserOrigin, requireBrowserOrigin } from '../src/browser-origin.js';

test('browser origin is exact, configured and fails closed on cross-site metadata', () => {
  assert.equal(browserOrigin('', true), null);
  assert.equal(browserOrigin('http://localhost:5173', true), 'http://localhost:5173');
  assert.equal(browserOrigin('https://example.invalid'), 'https://example.invalid');
  for (const value of ['null', '*', 'http://example.invalid', 'https://example.invalid/', 'https://u:p@example.invalid', 'https://example.invalid?x=1']) assert.throws(() => browserOrigin(value, true));
  assert.throws(() => browserOrigin('http://localhost:5173'));
  requireBrowserOrigin({ headers: {} }, null); // Native clients remain valid.
  assert.throws(() => requireBrowserOrigin({ headers: { origin: 'null' } }, null));
  assert.throws(() => requireBrowserOrigin({ headers: { 'sec-fetch-site': 'cross-site' } }, 'https://example.invalid'));
});
test('configured browser origin reaches API without CORS and still requires login for writes', async t => {
  const server = createApp({ db: { execute: async () => [[]] }, webOrigin: 'http://localhost:5173' }).listen(0, '127.0.0.1');
  await once(server, 'listening'); t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/api/v1/categories`;
  const r = await fetch(url, { headers: { Origin: 'http://localhost:5173', 'Sec-Fetch-Site': 'same-origin' } });
  assert.equal(r.status, 200); assert.equal(r.headers.get('access-control-allow-origin'), null);
  assert.equal((await fetch(url, { headers: { Origin: 'http://127.0.0.1:5173' } })).status, 403);
  assert.equal((await fetch(url, { method: 'POST', headers: { Origin: 'http://localhost:5173' } })).status, 401);
});

test('domain transition accepts only explicit legacy origins and preserves native/auth restrictions', async t => {
  const primary = 'https://vivero.example.invalid';
  const legacy = 'https://previous.example.invalid';
  requireBrowserOrigin({ headers: { origin: legacy } }, primary, [legacy]);
  assert.throws(() => requireBrowserOrigin({ headers: { origin: 'https://other.example.invalid' } }, primary, [legacy]));
  assert.throws(() => requireBrowserOrigin({ headers: { origin: legacy, 'sec-fetch-site': 'cross-site' } }, primary, [legacy]));
  const server = createApp({ db: { execute: async () => [[]] }, webOrigin: primary, webOriginAliases: [legacy] }).listen(0, '127.0.0.1');
  await once(server, 'listening'); t.after(() => new Promise(resolve => server.close(resolve)));
  const url = `http://127.0.0.1:${server.address().port}/api/v1/categories`;
  for (const origin of [primary, legacy]) {
    const response = await fetch(url, { headers: { Origin: origin, 'Sec-Fetch-Site': 'same-origin' } });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('access-control-allow-origin'), null);
    assert.equal((await fetch(url, { method: 'POST', headers: { Origin: origin } })).status, 401);
  }
});
