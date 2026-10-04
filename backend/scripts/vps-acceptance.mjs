// Synthetic acceptance only. The host must verify the isolated Compose project first.
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { openAdminDb } from './admin-db.js';
import { hashPassword } from '../src/auth/password.js';

const project = 'vivero-acceptance-20261003';
let stage = 'guard', token, db;
const directory = '/acceptance';
const mode = process.argv[2];
const key = () => randomBytes(32).toString('hex');
const report = {};
try {
  assert.equal(process.env.VIVERO_ACCEPTANCE_PROJECT, project);
  assert.ok(['seed', 'exercise', 'verify', 'kotlin-fixture'].includes(mode));
  db = await openAdminDb();
  const [[schema]] = await db.query("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema='vivero'");
  assert.equal(Number(schema.n), 55);
  if (mode === 'seed') {
    stage = 'empty-target';
    assert.equal(process.argv[3], '--acknowledge-empty-synthetic-target');
    for (const table of ['users', 'branches', 'products', 'sales', 'cashier_payments', 'inventory', 'inventory_movements']) {
      const [[row]] = await db.query(`SELECT COUNT(*) AS n FROM ${table}`);
      assert.equal(Number(row.n), 0);
    }
    const fixture = { project, accounts: {} };
    await db.beginTransaction();
    try {
      const [category] = await db.execute("INSERT INTO categories(name) VALUES('Ensayo aislado VPS')");
      const [product] = await db.execute("INSERT INTO products(internal_code,barcode,common_name,category_id,price_cents) VALUES('ENSAYO-500','ENSAYO-500','Planta de ensayo VPS',?,500)", [category.insertId]);
      fixture.productId = product.insertId; fixture.code = 'ENSAYO-500';
      for (const name of ['auto', 'tablet', 'web']) {
        const password = randomBytes(24).toString('hex');
        const email = `ensayo-${name}@example.invalid`, branchCode = `ENSAYO-${name.toUpperCase()}`;
        const [branch] = await db.execute('INSERT INTO branches(code,name) VALUES(?,?)', [branchCode, `Sucursal de ensayo ${name}`]);
        const [user] = await db.execute("INSERT INTO users(email,full_name,password_hash,role_id,branch_id,is_active) VALUES(?,?,?,(SELECT id FROM roles WHERE name='OWNER'),?,1)", [email, `Cuenta sintetica ${name}`, await hashPassword(password), branch.insertId]);
        await db.execute('INSERT INTO inventory(branch_id,product_id,quantity) VALUES(?,?,10)', [branch.insertId, product.insertId]);
        await db.execute('UPDATE branches SET inventory_enabled=1,inventory_activated_at=UTC_TIMESTAMP(6),inventory_activated_by=? WHERE id=?', [user.insertId, branch.insertId]);
        fixture.accounts[name] = { email, password, userId: user.insertId, branchId: branch.insertId, branchCode };
      }
      await db.commit();
      // Capture directly on the VPS into a private file. Never relay credentials.
      process.stdout.write(JSON.stringify(fixture));
    } catch (error) { await db.rollback(); throw error; }
  } else {
    const fixture = JSON.parse(await readFile(`${directory}/fixture.json`, 'utf8'));
    assert.equal(fixture.project, project);
    if (mode === 'kotlin-fixture') {
      stage = 'kotlin-fixture';
      assert.ok(!fixture.accounts.kotlin);
      const [[known]] = await db.query("SELECT COUNT(*) AS n FROM branches WHERE code IN ('ENSAYO-AUTO','ENSAYO-TABLET','ENSAYO-WEB')");
      assert.equal(Number(known.n), 3);
      const password = randomBytes(24).toString('hex'), email = 'ensayo-kotlin@example.invalid';
      await db.beginTransaction();
      try {
        const [branch] = await db.execute("INSERT INTO branches(code,name) VALUES('ENSAYO-KOTLIN','Sucursal de ensayo Kotlin')");
        const [user] = await db.execute("INSERT INTO users(email,full_name,password_hash,role_id,branch_id,is_active) VALUES(?,'Cuenta sintetica Kotlin',?,(SELECT id FROM roles WHERE name='OWNER'),?,1)", [email, await hashPassword(password), branch.insertId]);
        await db.execute('INSERT INTO inventory(branch_id,product_id,quantity) VALUES(?,?,10)', [branch.insertId, fixture.productId]);
        await db.execute('UPDATE branches SET inventory_enabled=1,inventory_activated_at=UTC_TIMESTAMP(6),inventory_activated_by=? WHERE id=?', [user.insertId, branch.insertId]);
        const account = { email, password, userId: user.insertId, branchId: branch.insertId, branchCode: 'ENSAYO-KOTLIN' };
        fixture.accounts.kotlin = account;
        await db.commit();
        await writeFile(`${directory}/fixture.json`, JSON.stringify(fixture), { mode: 0o600 });
        process.stdout.write(JSON.stringify({ project, ...account, productId: fixture.productId, code: fixture.code, productName: 'Planta de ensayo VPS' }));
      } catch (error) { await db.rollback(); throw error; }
    } else {
    const account = fixture.accounts.auto;
    if (mode === 'exercise') {
      await writeFile(`${directory}/auto-run-started`, 'Retain attempts and inspect before any rerun.\n', { flag: 'wx', mode: 0o600 });
      const saleKey = key(), paymentKey = key();
      await writeFile(`${directory}/auto-attempts.json`, JSON.stringify({ saleKey, paymentKey }), { flag: 'wx', mode: 0o600 });
      async function http(path, body, idempotency, expected = 200) {
        const response = await fetch(`http://web${path}`, {
          method: body === undefined ? 'GET' : 'POST',
          headers: { 'Content-Type': 'application/json', Origin: 'http://127.0.0.1:38003',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...(idempotency ? { 'Idempotency-Key': idempotency } : {}) },
          body: body === undefined ? undefined : JSON.stringify(body),
        });
        const data = await response.json();
        assert.equal(response.status, expected);
        return data;
      }
      stage = 'login';
      token = (await http('/api/v1/auth/login', { email: account.email, password: account.password })).access_token;
      try {
        const me = await http('/api/v1/auth/me');
        assert.equal(me.branch.id, account.branchId); assert.equal(me.role.name, 'OWNER');
        assert.ok(me.capabilities.includes('OPERATE_CASHIER'));
        stage = 'quote-and-sale';
        const items = [{ product_id: fixture.productId, quantity: 2 }];
        const quote = await http('/api/v1/sales/quote', { items });
        assert.equal(quote.total_cents, 1000);
        const input = { items, expected_total_cents: 1000 };
        const sale = await http('/api/v1/sales', input, saleKey, 201);
        assert.equal((await http('/api/v1/sales', input, saleKey)).id, sale.id);
        assert.equal((await http('/api/v1/sales/recover', {}, saleKey)).id, sale.id);
        report.saleId = sale.id;
        const stock = async () => {
          const [[row]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id=? AND product_id=?', [account.branchId, fixture.productId]);
          return String(row.quantity);
        };
        assert.equal(await stock(), '10.000');
        assert.ok((await http('/api/v1/cashier/sales')).items.some(row => row.id === sale.id));
        stage = 'claim-and-underpayment';
        const claim = await http(`/api/v1/cashier/sales/${sale.id}/claim`, { claim_token: null });
        const body = { claim_token: claim.claim_token, method: 'CASH', amount_received_cents: 1200, reference: null };
        await http(`/api/v1/cashier/sales/${sale.id}/payments`, { ...body, amount_received_cents: 999 }, key(), 400);
        assert.equal(await stock(), '10.000');
        stage = 'payment-and-replay';
        const paid = await http(`/api/v1/cashier/sales/${sale.id}/payments`, body, paymentKey, 201);
        assert.equal(paid.payment.change_cents, 200); assert.equal(paid.sale.status, 'PAID');
        assert.equal((await http(`/api/v1/cashier/sales/${sale.id}/payments`, body, paymentKey)).payment.id, paid.payment.id);
        assert.equal((await http(`/api/v1/cashier/sales/${sale.id}/payment-result`, {}, paymentKey)).payment.id, paid.payment.id);
        stage = 'receipt-and-stock';
        const receipt = await http(`/api/v1/cashier/receipts/${paid.payment.id}`);
        assert.equal(receipt.payment.amount_due_cents, 1000);
        assert.equal(receipt.payment.amount_received_cents, 1200);
        assert.equal(receipt.payment.change_cents, 200);
        assert.equal(receipt.items.length, 1); assert.equal(Number(receipt.items[0].quantity), 2);
        assert.equal(await stock(), '8.000');
        report.paymentId = paid.payment.id;
      } finally { if (token) { await http('/api/v1/auth/logout', {}); token = null; } }
      await writeFile(`${directory}/auto-result.json`, JSON.stringify(report), { mode: 0o600, flag: 'wx' });
    }
    stage = 'sql-reconciliation';
    const [[totals]] = await db.execute('SELECT COUNT(*) AS n,SUM(amount_due_cents) AS due,SUM(amount_received_cents) AS received,SUM(change_cents) AS change_total FROM cashier_payments WHERE branch_id=?', [account.branchId]);
    assert.equal(Number(totals.n), 1); assert.equal(Number(totals.due), 1000);
    assert.equal(Number(totals.received), 1200); assert.equal(Number(totals.change_total), 200);
    const [[sale]] = await db.execute('SELECT COUNT(*) AS n FROM sales WHERE branch_id=? AND status=\'PAID\'', [account.branchId]);
    assert.equal(Number(sale.n), 1);
    const [[movement]] = await db.execute("SELECT COUNT(*) AS n,SUM(quantity) AS quantity FROM inventory_movements WHERE branch_id=? AND movement_type='SALE'", [account.branchId]);
    assert.equal(Number(movement.n), 1); assert.equal(String(movement.quantity), '-2.000');
    const [[inventory]] = await db.execute('SELECT quantity FROM inventory WHERE branch_id=? AND product_id=?', [account.branchId, fixture.productId]);
    assert.equal(String(inventory.quantity), '8.000');
    console.log('PASS: isolated VPS sale/payment/receipt/replay, underpayment rejection, SQL totals 1000/1200/200 cents and stock 10->8 with one SALE movement. No production credentials printed.');
    }
  }
} catch {
  console.error(`FAIL: isolated acceptance at ${stage}. Inspect retained private attempts; do not blindly rerun.`);
  process.exitCode = 1;
} finally { await db?.end(); }
