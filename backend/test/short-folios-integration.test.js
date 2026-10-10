import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { hashPassword } from '../src/auth/password.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Isolated Compose required');
test('short aliases: concurrent workers, legacy compatibility, recovery, lookup scope and unchanged stock/payment', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(8).toString('hex'), password = randomBytes(24).toString('hex');
  const tokens = [], actors = [];
  const call = async (path, token, input, key, short = true) => {
    const response = await fetch(process.env.API_URL + path, { method: input === undefined ? 'GET' : 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(key ? { 'Idempotency-Key': key } : {}), ...(short ? { 'X-Vivero-Folio-Format': 'short-v1' } : {}) },
      body: input === undefined ? undefined : JSON.stringify(input) });
    return { status: response.status, body: await response.json() };
  };
  try {
    const hashed = await hashPassword(password);
    for (let i = 0; i < 3; i++) {
      const email = `short-${i}-${suffix}@example.invalid`;
      const [user] = await db.execute("INSERT INTO users(email,full_name,password_hash,branch_id,role_id,is_active) VALUES(?, 'Ensayo folios', ?,1,(SELECT id FROM roles WHERE name=?),1)", [email, hashed, i < 2 ? 'SALES' : 'OWNER']);
      actors.push(user.insertId);
      const login = await call('/api/v1/auth/login', null, { email, password });
      assert.equal(login.status, 200); tokens.push(login.body.access_token);
    }
    const [product] = await db.execute('INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES(?, ?,1,100)', [`SF-${suffix}`, 'Planta ficticia folios']);
    const input = { items: [{ product_id: product.insertId, quantity: 1 }], expected_total_cents: 100 };
    const keys = Array.from({ length: 20 }, () => randomBytes(32).toString('hex'));
    const [[before]] = await db.execute('SELECT (SELECT COUNT(*) FROM cashier_payments) AS payments, (SELECT COUNT(*) FROM inventory_movements) AS movements');
    const replies = await Promise.all(keys.map((key, i) => call('/api/v1/sales', tokens[i % 2], input, key)));
    assert.ok(replies.every(r => r.status === 201));
    assert.equal(new Set(replies.map(r => r.body.folio)).size, 20);
    for (const [i, reply] of replies.entries()) {
      assert.match(reply.body.folio, /^VD-[0-9]{4,16}$/);
      const legacy = await call('/api/v1/sales/recover', tokens[i % 2], {}, keys[i], false);
      assert.equal(legacy.status, 200); assert.match(legacy.body.folio, /^VD-[A-F0-9]{24}$/);
      assert.equal(legacy.body.id, reply.body.id);
      assert.deepEqual(Object.keys(reply.body).sort(), Object.keys(legacy.body).sort());
      const recovery = await call('/api/v1/sales/recover', tokens[i % 2], {}, keys[i]);
      assert.equal(recovery.body.folio, reply.body.folio);
      const document = await call(`/api/v1/sales/${reply.body.id}`, tokens[i % 2]);
      assert.equal(document.body.sale.folio, reply.body.folio);
      assert.ok(document.body.history.every(event => typeof event.created_at === 'string' && event.created_at.endsWith('Z')));
      for (const folio of [reply.body.folio, legacy.body.folio]) {
        const own = await call(`/api/v1/sales?folio=${folio}`, tokens[i % 2]);
        assert.deepEqual(own.body.items.map(s => s.id), [reply.body.id]);
        const other = await call(`/api/v1/sales?folio=${folio}`, tokens[(i + 1) % 2]);
        assert.deepEqual(other.body.items, []);
        const queue = await call(`/api/v1/cashier/sales?folio=${folio}`, tokens[2]);
        assert.deepEqual(queue.body.items.map(s => s.id), [reply.body.id]);
      }
    }
    const [[after]] = await db.execute('SELECT (SELECT COUNT(*) FROM cashier_payments) AS payments, (SELECT COUNT(*) FROM inventory_movements) AS movements');
    assert.deepEqual(after, before);
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM sales WHERE created_by IN (?,?)', actors.slice(0, 2));
    assert.equal(Number(count.n), 20);
    await assert.rejects(db.execute('INSERT INTO sales(folio,branch_id,created_by,subtotal_cents,total_cents) VALUES(?,1,?,0,0)', [replies[0].body.folio, actors[0]]), /SALE_FOLIO_ALIAS_CONFLICT/);
    // Exercise the boundary without 10,000 synthetic sales, inside a rollback.
    await db.beginTransaction();
    await db.execute('UPDATE sale_folio_counter SET last_value=9999 WHERE id=1');
    const [boundary] = await db.execute('INSERT INTO sales(folio,branch_id,created_by,subtotal_cents,total_cents) VALUES(?,1,?,0,0)', [`BOUNDARY-${suffix}`, actors[0]]);
    const [[alias]] = await db.execute('SELECT short_folio FROM sale_folio_aliases WHERE sale_id=?', [boundary.insertId]);
    assert.equal(alias.short_folio, 'VD-10000');
    await db.rollback();
    const stale = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
    try {
      await stale.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
      await stale.beginTransaction();
      await stale.execute('SELECT COUNT(*) FROM sales'); // Establish a snapshot before another writer commits.
      const [[counter]] = await db.execute('SELECT last_value FROM sale_folio_counter WHERE id=1');
      const reserved = `VD-${String(Number(counter.last_value) + 2).padStart(4, '0')}`;
      await db.execute('INSERT INTO sales(folio,branch_id,created_by,subtotal_cents,total_cents) VALUES(?,1,?,0,0)', [reserved, actors[0]]);
      const [fromStale] = await stale.execute('INSERT INTO sales(folio,branch_id,created_by,subtotal_cents,total_cents) VALUES(?,1,?,0,0)', [`STALE-${suffix}`, actors[0]]);
      const [[freshAlias]] = await stale.execute('SELECT short_folio FROM sale_folio_aliases WHERE sale_id=?', [fromStale.insertId]);
      assert.notEqual(freshAlias.short_folio, reserved, 'Current namespace locks must see a committed reservation outside the old snapshot');
    } finally { await stale.rollback(); await stale.end(); }
    const orderKey = randomBytes(32).toString('hex');
    const orderInput = { branch_id: 1, ...input, customer_name: 'Persona de ensayo', customer_phone: null, customer_email: `${suffix}@example.invalid`, notes: null };
    const publicOrder = await call('/api/v1/web-orders', null, orderInput, orderKey);
    assert.equal(publicOrder.status, 201);
    assert.equal(publicOrder.body.order_number, `VW-${String(publicOrder.body.id).padStart(4, '0')}`);
    const historical = await call('/api/v1/web-orders/recover', null, {}, orderKey, false);
    assert.equal(historical.body.order_number, `VW-${publicOrder.body.id}`);
    const ticket = await call('/api/v1/web-orders/ticket', null, {}, orderKey);
    assert.equal(ticket.body.order.order_number, publicOrder.body.order_number);
    await db.execute("UPDATE web_orders SET status='CONFIRMED', revision=1 WHERE id=?", [publicOrder.body.id]);
    const checkout = await call(`/api/v1/admin/web-orders/${publicOrder.body.id}/send-to-cashier`, tokens[2], {});
    assert.equal(checkout.status, 201); assert.match(checkout.body.folio, /^VD-[0-9]{4,16}$/);
    const oldCheckout = await call(`/api/v1/admin/web-orders/${publicOrder.body.id}/send-to-cashier`, tokens[2], {}, null, false);
    assert.equal(oldCheckout.body.sale_id, checkout.body.sale_id); assert.match(oldCheckout.body.folio, /^VD-[A-F0-9]{24}$/);
    for (const folio of [historical.body.order_number, publicOrder.body.order_number]) {
      const lookup = await call(`/api/v1/admin/web-orders?folio=${folio}`, tokens[2]);
      assert.deepEqual(lookup.body.items.map(o => o.id), [publicOrder.body.id]);
    }
    const [[linked]] = await db.execute('SELECT web_order_id FROM sales WHERE id=?', [checkout.body.sale_id]);
    assert.equal(linked.web_order_id, publicOrder.body.id);
    const first = replies[0].body;
    const claim = await call(`/api/v1/cashier/sales/${first.id}/claim`, tokens[2], { claim_token: null });
    assert.equal(claim.status, 200);
    const paymentKey = randomBytes(32).toString('hex');
    const paid = await call(`/api/v1/cashier/sales/${first.id}/payments`, tokens[2], { claim_token: claim.body.claim_token,
      method: 'CASH', amount_received_cents: 100, reference: null }, paymentKey);
    assert.equal(paid.status, 201); assert.equal(paid.body.sale.folio, first.folio);
    const printed = await call(`/api/v1/cashier/receipts/${paid.body.payment.id}`, tokens[2]);
    assert.equal(printed.body.sale.folio, first.folio);
    const oldResult = await call(`/api/v1/cashier/sales/${first.id}/payment-result`, tokens[2], {}, paymentKey, false);
    assert.match(oldResult.body.sale.folio, /^VD-[A-F0-9]{24}$/);
    assert.equal(oldResult.body.payment.id, paid.body.payment.id);
    for (const folio of [first.folio, oldResult.body.sale.folio]) {
      const refundLookup = await call(`/api/v1/cashier/refunds/lookup?folio=${folio}`, tokens[2]);
      assert.equal(refundLookup.status, 200); assert.equal(refundLookup.body.sale_id, first.id);
      assert.equal(refundLookup.body.folio, folio); // Old clients require an exact echo.
    }
    await db.beginTransaction();
    await db.execute(`INSERT INTO web_orders(id,branch_id,idempotency_hash,request_hash,customer_name,customer_email,subtotal_cents,discount_cents,total_cents)
      VALUES(10000,1,?,?,'Limite ficticio','boundary@example.invalid',100,0,100)`, [randomBytes(32), randomBytes(32)]);
    const [[webBoundary]] = await db.execute('SELECT short_folio FROM web_order_folio_aliases WHERE order_id=10000');
    assert.equal(webBoundary.short_folio, 'VW-10000');
    await db.rollback();
    await assert.rejects(db.execute("UPDATE sales SET folio='RENAMED-FIXTURE' WHERE id=?", [first.id]), /SALE_FOLIO_IMMUTABLE/);
    const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
    try {
      await assert.rejects(runtime.execute('UPDATE sale_folio_aliases SET ordinal=1 WHERE sale_id=?', [replies[0].body.id]));
      await assert.rejects(runtime.execute('CALL allocate_sale_folio(?)', [replies[0].body.id]));
      await assert.rejects(runtime.execute('SELECT * FROM sale_folio_namespace LIMIT 1'));
      await assert.rejects(runtime.execute('UPDATE sale_folio_counter SET last_value=0'));
    } finally { await runtime.end(); }
  } finally { await db.rollback().catch(() => {}); await db.end(); }
});
