import test from 'node:test';
import assert from 'node:assert/strict';
import { customerInput, customerQuery, customerScope } from '../src/customers.js';

test('customer capabilities preserve sales creation and administrative updates', () => {
  const context = { access_state: 'ACTIVE', capabilities: ['CREATE_SALES'] };
  customerScope(context);
  assert.throws(() => customerScope(context, true));
  customerScope({ ...context, capabilities: ['MANAGE_USERS'] }, true);
  for (const value of [{ ...context, capabilities: ['OPERATE_CASHIER'] }, { ...context, access_state: 'INACTIVE' }]) assert.throws(() => customerScope(value));
});
test('customer validates contact fields, full replacement and bounded queries', () => {
  const input = { full_name: ' Demo Cliente ', email: ' DEMO@EXAMPLE.INVALID ', phone: ' 12345678 ', is_active: true };
  assert.deepEqual(customerInput(input), { full_name: 'Demo Cliente', email: 'demo@example.invalid', phone: '12345678', is_active: true });
  assert.equal(customerInput({ ...input, email: '', phone: null }).email, null);
  for (const override of [{ email: 'bad@' }, { full_name: 'a' }, { phone: '123' }, { is_active: 1 }, { branch_id: 1 }, { full_name: 'bad\nname' }]) assert.throws(() => customerInput({ ...input, ...override }));
  assert.deepEqual(customerQuery(new URLSearchParams('search=Demo&limit=50')), { search: 'demo', limit: 50 });
  for (const query of ['', 'search=a', 'search=ab&limit=51', 'search=ab&search=cd', 'search=ab&status=all']) assert.throws(() => customerQuery(new URLSearchParams(query)));
});
