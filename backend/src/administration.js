import { ApiError, positiveId } from './catalog.js';
import { requireCapabilities } from './auth/service.js';
import { validateCredentials, hashPassword } from './auth/password.js';

const fail = (status, code) => { throw new ApiError(status, code); };
function exact(input, keys) {
  if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).length !== keys.length || keys.some(k => !Object.hasOwn(input, k))) fail(400, 'ADMIN_DATA_INVALID');
}
function text(input, min, max) {
  if (typeof input !== 'string' || !input.isWellFormed() || /[\u0000-\u001f\u007f-\u009f]/u.test(input)) fail(400, 'ADMIN_DATA_INVALID');
  const value = input.trim().replace(/\s+/gu, ' ');
  if ([...value].length < min || [...value].length > max) fail(400, 'ADMIN_DATA_INVALID');
  return value;
}
export function branchInput(input) {
  exact(input, ['code', 'name']);
  const code = text(input.code, 2, 24).toUpperCase();
  if (!/^[A-Z0-9][A-Z0-9_-]{1,23}$/.test(code)) fail(400, 'ADMIN_DATA_INVALID');
  return { code, name: text(input.name, 2, 120) };
}
export function staffInput(action, input) {
  if (action === 'active') {
    exact(input, ['is_active']);
    if (typeof input.is_active !== 'boolean') fail(400, 'ADMIN_DATA_INVALID');
    return input.is_active;
  }
  if (action === 'branch') {
    exact(input, ['branch_id']);
    if (typeof input.branch_id !== 'number') fail(400, 'ADMIN_DATA_INVALID');
    return positiveId(input.branch_id);
  }
  exact(input, ['role_name']);
  const role = text(input.role_name, 5, 24).toUpperCase();
  if (!['SALES', 'CASHIER', 'INVENTORY', 'MANAGER', 'ADMIN', 'OWNER'].includes(role)) fail(400, 'ROLE_ASSIGNMENT_INVALID');
  return role;
}
export function adminQuery(params, staff = false) {
  const allowed = staff ? ['limit', 'after_id', 'include_inactive', 'search', 'branch_id'] : ['limit', 'after_id', 'include_inactive'];
  if ([...params.keys()].some(k => !allowed.includes(k) || params.getAll(k).length !== 1)) fail(400, 'ADMIN_QUERY_INVALID');
  const limit = params.has('limit') ? positiveId(params.get('limit')) : 50;
  if (limit > 100 || (params.has('include_inactive') && !['true', 'false'].includes(params.get('include_inactive')))) fail(400, 'ADMIN_QUERY_INVALID');
  return { limit, after_id: params.has('after_id') ? positiveId(params.get('after_id')) : 0,
    include_inactive: params.get('include_inactive') === 'true', search: params.has('search') ? text(params.get('search'), 1, 80) : null,
    branch_id: params.has('branch_id') ? positiveId(params.get('branch_id')) : null };
}
export function ownerProtection(context, targetRole, requestedRole = null) {
  if (context.role?.name === 'ADMIN' && (targetRole === 'OWNER' || requestedRole === 'OWNER')) fail(403, 'ROLE_OWNER_RESTRICTED');
}
export function accountInput(input) {
  exact(input, ['email', 'password', 'full_name', 'role_name', 'branch_id', 'is_active']);
  const credentials = validateCredentials({ email: input.email, password: input.password }, true);
  if (credentials.password.startsWith('replace-with-')) fail(400, 'ADMIN_DATA_INVALID');
  return { ...credentials, full_name: text(input.full_name, 2, 160),
    role_name: staffInput('role', { role_name: input.role_name }),
    branch_id: staffInput('branch', { branch_id: input.branch_id }),
    is_active: staffInput('active', { is_active: input.is_active }) };
}
export function accountPassword(input) {
  exact(input, ['password']);
  const { password } = validateCredentials({ email: 'validation@example.invalid', password: input.password }, true);
  if (password.startsWith('replace-with-')) fail(400, 'ADMIN_DATA_INVALID');
  return password;
}
const branchColumns = `id, code, name, is_active,
  DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%sZ') AS created_at,
  DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%sZ') AS updated_at`;
const staffColumns = `u.id, u.full_name, u.is_active, u.branch_id, r.name AS role_name,
  r.display_name AS role_display_name, b.code AS branch_code, b.name AS branch_name,
  b.is_active AS branch_active, DATE_FORMAT(u.updated_at, '%Y-%m-%dT%H:%i:%sZ') AS updated_at`;
const staffFrom = 'FROM users u LEFT JOIN roles r ON r.id = u.role_id LEFT JOIN branches b ON b.id = u.branch_id';
const safeBranch = row => ({ ...row, is_active: Boolean(row.is_active) });
const safeStaff = row => ({ ...row, is_active: Boolean(row.is_active), branch_active: row.branch_active === null ? null : Boolean(row.branch_active) });

