import { inspectSourceExport } from './source-export-preflight.js';

export class SourceSalesError extends Error {}
const fail = code => { throw new SourceSalesError(code); };
function money(value) {
  if (typeof value !== 'string' || !/^\d+$/.test(value) || BigInt(value) > BigInt(Number.MAX_SAFE_INTEGER)) fail('HISTORY_MONEY_UNREPRESENTABLE');
  return BigInt(value);
}
function timestamp(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(value) || !Number.isFinite(Date.parse(value))) fail('HISTORY_DATE_INVALID');
}
// Reconcile the paid-history module before SQL. Other operational states and
// discounts require their own supported mapping; never silently discard them.
export function reconcileSourceSales(bytes, expectedSha256) {
  const inspected = inspectSourceExport(bytes);
  if (inspected.input_sha256 !== expectedSha256) fail('INPUT_HASH_MISMATCH');
  const { tables } = JSON.parse(bytes.toString('utf8'));
  if (tables.sale_discounts.length || tables.sale_items.some(row => money(row.discount_cents) !== 0n || row.promotion_id !== null)) fail('HISTORY_DISCOUNTS_REQUIRE_MAPPING');
  const sales = new Map(tables.sales.map(row => [row.id, row]));
  for (const entity of ['sale_items', 'sale_payments', 'sale_status_history', 'sale_payment_claims']) {
    if (tables[entity].some(row => !sales.has(row.sale_id))) fail('HISTORY_SALE_REFERENCE_INVALID');
  }
  let total = 0n, received = 0n, change = 0n;
  const grouped = new Map();
  for (const [index, sale] of tables.sales.entries()) {
    if (sale.status !== 'PAID') fail('HISTORY_STATE_REQUIRES_MAPPING');
    if (sale.customer_id !== null) fail('HISTORY_CUSTOMER_REQUIRES_MAPPING');
    if (typeof sale.folio !== 'string' || sale.folio.length < 1 || [...sale.folio].length > 40 || /[\u0000-\u001f\u007f]/u.test(sale.folio)) fail('HISTORY_FOLIO_INVALID');
    timestamp(sale.created_at); timestamp(sale.updated_at);
    if (money(sale.discount_cents) !== 0n) fail('HISTORY_DISCOUNTS_REQUIRE_MAPPING');
    const items = tables.sale_items.filter(row => row.sale_id === sale.id);
    if (!items.length) fail('HISTORY_ITEMS_MISSING');
    let itemTotal = 0n;
    for (const item of items) {
      timestamp(item.created_at);
      if (!Number.isSafeInteger(item.quantity) || item.quantity < 1) fail('HISTORY_QUANTITY_REQUIRES_MAPPING');
      const line = money(item.line_total_cents), price = money(item.unit_price_cents);
      if (line !== price * BigInt(item.quantity) || money(item.list_price_cents) !== price) fail('HISTORY_LINE_TOTAL_MISMATCH');
      itemTotal += line;
    }
    const due = money(sale.total_cents);
    if (due === 0n || itemTotal !== money(sale.subtotal_cents) || due !== itemTotal) fail('HISTORY_SALE_TOTAL_MISMATCH');
    const payments = tables.sale_payments.filter(row => row.sale_id === sale.id);
    if (payments.length !== 1) fail('HISTORY_PAYMENT_CARDINALITY');
    const payment = payments[0]; timestamp(payment.created_at);
    const paid = money(payment.amount_received_cents), returned = money(payment.change_cents);
    if (money(payment.amount_due_cents) !== due || paid < due || returned !== paid - due || payment.branch_id !== sale.branch_id) fail('HISTORY_PAYMENT_TOTAL_MISMATCH');
    if (!['CASH','CARD','TRANSFER'].includes(payment.method) || (payment.method !== 'CASH' && returned !== 0n)
      || (payment.method === 'CASH' && (payment.reference !== null || money(payment.requested_amount_received_cents) !== paid))
      || (payment.method !== 'CASH' && payment.requested_amount_received_cents !== null)
      || (payment.method === 'TRANSFER' && (typeof payment.reference !== 'string' || !payment.reference.trim()))) fail('HISTORY_PAYMENT_METHOD_INVALID');
    const claim = tables.sale_payment_claims.find(row => row.id === payment.claim_id);
    if (!claim || claim.sale_id !== sale.id || claim.branch_id !== sale.branch_id || claim.cashier_id !== payment.cashier_id
      || claim.closed_reason !== 'CONFIRMED' || !claim.consumed_at || claim.released_at !== null) fail('HISTORY_PAYMENT_CLAIM_INVALID');
    const history = tables.sale_status_history.filter(row => row.sale_id === sale.id);
    const transitions = [[null,'DRAFT'],['DRAFT','SENT_TO_CASHIER'],['SENT_TO_CASHIER','PAID']];
    if (history.length !== 3 || transitions.some(([previous,next]) => history.filter(row => row.previous_status === previous && row.new_status === next).length !== 1)) fail('HISTORY_TRANSITIONS_INVALID');
    const ordered = transitions.map(([previous,next]) => history.find(row => row.previous_status === previous && row.new_status === next));
    for (const row of ordered) timestamp(row.changed_at);
    if (ordered.some((row,i) => i > 0 && Date.parse(row.changed_at) < Date.parse(ordered[i-1].changed_at))) fail('HISTORY_CHRONOLOGY_INVALID');
    total += due; received += paid; change += returned;
    const group = grouped.get(sale.branch_id) ?? { source_branch_index: tables.branches.findIndex(row => row.id === sale.branch_id), sales: 0, total_cents: 0n };
    if (group.source_branch_index < 0 || !tables.profiles.some(row => row.id === sale.created_by)) fail('HISTORY_IDENTITY_REFERENCE_INVALID');
    group.sales++; group.total_cents += due; grouped.set(sale.branch_id,group);
  }
  const claims = tables.sale_payment_claims;
  for (const claim of claims) {
    timestamp(claim.created_at); timestamp(claim.expires_at);
    if (Date.parse(claim.expires_at) <= Date.parse(claim.created_at)) fail('HISTORY_CLAIM_EXPIRY_INVALID');
    if (claim.closed_reason === 'CONFIRMED' && claim.consumed_at && claim.released_at === null) {
      timestamp(claim.consumed_at);
      if (tables.sale_payments.filter(row => row.claim_id === claim.id).length !== 1) fail('HISTORY_CONFIRMED_CLAIM_ORPHAN');
    } else if (['RELEASED','EXPIRED'].includes(claim.closed_reason) && claim.released_at && claim.consumed_at === null) timestamp(claim.released_at);
    else fail('HISTORY_OPEN_OR_INVALID_CLAIM');
  }
  if (new Set(tables.sales.map(row => row.folio)).size !== tables.sales.length) fail('HISTORY_DUPLICATE_FOLIO');
  return { input_sha256: inspected.input_sha256, reconciled: true, import_applied: false,
    counts: Object.fromEntries(['sales','sale_items','sale_payments','sale_status_history','sale_payment_claims'].map(name => [name,tables[name].length])),
    totals: { total_cents: total.toString(), received_cents: received.toString(), change_cents: change.toString() },
    branches: [...grouped.values()].map(row => ({ ...row, total_cents: row.total_cents.toString() })),
    pending: ['Persistent historical ID mappings and SQL import', 'Preserve source timestamps with microsecond precision', 'Import inventory separately without replaying payments', 'Final snapshot and reconciliation after operational cutover'] };
}
