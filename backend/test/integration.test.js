// Run only through the local Compose test profile, against synthetic data.
import test from 'node:test';
import assert from 'node:assert/strict';
import mysql from 'mysql2/promise';
import { randomBytes } from 'node:crypto';
import { hashPassword } from '../src/auth/password.js';
import { sessionDigest } from '../src/auth/service.js';
import sharp from 'sharp';
import { createCatalog } from '../src/catalog.js';
import { importCatalog } from '../scripts/catalog-import.js';
import { importPromotions } from '../scripts/promotion-import.js';
import { importImages } from '../scripts/image-import.js';
import { createImageStore } from '../src/images.js';
import { createWebOrders } from '../src/web-orders.js';
import { createOrderAdmin } from '../src/web-order-admin.js';
import { readFile } from 'node:fs/promises';

const base = process.env.API_URL;
if (base !== 'http://api:3001' || process.env.DB_HOST !== 'db' || process.env.DB_NAME !== 'vivero') {
  throw new Error('Integration tests require the isolated local Compose environment');
}
const options = {
  host: 'db', database: 'vivero', user: process.env.DB_USER, password: process.env.DB_PASSWORD,
  supportBigNumbers: true, bigNumberStrings: true,
};
let headers = { 'Content-Type': 'application/json' };
const testPassword = randomBytes(32).toString('hex');
const testPasswordHash = await hashPassword(testPassword);

async function createUser(db, suffix, role = 'OWNER', active = true) {
  const email = `test-${suffix}@example.invalid`;
  const [created] = await db.execute(
    `INSERT INTO users (email, full_name, password_hash, branch_id, role_id, is_active)
     VALUES (?, 'Usuario sintetico', ?, 1, (SELECT id FROM roles WHERE name = ?), ?)`,
    [email, testPasswordHash, role, active],
  );
  return { id: created.insertId, email };
}

