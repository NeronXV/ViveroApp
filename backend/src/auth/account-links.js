import { randomBytes } from 'node:crypto';
import { ApiError } from '../catalog.js';
import { hashPassword, normalizeEmail, validateCredentials } from './password.js';
import { consumeLoginBudget, requireCapabilities, sessionDigest, transaction } from './service.js';

export function accountLinkInput(value, invite = false) {
  const keys = invite ? ['email', 'name'] : ['email'];
  if (!value || Array.isArray(value) || typeof value !== 'object' || Object.keys(value).length !== keys.length || keys.some(k => !Object.hasOwn(value, k))) throw new ApiError(400, 'INVALID_INPUT');
  const email = normalizeEmail(value.email);
  if (!invite) return { email };
  if (typeof value.name !== 'string' || value.name.trim().length < 2 || value.name.trim().length > 160 || /[\x00-\x1f\x7f]/.test(value.name)) throw new ApiError(400, 'INVALID_INPUT');
  return { email, name: value.name.trim() };
}
export function accountLinkPassword(value) {
  if (!value || Array.isArray(value) || typeof value !== 'object' || Object.keys(value).length !== 2 || typeof value.token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(value.token) || !Object.hasOwn(value, 'password')) throw new ApiError(400, 'LINK_INVALID');
  const { password } = validateCredentials({ email: 'validation@example.invalid', password: value.password }, true);
  return { token: value.token, password };
}
export function createAccountLinks(db, mailer) {
  let hashing = 0;
  let pendingMail = 0;
  function configured() { if (!mailer) throw new ApiError(503, 'MAIL_UNAVAILABLE'); }
  async function issue(connection, user, purpose) {
    const token = randomBytes(32).toString('base64url');
    await connection.execute('DELETE FROM account_links WHERE user_id=?', [user.id]);
    await connection.execute('INSERT INTO account_links(user_id,token_hash,purpose,expires_at) VALUES(?,?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 MINUTE))', [user.id, sessionDigest(token), purpose]);
    return { email: user.email, token, purpose };
  }
  async function deliver(message) { if (message) await mailer(message); }
  return {
    async request(input, address) {
      configured();
      await consumeLoginBudget(db, `recovery:${input.email}`, `recovery:${address}`);
      if (pendingMail >= 32) throw new ApiError(503, 'MAIL_UNAVAILABLE');
      pendingMail++;
      let message;
      try { message = await transaction(db, async connection => {
        const [[user]] = await connection.execute('SELECT id,email,is_active,password_hash FROM users WHERE email=? FOR UPDATE', [input.email]);
        return user?.is_active && user.password_hash ? issue(connection, user, 'RESET') : null;
      }); } catch (error) { pendingMail--; throw error; }
      // A timeout can mean delivery succeeded. Retain the link; another request
      // replaces it. Public responses do not disclose account/delivery status.
      // Never wait for the provider in this public response: its latency would
      // otherwise reveal whether the address belongs to a known account.
      void deliver(message).catch(() => {}).finally(() => { pendingMail--; });
      return { accepted: true };
    },
    async invite(connection, context, input) {
      configured();
      requireCapabilities(context, ['MANAGE_USERS']);
      const [[existing]] = await connection.execute('SELECT id,email,password_hash,is_active,role_id,branch_id FROM users WHERE email=? FOR UPDATE', [input.email]);
      if (existing && (existing.password_hash || !existing.is_active || existing.role_id || existing.branch_id)) throw new ApiError(409, 'ACCOUNT_EXISTS');
      let user = existing;
      if (!user) {
        const [row] = await connection.execute('INSERT INTO users(email,full_name,is_active) VALUES(?,?,1)', [input.email, input.name]);
        user = { id: row.insertId, email: input.email };
        await connection.execute("INSERT INTO administration_changes(actor_id,user_id,action,new_value) VALUES(?,?,'USER_CREATE',?)", [context.user.id, user.id, JSON.stringify({ invited: true, role: null, branch: null })]);
      }
      return issue(connection, user, 'INVITE');
    },
    deliver,
    async complete(input) {
      const digest = sessionDigest(input.token);
      const [[candidate]] = await db.execute('SELECT user_id FROM account_links WHERE token_hash=? AND expires_at>UTC_TIMESTAMP(6)', [digest]);
      if (!candidate) throw new ApiError(400, 'LINK_INVALID');
      if (hashing >= 2) throw new ApiError(429, 'LOGIN_RATE_LIMITED');
      hashing++;
      try {
        const passwordHash = await hashPassword(input.password);
        return await transaction(db, async connection => {
          const [[user]] = await connection.execute('SELECT id,is_active FROM users WHERE id=? FOR UPDATE', [candidate.user_id]);
          const [[link]] = await connection.execute('SELECT id FROM account_links WHERE user_id=? AND token_hash=? AND expires_at>UTC_TIMESTAMP(6) FOR UPDATE', [candidate.user_id, digest]);
          if (!user?.is_active || !link) throw new ApiError(400, 'LINK_INVALID');
          await connection.execute('UPDATE users SET password_hash=? WHERE id=?', [passwordHash, user.id]);
          await connection.execute("INSERT INTO administration_changes(actor_id,user_id,action,new_value) VALUES(?,?,'USER_PASSWORD',?)", [user.id, user.id, JSON.stringify({ recovery: true, sessions_revoked: true })]);
          return { password_changed: true };
        });
      } finally { hashing--; }
    },
  };
}
