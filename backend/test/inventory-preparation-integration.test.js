// Requires the dedicated synthetic project, never the regular local/prod DB.
import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import { sessionDigest } from '../src/auth/service.js';
import { importCatalog } from '../scripts/catalog-import.js';
import { preparePlantLists } from '../scripts/plant-list-preparation.js';

if (process.env.DB_HOST !== 'db' || process.env.API_URL !== 'http://api:3001' || process.env.TEST_SCOPE !== 'admin-block1-synthetic') {
  throw new Error('Use the dedicated admin-block1 synthetic Compose project');
}
let db, branch, otherBranch, category, people;
let sequence = 0;
const suffix = randomBytes(5).toString('hex');
const key = () => randomBytes(32).toString('hex');
async function call(path, method = 'GET', body, token = people.owner.token, requestKey = key(), extra = {}) {
  const response = await fetch(process.env.API_URL + '/api/v1/' + path, { method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': requestKey, ...extra },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  return { status: response.status, data: await response.json() };
}
async function product(legacy = true, price = 100) {
  const [p] = await db.execute(`INSERT INTO products (internal_code,common_name,category_id,price_cents,preparation_mode)
    VALUES (?,?,?,?,?)`, [`B1-${suffix}-${++sequence}`, 'Planta sintetica', category, price, legacy ? 'LEGACY' : 'PENDING']);
  return p.insertId;
}
async function quantity(id) {
  const [[r]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id=? AND product_id=?', [branch, id]);
  return r?.quantity ?? '0.000';
}
const receive = (id, amount, token = people.owner.token, k = key()) => call('inventory/receptions', 'POST', { product_id: id, quantity: String(amount), notes: 'Recepcion sintetica' }, token, k);
const observationBodies = new Map();
async function observe(id, amount, token = people.manager.token, k = key()) {
  let body = observationBodies.get(k);
  if (!body) {
    const baseline = (await call(`inventory/count-baseline?product_id=${id}`, 'GET', undefined, token)).data;
    body = { product_id: id, counted_quantity: String(amount), reason: 'Conteo sintetico', baseline_quantity: baseline.quantity, baseline_movement_id: baseline.movement_id };
    observationBodies.set(k, body);
  }
  return call('inventory/count-observations', 'POST', body, token, k);
}
const decide = (id, decision = 'APPROVE', token = people.owner.token, k = key()) => call(`inventory/count-observations/${id}`, 'POST', { decision, reason: 'Revision sintetica' }, token, k);

before(async () => {
  db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  assert.equal((await fetch(process.env.API_URL + '/health')).status, 200);
  const [b] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [`B1-${suffix}`, 'Sucursal sintetica']); branch = b.insertId;
  const [o] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [`B2-${suffix}`, 'Otra sucursal sintetica']); otherBranch = o.insertId;
  const [c] = await db.execute('INSERT INTO categories(name) VALUES(?)', [`B1-${suffix}`]); category = c.insertId;
  people = {};
  for (const [label, role, location] of [['owner', 'OWNER', branch], ['owner2', 'OWNER', branch], ['manager', 'MANAGER', branch], ['inventory', 'INVENTORY', branch], ['cashier', 'CASHIER', branch], ['admin', 'ADMIN', branch], ['other', 'OWNER', otherBranch]]) {
    const [u] = await db.execute('INSERT INTO users(email,full_name,role_id,branch_id,is_active) VALUES(?,?,(SELECT id FROM roles WHERE name=?),?,1)', [`${label}-${suffix}@example.invalid`, 'Persona sintetica', role, location]);
    const token = randomBytes(32).toString('base64url');
    await db.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [u.insertId, sessionDigest(token)]);
    people[label] = { id: u.insertId, token };
  }
  assert.equal((await call('inventory/activation', 'POST', { initial_count_confirmed: true })).status, 200);
  assert.equal((await call('inventory/activation', 'POST', { initial_count_confirmed: true }, people.other.token)).status, 200);
});
after(async () => { if (db) await db.end(); }); // Keep synthetic evidence; no cleanup of shared data.

