import assert from 'node:assert/strict';
import { openAdminDb } from './admin-db.js';

// Explicit local demo verification after first OWNER bootstrap, never a VPS tool.
let db, token;
async function request(path, options = {}) {
  return fetch('http://api:3001' + path, { ...options, headers: {
    'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}),
  } });
}
try {
  db = await openAdminDb();
  const [[schema]] = await db.execute("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema='vivero'");
  assert.equal(schema.n, 55);
  const [versions] = await db.execute('SELECT version FROM schema_migrations ORDER BY version');
  assert.equal(versions.length, 28);
  assert.equal(versions.at(-1).version, '029_inventory_imports');
  const [[branch]] = await db.execute("SELECT id,inventory_enabled FROM branches WHERE code='DEMO'");
  assert.equal(branch.inventory_enabled, 0);
  const [[products]] = await db.execute("SELECT COUNT(*) AS n FROM products WHERE internal_code IN ('DEMO-MONSTERA','DEMO-ECHEVERIA')");
  assert.equal(products.n, 2);
  const [[balances]] = await db.execute('SELECT COUNT(*) AS n FROM inventory WHERE branch_id=? AND quantity=10', [branch.id]);
  assert.equal(balances.n, 2);
  for (const table of ['sales', 'cashier_payments', 'web_orders', 'supplier_purchase_documents']) {
    const [[row]] = await db.execute(`SELECT COUNT(*) AS n FROM ${table}`); // Fixed table names only.
    assert.equal(row.n, 0);
  }
  assert.equal((await request('/health')).status, 200);
  const login = await request('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email: process.env.BOOTSTRAP_EMAIL, password: process.env.BOOTSTRAP_PASSWORD }) });
  assert.equal(login.status, 200);
  token = (await login.json()).access_token;
  const me = await request('/api/v1/auth/me');
  assert.equal(me.status, 200);
  const context = await me.json();
  assert.equal(context.role.name, 'OWNER');
  assert.equal(context.branch.code, 'DEMO');
  assert.ok(context.capabilities.includes('MANAGE_INVENTORY'));
  assert.equal((await request('/api/v1/products')).status, 200);
  assert.equal((await request('/api/v1/reports/daily-sales')).status, 200);
  const logout = await request('/api/v1/auth/logout', { method: 'POST' });
  assert.equal(logout.status, 200);
  assert.equal((await request('/api/v1/auth/me')).status, 401);
  token = null;
  console.log('Local demo installation verified: schema, seeds, stock disabled, OWNER login, catalog, reports and logout. No credentials printed.');
} catch {
  console.error('Local installation verification failed. Requires isolated demo database and completed OWNER bootstrap. No credentials printed.');
  process.exitCode = 1;
} finally {
  if (token) { try { await request('/api/v1/auth/logout', { method: 'POST' }); } catch {} }
  if (db) await db.end();
}
