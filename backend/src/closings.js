import { createHash } from 'node:crypto';
import { ApiError } from './catalog.js';
import { cashierScope } from './cashier.js';

const fail = (status, code) => { throw new ApiError(status, code); };
const MAX = BigInt(Number.MAX_SAFE_INTEGER);
function cents(value) {
  const n = BigInt(value);
  if (n < -MAX || n > MAX) fail(409, 'CLOSING_TOTAL_INVALID');
  return Number(n);
}
export function closingInput(input) {
  const keys = ['opening_cash_cents', 'counted_cash_cents'];
  if (!input || Array.isArray(input) || typeof input !== 'object' || Object.keys(input).length !== 2
    || keys.some(key => !Object.hasOwn(input, key) || !Number.isSafeInteger(input[key]) || input[key] < 0)) fail(400, 'CLOSING_DATA_INVALID');
  return { opening_cash_cents: input.opening_cash_cents, counted_cash_cents: input.counted_cash_cents };
}
export function closingKey(value, actor) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) fail(400, 'CLOSING_KEY_REQUIRED');
  return createHash('sha256').update(`closing:${actor}:${value}`).digest('hex');
}
export function closingTotals(payments, refunds, opening = 0, counted = 0) {
  const sums = { cash_sales_cents: 0n, card_sales_cents: 0n, transfer_sales_cents: 0n, cash_refunds_cents: 0n, other_refunds_cents: 0n };
  const paymentFields = { CASH: 'cash_sales_cents', CARD: 'card_sales_cents', TRANSFER: 'transfer_sales_cents' };
  for (const p of payments) sums[paymentFields[p.method]] += BigInt(p.amount_due_cents);
  for (const r of refunds) sums[r.method === 'CASH' ? 'cash_refunds_cents' : 'other_refunds_cents'] += BigInt(r.amount_cents);
  sums.expected_cash_cents = BigInt(opening) + sums.cash_sales_cents - sums.cash_refunds_cents;
  sums.difference_cents = BigInt(counted) - sums.expected_cash_cents;
  return Object.fromEntries(Object.entries(sums).map(([k, v]) => [k, cents(v)]));
}
const columns = `id, branch_id, cashier_id, opening_cash_cents, cash_sales_cents, card_sales_cents,
  transfer_sales_cents, cash_refunds_cents, other_refunds_cents, counted_cash_cents,
  expected_cash_cents, difference_cents, DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at`;
const money = row => Object.fromEntries(Object.entries(row).map(([k, v]) => [k, k.endsWith('_cents') ? cents(v) : v]));

// Called within auth.withAccess READ COMMITTED: its user row lock serializes
// this actor's payments/refunds/closings even across distinct auth sessions.
export function createClosings(db, context) {
  const branch = cashierScope(context), actor = context.user.id;
  async function pending(lock = false) {
    const suffix = lock ? ' FOR UPDATE' : '';
    const [payments] = await db.execute(`SELECT p.id, p.method, p.amount_due_cents FROM cashier_payments p
      WHERE p.branch_id = ? AND p.cashier_id = ? AND NOT EXISTS
      (SELECT 1 FROM cashier_closing_payments c WHERE c.payment_id = p.id) ORDER BY p.id${suffix}`, [branch, actor]);
    const [refunds] = await db.execute(`SELECT r.id, r.method, r.amount_cents FROM sale_refunds r
      WHERE r.branch_id = ? AND r.refunded_by = ? AND NOT EXISTS
      (SELECT 1 FROM cashier_closing_refunds c WHERE c.refund_id = r.id) ORDER BY r.id${suffix}`, [branch, actor]);
    return { payments, refunds };
  }
  async function detail(id) {
    const [[row]] = await db.execute(`SELECT ${columns} FROM cashier_closings WHERE id = ? AND branch_id = ? AND cashier_id = ?`, [id, branch, actor]);
    if (!row) fail(404, 'CLOSING_NOT_FOUND');
    const [payments] = await db.execute('SELECT payment_id FROM cashier_closing_payments WHERE closing_id = ? ORDER BY payment_id', [id]);
    const [refunds] = await db.execute('SELECT refund_id FROM cashier_closing_refunds WHERE closing_id = ? ORDER BY refund_id', [id]);
    return { schema_version: 1, closing: money(row), payment_ids: payments.map(p => p.payment_id), refund_ids: refunds.map(r => r.refund_id) };
  }
  async function byKey(key) {
    const [[row]] = await db.execute('SELECT id, branch_id, cashier_id, opening_cash_cents, counted_cash_cents FROM cashier_closings WHERE idempotency_key = ?', [closingKey(key, actor)]);
    if (row && (row.branch_id !== branch || row.cashier_id !== actor)) fail(409, 'CLOSING_IDEMPOTENCY_CONFLICT');
    return row;
  }
  return {
    detail,
    async preview() {
      const { payments, refunds } = await pending();
      const [[last]] = await db.execute(`SELECT ${columns} FROM cashier_closings WHERE branch_id = ? AND cashier_id = ? ORDER BY id DESC LIMIT 1`, [branch, actor]);
      const { expected_cash_cents, difference_cents, ...totals } = closingTotals(payments, refunds);
      return { schema_version: 1, branch_id: branch, cashier_id: actor, ...totals, payment_count: payments.length, refund_count: refunds.length, last_closing: last ? money(last) : null };
    },
    async close(input, key) {
      const previous = await byKey(key);
      if (previous) {
        if (BigInt(previous.opening_cash_cents) !== BigInt(input.opening_cash_cents) || BigInt(previous.counted_cash_cents) !== BigInt(input.counted_cash_cents)) fail(409, 'CLOSING_IDEMPOTENCY_CONFLICT');
        return { ...await detail(previous.id), idempotent_replay: true };
      }
      const { payments, refunds } = await pending(true);
      if (!payments.length && !refunds.length) fail(409, 'CLOSING_EMPTY');
      const totals = closingTotals(payments, refunds, input.opening_cash_cents, input.counted_cash_cents);
      const [created] = await db.execute(`INSERT INTO cashier_closings (branch_id, cashier_id, opening_cash_cents,
        cash_sales_cents, card_sales_cents, transfer_sales_cents, cash_refunds_cents, other_refunds_cents,
        counted_cash_cents, expected_cash_cents, difference_cents, idempotency_key)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [branch, actor, input.opening_cash_cents,
        totals.cash_sales_cents, totals.card_sales_cents, totals.transfer_sales_cents, totals.cash_refunds_cents,
        totals.other_refunds_cents, input.counted_cash_cents, totals.expected_cash_cents, totals.difference_cents, closingKey(key, actor)]);
      for (const p of payments) await db.execute('INSERT INTO cashier_closing_payments (payment_id, closing_id) VALUES (?, ?)', [p.id, created.insertId]);
      for (const r of refunds) await db.execute('INSERT INTO cashier_closing_refunds (refund_id, closing_id) VALUES (?, ?)', [r.id, created.insertId]);
      return { ...await detail(created.insertId), idempotent_replay: false };
    },
    async recover(key) {
      const row = await byKey(key);
      if (!row) fail(404, 'CLOSING_NOT_FOUND');
      return { ...await detail(row.id), idempotent_replay: true };
    },
  };
}
