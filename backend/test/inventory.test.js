import test from 'node:test';
import assert from 'node:assert/strict';
import { createInventory, inventoryBody, inventoryQuery, inventoryScope, activationInput } from '../src/inventory.js';

const context = { access_state: 'ACTIVE', role: { name: 'OWNER' }, user: { id: 7 }, branch: { id: 2, is_active: true }, capabilities: ['MANAGE_INVENTORY'] };
const key = 'inventory-synthetic-key-0001';

test('inventory writes require capabilities and an active assigned branch', () => {
  assert.equal(inventoryScope(context, true), 2);
  const reader = { ...context, capabilities: ['VIEW_INVENTORY_ALERTS'] };
  assert.equal(inventoryScope(reader), 2);
  assert.throws(() => inventoryScope(reader, true));
  for (const value of [{ ...context, branch: null }, { ...context, branch: { id: 2, is_active: false } }, { ...context, capabilities: [] }]) {
    assert.throws(() => inventoryScope(value));
  }
});

test('inventory inputs preserve exact quantities and reject fractional physical counts', () => {
  const input = { product_id: 3, quantity: '5.000', notes: null };
  const reception = inventoryBody('reception', input, key);
  assert.equal(reception.quantity, '5.000');
  assert.equal(reception.quantityMilli, 5000n);
  assert.notDeepEqual(reception.idempotencyHash, inventoryBody('count', { product_id: 3, counted_quantity: '5', reason: 'Conteo demo' }, key).idempotencyHash);
  for (const quantity of ['0', '-1', '0.5', '1.001', '100000000000', 5]) {
    assert.throws(() => inventoryBody('reception', { ...input, quantity }, key));
  }
  assert.throws(() => inventoryBody('reception', input, 'short'));
  assert.throws(() => inventoryQuery(new URLSearchParams('limit=101')));
  assert.throws(() => inventoryQuery(new URLSearchParams('limit=5&limit=10')));
  assert.equal(inventoryBody('count', { product_id: 3, counted_quantity: '0', reason: 'Conteo demo' }, key).quantity, '0.000');
});

function fakeDatabase({ quantity = '10.500', count = null, movement = null } = {}) {
  const calls = [];
  return {
    calls,
    async execute(sql, params) {
      calls.push({ sql, params });
      if (sql.includes('SELECT p.id FROM products')) return [[{ id: 3 }]];
      if (sql.startsWith('SELECT quantity, minimum_stock')) return [[{ quantity, minimum_stock: '2.000' }]];
      if (sql.includes('FROM inventory_counts c')) return [[...(count ? [count] : [])]];
      if (sql.startsWith('SELECT id, branch_id')) return [[...(movement ? [movement] : [])]];
      if (sql.includes('INSERT INTO inventory_movements')) return [{ insertId: 41 }];
      if (sql.includes('INSERT INTO inventory_counts')) return [{ insertId: 42 }];
      if (sql.startsWith('INSERT IGNORE INTO inventory (')) return [{ affectedRows: 1 }];
      if (sql.startsWith('SELECT id FROM branches')) return [[{ id: 2 }]];
      throw new Error(`Unexpected query: ${sql}`);
    },
  };
}

test('legacy direct count cannot create an adjustment without an observation', async () => {
  const db = fakeDatabase();
  await assert.rejects(createInventory(db, context).reconcile(inventoryBody('count', { product_id: 3, counted_quantity: '8', reason: 'Conteo demo' }, key)), { code: 'INVENTORY_OBSERVATION_REQUIRED' });
  assert.equal(db.calls.some(call => call.sql.includes('INSERT INTO inventory_movements')), false);
  assert.equal(db.calls.filter(call => call.sql.startsWith('SELECT quantity, minimum_stock')).length, 1);
});

test('inventory activation requires an explicit initial physical count acknowledgement', () => {
  assert.equal(activationInput({ initial_count_confirmed: true }).initial_count_confirmed, true);
  for (const input of [{}, { initial_count_confirmed: false }, { initial_count_confirmed: 1 }, { initial_count_confirmed: true, branch_id: 1 }]) assert.throws(() => activationInput(input));
});

test('legacy zero difference also requires the observation contract', async () => {
  const db = fakeDatabase({ quantity: '8.000' });
  await assert.rejects(createInventory(db, context).reconcile(inventoryBody('count', { product_id: 3, counted_quantity: '8', reason: 'Conteo demo' }, key)), { code: 'INVENTORY_OBSERVATION_REQUIRED' });
  assert.equal(db.calls.some(call => call.sql.includes('INSERT INTO inventory_movements')), false);
  assert.equal(db.calls.some(call => call.sql.includes('INSERT INTO inventory_counts')), false);
});

test('count replay uses the saved adjustment and a current locking read', async () => {
  const db = fakeDatabase({ count: { id: 42, branch_id: 2, product_id: 3, previous_quantity: '0.500', counted_quantity: '0.000', adjustment_quantity: '-0.500', reason: 'Conteo demo', counted_by: 7 } });
  const result = await createInventory(db, context).reconcile(inventoryBody('count', { product_id: 3, counted_quantity: '0', reason: 'Conteo demo' }, key));
  assert.equal(result.idempotent_replay, true);
  assert.equal(result.adjustment_quantity, '-0.500');
  assert.ok(db.calls.find(call => call.sql.includes('FROM inventory_counts c')).sql.endsWith('FOR UPDATE'));
  assert.equal(db.calls.some(call => call.sql.includes('INSERT INTO inventory_counts')), false);
});

test('history formats negative fractions without losing their sign', async () => {
  const db = { async execute() { return [[{ id: 41, quantity: '-0.500', movement_type: 'ADJUSTMENT_SUB', created_by_label: 'Demo' }]]; } };
  const result = await createInventory(db, context).history({ limit: 50, beforeId: null, productId: null });
  assert.equal(result.items[0].quantity, '-0.500');
});

test('dashboard reads branch minimums from inventory, including missing balances', async () => {
  const db = { async execute(sql) {
    assert.ok(sql.includes('COALESCE(i.minimum_stock, 0) AS minimum_stock'));
    assert.equal(sql.includes('p.minimum_stock'), false);
    return [[{ product_id: 3, total_quantity: '0.000', minimum_stock: '2.000' }]];
  } };
  const result = await createInventory(db, context).dashboard({ limit: 100, afterProductId: 0 });
  assert.equal(result.items[0].is_low_stock, true);
});

test('reusing a reception key with a different quantity fails before another movement', async () => {
  const db = fakeDatabase({ movement: { id: 41, branch_id: 2, product_id: 3, movement_type: 'RECEPTION', quantity: '5.000', notes: null, created_by: 7 } });
  await assert.rejects(createInventory(db, context).reception(inventoryBody('reception', { product_id: 3, quantity: '6', notes: null }, key)), { code: 'INVENTORY_IDEMPOTENCY_CONFLICT' });
  assert.equal(db.calls.some(call => call.sql.includes('INSERT INTO inventory_movements')), false);
});
