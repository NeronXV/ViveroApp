// Local-only fixture helper for the Web consumer contract test. Never use remotely.
import mysql from 'mysql2/promise';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';
if (process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero' || process.env.API_URL !== 'http://api:3001') throw new Error('Isolated local Compose required');
const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
const mode = process.argv[2];
try {
  if (mode === 'create') {
    const suffix = randomBytes(8).toString('hex'), token = randomBytes(32).toString('base64url');
    await db.beginTransaction();
    try {
      const [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`WOPS-${suffix}`, 'Demo contrato Web']);
      const [user] = await db.execute("INSERT INTO users (email, full_name, role_id, branch_id, is_active) VALUES (?, 'Demo contrato Web', (SELECT id FROM roles WHERE name = 'MANAGER'), ?, 1)", [`wops-${suffix}@example.invalid`, branch.insertId]);
      await db.execute('INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))', [user.insertId, sessionDigest(token)]);
      const [sale] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, status, subtotal_cents, total_cents) VALUES (?, ?, ?, 'PAID', 900, 900)", [`WOPS-${suffix}`, branch.insertId, user.insertId]);
      const [category] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`WOPS-${suffix}`]);
      const [product] = await db.execute("INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, 'Demo contrato Web', ?, 900)", [`WOPS-${suffix}`, category.insertId]);
      await db.execute("INSERT INTO sale_items (sale_id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, line_total_cents) VALUES (?, ?, 'Demo contrato Web', ?, 1, 900, 900, 900)", [sale.insertId, product.insertId, `WOPS-${suffix}`]);
      const [payment] = await db.execute("INSERT INTO cashier_payments (sale_id, cashier_id, branch_id, idempotency_key, method, amount_due_cents, amount_received_cents, change_cents) VALUES (?, ?, ?, ?, 'CASH', 900, 1000, 100)", [sale.insertId, user.insertId, branch.insertId, randomBytes(32).toString('hex')]);
      await db.commit();
      // Captured by the host runner; never print this output in a user report.
      console.log(JSON.stringify({ suffix, token, userId: user.insertId, branchId: branch.insertId, saleId: sale.insertId, paymentId: payment.insertId, categoryId: category.insertId, productId: product.insertId }));
    } catch (error) { await db.rollback(); throw error; }
  } else if (mode === 'verify' || mode === 'cleanup') {
    let raw = ''; for await (const chunk of process.stdin) raw += chunk;
    const fixture = JSON.parse(raw);
    assert.match(fixture.suffix, /^[a-f0-9]{16}$/);
    for (const key of ['userId', 'branchId', 'saleId', 'paymentId', 'categoryId', 'productId']) assert.ok(Number.isSafeInteger(fixture[key]) && fixture[key] > 0);
    const [[branch]] = await db.execute('SELECT code FROM branches WHERE id = ?', [fixture.branchId]);
    assert.equal(branch?.code, `WOPS-${fixture.suffix}`);
    const [[user]] = await db.execute('SELECT email, branch_id FROM users WHERE id = ?', [fixture.userId]);
    assert.equal(user?.email, `wops-${fixture.suffix}@example.invalid`); assert.equal(user.branch_id, fixture.branchId);
    const [[sale]] = await db.execute('SELECT folio, branch_id, created_by FROM sales WHERE id = ?', [fixture.saleId]);
    assert.equal(sale?.folio, `WOPS-${fixture.suffix}`); assert.equal(sale.branch_id, fixture.branchId); assert.equal(sale.created_by, fixture.userId);
    if (mode === 'verify') {
      const [refunds] = await db.execute('SELECT id, amount_cents FROM sale_refunds WHERE sale_id = ?', [fixture.saleId]);
      const [closings] = await db.execute('SELECT id, cash_sales_cents, cash_refunds_cents, expected_cash_cents, difference_cents FROM cashier_closings WHERE cashier_id = ?', [fixture.userId]);
      assert.equal(refunds.length, 1); assert.equal(Number(refunds[0].amount_cents), 900);
      assert.equal(closings.length, 1);
      for (const [key, expected] of Object.entries({ cash_sales_cents: 900, cash_refunds_cents: 900, expected_cash_cents: 100, difference_cents: -10 })) assert.equal(Number(closings[0][key]), expected);
      const [payments] = await db.execute('SELECT payment_id FROM cashier_closing_payments WHERE closing_id = ?', [closings[0].id]);
      const [members] = await db.execute('SELECT refund_id FROM cashier_closing_refunds WHERE closing_id = ?', [closings[0].id]);
      assert.deepEqual(payments.map(r => r.payment_id), [fixture.paymentId]); assert.deepEqual(members.map(r => r.refund_id), [refunds[0].id]);
      console.log('MariaDB: one refund, one closing, exact totals and memberships verified.');
    } else {
      await db.beginTransaction();
      try {
        await db.execute('DELETE m FROM cashier_closing_payments m JOIN cashier_closings c ON c.id = m.closing_id WHERE c.cashier_id = ?', [fixture.userId]);
        await db.execute('DELETE m FROM cashier_closing_refunds m JOIN cashier_closings c ON c.id = m.closing_id WHERE c.cashier_id = ?', [fixture.userId]);
        await db.execute('DELETE FROM cashier_closings WHERE cashier_id = ?', [fixture.userId]);
        await db.execute('DELETE FROM sale_refunds WHERE sale_id = ?', [fixture.saleId]);
        await db.execute('DELETE FROM cashier_payments WHERE sale_id = ?', [fixture.saleId]);
        await db.execute('DELETE FROM sale_items WHERE sale_id = ?', [fixture.saleId]);
        await db.execute('DELETE FROM sales WHERE id = ?', [fixture.saleId]);
        await db.execute('DELETE FROM products WHERE id = ? AND internal_code = ?', [fixture.productId, "WOPS-" + fixture.suffix]);
        await db.execute('DELETE FROM categories WHERE id = ? AND name = ?', [fixture.categoryId, "WOPS-" + fixture.suffix]);
        await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [fixture.userId]);
        await db.execute('DELETE FROM users WHERE id = ?', [fixture.userId]);
        await db.execute('DELETE FROM branches WHERE id = ?', [fixture.branchId]);
        await db.commit(); console.log('Synthetic fixtures removed.');
      } catch (error) { await db.rollback(); throw error; }
    }
  } else throw new Error('Expected create, verify or cleanup');
} finally { await db.end(); }
