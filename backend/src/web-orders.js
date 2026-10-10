import { createHash } from 'node:crypto';
import { ApiError, positiveId } from './catalog.js';
import { priceCatalogPage } from './catalog-pricing.js';

const fail = (code = 'WEB_ORDER_INVALID', status = 400) => { throw new ApiError(status, code); };
const digest = value => createHash('sha256').update(value).digest();
function keys(value, expected) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== expected.length || expected.some(key => !Object.hasOwn(value, key))) fail();
}
function id(value) { if (typeof value !== 'number') fail(); return positiveId(value); }
function text(value, max, nullable = false) {
  if (nullable && value === null) return null;
  if (typeof value !== 'string' || !value.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(value)) fail('WEB_ORDER_CUSTOMER_INVALID');
  const result = value.trim();
  if ([...result].length > max) fail('WEB_ORDER_CUSTOMER_INVALID');
  return nullable && !result ? null : result;
}
export function orderKey(value) {
  // A random 32-byte secret, not a public order ID. Only its digest is stored.
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) fail('WEB_ORDER_KEY_REQUIRED');
  return digest(value);
}
export function validateOrder(input, submit = false) {
  keys(input, submit ? ['branch_id', 'items', 'customer_name', 'customer_phone', 'customer_email', 'notes', 'expected_total_cents'] : ['branch_id', 'items']);
  const branch_id = id(input.branch_id);
  if (!Array.isArray(input.items) || input.items.length < 1 || input.items.length > 25) fail('WEB_ORDER_ITEMS_INVALID');
  const items = input.items.map(row => {
    keys(row, ['product_id', 'quantity']);
    if (!Number.isInteger(row.quantity) || row.quantity < 1 || row.quantity > 100) fail('WEB_ORDER_ITEMS_INVALID');
    return { product_id: id(row.product_id), quantity: row.quantity };
  }).sort((a, b) => a.product_id - b.product_id);
  if (new Set(items.map(row => row.product_id)).size !== items.length) fail('WEB_ORDER_ITEMS_INVALID');
  const result = { branch_id, items };
  if (!submit) return result;
  const customer_name = text(input.customer_name, 160);
  const customer_phone = text(input.customer_phone, 24, true);
  const customer_email = text(input.customer_email, 254, true)?.toLowerCase() ?? null;
  const notes = text(input.notes, 500, true);
  if ([...customer_name].length < 2 || (!customer_phone && !customer_email)
    || (customer_phone && !/^[0-9+() .-]{8,24}$/.test(customer_phone))
    || (customer_email && !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(customer_email))) fail('WEB_ORDER_CUSTOMER_INVALID');
  if (!Number.isSafeInteger(input.expected_total_cents) || input.expected_total_cents < 1) fail();
  return { ...result, customer_name, customer_phone, customer_email, notes, expected_total_cents: input.expected_total_cents };
}

export async function quoteOrderProducts(connection, input) {
  const [[branch]] = await connection.execute('SELECT id FROM branches WHERE id = ? AND is_active = 1 FOR UPDATE', [input.branch_id]);
  if (!branch) fail('WEB_ORDER_BRANCH_UNAVAILABLE', 409);
  const ids = JSON.stringify(input.items.map(item => item.product_id));
  // Coordinate availability/name/price changes with catalog writers. Pricing is
  // then read in one statement at READ COMMITTED, including all promotions.
  await connection.execute(`SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id
    WHERE p.id IN (SELECT value FROM JSON_TABLE(?, '$[*]' COLUMNS (value BIGINT PATH '$')) j)
    ORDER BY p.id FOR UPDATE`, [ids]);
  const [rows] = await connection.execute(priceCatalogPage(`SELECT p.id, p.common_name, p.internal_code, p.price_cents, p.is_active
    FROM products p JOIN categories c ON c.id = p.category_id
    WHERE p.is_active = 1 AND c.is_active = 1
    AND p.id IN (SELECT value FROM JSON_TABLE(?, '$[*]' COLUMNS (value BIGINT PATH '$')) j)`), [ids]);
  if (rows.length !== input.items.length) fail('WEB_ORDER_ITEMS_UNAVAILABLE', 409);
  let subtotal = 0n, total = 0n;
  const items = rows.map(row => {
    const quantity = input.items.find(item => item.product_id === row.id).quantity;
    const list = BigInt(row.price_cents), unit = BigInt(row.effective_price_cents);
    subtotal += list * BigInt(quantity); total += unit * BigInt(quantity);
    return { product_id: row.id, product_name: row.common_name, internal_code: row.internal_code, quantity,
      list_price_cents: Number(list), unit_price_cents: Number(unit), promotion_id: row.promotion_id,
      promotion_name: row.promotion_name, line_total_cents: Number(unit * BigInt(quantity)) };
  });
  if (subtotal > BigInt(Number.MAX_SAFE_INTEGER) || total <= 0n) fail('WEB_ORDER_TOTAL_INVALID', 409);
  return { schema_version: 1, branch_id: input.branch_id, subtotal_cents: Number(subtotal), discount_cents: Number(subtotal - total), total_cents: Number(total), items };
}

const receiptSql = `SELECT id, request_hash, status, total_cents,
  DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at FROM web_orders WHERE idempotency_hash = ?`;