async function login(email) {
  const result = await request('/api/v1/auth/login', 'POST', { email, password: testPassword }, false);
  assert.equal(result.status, 200);
  assert.equal(result.data.expires_in, 3600);
  headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${result.data.access_token}` };
  return result.data.access_token;
}

async function deleteUser(db, user) {
  if (!user) return;
  await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [user.id]);
  await db.execute('DELETE FROM users WHERE id = ?', [user.id]);
  await db.execute('DELETE FROM auth_login_limits WHERE key_hash = ?', [sessionDigest(`email:${user.email}`)]);
}
async function request(path, method = 'GET', body, authenticated = true) {
  const response = await fetch(base + path, {
    method, headers: authenticated ? headers : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: response.status, data: await response.json() };
}

test('MariaDB/API catalog CRUD, pagination, validation and soft deletion', async () => {
  const db = await mysql.createConnection(options);
  const suffix = randomBytes(8).toString('hex');
  let categoryId;
  let owner;
  const products = [];
  try {
    assert.equal((await request('/health')).status, 200);
    owner = await createUser(db, suffix);
    await login(owner.email);
    assert.equal((await request('/api/v1/categories', 'POST', { name: 'Denied' }, false)).status, 401);
    const category = await request('/api/v1/categories', 'POST', { name: `Test-${suffix}` });
    assert.equal(category.status, 201);
    categoryId = category.data.id;
    assert.ok(Number.isInteger(categoryId));
    assert.equal((await request('/api/v1/categories', 'POST', { name: `Test-${suffix}` })).status, 409);
    const input = { internal_code: `TEST-${suffix}`, common_name: 'Planta sintetica', category_id: categoryId, price_cents: 12500, wholesale_price_cents: 9000, watering_advice: 'Riego moderado', light_type: 'Indirecta', recommended_climate: 'Templado', unit: 'maceta', minimum_stock: 2.5 };
    assert.equal((await request('/api/v1/products', 'POST', { ...input, price_cents: 1.5 })).status, 400);
    assert.equal((await request('/api/v1/products', 'POST', { ...input, category_id: 4294967295 })).status, 409);
    const product = await request('/api/v1/products', 'POST', input);
    assert.equal(product.status, 201);
    const id = product.data.id;
    products.push(id);
    assert.equal((await request('/api/v1/products', 'POST', input)).status, 409);
    assert.equal((await request(`/api/v1/products/${id}`, 'PATCH', { price_cents: 14000 })).status, 200);
    // An unchanged update must not incorrectly return 404.
    assert.equal((await request(`/api/v1/products/${id}`, 'PATCH', { price_cents: 14000 })).status, 200);
    const page = await request(`/api/v1/products?after_id=${id - 1}&limit=1`);
    assert.equal(page.data.items[0].price_cents, 14000);
    assert.equal(page.data.items[0].watering_advice, input.watering_advice);
    assert.equal(page.data.items[0].light_type, input.light_type);
    assert.equal(page.data.items[0].recommended_climate, input.recommended_climate);
    assert.equal(Object.hasOwn(page.data.items[0], 'wholesale_price_cents'), false);
    const adminPage = await request(`/api/v1/products?status=all&after_id=${id - 1}&limit=1`);
    assert.equal(adminPage.data.items[0].wholesale_price_cents, 9000);
    assert.equal(adminPage.data.items[0].minimum_stock, '2.500');
    assert.ok(adminPage.data.items[0].created_at);
    assert.equal((await request(`/api/v1/products/${id}`, 'PATCH', { minimum_stock: 3.125 })).status, 200);
    const [[minimum]] = await db.execute('SELECT minimum_stock,quantity FROM inventory WHERE product_id=? AND branch_id=1', [id]);
    assert.equal(minimum.minimum_stock, '3.125');
    assert.equal(minimum.quantity, '0.000');
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'INVENTORY') WHERE id = ?", [owner.id]);
    for (const wholesale_price_cents of [8000, null]) {
      assert.equal((await request(`/api/v1/products/${id}`, 'PATCH', { wholesale_price_cents })).status, 403);
    }
    assert.equal((await request(`/api/v1/products/${id}`, 'PATCH', { wholesale_price_cents: 9000, watering_advice: 'Riego actualizado' })).status, 200);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'SALES') WHERE id = ?", [owner.id]);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'DELETE')).status, 403);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'PATCH', { description: 'No permitida' })).status, 403);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'OWNER') WHERE id = ?", [owner.id]);
    assert.equal((await request(`/api/v1/products/${id}`, 'PATCH', { wholesale_price_cents: null })).status, 200);
    assert.equal((await request(`/api/v1/products?status=all&after_id=${id - 1}&limit=1`)).data.items[0].wholesale_price_cents, null);
    const second = await request('/api/v1/products', 'POST', { ...input, internal_code: `TEST2-${suffix}` });
    assert.equal(second.status, 201);
    products.push(second.data.id);
    const filtered = `/api/v1/products?category_id=${categoryId}&search=PLANTA&limit=1`;
    const filteredFirst = await request(filtered, 'GET', undefined, false);
    assert.equal(filteredFirst.status, 200);
    assert.equal(filteredFirst.data.items[0].id, id);
    assert.equal(filteredFirst.data.next_after_id, id);
    const filteredNext = await request(`${filtered}&after_id=${id}`, 'GET', undefined, false);
    assert.deepEqual(filteredNext.data.items.map(row => row.id), [second.data.id]);
    assert.equal(filteredNext.data.next_after_id, null);
    await db.execute('UPDATE products SET scientific_name = ? WHERE id = ?', ['Ficus prueba', id]);
    assert.deepEqual((await request(`/api/v1/products?category_id=${categoryId}&search=Ficus`)).data.items.map(row => row.id), [id]);
    for (const search of ['%', '_', "' OR 1=1 --"]) {
      assert.deepEqual((await request(`/api/v1/products?category_id=${categoryId}&search=${encodeURIComponent(search)}`)).data.items, []);
    }
    assert.deepEqual((await request('/api/v1/products?category_id=4294967295')).data.items, []);
    for (const query of ['category_id=uuid', 'search=a&search=b', 'category_id=1&category_id=2', `search=${'a'.repeat(81)}`]) {
      assert.equal((await request(`/api/v1/products?${query}`)).status, 400);
    }
    assert.equal((await request('/api/v1/categories?search=Planta')).status, 400);
    assert.equal((await request(`${filtered}&status=all`, 'GET', undefined, false)).status, 401);
    await db.execute('UPDATE categories SET is_active = 0 WHERE id = ?', [categoryId]);
    assert.deepEqual((await request(filtered)).data.items, []);
    assert.equal((await request(`${filtered}&status=all`)).data.items[0].id, id);
    await db.execute('UPDATE categories SET is_active = 1 WHERE id = ?', [categoryId]);
    const firstPage = await request(`/api/v1/products?after_id=${id - 1}&limit=1`);
    assert.equal(firstPage.data.next_after_id, id);
    const secondPage = await request(`/api/v1/products?after_id=${firstPage.data.next_after_id}&limit=1`);
    assert.equal(secondPage.data.items[0].id, second.data.id);
    assert.equal((await request(`/api/v1/products/${id}`, 'DELETE', undefined, false)).status, 401);
    assert.equal((await request(`/api/v1/products/${id}`, 'DELETE')).status, 200);
    assert.equal((await request(`/api/v1/products/${id}`, 'DELETE')).status, 200);
    const active = await request(`/api/v1/products?after_id=${id - 1}`);
    assert.ok(active.data.items.every(item => item.id !== id));
    const all = await request(`/api/v1/products?status=all&after_id=${id - 1}`);
    assert.equal(all.data.items.find(item => item.id === id).is_active, false);
    assert.equal((await request('/api/v1/products?status=all', 'GET', undefined, false)).status, 401);
    assert.equal((await request('/api/v1/products?limit=101')).status, 400);
    assert.equal((await request('/api/v1/products/4294967295', 'PATCH', { price_cents: 1 })).status, 404);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'PATCH', { name: `Renamed-${suffix}` }, false)).status, 401);
    assert.equal((await request('/api/v1/categories/4294967295', 'PATCH', { description: 'Ausente' })).status, 404);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'PATCH', { name: `Renamed-${suffix}`, description: 'Editada' })).status, 200);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'DELETE')).status, 200);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'DELETE')).status, 200);
    assert.equal((await request(`/api/v1/products?after_id=${id - 1}`)).data.items.length, 0);
    assert.equal((await request(`/api/v1/categories/${categoryId}`, 'PATCH', { is_active: true })).status, 200);
    assert.equal((await request(`/api/v1/products?after_id=${id - 1}`)).data.items[0].id, second.data.id);
  } finally {
    for (const id of products) {
      await db.execute('DELETE FROM inventory WHERE product_id = ?', [id]);
      await db.execute('DELETE FROM products WHERE id = ?', [id]);
    }
    if (categoryId) await db.execute('DELETE FROM categories WHERE id = ?', [categoryId]);
    await deleteUser(db, owner);
    await db.end();
  }
});

test('image HTTP lifecycle, authorization, primary concurrency, visibility and limits', async () => {
  const db = await mysql.createConnection(options);
  const suffix = randomBytes(8).toString('hex');
  let owner;
  let productId;
  let imageCategoryId;
  const imageIds = [];
  const png = await sharp({ create: { width: 20, height: 10, channels: 3, background: 'green' } }).png().toBuffer();
  const upload = (bytes = png, type = 'image/png', authorization = headers.Authorization) => fetch(`${base}/api/v1/products/${productId}/images`, {
    method: 'POST', headers: { 'Content-Type': type, ...(authorization ? { Authorization: authorization } : {}) }, body: bytes,
  });
  try {
    owner = await createUser(db, `image-${suffix}`);
    await login(owner.email);
    const category = await request('/api/v1/categories', 'POST', { name: `Imagenes-${suffix}` });
    assert.equal(category.status, 201);
    imageCategoryId = category.data.id;
    const created = await request('/api/v1/products', 'POST', { internal_code: `IMG-${suffix}`, common_name: 'Imagen sintetica', category_id: imageCategoryId, price_cents: 100 });
    assert.equal(created.status, 201);
    productId = created.data.id;
    assert.equal((await upload(png, 'image/png', '')).status, 401);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'SALES') WHERE id = ?", [owner.id]);
    assert.equal((await upload()).status, 403);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'INVENTORY') WHERE id = ?", [owner.id]);
    assert.equal((await upload(Buffer.from('<svg/>'), 'image/svg+xml')).status, 415);
    assert.equal((await upload(Buffer.from('<svg/>'))).status, 400);
    assert.equal((await upload(Buffer.alloc(5 * 1024 * 1024 + 1))).status, 413);
    for (let i = 0; i < 2; i++) {
      const response = await upload();
      assert.equal(response.status, 201);
      imageIds.push((await response.json()).id);
    }
    const path = id => `/api/v1/products/${productId}/images/${id}`;
    const list = () => request(`/api/v1/products/${productId}/images`);
    const initial = await list();
    assert.equal(initial.data.items[0].id, imageIds[0]);
    assert.equal(initial.data.items[0].is_primary, true);
    const file = await fetch(`${base}/api/v1/images/${imageIds[0]}`);
    assert.equal(file.headers.get('content-type'), 'image/webp');
    assert.equal(file.headers.get('cache-control'), 'no-store');
    assert.equal((await sharp(Buffer.from(await file.arrayBuffer())).metadata()).width, 20);
    assert.equal((await request(path(imageIds[0]), 'PATCH', { alt_text: 'Planta de prueba', sort_order: 2 })).status, 200);
    assert.equal((await request(path(imageIds[0]), 'PATCH', { storage_key: '../../secret' })).status, 400);
    // Different sessions ensure serialization comes from the product lock too.
    const secondToken = await login(owner.email);
    const firstToken = await login(owner.email);
    const results = await Promise.all(imageIds.map((id, i) => fetch(base + path(id), { method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${i ? secondToken : firstToken}` }, body: JSON.stringify({ is_primary: true }) })));
    assert.deepEqual(results.map(result => result.status), [200, 200]);
    const primary = (await list()).data.items.filter(item => item.is_primary);
    assert.equal(primary.length, 1);
    const catalog = await request(`/api/v1/products?after_id=${productId - 1}&limit=1`);
    assert.equal(catalog.data.items[0].image.id, primary[0].id);
    await assert.rejects(db.execute('UPDATE product_images SET is_primary = 1 WHERE product_id = ?', [productId]), { code: 'ER_DUP_ENTRY' });
    await assert.rejects(db.execute('UPDATE product_images SET product_id = 4294967295 WHERE id = ?', [imageIds[0]]), { code: 'ER_NO_REFERENCED_ROW_2' });
    await request(`/api/v1/products/${productId}`, 'DELETE');
    assert.equal((await list()).status, 404);
    assert.equal((await fetch(`${base}/api/v1/images/${imageIds[0]}`)).status, 404);
    assert.equal((await fetch(`${base}/api/v1/images/${imageIds[0]}?status=all`, { headers })).status, 200);
    await request(`/api/v1/products/${productId}`, 'PATCH', { is_active: true });
    await db.execute('UPDATE categories SET is_active = 0 WHERE id = ?', [imageCategoryId]);
    assert.equal((await fetch(`${base}/api/v1/images/${imageIds[0]}`)).status, 404);
    await db.execute('UPDATE categories SET is_active = 1 WHERE id = ?', [imageCategoryId]);
    assert.equal((await request(path(primary[0].id), 'DELETE')).status, 200);
    assert.equal((await request(path(primary[0].id), 'DELETE')).status, 200);
    assert.equal((await fetch(`${base}/api/v1/images/${primary[0].id}?status=all`, { headers })).status, 404);
    assert.equal((await list()).data.items.filter(item => item.is_primary).length, 1);
    for (let i = 0; i < 11; i++) {
      const response = await upload();
      assert.equal(response.status, 201);
      imageIds.push((await response.json()).id);
    }
    assert.equal((await upload()).status, 409);
    assert.equal((await list()).data.items.length, 12);
  } finally {
    if (productId) {
      await db.execute('DELETE FROM product_images WHERE product_id = ?', [productId]);
      await db.execute('DELETE FROM products WHERE id = ?', [productId]);
    }
    if (imageCategoryId) await db.execute('DELETE FROM categories WHERE id = ?', [imageCategoryId]);
    await deleteUser(db, owner);
    await db.end();
  }
});

