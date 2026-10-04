import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { sessionDigest } from '../src/auth/service.js';

if (process.env.API_URL !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') throw new Error('Scan tests require isolated local Compose');
test('scan HTTP/SQL: exact normalized codes, promotions, ambiguity, visibility and authorization', async () => {
  const db = await mysql.createConnection({ host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD });
  const suffix = randomBytes(6).toString('hex'), categories = [], products = [], users = [];
  let promotion;
  try {
    for (const active of [true, false]) {
      const [row] = await db.execute('INSERT INTO categories(name,is_active) VALUES(?,?)', [`SCAN-${suffix}-${categories.length}`, active]); categories.push(row.insertId);
    }
    const code = `SCAN-${suffix}`, barcode = `001${suffix}`;
    const [first] = await db.execute('INSERT INTO products(internal_code,barcode,common_name,category_id,price_cents) VALUES(?,?,?,?,1000)', [code, barcode, 'Demo escaneo', categories[0]]); products.push(first.insertId);
    const [promo] = await db.execute("INSERT INTO promotions(name,promo_type,scope,fixed_amount_cents) VALUES(?,'FIXED_AMOUNT','SELECTED_PRODUCTS',100)", [`SCAN-${suffix}`]); promotion = promo.insertId;
    await db.execute('INSERT INTO promotion_products(promotion_id,product_id) VALUES(?,?)', [promotion, first.insertId]);
    async function session(role) {
      const [row] = await db.execute('INSERT INTO users(email,full_name,role_id,is_active) VALUES(?,?,(SELECT id FROM roles WHERE name=?),1)', [`scan-${suffix}-${users.length}@example.invalid`, 'Demo escaneo', role]); users.push(row.insertId);
      const token = randomBytes(32).toString('base64url');
      await db.execute('INSERT INTO auth_sessions(user_id,token_hash,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR))', [row.insertId, sessionDigest(token)]);
      return token;
    }
    const token = await session('CASHIER'), denied = await session('UNKNOWN');
    async function scan(value, useToken = token, query = '') {
      const response = await fetch(process.env.API_URL + '/api/v1/products/scan' + query, { method: 'POST', headers: { Authorization: `Bearer ${useToken}`, 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
      return { status: response.status, data: await response.json() };
    }
    const found = await scan({ code: `  ${code.toLowerCase()}  ` });
    assert.equal(found.status, 200); assert.equal(found.data.item.id, first.insertId);
    assert.equal(found.data.item.price_cents, 1000); assert.equal(found.data.item.effective_price_cents, 900);
    assert.equal(found.data.item.active_promotion.id, promotion);
    assert.equal(Object.hasOwn(found.data.item, 'wholesale_price_cents'), false);
    assert.equal((await scan({ code: barcode })).data.item.id, first.insertId);
    assert.equal((await scan({ code: code.slice(0, -1) })).data.item, null);
    assert.equal((await scan({ code }, denied)).status, 403);
    assert.equal((await scan({ code }, 'bad')).status, 401);
    assert.equal((await scan({ code }, token, '?status=all')).status, 400);
    for (const value of [{ code: 'x' }, { code: 'x'.repeat(129) }, { code: code + '\n' }, { code, branch_id: 1 }]) assert.equal((await scan(value)).status, 400);
    const [second] = await db.execute('INSERT INTO products(internal_code,barcode,common_name,category_id,price_cents) VALUES(?,?,?,?,1000)', [`SECOND-${suffix}`, code, 'Demo duplicado', categories[1]]); products.push(second.insertId);
    assert.equal((await scan({ code })).data.error, 'PRODUCT_SCAN_CODE_AMBIGUOUS'); // includes inactive-category matches, fail closed
    await db.execute('UPDATE products SET is_active=0 WHERE id=?', [second.insertId]);
    assert.equal((await scan({ code })).data.item.id, first.insertId);
    await db.execute('UPDATE categories SET is_active=0 WHERE id=?', [categories[0]]);
    assert.equal((await scan({ code })).data.item, null);
    await db.execute('UPDATE categories SET is_active=1 WHERE id=?', [categories[0]]);
    await db.execute('UPDATE products SET barcode=? WHERE id=?', [`Á-${suffix}`, first.insertId]);
    assert.equal((await scan({ code: `á-${suffix}` })).data.item.id, first.insertId);
    assert.equal((await scan({ code: `a-${suffix}` })).data.item, null);
    await db.execute('UPDATE products SET is_active=0 WHERE id=?', [first.insertId]);
    assert.equal((await scan({ code })).data.item, null);
  } finally {
    for (const user of users) { await db.execute('DELETE FROM auth_sessions WHERE user_id=?', [user]); await db.execute('DELETE FROM users WHERE id=?', [user]); }
    if (promotion) { await db.execute('DELETE FROM promotion_products WHERE promotion_id=?', [promotion]); await db.execute('DELETE FROM promotions WHERE id=?', [promotion]); }
    for (const product of products) await db.execute('DELETE FROM products WHERE id=?', [product]);
    for (const category of categories) await db.execute('DELETE FROM categories WHERE id=?', [category]);
    await db.end();
  }
});
