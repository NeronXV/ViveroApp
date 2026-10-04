import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';

const fail = code => { throw new ApiError(400, code); };
export function customerScope(context, update = false) {
  requireCapabilities(context, []);
  if (update ? !context.capabilities.includes('MANAGE_USERS')
    : !context.capabilities.some(c => ['CREATE_SALES', 'MANAGE_USERS'].includes(c))) throw new ApiError(403, 'CUSTOMER_UNAUTHORIZED');
}
function text(value, min, max, nullable = false) {
  if (nullable && value === null) return null;
  if (typeof value !== 'string' || !value.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(value)) fail('CUSTOMER_DATA_INVALID');
  const result = value.trim();
  if (nullable && !result) return null;
  if ([...result].length < min || [...result].length > max) fail('CUSTOMER_DATA_INVALID');
  return result;
}
export function customerInput(input) {
  const keys = ['full_name', 'email', 'phone', 'is_active'];
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== keys.length
    || keys.some(key => !Object.hasOwn(input, key)) || typeof input.is_active !== 'boolean') fail('CUSTOMER_DATA_INVALID');
  const email = text(input.email, 5, 254, true)?.toLowerCase() ?? null;
  if (email !== null && !/^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/.test(email)) fail('CUSTOMER_EMAIL_INVALID');
  return { full_name: text(input.full_name, 2, 160), email, phone: text(input.phone, 8, 20, true), is_active: input.is_active };
}
export function customerQuery(params) {
  if ([...params.keys()].some(k => !['search', 'limit'].includes(k) || params.getAll(k).length !== 1) || !params.has('search')) fail('CUSTOMER_QUERY_INVALID');
  const search = text(params.get('search'), 2, 80);
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 10;
  if (limit > 50) fail('CUSTOMER_QUERY_INVALID');
  return { search: search.toLowerCase(), limit };
}
const columns = `id, full_name, email, phone, is_active,
  DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at,
  DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS updated_at`;
const row = value => ({ ...value, is_active: Boolean(value.is_active) });

export function createCustomers(db, context) {
  customerScope(context);
  async function detail(id) {
    const [[value]] = await db.execute(`SELECT ${columns} FROM customers WHERE id = ?
      AND (is_active = 1 OR ? = 1)`, [id, context.capabilities.includes('MANAGE_USERS')]);
    if (!value) throw new ApiError(404, 'CUSTOMER_NOT_FOUND');
    return { schema_version: 1, customer: row(value) };
  }
  return {
    detail,
    async search({ search, limit }) {
      // Literal search, including %, _ and !. Never interpolate customer input.
      const pattern = `%${search.replace(/[!%_]/g, c => `!${c}`)}%`;
      const [rows] = await db.execute(`SELECT ${columns} FROM customers WHERE is_active = 1
        AND (full_name LIKE ? ESCAPE '!' OR email LIKE ? ESCAPE '!' OR phone LIKE ? ESCAPE '!')
        ORDER BY full_name, id LIMIT ?`, [pattern, pattern, pattern, limit]);
      return { schema_version: 1, items: rows.map(row) };
    },
    async create(input) {
      const [created] = await db.execute('INSERT INTO customers (full_name, email, phone, is_active, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?)', [input.full_name, input.email, input.phone, input.is_active, context.user.id, context.user.id]);
      // A sales actor may create an inactive customer under the existing contract.
      const [[saved]] = await db.execute(`SELECT ${columns} FROM customers WHERE id = ?`, [created.insertId]);
      return { schema_version: 1, customer: row(saved) };
    },
    async update(id, input) {
      customerScope(context, true);
      const [[current]] = await db.execute('SELECT id FROM customers WHERE id = ? FOR UPDATE', [id]);
      if (!current) throw new ApiError(404, 'CUSTOMER_NOT_FOUND');
      await db.execute('UPDATE customers SET full_name = ?, email = ?, phone = ?, is_active = ?, updated_by = ?, updated_at = UTC_TIMESTAMP(6) WHERE id = ?', [input.full_name, input.email, input.phone, input.is_active, context.user.id, id]);
      return detail(id);
    },
    async deactivate(id) {
      customerScope(context, true);
      const [[current]] = await db.execute('SELECT id FROM customers WHERE id = ? FOR UPDATE', [id]);
      if (!current) throw new ApiError(404, 'CUSTOMER_NOT_FOUND');
      await db.execute('UPDATE customers SET is_active = 0, updated_by = ?, updated_at = UTC_TIMESTAMP(6) WHERE id = ?', [context.user.id, id]);
      return detail(id);
    },
  };
}
