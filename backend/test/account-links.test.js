import test from 'node:test';
import assert from 'node:assert/strict';
import { accountLinkInput, accountLinkPassword } from '../src/auth/account-links.js';
import { createAccountMailer } from '../src/auth/mail.js';
import { sessionDigest } from '../src/auth/service.js';

test('account links validate shape, normalize email and preserve passwords', () => {
  assert.deepEqual(accountLinkInput({ email: ' A@EXAMPLE.invalid ' }), { email: 'a@example.invalid' });
  assert.throws(() => accountLinkInput({ email: 'a@example.invalid', role_id: 1 }));
  assert.throws(() => accountLinkInput({ email: 'a@example.invalid', name: '\nAdmin' }, true));
  assert.throws(() => accountLinkPassword({ token: 'x'.repeat(43), password: 'short' }));
  const password = '  Synthetic password 2026  ';
  assert.equal(accountLinkPassword({ token: 'x'.repeat(43), password }).password, password);
  assert.throws(() => accountLinkPassword({ token: 'x'.repeat(43), password, admin: true }));
});
test('mailer sends fragment links with server credentials and hides provider errors', async () => {
  assert.equal(createAccountMailer({}), null);
  let message;
  const mailer = createAccountMailer({ apiKey: 'synthetic', from: 'Demo <demo@example.invalid>', origin: 'https://example.invalid', fetcher: async (url, options) => {
    message = { url, options, body: JSON.parse(options.body) }; return new Response(JSON.stringify({ id: 'synthetic-id' }));
  } });
  await mailer({ email: 'a@example.invalid', token: 'x'.repeat(43), purpose: 'INVITE' });
  assert.equal(message.url, 'https://api.resend.com/emails');
  assert.equal(message.options.redirect, 'error');
  assert.equal(message.options.headers['Idempotency-Key'], `account-${sessionDigest('x'.repeat(43)).toString('hex')}`);
  assert.ok(message.body.text.includes('/recuperar#token='));
  const failing = createAccountMailer({ apiKey: 'synthetic', from: 'demo@example.invalid', origin: 'https://example.invalid', fetcher: async () => { throw new Error('private provider detail'); } });
  await assert.rejects(failing({}), error => error.code === 'MAIL_UNAVAILABLE' && !error.message.includes('private'));
});