test('promotions: atomic administration, exact SQL pricing, scope, UTC boundaries and permissions', async () => {
  const db = await mysql.createConnection(options);
  const suffix = randomBytes(8).toString('hex');
  let actor;
  const products = [];
  const promotions = [];
  const basePromotion = { name: `Promo-${suffix}`, scope: 'SELECTED_PRODUCTS', promo_type: 'PERCENTAGE', percentage_bps: 1000, product_ids: [] };
  const readPrice = async id => {
    const page = await request(`/api/v1/products?after_id=${id - 1}&limit=1`);
    assert.equal(page.status, 200);
    return page.data.items[0];
  };
  const save = async (data, id) => {
    const result = await request(id ? `/api/v1/promotions/${id}` : '/api/v1/promotions', id ? 'PUT' : 'POST', data);
    assert.equal(result.status, id ? 200 : 201);
    if (!id) promotions.push(result.data.id);
    return result.data.id;
  };
  try {
    actor = await createUser(db, `promo-${suffix}`);
    await login(actor.email);
    for (const price of [105, 10000, Number.MAX_SAFE_INTEGER]) {
      const response = await request('/api/v1/products', 'POST', { internal_code: `PROMO-${suffix}-${products.length}`, common_name: 'Precio sintetico', category_id: 1, price_cents: price });
      assert.equal(response.status, 201);
      products.push(response.data.id);
    }
    const [small, regular, large] = products;
    const input = { ...basePromotion, product_ids: [small] };
    assert.equal((await request('/api/v1/promotions', 'POST', input, false)).status, 401);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'INVENTORY') WHERE id = ?", [actor.id]);
    assert.equal((await request('/api/v1/promotions', 'POST', input)).status, 403);
    assert.equal((await request('/api/v1/promotions')).status, 403);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'MANAGER') WHERE id = ?", [actor.id]);
    const first = await save(input);
    let priced = await readPrice(small);
    assert.equal(priced.price_cents, 105);
    assert.equal(priced.effective_price_cents, 94); // 10.5 cents rounds up.
    assert.equal(priced.active_promotion.discount_percent, 10.48);
    assert.equal((await readPrice(regular)).effective_price_cents, 10000);
    const fixedInput = { ...input, promo_type: 'FIXED_AMOUNT', percentage_bps: null, fixed_amount_cents: 11 };
    const second = await save(fixedInput);
    await db.execute("UPDATE promotions SET created_at = '2020-01-01 00:00:00.000000' WHERE id IN (?, ?)", [first, second]);
    assert.equal((await readPrice(small)).active_promotion.id, first); // Same timestamp: lower ID.
    await db.execute("UPDATE promotions SET created_at = '2019-01-01 00:00:00.000000' WHERE id = ?", [second]);
    assert.equal((await readPrice(small)).active_promotion.id, second);
    await save({ ...fixedInput, fixed_amount_cents: 20 }, second);
    assert.equal((await readPrice(small)).effective_price_cents, 85); // No stacking.
    assert.equal((await request(`/api/v1/promotions/${second}`, 'PUT', { ...fixedInput, product_ids: [4294967295] })).status, 409);
    assert.equal((await readPrice(small)).effective_price_cents, 85); // Failed replacement preserved prior links/value.
    await save({ ...fixedInput, fixed_amount_cents: 500, max_discount_cents: 9 }, second);
    assert.equal((await readPrice(small)).active_promotion.id, first);
    await save({ ...fixedInput, fixed_amount_cents: 500, min_purchase_cents: 106 }, second);
    assert.equal((await readPrice(small)).active_promotion.id, first);
    await save({ ...fixedInput, fixed_amount_cents: 500 }, second);
    assert.equal((await readPrice(small)).effective_price_cents, 0);
    await save({ ...input, product_ids: [regular] }, first);
    await request(`/api/v1/promotions/${second}`, 'DELETE');
    assert.equal((await readPrice(small)).active_promotion, null);
    assert.equal((await readPrice(regular)).effective_price_cents, 9000);
    await save({ ...input, scope: 'ALL_PRODUCTS', product_ids: [] }, first);
    assert.equal((await readPrice(small)).effective_price_cents, 94);
    const admin = await request(`/api/v1/promotions?${first > 1 ? `after_id=${first - 1}&` : ''}limit=1`);
    assert.equal(admin.status, 200);
    assert.equal(admin.data.next_after_id, first);
    assert.deepEqual(admin.data.items[0].product_ids, []);
    await save({ ...input, percentage_bps: 9999, product_ids: [large] }, first);
    const price = BigInt(Number.MAX_SAFE_INTEGER);
    const discount = (price * 9999n + 5000n) / 10000n;
    assert.equal((await readPrice(large)).effective_price_cents, Number(price - discount));
    // Very small effective percentage must not be rounded twice during division.
    await save({ ...fixedInput, fixed_amount_cents: 1, product_ids: [regular] }, first);
    await db.execute('UPDATE products SET price_cents = 20202 WHERE id = ?', [regular]);
    assert.equal((await readPrice(regular)).active_promotion.discount_percent, 0);
    // Freeze only this test connection's SQL clock, never the API/global server.
    await db.query('SET timestamp = 1767225600'); // 2026-01-01T00:00:00Z
    await db.execute("UPDATE promotions SET starts_at = '2026-01-01 00:00:00.000', ends_at = '2026-01-02 00:00:00.000' WHERE id = ?", [first]);
    const localPrice = async () => (await createCatalog(db).list('products', { limit: 1, afterId: regular - 1 }, false)).items[0];
    assert.equal((await localPrice()).effective_price_cents, 20201); // Start inclusive.
    await db.execute("UPDATE promotions SET starts_at = '2025-12-01 00:00:00.000', ends_at = '2026-01-01 00:00:00.000' WHERE id = ?", [first]);
    assert.equal((await localPrice()).effective_price_cents, 20202); // End exclusive.
    await db.execute("UPDATE promotions SET starts_at = '2026-01-02 00:00:00.000', ends_at = NULL WHERE id = ?", [first]);
    assert.equal((await localPrice()).active_promotion, null);
    await db.query('SET timestamp = DEFAULT');
    await save({ ...fixedInput, product_ids: [regular] }, first);
    await db.execute('UPDATE products SET price_cents = 0 WHERE id = ?', [regular]);
    assert.equal((await readPrice(regular)).active_promotion, null);
    await db.execute('UPDATE products SET is_active = 0 WHERE id = ?', [regular]);
    const inactive = await request(`/api/v1/products?status=all&after_id=${regular - 1}&limit=1`);
    assert.equal(inactive.data.items[0].active_promotion, null);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'INVENTORY') WHERE id = ?", [actor.id]);
    assert.equal((await request(`/api/v1/promotions/${first}`, 'DELETE')).status, 403);
    assert.equal((await request(`/api/v1/promotions/${first}`, 'PUT', input)).status, 403);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'MANAGER') WHERE id = ?", [actor.id]);
    assert.equal((await request(`/api/v1/promotions/${first}`, 'DELETE')).status, 200);
    assert.equal((await request(`/api/v1/promotions/${first}`, 'DELETE')).status, 200);
    assert.equal((await request('/api/v1/promotions/4294967295', 'PUT', input)).status, 404);
    await assert.rejects(db.execute('UPDATE promotions SET percentage_bps = 10001, promo_type = ?, fixed_amount_cents = NULL WHERE id = ?', ['PERCENTAGE', first]));
    await assert.rejects(db.execute('INSERT INTO promotion_products (promotion_id, product_id) VALUES (?, 4294967295)', [first]), { code: 'ER_NO_REFERENCED_ROW_2' });
  } finally {
    for (const id of promotions) {
      await db.execute('DELETE FROM promotion_products WHERE promotion_id = ?', [id]);
      await db.execute('DELETE FROM promotions WHERE id = ?', [id]);
    }
    for (const id of products) await db.execute('DELETE FROM products WHERE id = ?', [id]);
    await deleteUser(db, actor);
    await db.end();
  }
});