test('effective owner policy, observation without stock change, replay, scope and historic count endpoint', async () => {
  const p = await product();
  const permission = (await call('inventory/permissions', 'GET', undefined, people.manager.token)).data.permissions;
  assert.deepEqual(permission, { can_record_count: true, can_receive: false, can_approve_counts: false });
  for (const name of ['manager', 'inventory', 'cashier', 'admin']) assert.equal((await receive(p, 4, people[name].token)).status, 403);
  const k = key();
  const received = await receive(p, 4, people.owner.token, k);
  assert.equal(received.status, 201);
  assert.equal((await receive(p, 4, people.owner.token, k)).status, 200);
  const observedKey = key();
  const observed = await observe(p, 8, people.manager.token, observedKey);
  assert.equal(observed.status, 201); assert.equal(observed.data.status, 'PENDING');
  assert.equal(await quantity(p), '4.000');
  assert.equal((await observe(p, 8, people.manager.token, observedKey)).status, 200);
  assert.equal((await call('inventory/count-observations/result', 'POST', observationBodies.get(observedKey), people.manager.token, observedKey)).data.observation_id, observed.data.observation_id);
  assert.equal((await decide(observed.data.observation_id, 'APPROVE', people.manager.token)).status, 403);
  assert.equal((await decide(observed.data.observation_id, 'APPROVE', people.other.token)).status, 404);
  assert.equal((await call('inventory/counts', 'POST', { product_id: p, counted_quantity: '99', reason: 'Conteo antiguo' }, people.manager.token)).data.error, 'INVENTORY_OBSERVATION_REQUIRED');
  assert.equal(await quantity(p), '4.000');
  const denial = await call('inventory/count-observations', 'POST', observationBodies.get(observedKey), people.manager.token, key(), { 'X-Expected-Branch-Id': String(otherBranch) });
  assert.equal(denial.status, 403);
});

test('approved differences exactly once, two owners, rejection and zero initial count audit', async () => {
  const p = await product();
  const obs = (await observe(p, 5)).data;
  const k = key();
  const same = await Promise.all([decide(obs.observation_id, 'APPROVE', people.owner.token, k), decide(obs.observation_id, 'APPROVE', people.owner.token, k)]);
  assert.deepEqual(same.map(r => r.status).sort(), [200, 201]);
  assert.equal(await quantity(p), '5.000');
  assert.equal((await decide(obs.observation_id, 'APPROVE', people.owner2.token)).status, 409);
  const next = (await observe(p, 6)).data;
  const race = await Promise.all([decide(next.observation_id), decide(next.observation_id, 'APPROVE', people.owner2.token)]);
  assert.deepEqual(race.map(r => r.status).sort(), [201, 409]); assert.equal(await quantity(p), '6.000');
  const rejected = (await observe(p, 90)).data;
  assert.equal((await decide(rejected.observation_id, 'REJECT')).data.status, 'REJECTED'); assert.equal(await quantity(p), '6.000');
  const zero = await product(false);
  const zeroCount = (await observe(zero, 0)).data;
  assert.equal((await decide(zeroCount.observation_id)).data.status, 'CLOSED');
  const [[movements]] = await db.execute('SELECT COUNT(*) n FROM inventory_movements WHERE product_id=?', [zero]); assert.equal(movements.n, 0);
  const [[audit]] = await db.execute('SELECT * FROM inventory_count_observations WHERE id=?', [zeroCount.observation_id]);
  assert.equal(audit.observed_by, people.manager.id); assert.equal(audit.decided_by, people.owner.id); assert.ok(audit.decided_at); assert.ok(audit.count_id);
  assert.equal((await call(`products/${zero}/preparation`)).data.initial_count_confirmed, true);
});

test('stale observations reject stock changes, including ABA with equal final quantity', async () => {
  const p = await product(); await receive(p, 4);
  const old = (await observe(p, 7)).data;
  await receive(p, 1);
  const current = (await observe(p, 4)).data; assert.equal((await decide(current.observation_id)).status, 201);
  assert.equal(await quantity(p), '4.000');
  const result = await decide(old.observation_id);
  assert.equal(result.status, 409); assert.equal(result.data.error, 'INVENTORY_COUNT_STALE');
  assert.equal(await quantity(p), '4.000');
});

