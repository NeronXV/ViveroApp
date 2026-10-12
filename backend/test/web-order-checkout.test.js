import test from 'node:test';
import assert from 'node:assert/strict';
import { checkoutScope, sendOrderToCashier } from '../src/web-order-checkout.js';

test('sending orders to cashier requires operational capability and own active branch', () => {
  const context = { access_state: 'ACTIVE', capabilities: ['OPERATE_CASHIER'], branch: { id: 2, is_active: true } };
  assert.equal(checkoutScope(context), 2);
  assert.equal(checkoutScope({ ...context, capabilities: ['VIEW_ALL_SALES'] }), 2);
  assert.throws(() => checkoutScope({ ...context, capabilities: ['CREATE_SALES'] }));
  assert.throws(() => checkoutScope({ ...context, branch: null }));
  assert.throws(() => checkoutScope({ ...context, branch: { id: 2, is_active: false } }));
});

test('Web checkout preserves list subtotal, header promotion discount and accepted item prices on replay', async () => {
  const context = { access_state: 'ACTIVE', user: { id: 2 }, capabilities: ['OPERATE_CASHIER'], branch: { id: 1, is_active: true } };
  const order = { id: 1, status: 'CONFIRMED', subtotal_cents: 1000, discount_cents: 200, total_cents: 800 };
  const item = { product_id: 1, product_name: 'Planta sintética', internal_code: 'TEST', quantity: 1,
    list_price_cents: 1000, unit_price_cents: 800, line_total_cents: 800, promotion_id: 1, promotion_name: 'Promoción sintética',
    product_active: 1, category_active: 1, commercially_ready: 1 };
  const original = structuredClone({ order, item });
  let saved = null, inserts = 0;
  const storedItems = [];
  const db = { execute: async (sql, params) => {
    if (sql.startsWith('SELECT id, status, subtotal_cents')) return [[order]];
    if (sql.startsWith('SELECT id, folio, status, total_cents')) return [saved ? [saved] : []];
    if (sql.startsWith('SELECT i.*')) return [[item]];
    if (sql.startsWith('INSERT INTO sales ')) {
      inserts++;
      saved = { id: 1, folio: params[0], status: 'SENT_TO_CASHIER', subtotal_cents: params[3], discount_cents: params[4], total_cents: params[5] };
      return [{ insertId: 1 }];
    }
    if (sql.startsWith('INSERT INTO sale_items ')) { storedItems.push(params); return [{ insertId: 1 }]; }
    if (sql.startsWith('INSERT INTO sale_status_history ')) return [{ affectedRows: 2 }];
    assert.fail('Unexpected checkout SQL');
  } };
  const result = await sendOrderToCashier(db, context, 1);
  assert.equal(result.total_cents, 800);
  assert.equal(result.idempotent_replay, false);
  assert.deepEqual([saved.subtotal_cents, saved.discount_cents, saved.total_cents], [1000, 200, 800]);
  assert.deepEqual(storedItems[0].slice(4), [1, 1000, 800, 800, 1, 'Promoción sintética']);
  assert.equal((await sendOrderToCashier(db, context, 1)).idempotent_replay, true);
  assert.equal(inserts, 1);
  assert.equal(storedItems.length, 1);
  assert.deepEqual({ order, item }, original);
});