test('catalog import: read-only preview, repeatable mappings, conflict detection and atomic rollback', async () => {
  const db = await mysql.createConnection(options);
  const source = `test-import-${randomBytes(6).toString('hex')}`;
  const input = JSON.parse(await readFile(new URL('./fixtures/catalog-import-demo.json', import.meta.url), 'utf8'));
  input.source_key = source;
  input.categories[0].name = source;
  input.products[0].internal_code = source;
  const tableState = async () => {
    const [tables] = await db.execute("SELECT table_name, auto_increment FROM information_schema.tables WHERE table_schema = 'vivero' AND table_name IN ('categories', 'products', 'catalog_category_sources', 'catalog_product_sources') ORDER BY table_name");
    const [[counts]] = await db.execute('SELECT (SELECT COUNT(*) FROM categories) AS categories, (SELECT COUNT(*) FROM products) AS products, (SELECT COUNT(*) FROM catalog_category_sources) AS category_maps, (SELECT COUNT(*) FROM catalog_product_sources) AS product_maps');
    return { tables, counts };
  };
  let categoryId;
  let productId;
  try {
    const before = await tableState();
    const preview = await importCatalog(db, input);
    assert.equal(preview.can_apply, true);
    assert.deepEqual(preview.items.map(item => item.action), ['create', 'create']);
    assert.deepEqual(await tableState(), before); // Includes AUTO_INCREMENT: no insert/rollback simulation.
    const applied = await importCatalog(db, input, { apply: true });
    assert.equal(applied.committed, true);
    [categoryId, productId] = applied.items.map(item => item.target_id);
    assert.ok(Number.isInteger(categoryId) && Number.isInteger(productId));
    const [[product]] = await db.execute('SELECT category_id, price_cents FROM products WHERE id = ?', [productId]);
    assert.equal(product.category_id, categoryId);
    assert.equal(Number(product.price_cents), 12000);
    const [[fingerprint]] = await db.execute('SELECT OCTET_LENGTH(source_hash) AS bytes FROM catalog_product_sources WHERE product_id = ?', [productId]);
    assert.equal(fingerprint.bytes, 32);
    const after = await tableState();
    const repeat = await importCatalog(db, input, { apply: true });
    assert.deepEqual(repeat.items.map(item => [item.action, item.target_id]), [['reuse', categoryId], ['reuse', productId]]);
    assert.deepEqual(await tableState(), after);
    const changed = structuredClone(input);
    changed.products[0].price_cents++;
    const conflict = await importCatalog(db, changed, { apply: true });
    assert.equal(conflict.can_apply, false);
    assert.equal(conflict.items[1].code, 'SOURCE_CHANGED');
    await db.execute("UPDATE products SET common_name = 'Edicion posterior' WHERE id = ?", [productId]);
    assert.equal((await importCatalog(db, input)).items[1].code, 'TARGET_CHANGED');
    await db.execute('UPDATE products SET common_name = ? WHERE id = ?', [input.products[0].common_name, productId]);
    const otherSource = structuredClone(input);
    otherSource.source_key += '-other';
    const collision = await importCatalog(db, otherSource, { apply: true });
    assert.equal(collision.can_apply, false);
    assert.equal(collision.items[0].code, 'UNMAPPED_BUSINESS_KEY_COLLISION');
    const duplicate = structuredClone(input);
    duplicate.source_key += '-duplicates';
    duplicate.categories = [
      { ...input.categories[0], name: `${source}-Árbol` },
      { ...input.categories[0], id: '10000000-0000-4000-8000-000000000002', name: `${source}-arbol` },
    ];
    duplicate.products = [];
    assert.equal((await importCatalog(db, duplicate)).items[0].code, 'DUPLICATE_BUSINESS_KEY_IN_BATCH');
    const fresh = structuredClone(input);
    fresh.source_key += '-rollback';
    fresh.categories[0].name += '-rollback';
    fresh.products[0].internal_code += '-rollback';
    // Inject a failure after the category/map INSERTs, on the product INSERT.
    const failingDb = {
      query: (...args) => db.query(...args), rollback: () => db.rollback(), commit: () => db.commit(),
      execute: (sql, args) => sql.startsWith('INSERT INTO products ') ? Promise.reject(new Error('synthetic failure')) : db.execute(sql, args),
    };
    const [[countsBefore]] = await db.execute('SELECT COUNT(*) AS total FROM categories');
    await assert.rejects(importCatalog(failingDb, fresh, { apply: true }), { code: 'IMPORT_FAILED_NO_PARTIAL_COMMIT' });
    const [[countsAfter]] = await db.execute('SELECT COUNT(*) AS total FROM categories');
    assert.deepEqual(countsAfter, countsBefore);
    const [[maps]] = await db.execute('SELECT COUNT(*) AS total FROM catalog_category_sources WHERE source_key = ?', [fresh.source_key]);
    assert.equal(Number(maps.total), 0);
    await assert.rejects(db.execute('UPDATE catalog_product_sources SET product_id = 4294967295 WHERE product_id = ?', [productId]), { code: 'ER_NO_REFERENCED_ROW_2' });
  } finally {
    await db.execute('DELETE FROM catalog_product_sources WHERE source_key = ?', [source]);
    await db.execute('DELETE FROM catalog_category_sources WHERE source_key = ?', [source]);
    if (productId) await db.execute('DELETE FROM products WHERE id = ?', [productId]);
    if (categoryId) await db.execute('DELETE FROM categories WHERE id = ?', [categoryId]);
    await db.end();
  }
});