test('new preparation: price > zero, owner initial count, branch activation, catalog, scan and direct sale gates', async () => {
  const made = await call('products', 'POST', { internal_code: `NEW-${suffix}`, common_name: 'Planta pendiente sintetica', category_id: category, price_cents: 0 });
  assert.equal(made.status, 201); const p = made.data.id;
  const quote = () => call('sales/quote', 'POST', { items: [{ product_id: p, quantity: 1 }] }, people.manager.token);
  assert.equal((await quote()).status, 409);
  assert.equal((await call(`products/${p}/preparation/activate`, 'POST', {})).status, 409);
  const scan = await call('products/scan', 'POST', { code: `NEW-${suffix}` }); assert.equal(scan.data.item, null);
  const all = await call('products?status=all&limit=100'); assert.ok(all.data.items.some(i => i.id === p));
  const patch = await call(`products/${p}`, 'PATCH', { price_cents: 100 }, people.manager.token); assert.equal(patch.status, 200);
  assert.equal((await quote()).status, 409);
  const obs = (await observe(p, 3)).data; assert.equal((await decide(obs.observation_id)).status, 201);
  assert.equal((await quote()).status, 409);
  const activated = await call(`products/${p}/preparation/activate`, 'POST', {}, people.manager.token); assert.equal(activated.data.ready, true);
  assert.equal((await quote()).status, 200);
  assert.equal((await call('sales/quote', 'POST', { items: [{ product_id: p, quantity: 1 }] }, people.other.token)).status, 409);
  assert.equal((await call('products/scan', 'POST', { code: `NEW-${suffix}` })).data.item.id, p);
  const publicQuote = await fetch(process.env.API_URL + '/api/v1/web-orders/quote', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ branch_id: otherBranch, items: [{ product_id: p, quantity: 1 }] }) });
  assert.equal(publicQuote.status, 409);
  assert.equal((await call(`products/${p}`, 'PATCH', { is_active: false })).status, 200); assert.equal((await quote()).status, 409);
  assert.equal((await call(`products/${p}`, 'PATCH', { is_active: true })).status, 200); assert.equal((await quote()).status, 409); // Explicit reactivation required.
});

test('historical valid products, pay/refund recovery, and incomplete product cannot be paid via direct API', async () => {
  const p = await product(); await receive(p, 5);
  const saleKey = key();
  const submit = { items: [{ product_id: p, quantity: 1 }], expected_total_cents: 100 };
  const sold = await call('sales', 'POST', submit, people.manager.token, saleKey); assert.equal(sold.status, 201);
  assert.equal((await call('sales/recover', 'POST', {}, people.manager.token, saleKey)).data.id, sold.data.id);
  const sale = sold.data.id;
  const claim = (await call(`cashier/sales/${sale}/claim`, 'POST', { claim_token: null }, people.cashier.token)).data.claim_token;
  const payment = { claim_token: claim, method: 'CASH', amount_received_cents: 100, reference: null }, paymentKey = key();
  const paid = await call(`cashier/sales/${sale}/payments`, 'POST', payment, people.cashier.token, paymentKey); assert.equal(paid.status, 201);
  assert.equal((await call(`cashier/sales/${sale}/payments`, 'POST', payment, people.cashier.token, paymentKey)).status, 200);
  assert.equal(await quantity(p), '4.000');
  const refund = { reason: 'Devolucion sintetica', method: 'CASH', restock: true, money_returned: true }, refundKey = key();
  assert.equal((await call(`cashier/sales/${sale}/refunds`, 'POST', refund, people.manager.token, refundKey)).status, 201);
  assert.equal((await call(`cashier/sales/${sale}/refunds`, 'POST', refund, people.manager.token, refundKey)).status, 200); assert.equal(await quantity(p), '5.000');
  const incomplete = await product(false);
  const [s] = await db.execute("INSERT INTO sales(folio,branch_id,created_by,status,subtotal_cents,total_cents) VALUES(?,?,?,'SENT_TO_CASHIER',100,100)", [`DIRECT-${suffix}`, branch, people.manager.id]);
  await db.execute('INSERT INTO sale_items(sale_id,product_id,product_name,quantity,unit_price_cents,line_total_cents) VALUES(?,?,?,1,100,100)', [s.insertId, incomplete, 'Planta sintetica']);
  const badClaim = (await call(`cashier/sales/${s.insertId}/claim`, 'POST', { claim_token: null }, people.cashier.token)).data.claim_token;
  assert.equal((await call(`cashier/sales/${s.insertId}/payments`, 'POST', { ...payment, claim_token: badClaim }, people.cashier.token)).data.error, 'PRODUCT_PREPARATION_INCOMPLETE');
  const [[payments]] = await db.execute('SELECT COUNT(*) n FROM cashier_payments WHERE sale_id=?', [s.insertId]); assert.equal(payments.n, 0);
});

test('supplier confirmation requires owner; no alternate inventory entry for manager', async () => {
  const p = await product();
  const supplier = (await call('suppliers', 'POST', { code: `B1-${suffix}`, name: 'Proveedor sintetico', is_active: true })).data.supplier;
  const draft = await call('supplier-purchases', 'POST', { supplier_id: supplier.id, document_date: '2026-10-10', external_reference: null, payment_terms: 'CASH', expected_total_cents: 100, source_file_name: null,
    items: [{ line_number: 1, raw_description: 'Planta sintetica', container_code: 'M1', suggested_common_name: null, suggested_presentation: null, quantity: 1, unit_cost_cents: 100 }] });
  assert.equal(draft.status, 201);
  const purchase = draft.data.purchase;
  assert.equal((await call(`supplier-purchases/${purchase.id}/items/${purchase.items[0].id}`, 'PATCH', { resolution_status: 'MATCHED', product_id: p, suggested_common_name: null, suggested_presentation: null })).status, 200);
  assert.equal((await call(`supplier-purchases/${purchase.id}/confirm`, 'POST', {}, people.manager.token)).status, 403);
  assert.equal(await quantity(p), '0.000');
  assert.equal((await call(`supplier-purchases/${purchase.id}/confirm`, 'POST', {})).status, 201); assert.equal(await quantity(p), '1.000');
});

