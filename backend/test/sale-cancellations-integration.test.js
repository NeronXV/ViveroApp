import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';
if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Isolated local Compose required');
test('pending cancellations HTTP/SQL: audit, replay, races, payment uncertainty, orders, permissions and unchanged stock', async t => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(8).toString('hex'), key = () => randomBytes(32).toString('hex');
  let failureTrigger = false, orderFailureTrigger = false;
  const call = async (path, token, input, attempt = key(), short = true) => {
    const r = await fetch(process.env.API_URL + path, { method: input === undefined ? 'GET' : 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Idempotency-Key': attempt,
        ...(short ? { 'X-Vivero-Folio-Format': 'short-v1' } : {}) }, body: input === undefined ? undefined : JSON.stringify(input) });
    return { status: r.status, body: await r.json() };
  };
  const reason = { reason: 'Cliente solicitó cancelar antes de pagar' };
  try {
    const [branch] = await db.execute('INSERT INTO branches(code,name) VALUES(?, ?)', [`CAN-${suffix}`, 'Ensayo cancelaciones']);
    const [other] = await db.execute('INSERT INTO branches(code,name) VALUES(?, ?)', [`CAN-O-${suffix}`, 'Otra sucursal ensayo']);
    const users = [], tokens = [];
    for (const [i, role] of ['OWNER', 'OWNER', 'CASHIER', 'SALES', 'OWNER'].entries()) {
      const token = randomBytes(32).toString('base64url');
      const [u] = await db.execute('INSERT INTO users(email,full_name,role_id,branch_id,is_active) VALUES(?, ?, (SELECT id FROM roles WHERE name=?), ?,1)', [`cancel-${i}-${suffix}@example.invalid`, 'Usuario ficticio', role, i === 4 ? other.insertId : branch.insertId]);
      await db.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [u.insertId, sessionDigest(token)]);
      users.push(u.insertId); tokens.push(token);
    }
    const [product] = await db.execute('INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES(?,?,1,100)', [`CAN-${suffix}`, 'Planta ficticia']);
    const inventory = await call('/api/v1/inventory/receptions', tokens[0], { product_id: product.insertId, quantity: '10', notes: null });
    assert.equal(inventory.status, 201);
    assert.equal((await call('/api/v1/inventory/activation', tokens[0], { initial_count_confirmed: true })).status, 200);
    async function newSale(attempt = key()) {
      const r = await call('/api/v1/sales', tokens[0], { items: [{ product_id: product.insertId, quantity: 1 }], expected_total_cents: 100 }, attempt);
      assert.equal(r.status, 201); return r.body.id;
    }
    const path = id => `/api/v1/cashier/sales/${id}`;
    const snapshot = async () => ({
      inventory: (await db.execute('SELECT * FROM inventory WHERE branch_id=? ORDER BY product_id', [branch.insertId]))[0],
      movements: (await db.execute('SELECT * FROM inventory_movements WHERE branch_id=? ORDER BY id', [branch.insertId]))[0],
      payments: (await db.execute('SELECT * FROM cashier_payments WHERE branch_id=? ORDER BY id', [branch.insertId]))[0],
    });
    let cancelled;
    await t.test('response stream lost after commit recovers the same cancellation and audit', async () => {
      const id = await newSale(), original = key(), before = await snapshot();
      const lost = await fetch(process.env.API_URL + path(id) + '/cancel', { method: 'POST', headers: {
        Authorization: `Bearer ${tokens[0]}`, 'Content-Type': 'application/json', 'Idempotency-Key': original }, body: JSON.stringify(reason) });
      assert.equal(lost.status, 201);
      await lost.body.cancel(); // Discard the real HTTP body before decoding any result.
      const recovered = await call(path(id) + '/cancellation-result', tokens[0], {}, original);
      assert.equal(recovered.status, 200); assert.equal(recovered.body.sale.id, id);
      const retried = await call(path(id) + '/cancel', tokens[0], reason, original);
      assert.equal(retried.body.cancellation.id, recovered.body.cancellation.id);
      const [[audit]] = await db.execute("SELECT COUNT(*) AS n FROM sale_status_history WHERE sale_id=? AND new_status='CANCELLED'", [id]);
      assert.equal(Number(audit.n), 1); assert.deepEqual(await snapshot(), before);
    });
    await t.test('normal, repeated, recovery after a lost reply, and exact audit', async () => {
      const saleKey = key(); cancelled = await newSale(saleKey); const original = key(), before = await snapshot();
      assert.equal((await call(path(cancelled) + '/cancel-options', tokens[0])).body.can_cancel, true);
      const first = await call(path(cancelled) + '/cancel', tokens[0], reason, original);
      assert.equal(first.status, 201); assert.match(first.body.sale.folio, /^VD-[0-9]{4,}$/);
      assert.equal(first.body.cancellation.cancelled_by, users[0]); assert.equal(first.body.cancellation.branch_id, branch.insertId);
      assert.equal(first.body.cancellation.reason, reason.reason); assert.match(first.body.cancellation.created_at, /\.\d{6}Z$/);
      for (const action of ['cancel', 'cancellation-result']) {
        const r = await call(path(cancelled) + '/' + action, tokens[0], action === 'cancel' ? reason : {}, original);
        assert.equal(r.status, 200); assert.equal(r.body.cancellation.id, first.body.cancellation.id); assert.equal(r.body.idempotent_replay, true);
      }
      assert.equal((await call(path(cancelled) + '/cancel', tokens[0], { reason: 'Otro motivo' }, original)).body.error, 'CANCELLATION_IDEMPOTENCY_CONFLICT');
      assert.equal((await call(path(cancelled) + '/cancel', tokens[1], reason)).body.error, 'SALE_ALREADY_CANCELLED');
      const [[count]] = await db.execute("SELECT COUNT(*) AS n FROM sale_status_history WHERE sale_id=? AND new_status='CANCELLED'", [cancelled]); assert.equal(Number(count.n), 1);
      assert.deepEqual(await snapshot(), before);
      assert.equal((await call(path(cancelled) + '/claim', tokens[0], { claim_token: null })).status, 409);
      assert.equal((await call(path(cancelled) + '/payments', tokens[0], { claim_token: 'a'.repeat(64), method: 'CASH', amount_received_cents: 100, reference: null })).status, 409);
      await assert.rejects(db.execute("UPDATE sales SET status='SENT_TO_CASHIER' WHERE id=?", [cancelled]), /SALE_CANCELLATION_FINAL/);
      const legacy = await call(path(cancelled), tokens[0], undefined, key(), false);
      assert.match(legacy.body.sale.folio, /^VD-[A-F0-9]{24}$/); assert.equal(legacy.body.sale.status, 'CANCELLED');
      const oldRecovery = await call('/api/v1/sales/recover', tokens[0], {}, saleKey, false);
      assert.equal(oldRecovery.status, 200); assert.equal(oldRecovery.body.status, 'CANCELLED'); assert.equal(oldRecovery.body.folio, legacy.body.sale.folio);
      const oldQueue = await call('/api/v1/cashier/sales?folio=' + legacy.body.sale.folio, tokens[0], undefined, key(), false);
      assert.deepEqual(oldQueue.body.items, []);
    });
    await t.test('two different workers cancel once; claims race safely with cancellation', async () => {
      for (let i = 0; i < 8; i++) {
        const id = await newSale();
        const race = await Promise.all(tokens.slice(0, 2).map(token => call(path(id) + '/cancel', token, reason)));
        assert.deepEqual(race.map(r => r.status).sort(), [201, 409]);
      }
      for (let i = 0; i < 8; i++) {
        const id = await newSale();
        const race = await Promise.all([call(path(id) + '/claim', tokens[0], { claim_token: null }), call(path(id) + '/cancel', tokens[1], reason)]);
        assert.ok((race[0].status === 200 && race[1].status === 409) || (race[0].status === 409 && race[1].status === 201));
      }
    });
    await t.test('open/expired claims and PAYMENT_PENDING require reconciliation; paid sales require refund', async () => {
      const id = await newSale();
      const claim = await call(path(id) + '/claim', tokens[0], { claim_token: null });
      const body = { claim_token: claim.body.claim_token, method: 'CASH', amount_received_cents: 100, reference: null };
      const before = await snapshot();
      assert.equal((await call(path(id) + '/cancel', tokens[1], reason)).body.error, 'CANCELLATION_RECONCILIATION_REQUIRED');
      assert.deepEqual(await snapshot(), before);
      const race = await Promise.all([call(path(id) + '/payments', tokens[0], body), call(path(id) + '/cancel', tokens[1], reason)]);
      assert.equal(race[0].status, 201); assert.equal(race[1].status, 409);
      assert.equal((await call(path(id) + '/cancel', tokens[0], reason)).body.error, 'CANCELLATION_REFUND_REQUIRED');
      const paidSnapshot = await snapshot();
      await assert.rejects(db.execute("UPDATE sales SET status='CANCELLED' WHERE id=?", [id]), /SALE_CANCELLATION_UNSAFE/);
      assert.deepEqual(await snapshot(), paidSnapshot);
      const pending = await newSale();
      await db.execute("UPDATE sales SET status='PAYMENT_PENDING' WHERE id=?", [pending]);
      assert.equal((await call(path(pending) + '/cancel', tokens[0], reason)).body.error, 'CANCELLATION_RECONCILIATION_REQUIRED');
      const expired = await newSale(); await call(path(expired) + '/claim', tokens[0], { claim_token: null });
      await db.execute('UPDATE sale_payment_claims SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE), expires_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) WHERE active_sale_id=?', [expired]);
      assert.equal((await call(path(expired) + '/cancel', tokens[1], reason)).body.error, 'CANCELLATION_RECONCILIATION_REQUIRED');
      const [claims] = await db.execute('SELECT claim_token FROM sale_payment_claims WHERE active_sale_id=?', [expired]);
      assert.equal((await call(path(expired) + '/release', tokens[0], { claim_token: claims[0].claim_token })).status, 200);
      assert.equal((await call(path(expired) + '/cancel', tokens[1], reason)).status, 201);
    });
    await t.test('permissions, branch isolation, missing reason and runtime audit privileges', async () => {
      const id = await newSale(), before = await snapshot();
      assert.equal((await call(path(id) + '/cancel-options', tokens[2])).body.can_cancel, false);
      for (const token of [tokens[2], tokens[3]]) assert.equal((await call(path(id) + '/cancel', token, reason)).status, 403);
      assert.equal((await call(path(id) + '/cancel', tokens[4], reason)).status, 404);
      assert.equal((await call(path(id) + '/cancel', tokens[0], {})).status, 400);
      assert.deepEqual(await snapshot(), before);
      const runtime = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
      try { await assert.rejects(runtime.execute("UPDATE sale_cancellations SET reason='alterado' WHERE sale_id=?", [cancelled]), /denied/i); }
      finally { await runtime.end(); }
    });
    await t.test('linked web order is cancelled atomically and checkout never recreates a cobrable sale', async () => {
      const publicKey = key();
      const r = await call('/api/v1/web-orders', null, { branch_id: branch.insertId, items: [{ product_id: product.insertId, quantity: 1 }], expected_total_cents: 100, customer_name: 'Cliente ficticio', customer_phone: null, customer_email: `${suffix}@example.invalid`, notes: null }, publicKey);
      assert.equal(r.status, 201);
      await db.execute("UPDATE web_orders SET status='CONFIRMED', revision=1 WHERE id=?", [r.body.id]);
      const checkout = await call(`/api/v1/admin/web-orders/${r.body.id}/send-to-cashier`, tokens[0], {});
      assert.equal(checkout.status, 201);
      const before = await snapshot(), original = key();
      const [[originalOrder]] = await db.execute('SELECT * FROM web_orders WHERE id=?', [r.body.id]);
      await db.query(`CREATE TRIGGER cancel_order_failure_${suffix} BEFORE INSERT ON web_order_status_history FOR EACH ROW SET NEW.new_status=IF(NEW.order_id=${r.body.id} AND NEW.new_status='CANCELLED','INVALID',NEW.new_status)`); orderFailureTrigger = true;
      assert.equal((await call(path(checkout.body.sale_id) + '/cancel', tokens[0], reason, original)).status, 400);
      const [[rolledOrder]] = await db.execute('SELECT * FROM web_orders WHERE id=?', [r.body.id]); assert.deepEqual(rolledOrder, originalOrder);
      const [[rolledSale]] = await db.execute('SELECT status FROM sales WHERE id=?', [checkout.body.sale_id]); assert.equal(rolledSale.status, 'SENT_TO_CASHIER');
      assert.equal((await call(path(checkout.body.sale_id) + '/cancellation-result', tokens[0], {}, original)).status, 404);
      await db.query(`DROP TRIGGER cancel_order_failure_${suffix}`); orderFailureTrigger = false;
      const cancellation = await call(path(checkout.body.sale_id) + '/cancel', tokens[0], reason, original);
      assert.equal(cancellation.status, 201); assert.equal(cancellation.body.web_order_id, r.body.id);
      const again = await call(`/api/v1/admin/web-orders/${r.body.id}/send-to-cashier`, tokens[1], {});
      assert.equal(again.status, 200); assert.equal(again.body.sale_id, checkout.body.sale_id); assert.equal(again.body.status, 'CANCELLED');
      const ticket = await call('/api/v1/web-orders/ticket', null, {}, publicKey); assert.equal(ticket.body.order.status, 'CANCELLED');
      const [[order]] = await db.execute('SELECT status,revision FROM web_orders WHERE id=?', [r.body.id]); assert.equal(order.status, 'CANCELLED'); assert.equal(order.revision, 2);
      assert.deepEqual(await snapshot(), before);
    });
    await t.test('late sale audit error rolls back status and cancellation; same key retries once', async () => {
      const id = await newSale(), original = key(), before = await snapshot();
      await db.query(`CREATE TRIGGER cancel_failure_${suffix} BEFORE INSERT ON sale_status_history FOR EACH ROW SET NEW.observation = IF(NEW.sale_id=${id} AND NEW.new_status='CANCELLED',NULL,NEW.observation)`); failureTrigger = true;
      assert.equal((await call(path(id) + '/cancel', tokens[0], reason, original)).status, 503);
      const [[state]] = await db.execute('SELECT status FROM sales WHERE id=?', [id]); assert.equal(state.status, 'SENT_TO_CASHIER');
      assert.equal((await call(path(id) + '/cancellation-result', tokens[0], {}, original)).status, 404);
      assert.deepEqual(await snapshot(), before);
      await db.query(`DROP TRIGGER cancel_failure_${suffix}`); failureTrigger = false;
      assert.equal((await call(path(id) + '/cancel', tokens[0], reason, original)).status, 201);
    });
  } finally {
    if (failureTrigger) await db.query(`DROP TRIGGER cancel_failure_${suffix}`);
    if (orderFailureTrigger) await db.query(`DROP TRIGGER cancel_order_failure_${suffix}`);
    await db.end();
  }
});