test('promotion imports preserve UUID tie order across batches, microseconds, selections and rollback', async () => {
  const db = await mysql.createConnection(options);
  const source = `promo-import-${randomBytes(5).toString('hex')}`;
  const catalog = JSON.parse(await readFile(new URL('./fixtures/catalog-import-demo.json', import.meta.url), 'utf8'));
  catalog.source_key = source;
  catalog.categories[0].name = source;
  catalog.products[0].internal_code = source;
  const input = JSON.parse(await readFile(new URL('./fixtures/promotion-import-demo.json', import.meta.url), 'utf8'));
  input.source_key = source;
  const low = structuredClone(input);
  const high = structuredClone(input);
  high.promotions[0].id = 'f0000000-0000-4000-8000-000000000001';
  high.promotions[0].name = 'Empate alto';
  const importedIds = [];
  let categoryId;
  let productId;
  let actor;
  const state = async () => (await db.execute("SELECT table_name, auto_increment FROM information_schema.tables WHERE table_schema = 'vivero' AND table_name IN ('promotions', 'promotion_products', 'catalog_promotion_sources') ORDER BY table_name"))[0];
  try {
    const absent = await importPromotions(db, high);
    assert.equal(absent.can_apply, false);
    assert.equal(absent.items[0].code, 'PRODUCT_MAPPING_REQUIRED');
    const imported = await importCatalog(db, catalog, { apply: true });
    [categoryId, productId] = imported.items.map(item => item.target_id);
    const before = await state();
    assert.equal((await importPromotions(db, high)).items[0].action, 'create');
    assert.deepEqual(await state(), before);
    const highId = (await importPromotions(db, high, { apply: true })).items[0].target_id;
    importedIds.push(highId);
    const lowId = (await importPromotions(db, low, { apply: true })).items[0].target_id;
    importedIds.push(lowId);
    assert.ok(lowId > highId); // Arrival order intentionally opposes the source UUID.
    const page = await request(`/api/v1/products?after_id=${productId - 1}&limit=1`);
    assert.equal(page.status, 200);
    assert.equal(page.data.items[0].active_promotion.id, lowId);
    assert.equal(page.data.items[0].effective_price_cents, 10500);
    const repeatBefore = await state();
    assert.equal((await importPromotions(db, low, { apply: true })).items[0].action, 'reuse');
    assert.deepEqual(await state(), repeatBefore);
    const changed = structuredClone(low);
    changed.promotions[0].value = '15.00';
    assert.equal((await importPromotions(db, changed, { apply: true })).items[0].code, 'SOURCE_CHANGED');
    await db.execute('UPDATE promotion_products SET is_active = 0 WHERE promotion_id = ?', [lowId]);
    assert.equal((await importPromotions(db, low)).items[0].code, 'TARGET_SELECTION_CHANGED');
    await db.execute('UPDATE promotion_products SET is_active = 1 WHERE promotion_id = ?', [lowId]);
    await db.execute("UPDATE promotions SET name = 'Cambio local' WHERE id = ?", [lowId]);
    assert.equal((await importPromotions(db, low)).items[0].code, 'TARGET_CHANGED');
    await db.execute('UPDATE promotions SET name = ? WHERE id = ?', [low.promotions[0].name, lowId]);
    const timed = structuredClone(input);
    timed.promotions[0].id = '40000000-0000-4000-8000-000000000001';
    timed.promotions[0].value = '50.00';
    timed.promotions[0].starts_at = '2026-01-01T00:00:00.000123Z';
    timed.promotions[0].ends_at = '2026-01-01T00:00:00.000789Z';
    const timedId = (await importPromotions(db, timed, { apply: true })).items[0].target_id;
    importedIds.push(timedId);
    const [[dates]] = await db.execute("SELECT DATE_FORMAT(created_at, '%f') AS created, DATE_FORMAT(starts_at, '%f') AS starts, DATE_FORMAT(ends_at, '%f') AS ends FROM promotions WHERE id = ?", [timedId]);
    assert.deepEqual(dates, { created: '000123', starts: '000123', ends: '000789' });
    await db.query('SET timestamp = 1767225600.000123');
    const localPrice = async () => (await createCatalog(db).list('products', { limit: 1, afterId: productId - 1 }, false)).items[0];
    assert.equal((await localPrice()).active_promotion.id, timedId);
    await db.query('SET timestamp = 1767225600.000789');
    assert.equal((await localPrice()).active_promotion.id, lowId);
    await db.query('SET timestamp = DEFAULT');
    // Admin read-edit round trip must retain imported microseconds.
    actor = await createUser(db, `import-dates-${randomBytes(4).toString('hex')}`);
    await login(actor.email);
    const admin = await request(`/api/v1/promotions?after_id=${timedId - 1}&limit=1`);
    assert.equal(admin.status, 200);
    const { id: _id, created_at, updated_at: _updated, ...replacement } = admin.data.items[0];
    assert.equal(created_at, timed.promotions[0].created_at);
    assert.equal(replacement.starts_at, timed.promotions[0].starts_at);
    assert.equal((await request(`/api/v1/promotions/${timedId}`, 'PUT', replacement)).status, 200);
    assert.equal((await importPromotions(db, timed)).items[0].action, 'reuse');
    const rollback = structuredClone(input);
    rollback.promotions[0].id = '50000000-0000-4000-8000-000000000001';
    const [[countsBefore]] = await db.execute('SELECT COUNT(*) AS total FROM promotions');
    const failingDb = {
      query: (...args) => db.query(...args), rollback: () => db.rollback(), commit: () => db.commit(),
      execute: (sql, args) => sql.startsWith('INSERT INTO catalog_promotion_sources ') ? Promise.reject(new Error('synthetic failure')) : db.execute(sql, args),
    };
    await assert.rejects(importPromotions(failingDb, rollback, { apply: true }), { code: 'IMPORT_FAILED_NO_PARTIAL_COMMIT' });
    assert.deepEqual((await db.execute('SELECT COUNT(*) AS total FROM promotions'))[0][0], countsBefore);
    const global = structuredClone(input);
    global.promotions[0] = { ...global.promotions[0], id: '60000000-0000-4000-8000-000000000001', scope: 'ALL_PRODUCTS', product_ids: [], is_active: false };
    const globalId = (await importPromotions(db, global, { apply: true })).items[0].target_id;
    importedIds.push(globalId);
    assert.equal((await importPromotions(db, global)).items[0].action, 'reuse');
    await assert.rejects(db.execute('UPDATE catalog_promotion_sources SET promotion_id = 4294967295 WHERE promotion_id = ?', [lowId]), { code: 'ER_NO_REFERENCED_ROW_2' });
  } finally {
    await db.query('SET timestamp = DEFAULT');
    await db.execute('DELETE FROM catalog_promotion_sources WHERE source_key = ?', [source]);
    for (const id of importedIds) {
      await db.execute('DELETE FROM promotion_products WHERE promotion_id = ?', [id]);
      await db.execute('DELETE FROM promotions WHERE id = ?', [id]);
    }
    await db.execute('DELETE FROM catalog_product_sources WHERE source_key = ?', [source]);
    await db.execute('DELETE FROM catalog_category_sources WHERE source_key = ?', [source]);
    if (productId) await db.execute('DELETE FROM products WHERE id = ?', [productId]);
    if (categoryId) await db.execute('DELETE FROM categories WHERE id = ?', [categoryId]);
    await deleteUser(db, actor);
    await db.end();
  }
});

test('image import reconciles files, preserves IDs and rolls back SQL without deleting uncertain files', async () => {
  const db = await mysql.createConnection(options);
  const { mkdtemp, rm } = await import('node:fs/promises');
  const directory = await mkdtemp('/tmp/vivero-import-test-');
  const store = createImageStore(directory);
  const input = JSON.parse(await readFile('test/fixtures/image-import-demo.json', 'utf8'));
  const catalog = JSON.parse(await readFile('test/fixtures/catalog-import-demo.json', 'utf8'));
  const suffix = randomBytes(8).toString('hex');
  input.source_key = catalog.source_key = `images-${suffix}`;
  catalog.categories[0].name = `Images ${suffix}`;
  catalog.products[0].internal_code = `IMG-${suffix}`;
  const config = { directory: 'test/fixtures/images', store };
  let productId, categoryId;
  try {
    assert.equal((await importImages(db, input, config)).items[0].code, 'PRODUCT_MAPPING_REQUIRED');
    const created = await importCatalog(db, catalog, { apply: true });
    categoryId = created.items[0].target_id; productId = created.items[1].target_id;
    let puts = 0;
    const observed = { ...store, async put(data) { puts++; return store.put(data); } };
    const preview = await importImages(db, input, { ...config, store: observed });
    assert.equal(preview.can_apply, true); assert.equal(puts, 0);
    const result = await importImages(db, input, { ...config, store: observed, apply: true });
    assert.equal(result.committed, true); assert.equal(puts, 1);
    const id = result.items[0].target_id;
    assert.equal((await importImages(db, input, { ...config, store: observed, apply: true })).items[0].target_id, id);
    assert.equal(puts, 1);
    await assert.rejects(db.execute('DELETE FROM product_images WHERE id = ?', [id]), { code: 'ER_ROW_IS_REFERENCED_2' });
    const changed = structuredClone(input); changed.images[0].alt_text = 'Changed';
    assert.equal((await importImages(db, changed, config)).can_apply, false);
    const bad = structuredClone(input); bad.images[0].sha256 = '0'.repeat(64);
    await assert.rejects(importImages(db, bad, config), { code: 'SOURCE_FILE_HASH_MISMATCH' });
    const missing = structuredClone(input); missing.images[0].file = 'missing.png';
    await assert.rejects(importImages(db, missing, config), { code: 'SOURCE_FILE_UNREADABLE' });
    assert.equal((await importImages(db, input, { ...config, store: { ...store, get: async () => Buffer.from('corrupt') } })).items[0].code, 'TARGET_FILE_CHANGED');
    assert.equal((await importImages(db, input, { ...config, store: { ...store, get: async () => { throw new Error(); } } })).items[0].code, 'TARGET_FILE_UNREADABLE');
    await db.execute('UPDATE product_images SET alt_text = ? WHERE id = ?', ['Local edit', id]);
    assert.equal((await importImages(db, input, config)).items[0].code, 'TARGET_CHANGED');
    await db.execute('UPDATE product_images SET alt_text = ? WHERE id = ?', [input.images[0].alt_text, id]);
    const incomplete = structuredClone(input); incomplete.images[0].id = '40000000-0000-4000-8000-000000000002';
    assert.equal((await importImages(db, incomplete, { ...config, apply: true })).items[0].code, 'UNLISTED_ACTIVE_IMAGE');
    const expanded = structuredClone(input);
    expanded.images.push({ ...input.images[0], id: '40000000-0000-4000-8000-000000000002', is_primary: false, sort_order: 1 });
    const failingDb = {
      query: db.query.bind(db), rollback: db.rollback.bind(db), commit: db.commit.bind(db),
      execute(sql, values) {
        if (sql.startsWith('INSERT INTO catalog_image_sources')) throw new Error('injected');
        return db.execute(sql, values);
      },
    };
    await assert.rejects(importImages(failingDb, expanded, { ...config, apply: true }), { code: 'IMPORT_FAILED_NO_PARTIAL_COMMIT' });
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM product_images WHERE product_id = ?', [productId]);
    assert.equal(Number(count.n), 1);
    assert.equal((await importImages(db, input, config)).can_apply, true);
  } finally {
    if (productId) {
      await db.execute('DELETE FROM catalog_image_sources WHERE source_key = ?', [input.source_key]);
      await db.execute('DELETE FROM product_images WHERE product_id = ?', [productId]);
      await db.execute('DELETE FROM catalog_product_sources WHERE source_key = ?', [input.source_key]);
      await db.execute('DELETE FROM products WHERE id = ?', [productId]);
      await db.execute('DELETE FROM catalog_category_sources WHERE source_key = ?', [input.source_key]);
      await db.execute('DELETE FROM categories WHERE id = ?', [categoryId]);
    }
    await db.end();
    // Only this test's mkdtemp directory; never the persistent API volume.
    await rm(directory, { recursive: true, force: true });
  }
});