function receipt(row, replay) {
  return { schema_version: 1, id: row.id, order_number: `VW-${row.id}`, status: row.status,
    total_cents: Number(row.total_cents), created_at: row.created_at, idempotent_replay: replay };
}

export function createWebOrders(db) {
  async function transact(action, serialized = false) {
    const connection = await db.getConnection();
    let locked = false, committing = false;
    try {
      if (serialized) {
        const [[lock]] = await connection.execute("SELECT GET_LOCK('vivero_web_order_submit', 5) AS acquired");
        if (lock.acquired !== 1) fail('WEB_ORDER_BUSY', 503);
        locked = true;
      }
      await connection.query('SET TRANSACTION ISOLATION LEVEL READ COMMITTED');
      await connection.beginTransaction();
      const result = await action(connection);
      committing = true;
      await connection.commit();
      return result;
    } catch (error) {
      await connection.rollback().catch(() => {});
      if (committing) fail('WEB_ORDER_RESULT_UNCERTAIN', 503);
      throw error;
    } finally {
      // Never return a session with an unconfirmed named lock to the pool.
      if (locked) {
        try { await connection.execute("SELECT RELEASE_LOCK('vivero_web_order_submit')"); }
        catch { connection.destroy(); }
      }
      connection.release();
    }
  }
  return {
    async options() {
      const [branches] = await db.execute('SELECT id, code, name FROM branches WHERE is_active = 1 ORDER BY name, id');
      return { schema_version: 1, branches };
    },
    async quote(input) { return transact(connection => quoteOrderProducts(connection, validateOrder(input))); },
    async recover(key) {
      const [[row]] = await db.execute(receiptSql, [orderKey(key)]);
      if (!row) fail('WEB_ORDER_NOT_FOUND', 404);
      return receipt(row, true);
    },
    // The same opaque recovery secret authorizes this read. Never look up a
    // public receipt by its predictable order number or ID.
    async ticket(key) {
      const keyHash = orderKey(key);
      return transact(async connection => {
        const [[row]] = await connection.execute(receiptSql, [keyHash]);
        if (!row) fail('WEB_ORDER_NOT_FOUND', 404);
        const [[details]] = await connection.execute(`SELECT b.id, b.code, b.name,
          o.subtotal_cents, o.discount_cents FROM web_orders o
          JOIN branches b ON b.id = o.branch_id WHERE o.id = ?`, [row.id]);
        const [items] = await connection.execute(`SELECT product_id, product_name, quantity,
          list_price_cents, unit_price_cents, line_total_cents
          FROM web_order_items WHERE order_id = ? ORDER BY id`, [row.id]);
        return { schema_version: 1, order: receipt(row, true),
          branch: { id: details.id, code: details.code, name: details.name },
          subtotal_cents: Number(details.subtotal_cents), discount_cents: Number(details.discount_cents),
          items: items.map(item => ({ ...item, list_price_cents: Number(item.list_price_cents),
            unit_price_cents: Number(item.unit_price_cents), line_total_cents: Number(item.line_total_cents) })) };
      });
    },
    async submit(input, key) {
      const keyHash = orderKey(key);
      const data = validateOrder(input, true);
      const requestHash = digest(JSON.stringify(data));
      return transact(async connection => {
        const [[previous]] = await connection.execute(receiptSql, [keyHash]);
        if (previous) {
          if (!previous.request_hash.equals(requestHash)) fail('WEB_ORDER_IDEMPOTENCY_CONFLICT', 409);
          return receipt(previous, true);
        }
        const priced = await quoteOrderProducts(connection, data);
        if (priced.total_cents !== data.expected_total_cents) fail('WEB_ORDER_PRICE_CHANGED', 409);
        // Serialized across API instances; both contacts independently count.
        const [[budget]] = await connection.execute(`SELECT COUNT(*) AS total FROM web_orders
          WHERE created_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 15 MINUTE) AND status <> 'CANCELLED'
          AND (customer_phone = ? OR customer_email = ?)`, [data.customer_phone, data.customer_email]);
        if (Number(budget.total) >= 3) fail('WEB_ORDER_RATE_LIMITED', 429);
        const [saved] = await connection.execute(`INSERT INTO web_orders
          (branch_id, idempotency_hash, request_hash, customer_name, customer_phone, customer_email, notes, subtotal_cents, discount_cents, total_cents)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [data.branch_id, keyHash, requestHash, data.customer_name, data.customer_phone, data.customer_email, data.notes,
          priced.subtotal_cents, priced.discount_cents, priced.total_cents]);
        for (const item of priced.items) {
          await connection.execute(`INSERT INTO web_order_items
            (order_id, product_id, product_name, internal_code, quantity, list_price_cents, unit_price_cents, promotion_id, promotion_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`, [saved.insertId, item.product_id, item.product_name, item.internal_code, item.quantity,
            item.list_price_cents, item.unit_price_cents, item.promotion_id, item.promotion_name]);
        }
        await connection.execute("INSERT INTO web_order_status_history (order_id, revision, new_status) VALUES (?, 0, 'PENDING')", [saved.insertId]);
        const [[row]] = await connection.execute(receiptSql, [keyHash]);
        return receipt(row, false);
      }, true);
    },
  };
}
