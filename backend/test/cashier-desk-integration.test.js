import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { writeFile } from 'node:fs/promises';
import { hashPassword } from '../src/auth/password.js';
if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Isolated Compose required');
test('cashier desk opt-in: accurate origin, legacy shape, both folios, branch scope and read-only queries', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), password = process.env.STAGE5_UI_PASSWORD ?? randomBytes(24).toString('hex');
  const key = () => randomBytes(32).toString('hex');
  const call = async (path, token, body, attempt = key(), desk = false) => {
    const r = await fetch(process.env.API_URL + path, { method: body === undefined ? 'GET' : 'POST', headers: {
      'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Idempotency-Key': attempt,
      'X-Vivero-Folio-Format': 'short-v1', ...(desk ? { 'X-Vivero-Cashier-View': 'desk-v1' } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: r.status, data: await r.json() };
  };
  try {
    const [branch] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [`UI5-${suffix}`, 'Sucursal de ensayo · Caja']);
    const actors = [], tokens = [], emails = [];
    const hashed = await hashPassword(password);
    for (let i = 0; i < 2; i++) {
      const email = `caja-${i}-${suffix}@example.invalid`;
      const [u] = await db.execute("INSERT INTO users(email,full_name,password_hash,role_id,branch_id,is_active) VALUES(?,?,?,(SELECT id FROM roles WHERE name='OWNER'),?,1)", [email, `Cajero de ensayo ${i ? 'B' : 'A'}`, hashed, branch.insertId]);
      const login = await call('/api/v1/auth/login', null, { email, password }); assert.equal(login.status, 200);
      actors.push(u.insertId); tokens.push(login.data.access_token); emails.push(email);
    }
    const products = [];
    for (const [i, [name, price]] of [['Monstera deliciosa', 25000], ['Sansevieria', 18000], ['Echeveria', 8500]].entries()) {
      const [p] = await db.execute('INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES(?,?,1,?)', [`UI5-${suffix}-${i}`, name, price]);
      products.push(p.insertId);
      assert.equal((await call('/api/v1/inventory/receptions', tokens[0], { product_id: p.insertId, quantity: '100', notes: 'Conteo ficticio' })).status, 201);
    }
    assert.equal((await call('/api/v1/inventory/activation', tokens[0], { initial_count_confirmed: true })).status, 200);
    const sales = [];
    const mixed = { items: [{ product_id: products[0], quantity: 1 }, { product_id: products[1], quantity: 2 }], expected_total_cents: 61000 };
    for (let i = 0; i < 5; i++) {
      const r = await call('/api/v1/sales', tokens[0], i < 2 ? mixed : { items: [{ product_id: products[2], quantity: 1 }], expected_total_cents: 8500 });
      assert.equal(r.status, 201); sales.push({ id: r.data.id, folio: r.data.folio });
    }
    assert.equal((await call(`/api/v1/cashier/sales/${sales[2].id}/claim`, tokens[1], { claim_token: null })).status, 200);
    const order = await call('/api/v1/web-orders', null, { branch_id: branch.insertId, items: [{ product_id: products[0], quantity: 1 }], expected_total_cents: 25000,
      customer_name: 'Cliente ficticio', customer_phone: null, customer_email: `${suffix}@example.invalid`, notes: null });
    assert.equal(order.status, 201);
    await db.execute("UPDATE web_orders SET status='CONFIRMED', revision=1 WHERE id=?", [order.data.id]);
    const web = await call(`/api/v1/admin/web-orders/${order.data.id}/send-to-cashier`, tokens[0], {}); assert.equal(web.status, 201);
    const snapshot = async () => ({ inventory: (await db.execute('SELECT * FROM inventory WHERE branch_id=? ORDER BY id', [branch.insertId]))[0],
      payments: (await db.execute('SELECT * FROM cashier_payments WHERE branch_id=?', [branch.insertId]))[0], movements: (await db.execute('SELECT * FROM inventory_movements WHERE branch_id=? ORDER BY id', [branch.insertId]))[0] });
    const before = await snapshot(), old = await call('/api/v1/cashier/sales?view=operations', tokens[0]);
    const modern = await call('/api/v1/cashier/sales?view=operations', tokens[0], undefined, key(), true);
    assert.equal(modern.status, 200); assert.equal(modern.data.items.length, 6);
    for (const row of modern.data.items) {
      assert.equal(row.operation.origin, row.id === web.data.sale_id ? 'WEB_ORDER' : 'DIRECT_SALE');
      assert.equal('web_order_id' in row, false);
      const { origin, ...operation } = row.operation;
      const previous = old.data.items.find(item => item.id === row.id);
      assert.equal('origin' in previous.operation, false);
      for (const field of ['item_count','claim_state','created_by_label']) assert.equal(operation[field], previous.operation[field]);
      assert.deepEqual(Object.keys(row).sort(), Object.keys(previous).sort());
    }
    const [[legacy]] = await db.execute('SELECT folio FROM sales WHERE id=?', [sales[0].id]);
    for (const folio of [sales[0].folio, legacy.folio]) {
      const found = await call('/api/v1/cashier/sales?view=operations&folio=' + encodeURIComponent(folio), tokens[0], undefined, key(), true);
      assert.deepEqual(found.data.items.map(row => row.id), [sales[0].id]);
    }
    assert.deepEqual(await snapshot(), before);
    if (process.env.STAGE5_FIXTURE_FILE) await writeFile(process.env.STAGE5_FIXTURE_FILE, JSON.stringify({ email: emails[0], secondEmail: emails[1], password,
      userId: actors[0], secondUserId: actors[1], branchId: branch.insertId, products, sales, webSaleId: web.data.sale_id, webFolio: web.data.folio }), { mode: 0o600 });
  } finally { await db.end(); }
});