test('web orders quote server prices, serialize retries, recover receipts and roll back failed items', async () => {
  const db = await mysql.createConnection(options);
  const pool = mysql.createPool({ ...options, user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD, connectionLimit: 4 });
  const suffix = randomBytes(8).toString('hex');
  let branchId, categoryId, productId, promoId;
  let orderManager;
  const call = async (path, data, key) => {
    const response = await fetch(base + '/api/v1/web-orders' + path, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...(key ? { 'Idempotency-Key': key } : {}) }, body: JSON.stringify(data),
    });
    return { status: response.status, data: await response.json() };
  };
  try {
    [ { insertId: branchId } ] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`WO-${suffix}`, 'Sucursal sintetica']);
    [ { insertId: categoryId } ] = await db.execute('INSERT INTO categories (name) VALUES (?)', [`WO-${suffix}`]);
    [ { insertId: productId } ] = await db.execute('INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES (?, ?, ?, 105)', [`WO-${suffix}`, 'Producto sintetico', categoryId]);
    [ { insertId: promoId } ] = await db.execute("INSERT INTO promotions (name, scope, promo_type, percentage_bps) VALUES ('Demo pedido', 'SELECTED_PRODUCTS', 'PERCENTAGE', 1000)");
    await db.execute('INSERT INTO promotion_products (promotion_id, product_id) VALUES (?, ?)', [promoId, productId]);
    const quoteInput = { branch_id: branchId, items: [{ product_id: productId, quantity: 3 }] };
    const optionsResponse = await request('/api/v1/web-orders/options', 'GET', undefined, false);
    assert.ok(optionsResponse.data.branches.some(branch => branch.id === branchId));
    const quoted = await call('/quote', quoteInput);
    assert.equal(quoted.status, 200); assert.equal(quoted.data.total_cents, 282);
    assert.equal(quoted.data.subtotal_cents, 315); assert.equal(quoted.data.discount_cents, 33);
    assert.equal(quoted.data.items[0].promotion_id, promoId);
    assert.equal((await call('/quote', { ...quoteInput, items: [{ product_id: productId, quantity: 3, price_cents: 1 }] })).status, 400);
    assert.equal((await call('/quote', { ...quoteInput, branch_id: 4294967295 })).status, 409);
    const input = { ...quoteInput, customer_name: 'Cliente sintetico', customer_phone: null,
      customer_email: `${suffix}@example.invalid`, notes: null, expected_total_cents: 282 };
    const key = randomBytes(32).toString('hex');
    assert.equal((await call('', input)).status, 400);
    assert.equal((await call('/recover', {}, key)).status, 404);
    assert.equal((await call('', { ...input, expected_total_cents: 1 }, key)).data.error, 'WEB_ORDER_PRICE_CHANGED');
    const results = await Promise.all([call('', input, key), call('', input, key), call('', input, key)]);
    assert.deepEqual(results.map(row => row.status).sort(), [200, 200, 201]);
    assert.equal(new Set(results.map(row => row.data.id)).size, 1);
    const receipt = results.find(row => row.status === 201).data;
    assert.equal(receipt.status, 'PENDING');
    assert.equal(Object.hasOwn(receipt, 'customer_email'), false);
    assert.equal((await call('', { ...input, notes: 'changed' }, key)).data.error, 'WEB_ORDER_IDEMPOTENCY_CONFLICT');
    const [[count]] = await db.execute('SELECT COUNT(*) AS n FROM web_orders WHERE branch_id = ?', [branchId]);
    assert.equal(Number(count.n), 1);
    const [[item]] = await db.execute('SELECT unit_price_cents, line_total_cents FROM web_order_items WHERE order_id = ?', [receipt.id]);
    assert.equal(Number(item.unit_price_cents), 94); assert.equal(Number(item.line_total_cents), 282);
    await db.execute('UPDATE branches SET is_active = 0 WHERE id = ?', [branchId]);
    await db.execute('UPDATE products SET price_cents = 900 WHERE id = ?', [productId]);
    await db.execute('UPDATE products SET common_name = ? WHERE id = ?', ['Nombre posterior', productId]);
    const beforeTicket = await db.execute(`SELECT
      (SELECT COUNT(*) FROM web_orders) AS orders,
      (SELECT COUNT(*) FROM sales) AS sales,
      (SELECT COUNT(*) FROM cashier_payments) AS payments,
      (SELECT COUNT(*) FROM inventory_movements) AS movements`);
    const ticket = await call('/ticket', {}, key);
    assert.equal(ticket.status, 200);
    assert.deepEqual(ticket.data.order, { ...receipt, idempotent_replay: true });
    assert.deepEqual(ticket.data.branch, { id: branchId, code: `WO-${suffix}`, name: 'Sucursal sintetica' });
    assert.deepEqual(ticket.data.items, [{ product_id: productId, product_name: 'Producto sintetico', quantity: 3,
      list_price_cents: 105, unit_price_cents: 94, line_total_cents: 282 }]);
    assert.equal(ticket.data.subtotal_cents, 315); assert.equal(ticket.data.discount_cents, 33);
    assert.equal(Object.hasOwn(ticket.data, 'customer_email'), false);
    for (let replay = 0; replay < 3; replay++) assert.deepEqual((await call('/ticket', {}, key)).data, ticket.data);
    const [afterTicket] = await db.execute(`SELECT
      (SELECT COUNT(*) FROM web_orders) AS orders,
      (SELECT COUNT(*) FROM sales) AS sales,
      (SELECT COUNT(*) FROM cashier_payments) AS payments,
      (SELECT COUNT(*) FROM inventory_movements) AS movements`);
    assert.deepEqual(afterTicket, beforeTicket[0]);
    assert.equal((await call('/ticket', {}, randomBytes(32).toString('hex'))).status, 404);
    assert.equal((await call('/ticket', {})).status, 400);
    assert.equal((await call('/ticket', {}, String(receipt.id))).status, 400);
    assert.equal((await call('/ticket', { id: receipt.id }, key)).status, 400);
    assert.equal((await request(`/api/v1/web-orders/ticket?id=${receipt.id}`, 'GET', undefined, false)).status, 400);
    assert.equal((await request(`/api/v1/web-orders/${receipt.id}/ticket`, 'GET', undefined, false)).status, 404);
    assert.equal((await call('', input, key)).data.total_cents, 282);
    assert.deepEqual((await call('/recover', {}, key)).data, { ...receipt, idempotent_replay: true });
    assert.equal((await request(`/api/v1/web-orders/${receipt.id}`, 'GET', undefined, false)).status, 404);
    assert.equal((await call('/quote', quoteInput)).data.error, 'WEB_ORDER_BRANCH_UNAVAILABLE');
    await db.execute('UPDATE branches SET is_active = 1 WHERE id = ?', [branchId]);
    await db.execute('UPDATE products SET price_cents = 105 WHERE id = ?', [productId]);
    await db.execute('UPDATE categories SET is_active = 0 WHERE id = ?', [categoryId]);
    assert.equal((await call('/quote', quoteInput)).data.error, 'WEB_ORDER_ITEMS_UNAVAILABLE');
    await db.execute('UPDATE categories SET is_active = 1 WHERE id = ?', [categoryId]);
    await db.execute('UPDATE products SET price_cents = 9007199254740991 WHERE id = ?', [productId]);
    assert.equal((await call('/quote', quoteInput)).data.error, 'WEB_ORDER_TOTAL_INVALID');
    await db.execute('UPDATE products SET price_cents = 105 WHERE id = ?', [productId]);
    const limited = await Promise.all(Array.from({ length: 3 }, () => call('', input, randomBytes(32).toString('hex'))));
    assert.deepEqual(limited.map(row => row.status).sort(), [201, 201, 429]);
    assert.equal((await call('', input, randomBytes(32).toString('hex'))).data.error, 'WEB_ORDER_RATE_LIMITED');
    assert.equal((await call('', input, key)).status, 200); // Replays do not consume budget.
    const wrap = mode => ({ async getConnection() {
      const connection = await pool.getConnection();
      return {
        query: connection.query.bind(connection), beginTransaction: connection.beginTransaction.bind(connection),
        rollback: connection.rollback.bind(connection), release: connection.release.bind(connection), destroy: connection.destroy.bind(connection),
        execute(sql, params) {
          if (mode === 'item' && sql.startsWith('INSERT INTO web_order_items')) throw new Error('injected item failure');
          return connection.execute(sql, params);
        },
        async commit() { await connection.commit(); if (mode === 'commit') throw new Error('lost commit acknowledgment'); },
      };
    } });
    const other = { ...input, customer_email: `other-${suffix}@example.invalid` };
    const failureKey = randomBytes(32).toString('hex');
    await assert.rejects(createWebOrders(wrap('item')).submit(other, failureKey), /injected item failure/);
    assert.equal((await call('/recover', {}, failureKey)).status, 404);
    const uncertainKey = randomBytes(32).toString('hex');
    await assert.rejects(createWebOrders(wrap('commit')).submit(other, uncertainKey), { code: 'WEB_ORDER_RESULT_UNCERTAIN' });
    assert.equal((await call('/recover', {}, uncertainKey)).status, 200);
    assert.equal((await call('', other, uncertainKey)).status, 200);
    orderManager = await createUser(db, `orders-${suffix}`, 'MANAGER');
    await db.execute('UPDATE users SET branch_id = ? WHERE id = ?', [branchId, orderManager.id]);
    await login(orderManager.email);
    const adminPath = '/api/v1/admin/web-orders';
    assert.equal((await request(adminPath, 'GET', undefined, false)).status, 401);
    const listing = await request(`${adminPath}?limit=1`);
    assert.equal(listing.status, 200); assert.equal(listing.data.items.length, 1);
    assert.ok(listing.data.next_before_id);
    assert.ok(listing.data.items.every(row => row.branch_id === branchId));
    assert.equal(Object.hasOwn(listing.data.items[0], 'request_hash'), false);
    const next = await request(`${adminPath}?limit=1&before_id=${listing.data.next_before_id}`);
    assert.ok(next.data.items[0].id < listing.data.items[0].id);
    assert.equal((await request(`${adminPath}?branch_id=1`)).status, 403);
    const detail = await request(`${adminPath}/${receipt.id}`);
    assert.equal(detail.data.history.length, 1);
    assert.equal(detail.data.history[0].changed_by, null);
    assert.equal(detail.data.order.customer_name, input.customer_name);
    const change = { expected_revision: 0, status: 'CONFIRMED', observation: 'Confirmado' };
    const changed = await Promise.all([request(`${adminPath}/${receipt.id}`, 'PATCH', change), request(`${adminPath}/${receipt.id}`, 'PATCH', change)]);
    assert.ok(changed.every(row => row.status === 200));
    assert.deepEqual(changed.map(row => row.data.idempotent_replay).sort(), [false, true]);
    assert.equal((await request(`${adminPath}/${receipt.id}`)).data.history.length, 2);
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', { ...change, status: 'CANCELLED' })).status, 409);
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', { expected_revision: 1, status: 'READY', observation: null })).status, 200);
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', { expected_revision: 2, status: 'COMPLETED', observation: null })).data.error, 'WEB_ORDER_PAYMENT_REQUIRED');
    const connection = await pool.getConnection();
    try {
      await connection.beginTransaction();
      const failing = { execute(sql, values) {
        if (sql.startsWith('INSERT INTO web_order_status_history')) throw new Error('history failure');
        return connection.execute(sql, values);
      } };
      await assert.rejects(createOrderAdmin(failing, { access_state: 'ACTIVE', capabilities: ['VIEW_BRANCH_SALES'], branch: { id: branchId, is_active: true }, user: { id: orderManager.id } })
        .update(receipt.id, { expected_revision: 2, status: 'CANCELLED', observation: null }), /history failure/);
      await connection.rollback();
    } finally { connection.release(); }
    assert.equal((await request(`${adminPath}/${receipt.id}`)).data.order.status, 'READY');
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', { expected_revision: 2, status: 'CANCELLED', observation: null })).status, 200);
    assert.equal((await call('/recover', {}, key)).data.status, 'CANCELLED');
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', { expected_revision: 3, status: 'CONFIRMED', observation: null })).status, 409);
    await db.execute('UPDATE users SET branch_id = 1 WHERE id = ?', [orderManager.id]);
    assert.equal((await request(`${adminPath}/${receipt.id}`)).status, 404);
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', change)).status, 404);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'OWNER') WHERE id = ?", [orderManager.id]);
    assert.equal((await request(`${adminPath}/${receipt.id}`)).status, 200);
    assert.equal((await request(`${adminPath}/${receipt.id}`, 'PATCH', change)).status, 404); // Global read does not bypass write branch.
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'SALES') WHERE id = ?", [orderManager.id]);
    assert.equal((await request(adminPath)).status, 403);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'MANAGER'), branch_id = ? WHERE id = ?", [branchId, orderManager.id]);
    await db.execute('UPDATE branches SET is_active = 0 WHERE id = ?', [branchId]);
    assert.equal((await request(adminPath)).status, 403);
    const [[sales]] = await db.execute('SELECT COUNT(*) AS n FROM sales WHERE branch_id = ?', [branchId]);
    assert.equal(Number(sales.n), 0);
  } finally {
    if (branchId) {
      await db.execute('DELETE h FROM web_order_status_history h JOIN web_orders o ON o.id = h.order_id WHERE o.branch_id = ?', [branchId]);
      await db.execute('DELETE i FROM web_order_items i JOIN web_orders o ON o.id = i.order_id WHERE o.branch_id = ?', [branchId]);
      await db.execute('DELETE FROM web_orders WHERE branch_id = ?', [branchId]);
    }
    if (promoId) { await db.execute('DELETE FROM promotion_products WHERE promotion_id = ?', [promoId]); await db.execute('DELETE FROM promotions WHERE id = ?', [promoId]); }
    if (productId) await db.execute('DELETE FROM products WHERE id = ?', [productId]);
    if (categoryId) await db.execute('DELETE FROM categories WHERE id = ?', [categoryId]);
    await deleteUser(db, orderManager);
    if (branchId) await db.execute('DELETE FROM branches WHERE id = ?', [branchId]);
    await pool.end(); await db.end();
  }
});