// Writes must run inside auth.withAccess({ administration: true, readCommitted: true }).
export function createAdministration(db, context) {
  async function audit(action, id, previous, next, user = false) {
    await db.execute('INSERT INTO administration_changes (actor_id, user_id, branch_id, action, previous_value, new_value) VALUES (?, ?, ?, ?, ?, ?)', [context.user.id, user ? id : null, user ? null : id, action, previous === null ? null : JSON.stringify(previous), JSON.stringify(next)]);
  }
  async function branch(id) {
    const [[row]] = await db.execute(`SELECT ${branchColumns} FROM branches WHERE id = ? FOR UPDATE`, [id]);
    if (!row) fail(404, 'BRANCH_NOT_FOUND');
    return safeBranch(row);
  }
  async function target(id) {
    const [[row]] = await db.execute(`SELECT ${staffColumns} ${staffFrom} WHERE u.id = ? FOR UPDATE`, [id]);
    if (!row) fail(404, 'STAFF_NOT_FOUND');
    return safeStaff(row);
  }
  async function ownerCount() {
    const [[row]] = await db.execute("SELECT COUNT(*) AS n FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'OWNER' AND u.is_active = 1");
    return Number(row.n);
  }
  return {
    async createAccount(input) {
      requireCapabilities(context, ['MANAGE_USERS', 'ASSIGN_ROLES']);
      if (!['OWNER', 'ADMIN'].includes(context.role?.name)) fail(403, 'ROLE_ASSIGNMENT_UNAUTHORIZED');
      ownerProtection(context, null, input.role_name);
      const destination = await branch(input.branch_id);
      if (!destination.is_active) fail(409, 'BRANCH_INACTIVE');
      const [[role]] = await db.execute('SELECT id FROM roles WHERE name=?', [input.role_name]);
      const passwordHash = await hashPassword(input.password);
      const [row] = await db.execute('INSERT INTO users(email,full_name,password_hash,branch_id,role_id,is_active) VALUES(?,?,?,?,?,?)', [input.email, input.full_name, passwordHash, input.branch_id, role.id, input.is_active]);
      await audit('USER_CREATE', row.insertId, null, { role_name: input.role_name, branch_id: input.branch_id, is_active: input.is_active }, true);
      return { schema_version: 1, staff: await target(row.insertId) };
    },
    async resetPassword(id, password) {
      requireCapabilities(context, ['MANAGE_USERS', 'ASSIGN_ROLES']);
      if (!['OWNER', 'ADMIN'].includes(context.role?.name)) fail(403, 'ROLE_ASSIGNMENT_UNAUTHORIZED');
      const before = await target(id);
      ownerProtection(context, before.role_name);
      await db.execute('UPDATE users SET password_hash=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?', [await hashPassword(password), id]);
      // Existing trigger revokes every session in this same transaction.
      await audit('USER_PASSWORD', id, null, { sessions_revoked: true }, true);
      return { schema_version: 1, staff_id: id, password_reset: true, sessions_revoked: true };
    },
    async branches(query) {
      requireCapabilities(context, []);
      if (!context.capabilities.some(c => ['MANAGE_BRANCHES', 'MANAGE_USERS'].includes(c))) fail(403, 'ADMIN_UNAUTHORIZED');
      const [rows] = await db.execute(`SELECT ${branchColumns},
        (SELECT COUNT(*) FROM users u WHERE u.branch_id = branches.id AND u.is_active = 1) AS active_staff_count,
        (SELECT COUNT(*) FROM sales s WHERE s.branch_id = branches.id AND s.status IN ('SENT_TO_CASHIER', 'PAYMENT_PENDING')) AS pending_sale_count
        FROM branches WHERE id > ? AND (is_active = 1 OR ? = 1) ORDER BY id LIMIT ?`, [query.after_id, query.include_inactive, query.limit + 1]);
      return { schema_version: 1, items: rows.slice(0, query.limit).map(safeBranch), next_after_id: rows.length > query.limit ? rows[query.limit - 1].id : null };
    },
    async staff(query) {
      requireCapabilities(context, ['MANAGE_USERS']);
      const pattern = query.search === null ? null : `%${query.search.replace(/[!%_]/g, c => `!${c}`)}%`;
      const [rows] = await db.execute(`SELECT ${staffColumns} ${staffFrom} WHERE u.id > ? AND (u.is_active = 1 OR ? = 1)
        AND (? IS NULL OR u.branch_id = ?) AND (? IS NULL OR u.full_name LIKE ? ESCAPE '!') ORDER BY u.id LIMIT ?`, [query.after_id, query.include_inactive, query.branch_id, query.branch_id, pattern, pattern, query.limit + 1]);
      return { schema_version: 1, items: rows.slice(0, query.limit).map(safeStaff), next_after_id: rows.length > query.limit ? rows[query.limit - 1].id : null };
    },
    async roles() {
      requireCapabilities(context, ['ASSIGN_ROLES']);
      if (!['ADMIN', 'OWNER'].includes(context.role?.name)) fail(403, 'ROLE_ASSIGNMENT_UNAUTHORIZED');
      const [rows] = await db.execute("SELECT id, name, display_name FROM roles WHERE name <> 'OWNER' OR ? = 'OWNER' ORDER BY id", [context.role.name]);
      for (const row of rows) {
        const [permissions] = await db.execute('SELECT p.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id WHERE rp.role_id = ? ORDER BY p.name', [row.id]);
        row.capabilities = permissions.map(p => p.name);
      }
      return { schema_version: 1, actor_role: context.role.name, items: rows };
    },
    async createBranch(input) {
      requireCapabilities(context, ['MANAGE_BRANCHES']);
      const [row] = await db.execute('INSERT INTO branches (code, name) VALUES (?, ?)', [input.code, input.name]);
      const created = await branch(row.insertId);
      await audit('BRANCH_CREATE', row.insertId, null, input);
      return { schema_version: 1, branch: created };
    },
    async updateBranch(id, input) {
      requireCapabilities(context, ['MANAGE_BRANCHES']);
      const before = await branch(id);
      await db.execute('UPDATE branches SET code = ?, name = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?', [input.code, input.name, id]);
      await audit('BRANCH_UPDATE', id, { code: before.code, name: before.name }, input);
      return { schema_version: 1, branch: await branch(id) };
    },
    async branchActive(id, active) {
      requireCapabilities(context, ['MANAGE_BRANCHES']);
      const before = await branch(id);
      if (before.is_active === active) return { schema_version: 1, branch: before, idempotent_replay: true };
      if (!active) {
        const [[blocked]] = await db.execute(`SELECT
          EXISTS(SELECT 1 FROM users WHERE branch_id = ? AND is_active = 1) OR
          EXISTS(SELECT 1 FROM sales WHERE branch_id = ? AND status IN ('SENT_TO_CASHIER', 'PAYMENT_PENDING')) OR
          EXISTS(SELECT 1 FROM web_orders WHERE branch_id = ? AND status IN ('PENDING', 'CONFIRMED', 'READY')) AS blocked`, [id, id, id]);
        if (blocked.blocked) fail(409, 'BRANCH_HAS_OPERATIONS');
      }
      await db.execute('UPDATE branches SET is_active = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?', [active, id]);
      await audit('BRANCH_ACTIVE', id, { is_active: before.is_active }, { is_active: active });
      return { schema_version: 1, branch: await branch(id), idempotent_replay: false };
    },
    async changeStaff(id, action, value) {
      requireCapabilities(context, [action === 'role' ? 'ASSIGN_ROLES' : 'MANAGE_USERS']);
      if (action === 'role' && !['ADMIN', 'OWNER'].includes(context.role?.name)) fail(403, 'ROLE_ASSIGNMENT_UNAUTHORIZED');
      const before = await target(id);
      ownerProtection(context, before.role_name, action === 'role' ? value : null);
      if (action !== 'active' && !before.is_active) fail(409, 'STAFF_INACTIVE');
      if (action === 'role') {
        if (before.role_name === value) return { schema_version: 1, staff: before, idempotent_replay: true };
        if (before.role_name === 'OWNER' && await ownerCount() <= 1) fail(409, 'ROLE_LAST_OWNER_REQUIRED');
        const [[role]] = await db.execute('SELECT id FROM roles WHERE name = ?', [value]);
        await db.execute('UPDATE users SET role_id = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?', [role.id, id]);
        await audit('USER_ROLE', id, { role_name: before.role_name }, { role_name: value }, true);
      } else if (action === 'active') {
        if (before.is_active === value) return { schema_version: 1, staff: before, idempotent_replay: true };
        if (before.role_name === 'OWNER' && !value && await ownerCount() <= 1) fail(409, 'ROLE_LAST_OWNER_REQUIRED');
        await db.execute('UPDATE users SET is_active = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?', [value, id]);
        await audit('USER_ACTIVE', id, { is_active: before.is_active }, { is_active: value }, true);
      } else {
        const destination = await branch(value);
        if (!destination.is_active) fail(409, 'BRANCH_INACTIVE');
        if (before.branch_id === value) return { schema_version: 1, staff: before, idempotent_replay: true };
        await db.execute('UPDATE users SET branch_id = ?, updated_at = UTC_TIMESTAMP() WHERE id = ?', [value, id]);
        await audit('USER_BRANCH', id, { branch_id: before.branch_id }, { branch_id: value }, true);
      }
      // Revoke every token after a real administrative access change. Existing
      // password/activation trigger remains intact for non-API maintenance too.
      await db.execute('DELETE FROM auth_sessions WHERE user_id = ?', [id]);
      return { schema_version: 1, staff: await target(id), idempotent_replay: false };
    },
  };
}
