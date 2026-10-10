import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
if (process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero' || process.env.API_URL !== 'http://api:3001') throw new Error('Isolated Compose only');
test('upgrade 031→032 preserves existing business rows, alias metadata and permissions', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD, multipleStatements: true });
  try {
    const [version] = await db.execute('SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1');
    assert.equal(version[0].version, '031_short_folios', 'One-shot migration rehearsal only');
    if (process.env.ADD_UPGRADE_FIXTURES === '1') {
      const suffix = randomBytes(8).toString('hex');
      const [u] = await db.execute("INSERT INTO users(email,full_name,branch_id,role_id,is_active) VALUES(?, 'Ensayo migración', 1, (SELECT id FROM roles WHERE name='OWNER'),1)", [`upgrade-cancel-${suffix}@example.invalid`]);
      const [p] = await db.execute('INSERT INTO products(internal_code,common_name,category_id,price_cents) VALUES(?,?,1,100)', [`CU-${suffix}`, 'Planta ficticia']);
      await db.execute('INSERT INTO inventory(branch_id,product_id,quantity) VALUES(1,?,0)', [p.insertId]);
      await db.execute("INSERT INTO inventory_movements(branch_id,product_id,movement_type,quantity,idempotency_hash,created_by) VALUES(1,?,'RECEPTION',10,?,?)", [p.insertId,randomBytes(32),u.insertId]);
      const [o] = await db.execute("INSERT INTO web_orders(branch_id,idempotency_hash,request_hash,customer_name,customer_email,status,subtotal_cents,discount_cents,total_cents) VALUES(1,?,?,'Cliente ficticio',?,'CONFIRMED',100,0,100)", [randomBytes(32),randomBytes(32),`${suffix}@example.invalid`]);
      const [s] = await db.execute("INSERT INTO sales(folio,branch_id,created_by,status,subtotal_cents,total_cents,web_order_id) VALUES(?,1,?,'PAID',100,100,?)", [`HIST-CANCEL-${suffix}`,u.insertId,o.insertId]);
      await db.execute('INSERT INTO sale_items(sale_id,product_id,product_name,quantity,unit_price_cents,line_total_cents) VALUES(?,?,?,1,100,100)', [s.insertId,p.insertId,'Planta ficticia']);
      await db.execute("INSERT INTO cashier_payments(sale_id,cashier_id,branch_id,idempotency_key,method,amount_due_cents,amount_received_cents,change_cents) VALUES(?,?,1,?,'CASH',100,100,0)", [s.insertId,u.insertId,randomBytes(32).toString('hex')]);
    }
    const tables = ['sales', 'sale_items', 'sale_status_history', 'cashier_payments', 'sale_payment_claims', 'inventory', 'inventory_movements',
      'web_orders', 'web_order_items', 'web_order_status_history', 'sale_folio_aliases', 'web_order_folio_aliases', 'sale_folio_namespace', 'role_permissions'];
    const snapshot = async () => {
      const value = {};
      for (const table of tables) value[table] = (await db.query(`SELECT * FROM ${table} ORDER BY 1`))[0];
      return value;
    };
    const before = await snapshot();
    assert.ok(before.sales.length > 0 && before.cashier_payments.length > 0 && before.web_orders.length > 0, 'Representative synthetic history required');
    await db.query(await readFile('/tmp/032_pending_sale_cancellations.sql', 'utf8'));
    assert.deepEqual(await snapshot(), before);
    const [[audit]] = await db.query('SELECT COUNT(*) AS n FROM sale_cancellations'); assert.equal(Number(audit.n), 0);
  } finally { await db.end(); }
});