test('schema enforces foreign keys, money, uniqueness and runtime least privilege', async () => {
  const db = await mysql.createConnection(options);
  const runtime = await mysql.createConnection({ ...options, user: 'catalog_api', password: process.env.CATALOG_DB_PASSWORD });
  try {
    const [tables] = await db.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'vivero'");
    assert.equal(tables.length, 61); // Canonical schema through migration 032.
    const [[migration]] = await db.execute("SELECT COUNT(*) AS n FROM schema_migrations WHERE version='030_purchase_draft_retirement'");
    assert.equal(Number(migration.n), 1);
    await assert.rejects(runtime.execute('DELETE FROM payment_attempt_retirements WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('UPDATE payment_attempt_retirements SET sale_id = 1 WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM web_orders WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('UPDATE web_orders SET total_cents = 1 WHERE id = 0'), { code: 'ER_COLUMNACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM web_order_status_history WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('SELECT * FROM catalog_image_sources'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('UPDATE sales SET total_cents = 1 WHERE id = 0'), { code: 'ER_COLUMNACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM sales WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await runtime.execute('UPDATE users SET role_id = 6 WHERE id = 0'); // Administrative API owns role mutations.
    await assert.rejects(runtime.execute('UPDATE users SET full_name = full_name WHERE id = 0'), { code: 'ER_COLUMNACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('UPDATE users SET email = email WHERE id = 0'), { code: 'ER_COLUMNACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM administration_changes WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM role_permissions WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM products WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM product_images WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('DELETE FROM promotions WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('SELECT * FROM catalog_category_sources'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('SELECT * FROM catalog_product_sources'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await runtime.execute('SELECT promotion_id, source_id FROM catalog_promotion_sources LIMIT 1');
    await assert.rejects(runtime.execute('SELECT source_hash FROM catalog_promotion_sources'), { code: 'ER_COLUMNACCESS_DENIED_ERROR' });
    await assert.rejects(runtime.execute('UPDATE catalog_promotion_sources SET source_id = source_id WHERE id = 0'), { code: 'ER_TABLEACCESS_DENIED_ERROR' });
    await db.beginTransaction();
    await assert.rejects(db.execute("INSERT INTO products (internal_code, common_name, category_id, price_cents) VALUES ('FK-TEST', 'Prueba', 4294967295, 1)"), { code: 'ER_NO_REFERENCED_ROW_2' });
    await assert.rejects(db.execute('UPDATE products SET price_cents = -1 WHERE id = 1'));
    await assert.rejects(db.execute('UPDATE products SET wholesale_price_cents = -1 WHERE id = 1'));
    await assert.rejects(db.execute('UPDATE products SET wholesale_price_cents = 9007199254740992 WHERE id = 1'));
    await assert.rejects(db.execute('DELETE FROM categories WHERE id = 1'), { code: 'ER_ROW_IS_REFERENCED_2' });
    await assert.rejects(db.execute('INSERT INTO inventory (branch_id, product_id, quantity) VALUES (1, 1, 0)'), { code: 'ER_DUP_ENTRY' });
    await assert.rejects(db.execute('UPDATE inventory SET quantity = -1 WHERE id = 1'));
    const [sale] = await db.execute("INSERT INTO sales (folio, branch_id, created_by, subtotal_cents, total_cents) VALUES ('TEST-SALE', 1, 1, 100, 100)");
    await assert.rejects(db.execute('UPDATE sales SET total_cents = 99 WHERE id = ?', [sale.insertId]));
    await db.execute("INSERT INTO cashier_payments (sale_id, cashier_id, idempotency_key, method, amount_due_cents, amount_received_cents, change_cents) VALUES (?, 1, 'test-payment-key-001', 'CASH', 100, 100, 0)", [sale.insertId]);
    await assert.rejects(db.execute("INSERT INTO cashier_payments (sale_id, cashier_id, idempotency_key, method, amount_due_cents, amount_received_cents, change_cents) VALUES (?, 1, 'test-payment-key-002', 'CASH', 100, 100, 0)", [sale.insertId]), { code: 'ER_DUP_ENTRY' });
    await assert.rejects(db.execute('INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price_cents, line_total_cents) VALUES (?, 1, ?, 0, 100, 0)', [sale.insertId, 'Prueba']));
  } finally {
    await db.rollback();
    await runtime.end();
    await db.end();
  }
});

test('real sessions: current permissions, expiry, logout and account/password revocation', async () => {
  const db = await mysql.createConnection(options);
  const suffix = randomBytes(8).toString('hex');
  const users = [];
  let branchId;
  try {
    const owner = await createUser(db, `${suffix}-owner`);
    users.push(owner);
    const [branch] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [`T-${suffix}`, 'Sucursal sintetica']);
    branchId = branch.insertId;
    await db.execute('UPDATE users SET branch_id = ? WHERE id = ?', [branchId, owner.id]);
    const inactive = await createUser(db, `${suffix}-inactive`, 'OWNER', false);
    users.push(inactive);
    const noRole = await createUser(db, `${suffix}-norole`, null);
    users.push(noRole);
    for (const email of [owner.email, inactive.email, `missing-${suffix}@example.invalid`]) {
      const rejected = await request('/api/v1/auth/login', 'POST', { email, password: 'wrong-password-for-testing' }, false);
      assert.equal(rejected.status, 401);
      assert.deepEqual(rejected.data, { error: 'INVALID_CREDENTIALS' });
    }
    assert.equal((await request('/api/v1/auth/login', 'POST', { email: inactive.email, password: testPassword }, false)).status, 401);
    assert.equal((await request('/api/v1/auth/login', 'POST', { email: owner.email, password: testPassword, role_id: 6 }, false)).status, 400);
    assert.equal((await request('/api/v1/auth/register', 'POST', {}, false)).status, 404);
    let token = await login(owner.email);
    const me = await request('/api/v1/auth/me');
    assert.equal(me.status, 200);
    assert.equal(me.data.user.id, owner.id);
    assert.equal(me.data.branch.id, branchId);
    assert.ok(me.data.capabilities.includes('MANAGE_PRICES'));
    assert.ok(!JSON.stringify(me.data).includes(testPasswordHash));
    assert.ok(!JSON.stringify(me.data).includes(token));
    const [[session]] = await db.execute('SELECT token_hash FROM auth_sessions WHERE user_id = ?', [owner.id]);
    assert.ok(session.token_hash.equals(sessionDigest(token)));
    const forged = await fetch(base + '/api/v1/auth/me', { headers: { Authorization: `Bearer ${randomBytes(32).toString('base64url')}` } });
    assert.equal(forged.status, 401);
    await db.execute("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'INVENTORY') WHERE id = ?", [owner.id]);
    const changed = await request('/api/v1/auth/me');
    assert.ok(changed.data.capabilities.includes('MANAGE_PRODUCTS'));
    assert.ok(!changed.data.capabilities.includes('MANAGE_PRICES'));
    const denied = await request('/api/v1/products', 'POST', { internal_code: `DENIED-${suffix}`, common_name: 'Prueba', category_id: 1, price_cents: 100 });
    assert.equal(denied.status, 403);
    await db.execute('UPDATE branches SET is_active = 0 WHERE id = ?', [branchId]);
    assert.equal((await request('/api/v1/auth/me')).data.branch.is_active, false);
    await db.execute('UPDATE branches SET is_active = 1 WHERE id = ?', [branchId]);
    await db.execute('UPDATE users SET branch_id = NULL WHERE id = ?', [owner.id]);
    assert.equal((await request('/api/v1/auth/me')).data.branch, null);
    await db.execute('UPDATE users SET is_active = 0 WHERE id = ?', [owner.id]);
    assert.equal((await request('/api/v1/auth/me')).status, 401);
    await db.execute('UPDATE users SET is_active = 1 WHERE id = ?', [owner.id]);
    assert.equal((await request('/api/v1/auth/me')).status, 401); // Old token remains revoked.
    token = await login(owner.email);
    await db.execute("UPDATE auth_sessions SET created_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR), expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR) WHERE token_hash = ?", [sessionDigest(token)]);
    assert.equal((await request('/api/v1/auth/me')).status, 401);
    await login(owner.email);
    assert.equal((await request('/api/v1/auth/logout', 'POST')).status, 200);
    assert.equal((await request('/api/v1/auth/logout', 'POST')).status, 200);
    assert.equal((await request('/api/v1/auth/me')).status, 401);
    await login(owner.email);
    await db.execute('UPDATE users SET password_hash = NULL WHERE id = ?', [owner.id]);
    assert.equal((await request('/api/v1/auth/me')).status, 401);
    await login(noRole.email);
    const pending = await request('/api/v1/auth/me');
    assert.equal(pending.data.access_state, 'NO_ROLE');
    assert.deepEqual(pending.data.capabilities, []);
    assert.equal((await request('/api/v1/categories', 'POST', { name: `Denied-${suffix}` })).status, 403);
    assert.equal((await request('/api/v1/auth/me', 'GET', undefined, false)).status, 401);
  } finally {
    for (const user of users) await deleteUser(db, user);
    if (branchId) await db.execute('DELETE FROM branches WHERE id = ?', [branchId]);
    await db.execute('DELETE FROM auth_login_limits WHERE key_hash = ?', [sessionDigest(`email:missing-${suffix}@example.invalid`)]);
    await db.end();
  }
});

test('persistent login budget rejects repeated attempts without revealing accounts', async () => {
  const db = await mysql.createConnection(options);
  const email = `limited-${randomBytes(8).toString('hex')}@example.invalid`;
  const key = sessionDigest(`email:${email}`);
  try {
    await db.execute('INSERT INTO auth_login_limits (key_hash, attempts, reset_at) VALUES (?, 10, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 15 MINUTE))', [key]);
    const response = await request('/api/v1/auth/login', 'POST', { email, password: testPassword }, false);
    assert.equal(response.status, 429);
    await db.execute('UPDATE auth_login_limits SET reset_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE key_hash = ?', [key]);
    const reset = await request('/api/v1/auth/login', 'POST', { email, password: testPassword }, false);
    assert.equal(reset.status, 401);
    const [[row]] = await db.execute('SELECT attempts FROM auth_login_limits WHERE key_hash = ?', [key]);
    assert.equal(row.attempts, 1);
  } finally {
    await db.execute('DELETE FROM auth_login_limits WHERE key_hash = ?', [key]);
    await db.end();
  }
});