test('list import dry-run, stable codes, repeated import and preserved confirmed edits', async () => {
  const input = { schema_version: 1, source_key: `b1_${suffix}`, items: [{ entry_id: 'f-001', original_name: 'Planta de lista sintetica', category: 'Follaje', size: 'Maceta sintetica' }] };
  const plan = preparePlantLists(input, { categories: [], products: [] });
  // Isolate this importer exercise from classifications created by prior runs.
  for (const c of plan.catalog.categories) c.name += ` ${suffix}`;
  assert.equal(plan.can_apply, true);
  assert.equal((await importCatalog(db, plan.catalog)).can_apply, true);
  const applied = await importCatalog(db, plan.catalog, { apply: true }); assert.equal(applied.can_apply, true);
  assert.equal((await importCatalog(db, plan.catalog, { apply: true })).items.filter(i => i.action === 'create').length, 0);
  const [[p]] = await db.execute('SELECT id, preparation_mode, price_confirmed_at, price_cents, is_active FROM products WHERE internal_code=?', [plan.catalog.products[0].internal_code]);
  assert.equal(p.preparation_mode, 'PENDING'); assert.equal(p.price_confirmed_at, null); assert.equal(Number(p.price_cents), 0); assert.equal(p.is_active, 0);
  assert.equal((await call(`products/${p.id}/preparation`)).data.commercial_state, 'IMPORTED_PENDING');
  assert.equal((await call(`products?status=all&search=${plan.catalog.products[0].internal_code}`)).data.items[0].id, p.id);
  await call(`products/${p.id}`, 'PATCH', { price_cents: 1234 });
  assert.equal((await importCatalog(db, plan.catalog, { apply: true })).can_apply, false);
  const [[kept]] = await db.execute('SELECT COUNT(*) n, MAX(price_cents) price FROM products WHERE internal_code=?', [plan.catalog.products[0].internal_code]);
  assert.equal(kept.n, 1); assert.equal(Number(kept.price), 1234);
});

test('payment concurrent with approval is serial and never overwrites a subsequent sale', async () => {
  const p = await product(); await receive(p, 5);
  const obs = (await observe(p, 7)).data;
  const sold = await call('sales', 'POST', { items: [{ product_id: p, quantity: 1 }], expected_total_cents: 100 }, people.manager.token);
  assert.equal(sold.status, 201);
  const id = sold.data.id;
  const claimed = (await call(`cashier/sales/${id}/claim`, 'POST', { claim_token: null }, people.cashier.token)).data;
  const [payment, approval] = await Promise.all([
    call(`cashier/sales/${id}/payments`, 'POST', { claim_token: claimed.claim_token, method: 'CASH', amount_received_cents: 100, reference: null }, people.cashier.token),
    decide(obs.observation_id),
  ]);
  assert.equal(payment.status, 201);
  assert.ok([201, 409].includes(approval.status));
  if (approval.status === 409) assert.equal(approval.data.error, 'INVENTORY_COUNT_STALE');
  assert.equal(await quantity(p), approval.status === 201 ? '6.000' : '4.000');
});

test('late failure rolls back stock, count and decision; same key safely retries', async () => {
  const p = await product(), obs = (await observe(p, 3)).data, k = key();
  const trigger = `block1_fail_${suffix}`;
  await db.query(`CREATE TRIGGER ${trigger} BEFORE UPDATE ON inventory_count_observations FOR EACH ROW
    BEGIN IF NEW.id = ${obs.observation_id} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Synthetic failure'; END IF; END`);
  try {
    const failed = await decide(obs.observation_id, 'APPROVE', people.owner.token, k); assert.equal(failed.status, 503);
    assert.equal(await quantity(p), '0.000');
    const [[count]] = await db.execute('SELECT COUNT(*) n FROM inventory_counts WHERE product_id=?', [p]); assert.equal(count.n, 0);
    const [[row]] = await db.execute('SELECT status FROM inventory_count_observations WHERE id=?', [obs.observation_id]); assert.equal(row.status, 'PENDING');
  } finally { await db.query(`DROP TRIGGER ${trigger}`); }
  assert.equal((await decide(obs.observation_id, 'APPROVE', people.owner.token, k)).status, 201);
  assert.equal((await decide(obs.observation_id, 'APPROVE', people.owner.token, k)).status, 200);
  assert.equal(await quantity(p), '3.000');
});

