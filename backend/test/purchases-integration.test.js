import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Purchases require isolated local Compose');
test('supplier purchases: branch scope, draft replay, review learning, atomic receptions and concurrent confirmation', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), users = [];
  let branchId, supplierId, productId, trigger = false;
  try {
    const [b] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [`PUR-${suffix}`, 'Demo compras']); branchId = b.insertId;
    const [p] = await db.execute('INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES(?,?,1,999)', [`PUR-${suffix}`, 'Demo compra']); productId = p.insertId;
    async function user(role, branch = branchId) {
      const [u] = await db.execute('INSERT INTO users(email,full_name,role_id,branch_id,is_active) VALUES(?,?,(SELECT id FROM roles WHERE name=?),?,1)', [`pur-${suffix}-${users.length}@example.invalid`, 'Demo', role, branch]); users.push(u.insertId);
      const token = randomBytes(32).toString('base64url');
      await db.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [u.insertId, sessionDigest(token)]);
      return token;
    }
    const owner = await user('OWNER'), inventory = await user('INVENTORY'), seller = await user('SALES'), other = await user('OWNER', 1);
    async function call(path, method = 'GET', body, key, token = owner) {
      const r = await fetch(process.env.API_URL + '/api/v1/' + path, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...(key ? { 'Idempotency-Key': key } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, data: await r.json() };
    }
    const supplier = { code: `DEMO-${suffix.toUpperCase()}`, name: 'Demo proveedor', is_active: true };
    assert.equal((await call('suppliers', 'POST', supplier, null, seller)).status, 403);
    const made = await call('suppliers', 'POST', supplier); assert.equal(made.status, 201); supplierId = made.data.supplier.id;
    assert.equal((await call('suppliers', 'POST', supplier)).status, 409);
    assert.equal((await call('suppliers?include_inactive=true', 'GET', undefined, null, inventory)).status, 200);
    const presentation = { code: 'M1', display_name: 'Maceta demo', nominal_size: '12.50', size_unit: 'cm', notes: null };
    assert.equal((await call(`suppliers/${supplierId}/presentations`, 'PUT', presentation)).status, 200);
    const line = { line_number: 1, raw_description: 'Demo proveedor planta', container_code: 'M1', suggested_common_name: null, suggested_presentation: null, quantity: 3, unit_cost_cents: 101 };
    const input = { supplier_id: supplierId, document_date: '2026-09-30', external_reference: `DOC-${suffix}`, payment_terms: 'CREDIT', expected_total_cents: 505, source_file_name: 'demo.csv', items: [line, { ...line, line_number: 2, raw_description: 'Otra planta', quantity: 2 }] };
    const key = `draft-${suffix}-001`;
    const drafts = await Promise.all([call('supplier-purchases', 'POST', input, key), call('supplier-purchases', 'POST', input, key)]);
    assert.deepEqual(drafts.map(r => r.status).sort(), [200, 201]);
    const doc = drafts[0].data.purchase, path = `supplier-purchases/${doc.id}`;
    assert.equal(doc.items[0].resolution_status, 'UNMATCHED');
    assert.equal((await call(path, 'GET', undefined, null, other)).status, 404);
    assert.equal((await call('supplier-purchases', 'POST', { ...input, payment_terms: 'CASH' }, key)).status, 409);
    assert.equal((await call('supplier-purchases', 'POST', input, `draft-${suffix}-002`)).status, 409);
    const confirmKey = `confirm-${suffix}-001`;
    assert.equal((await call(path + '/confirm', 'POST', {}, confirmKey)).status, 409);
    const matched = { resolution_status: 'MATCHED', product_id: productId, suggested_common_name: null, suggested_presentation: null };
    for (const item of doc.items) assert.equal((await call(path + `/items/${item.id}`, 'PATCH', matched)).status, 200);
    // Failure after first reception must also roll back its balance and link.
    await db.query(`CREATE TRIGGER purchase_test_fail BEFORE INSERT ON inventory_movements FOR EACH ROW BEGIN IF NEW.notes = 'Compra ${doc.id}, línea 2' THEN SET NEW.quantity = NULL; END IF; END`); trigger = true;
    assert.equal((await call(path + '/confirm', 'POST', {}, confirmKey)).status, 503);
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM inventory_movements WHERE branch_id=?', [branchId]); assert.equal(count.n, 0);
    assert.equal((await call(path)).data.purchase.status, 'DRAFT');
    await db.query('DROP TRIGGER purchase_test_fail'); trigger = false;
    const race = await Promise.all([call(path + '/confirm', 'POST', {}, confirmKey), call(path + '/confirm', 'POST', {}, confirmKey)]);
    assert.deepEqual(race.map(r => r.status).sort(), [200, 201]);
    const [[balance]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id=? AND product_id=?', [branchId, productId]); assert.equal(balance.quantity, '5.000');
    const [[price]] = await db.execute('SELECT price_cents FROM products WHERE id=?', [productId]); assert.equal(Number(price.price_cents), 999);
    assert.equal((await call(path + '/confirm', 'POST', {}, `confirm-${suffix}-002`)).status, 409);
    assert.equal((await call(path + `/items/${doc.items[0].id}`, 'PATCH', matched)).status, 409);
    const learned = await call('supplier-purchases', 'POST', { ...input, external_reference: null }, `draft-${suffix}-003`);
    assert.equal(learned.status, 201); assert.ok(learned.data.purchase.items.every(i => i.resolution_status === 'AUTO_MATCHED'));
    const second = `supplier-purchases/${learned.data.purchase.id}`;
    const ignored = { ...matched, resolution_status: 'IGNORED', product_id: null };
    for (const item of learned.data.purchase.items) assert.equal((await call(second + `/items/${item.id}`, 'PATCH', ignored)).status, 200);
    assert.equal((await call(second + '/confirm', 'POST', {}, `confirm-${suffix}-003`)).status, 409);
    await call(`suppliers/${supplierId}`, 'PATCH', { ...supplier, is_active: false });
    assert.equal((await call('supplier-purchases', 'POST', input, key)).status, 200);
    assert.equal((await call('supplier-purchases', 'POST', { ...input, external_reference: null }, `draft-${suffix}-004`)).status, 409);
    const rejectedKey = `draft-${suffix}-004`;
    assert.equal((await call('supplier-purchases/retire', 'POST', {}, rejectedKey, seller)).status, 403);
    assert.equal((await call('supplier-purchases/retire', 'POST', {}, rejectedKey)).data.status, 'RETIRED');
    assert.equal((await call('supplier-purchases/retire', 'POST', {}, rejectedKey)).data.status, 'RETIRED');
    await call(`suppliers/${supplierId}`, 'PATCH', supplier);
    assert.equal((await call('supplier-purchases', 'POST', { ...input, external_reference: null }, rejectedKey)).data.error, 'PURCHASE_ATTEMPT_RETIRED');
    assert.equal((await call('supplier-purchases/retire', 'POST', {}, rejectedKey, other)).status, 409);
    assert.equal((await call('supplier-purchases/retire', 'POST', {}, key)).data.receipt.purchase.id, doc.id);
    const raceKey = `draft-${suffix}-race`;
    const retireRace = await Promise.all([
      call('supplier-purchases', 'POST', { ...input, external_reference: null }, raceKey),
      call('supplier-purchases/retire', 'POST', {}, raceKey),
    ]);
    const retirement = retireRace[1].data;
    assert.ok(['RETIRED', 'COMMITTED'].includes(retirement.status));
    if (retirement.status === 'RETIRED') assert.equal(retireRace[0].data.error, 'PURCHASE_ATTEMPT_RETIRED');
    else assert.equal(retirement.receipt.purchase.id, retireRace[0].data.purchase.id);
    const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
    try {
      await assert.rejects(runtime.execute('DELETE FROM supplier_purchase_documents WHERE id=0'));
      await assert.rejects(runtime.execute('DELETE FROM purchase_draft_retirements WHERE id=0'));
      await assert.rejects(runtime.execute('UPDATE supplier_purchase_items SET unit_cost_cents=0 WHERE id=0'));
      await assert.rejects(runtime.execute('UPDATE supplier_purchase_documents SET expected_total_cents=0 WHERE id=0'));
    } finally { await runtime.end(); }
  } finally {
    if (trigger) await db.query('DROP TRIGGER purchase_test_fail');
    if (supplierId) {
      await db.execute('DELETE i FROM supplier_purchase_items i JOIN supplier_purchase_documents d ON d.id=i.purchase_id WHERE d.supplier_id=?', [supplierId]);
      await db.execute('DELETE FROM supplier_purchase_documents WHERE supplier_id=?', [supplierId]);
      await db.execute('DELETE FROM supplier_product_aliases WHERE supplier_id=?', [supplierId]);
      await db.execute('DELETE FROM supplier_presentations WHERE supplier_id=?', [supplierId]);
      await db.execute('DELETE FROM suppliers WHERE id=?', [supplierId]);
    }
    if (branchId) { await db.execute('DELETE FROM inventory_movements WHERE branch_id=?', [branchId]); await db.execute('DELETE FROM inventory WHERE branch_id=?', [branchId]); }
    for (const id of users) { await db.execute('DELETE FROM purchase_draft_retirements WHERE actor_id=?', [id]); await db.execute('DELETE FROM auth_sessions WHERE user_id=?', [id]); await db.execute('DELETE FROM users WHERE id=?', [id]); }
    if (productId) await db.execute('DELETE FROM products WHERE id=?', [productId]);
    if (branchId) await db.execute('DELETE FROM branches WHERE id=?', [branchId]);
    await db.end();
  }
});
