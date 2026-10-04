import test from 'node:test';
import assert from 'node:assert/strict';
import { bootstrapInput } from '../scripts/bootstrap-owner.js';
const env = { BOOTSTRAP_EMAIL: 'demo@example.invalid', BOOTSTRAP_PASSWORD: 'Synthetic initial password 2026', BOOTSTRAP_FULL_NAME: 'Demo owner', BOOTSTRAP_BRANCH_CODE: 'CENTRO' };
test('bootstrap supports existing branch and validates optional new branch before connecting', () => {
  assert.equal(bootstrapInput(env).branch, null);
  assert.deepEqual(bootstrapInput({ ...env, BOOTSTRAP_BRANCH_CODE: ' centro ', BOOTSTRAP_BRANCH_NAME: ' Sucursal   Centro ' }).branch, { code: 'CENTRO', name: 'Sucursal Centro' });
  for (const patch of [{ BOOTSTRAP_BRANCH_CODE: 'bad code', BOOTSTRAP_BRANCH_NAME: 'Demo' }, { BOOTSTRAP_BRANCH_NAME: 'x' }, { BOOTSTRAP_FULL_NAME: 'bad\nname' }, { BOOTSTRAP_PASSWORD: 'short' }]) assert.throws(() => bootstrapInput({ ...env, ...patch }));
});
