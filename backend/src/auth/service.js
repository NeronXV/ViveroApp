import { createHash, randomBytes } from 'node:crypto';
import { ApiError } from '../catalog.js';
import { validateCredentials, verifyPassword } from './password.js';

export const sessionDigest = value => createHash('sha256').update(value).digest();
export const SESSION_SECONDS = 3600;

export function bearerToken(request) {
  const match = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(request.headers.authorization ?? '');
  if (!match) throw new ApiError(401, 'UNAUTHORIZED');
  return match[1];
}

export function accessContext(row, capabilities) {
  return {
    schema_version: 1,
    user: { id: row.id, email: row.email, full_name: row.full_name },
    access_state: row.role_id ? 'ACTIVE' : 'NO_ROLE',
    role: row.role_id ? { id: row.role_id, name: row.role_name, display_name: row.role_display_name } : null,
    branch: row.branch_id ? { id: row.branch_id, code: row.branch_code, name: row.branch_name, is_active: Boolean(row.branch_active) } : null,
    capabilities: row.role_id ? capabilities : [],
  };
}

export function requireCapabilities(context, required, branchId = undefined) {
  if (context.access_state !== 'ACTIVE' || required.some(name => !context.capabilities.includes(name))) {
    throw new ApiError(403, 'FORBIDDEN');
  }
  // Use for future branch-scoped operations. Global catalog is not branch-scoped.
  if (branchId !== undefined && (!Number.isInteger(branchId) || !context.branch?.is_active || context.branch.id !== branchId)) {
    throw new ApiError(403, 'BRANCH_FORBIDDEN');
  }
}

export async function transaction(db, action, { readCommitted = false, administration = false } = {}) {
  const connection = await db.getConnection();
  let adminLock = false;
  try {
    // Acquire before actor/target locks. Two administrators must not each lock
    // themselves and then wait on the other, or race the last active OWNER.
    if (administration) {
      const [[lock]] = await connection.execute("SELECT GET_LOCK('vivero_staff_branch_admin', 5) AS acquired");
      if (lock.acquired !== 1) throw new ApiError(409, 'ADMINISTRATION_BUSY');
      adminLock = true;
    }
    if (readCommitted) await connection.query('SET TRANSACTION ISOLATION LEVEL READ COMMITTED');
    await connection.beginTransaction();
    const value = await action(connection);
    await connection.commit();
    return value;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    if (adminLock) {
      try { await connection.execute("SELECT RELEASE_LOCK('vivero_staff_branch_admin')"); }
      catch { connection.destroy(); }
    }
    connection.release();
  }
}

export async function consumeLoginBudget(db, email, address) {
  // Database counters survive restarts. Do not trust X-Forwarded-For.
  const exceeded = await transaction(db, async connection => {
    for (const [key, limit] of [[`ip:${address}`, 60], [`email:${email}`, 10]]) {
      const hash = sessionDigest(key);
      await connection.execute(
        `INSERT INTO auth_login_limits (key_hash, attempts, reset_at)
         VALUES (?, 1, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 15 MINUTE))
         ON DUPLICATE KEY UPDATE
         attempts = IF(reset_at <= UTC_TIMESTAMP(6), 1, LEAST(attempts + 1, 65535)),
         reset_at = IF(reset_at <= UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 15 MINUTE), reset_at)`, [hash],
      );
      const [[row]] = await connection.execute('SELECT attempts FROM auth_login_limits WHERE key_hash = ?', [hash]);
      if (row.attempts > limit) return true;
    }
    return false;
  });
  if (exceeded) throw new ApiError(429, 'LOGIN_RATE_LIMITED');
}

export function createAuth(db) {
  let passwordChecks = 0;
  return {
    async login(input, address) {
      const { email, password } = validateCredentials(input);
      await consumeLoginBudget(db, email, address);
      // Bound memory/CPU while using asynchronous scrypt; no unbounded KDF queue.
      if (passwordChecks >= 2) throw new ApiError(429, 'LOGIN_RATE_LIMITED');
      passwordChecks++;
      try {
        const [[user]] = await db.execute('SELECT id, password_hash, is_active FROM users WHERE email = ?', [email]);
        const valid = await verifyPassword(password, user?.password_hash);
        if (!valid || !user?.is_active) throw new ApiError(401, 'INVALID_CREDENTIALS');
        return await transaction(db, async connection => {
          // Check again under lock: a concurrent password reset/deactivation must win.
          const [[current]] = await connection.execute('SELECT password_hash, is_active FROM users WHERE id = ? FOR UPDATE', [user.id]);
          if (!current?.is_active || current.password_hash !== user.password_hash) throw new ApiError(401, 'INVALID_CREDENTIALS');
          const token = randomBytes(32).toString('base64url');
          await connection.execute('DELETE FROM auth_sessions WHERE user_id = ? AND expires_at <= UTC_TIMESTAMP(6)', [user.id]);
          await connection.execute(
            'INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR))',
            [user.id, sessionDigest(token)],
          );
          return { token_type: 'Bearer', access_token: token, expires_in: SESSION_SECONDS };
        });
      } finally { passwordChecks--; }
    },
    async withAccess(request, required, action, options = {}) {
      const hash = sessionDigest(bearerToken(request));
      return transaction(db, async connection => {
        const [[row]] = await connection.execute(
          `SELECT u.id, u.email, u.full_name, u.is_active, r.id AS role_id,
                  r.name AS role_name, r.display_name AS role_display_name,
                  b.id AS branch_id, b.code AS branch_code, b.name AS branch_name, b.is_active AS branch_active
           FROM auth_sessions s JOIN users u ON u.id = s.user_id
           LEFT JOIN roles r ON r.id = u.role_id LEFT JOIN branches b ON b.id = u.branch_id
           WHERE s.token_hash = ? AND s.expires_at > UTC_TIMESTAMP(6) FOR UPDATE`, [hash],
        );
        if (!row?.is_active) throw new ApiError(401, 'UNAUTHORIZED');
        const [permissions] = row.role_id ? await connection.execute(
          `SELECT p.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
           WHERE rp.role_id = ? ORDER BY p.name LOCK IN SHARE MODE`, [row.role_id],
        ) : [[]];
        const context = accessContext(row, permissions.map(permission => permission.name));
        if (required.length) requireCapabilities(context, required);
        return action(connection, context);
      }, options);
    },
    async logout(request) {
      const hash = sessionDigest(bearerToken(request));
      await db.execute('DELETE FROM auth_sessions WHERE token_hash = ?', [hash]);
      // Idempotent even for an expired/already revoked (well-formed) token.
      return { signed_out: true };
    },
  };
}