test('a lost request cannot submit an old physical count after an intervening movement', async () => {
  const p = await product();
  const baseline = (await call(`inventory/count-baseline?product_id=${p}`, 'GET', undefined, people.manager.token)).data;
  const k = key(), body = { product_id: p, counted_quantity: '5', reason: 'Conteo antes de conexion', baseline_quantity: baseline.quantity, baseline_movement_id: baseline.movement_id };
  assert.equal((await call('inventory/count-observations/result', 'POST', body, people.manager.token, k)).status, 404);
  await receive(p, 3);
  const result = await call('inventory/count-observations', 'POST', body, people.manager.token, k);
  assert.equal(result.data.error, 'INVENTORY_COUNT_STALE'); assert.equal(await quantity(p), '3.000');
  const [[rows]] = await db.execute('SELECT COUNT(*) n FROM inventory_count_observations WHERE product_id=?', [p]); assert.equal(rows.n, 0);
});

test('upgrade 032 to 033 preserves valid catalog, balances, sales, counts and role grants', async () => {
  const upgrade = await mysql.createConnection({ host: 'db', user: 'root', password: process.env.DB_PASSWORD, multipleStatements: true });
  const name = `block1_upgrade_${suffix}`;
  const directory = '/app/database/mysql';
  // Runtime grants are validated in the main synthetic database. Removing only
  // GRANT/REVOKE statements here prevents another schema's DDL from changing the
  // hard-coded canonical runtime account grants for vivero.
  const ddl = text => text.replace(/\b(?:GRANT|REVOKE)\s[\s\S]*?;/g, '');
  try {
    await upgrade.query(`CREATE DATABASE ${name} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
    await upgrade.query(`USE ${name}`);
    await upgrade.query(await readFile(`${directory}/schema.sql`, 'utf8'));
    await upgrade.query(await readFile(`${directory}/seed.sql`, 'utf8'));
    const files = (await readdir(`${directory}/migrations`)).filter(f => /^\d{3}_[a-z_]+\.sql$/.test(f)).sort();
    for (const file of files.filter(f => f < '033_')) await upgrade.query(ddl(await readFile(`${directory}/migrations/${file}`, 'utf8')));
    await upgrade.execute("INSERT INTO products(internal_code, common_name, category_id, price_cents) VALUES('UPGRADE-ZERO','Planta incompleta sintetica',1,0)");
    await upgrade.execute("INSERT INTO sales(folio,branch_id,created_by,status,subtotal_cents,total_cents) VALUES('UPGRADE-SALE',1,1,'SENT_TO_CASHIER',100,100)");
    await upgrade.execute("INSERT INTO inventory_counts(idempotency_hash,branch_id,product_id,previous_quantity,counted_quantity,adjustment_quantity,reason,counted_by) VALUES(?,1,1,10,10,0,'Conteo historico sintetico',1)", [randomBytes(32)]);
    async function retained() {
      const result = {};
      for (const [table, columns] of [['products', 'id,internal_code,common_name,category_id,price_cents,is_active'], ['inventory', '*'], ['sales', '*'], ['inventory_counts', '*'], ['role_permissions', '*']]) {
        result[table] = (await upgrade.query(`SELECT ${columns} FROM ${table} ORDER BY 1`))[0];
      }
      return result;
    }
    const before = await retained();
    await upgrade.query(ddl(await readFile(`${directory}/migrations/033_inventory_preparation.sql`, 'utf8')));
    assert.deepEqual(await retained(), before);
    const [modes] = await upgrade.query('SELECT internal_code, preparation_mode FROM products ORDER BY id');
    assert.ok(modes.filter(p => p.internal_code !== 'UPGRADE-ZERO').every(p => p.preparation_mode === 'LEGACY'));
    assert.equal(modes.find(p => p.internal_code === 'UPGRADE-ZERO').preparation_mode, 'PENDING');
    const [newProduct] = await upgrade.execute("INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES('UPGRADE-NEW','Planta nueva sintetica',1,100)");
    const [[created]] = await upgrade.execute('SELECT preparation_mode FROM products WHERE id=?', [newProduct.insertId]); assert.equal(created.preparation_mode, 'PENDING');
  } finally { await upgrade.end(); } // Retain isolated upgrade evidence, no DROP.
});
