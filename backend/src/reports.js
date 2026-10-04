import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

const fail = (status, code) => { throw new ApiError(status, code); };
function date(value) {
  if (!/^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$/.test(value ?? '') || !Number.isFinite(Date.parse(value + 'T00:00:00Z')) || new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) !== value) fail(400, 'REPORT_DATE_INVALID');
  return Date.parse(value + 'T00:00:00Z');
}
export function reportQuery(params, top = false, now = new Date()) {
  const allowed = ['branch_id', 'start_date', 'end_date', 'limit'];
  if ([...params.keys()].some(k => !allowed.includes(k) || params.getAll(k).length !== 1)) fail(400, 'REPORT_QUERY_INVALID');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : top ? 10 : 200;
  if (limit > (top ? 100 : 500)) fail(400, 'REPORT_QUERY_INVALID');
  const ranged = !top || params.has('start_date') || params.has('end_date');
  const end = params.has('end_date') ? date(params.get('end_date')) + 86400000 : now.getTime();
  const start = params.has('start_date') ? date(params.get('start_date')) : end - 30 * 86400000;
  if (ranged && (start >= end || end - start > 366 * 86400000)) fail(400, 'REPORT_RANGE_INVALID');
  return { branch_id: params.has('branch_id') ? positiveId(params.get('branch_id')) : null, limit,
    start: ranged ? new Date(start).toISOString().slice(0, 19).replace('T', ' ') : null,
    end: ranged ? new Date(end).toISOString().slice(0, 19).replace('T', ' ') : null };
}
export function reportScope(context, requested) {
  requireCapabilities(context, ['VIEW_REPORTS']);
  if (context.capabilities.includes('VIEW_ALL_SALES')) return requested;
  if (!context.branch?.id || (requested !== null && requested !== context.branch.id)) fail(403, 'REPORT_BRANCH_FORBIDDEN');
  return context.branch.id;
}
export function reportInteger(value) {
  const raw = String(value);
  if (!/^\d+(?:\.0+)?$/.test(raw)) fail(409, 'REPORT_TOTAL_INVALID');
  const result = BigInt(raw.split('.')[0]);
  if (result > BigInt(Number.MAX_SAFE_INTEGER)) fail(409, 'REPORT_TOTAL_INVALID');
  return Number(result);
}
export function createReports(db, context) {
  return {
    async daily(query) {
      const branch = reportScope(context, query.branch_id);
      const [rows] = await db.execute(`SELECT s.branch_id, b.name AS branch_name,
        DATE_FORMAT(p.created_at,'%Y-%m-%d') AS day, COUNT(*) AS sales_count,
        SUM(p.amount_due_cents) AS revenue_cents,
        SUM(s.discount_cents + COALESCE(d.item_discount,0)) AS discount_cents
        FROM cashier_payments p JOIN sales s ON s.id=p.sale_id JOIN branches b ON b.id=s.branch_id
        LEFT JOIN (SELECT sale_id, SUM((COALESCE(list_price_cents,unit_price_cents)-unit_price_cents)*quantity) AS item_discount FROM sale_items GROUP BY sale_id) d ON d.sale_id=s.id
        WHERE (? IS NULL OR s.branch_id=?) AND p.created_at>=? AND p.created_at<?
        GROUP BY s.branch_id,b.name,DATE_FORMAT(p.created_at,'%Y-%m-%d')
        ORDER BY day DESC,branch_name ASC,s.branch_id ASC LIMIT ?`, [branch, branch, query.start, query.end, query.limit + 1]);
      return { schema_version: 1, timezone: 'UTC', branch_id: branch, start: query.start, end_exclusive: query.end,
        items: rows.slice(0, query.limit).map(r => ({ ...r, sales_count: reportInteger(r.sales_count), revenue_cents: reportInteger(r.revenue_cents), discount_cents: reportInteger(r.discount_cents) })), has_more: rows.length > query.limit };
    },
    async top(query) {
      const branch = reportScope(context, query.branch_id);
      const [rows] = await db.execute(`SELECT i.product_id,i.product_name,i.internal_code AS product_code,
        SUM(i.quantity) AS total_quantity,SUM(i.line_total_cents) AS total_revenue_cents
        FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.status='PAID'
        AND (? IS NULL OR s.branch_id=?) AND (? IS NULL OR s.created_at>=?) AND (? IS NULL OR s.created_at<?)
        GROUP BY i.product_id,i.product_name,i.internal_code
        ORDER BY total_quantity DESC,i.product_id ASC,i.product_name ASC,i.internal_code ASC LIMIT ?`, [branch, branch, query.start, query.start, query.end, query.end, query.limit]);
      return { schema_version: 1, timezone: 'UTC', branch_id: branch, start: query.start, end_exclusive: query.end,
        items: rows.map(r => ({ ...r, total_quantity: String(r.total_quantity), total_revenue_cents: reportInteger(r.total_revenue_cents) })) };
    },
  };
}
