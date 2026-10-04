import { ApiError } from '../catalog.js';
import { consumeLoginBudget } from './service.js';
import { validateCredentials, verifyPassword, hashPassword } from './password.js';

export function passwordChangeInput(input) {
  if (!input || Array.isArray(input) || typeof input !== 'object' ||
      Object.keys(input).sort().join(',') !== 'current_password,new_password') throw new ApiError(400, 'INVALID_INPUT');
  const current = validateCredentials({ email: 'validation@example.invalid', password: input.current_password }).password;
  const next = validateCredentials({ email: 'validation@example.invalid', password: input.new_password }, true, 6).password;
  if (next.startsWith('replace-with-')) throw new ApiError(400, 'INVALID_INPUT');
  if (next === current) throw new ApiError(400, 'PASSWORD_UNCHANGED');
  return { current, next };
}

export function createPasswordChanger(db, auth) {
  let working = 0;
  return async (request, input, address) => {
    const { current, next } = passwordChangeInput(input);
    // Authenticate before allocating KDF work or consuming an account's budget.
    const email = await auth.withAccess(request, [], (_connection, context) => context.user.email);
    await consumeLoginBudget(db, email, address);
    if (working >= 2) throw new ApiError(429, 'LOGIN_RATE_LIMITED');
    working++;
    try {
      return await auth.withAccess(request, [], async (connection, context) => {
        const id = context.user.id;
        const [[user]] = await connection.execute('SELECT password_hash FROM users WHERE id=? FOR UPDATE', [id]);
        if (!await verifyPassword(current, user?.password_hash)) throw new ApiError(403, 'CURRENT_PASSWORD_INCORRECT');
        await connection.execute('UPDATE users SET password_hash=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?', [await hashPassword(next, 6), id]);
        // Existing trigger revokes sessions and recovery links in this transaction.
        await connection.execute('INSERT INTO administration_changes (actor_id,user_id,branch_id,action,previous_value,new_value) VALUES (?,?,NULL,?,NULL,?)',
          [id, id, 'USER_PASSWORD', JSON.stringify({ sessions_revoked: true, self_service: true })]);
        return { schema_version: 1, password_changed: true, sessions_revoked: true };
      }, { administration: true, readCommitted: true });
    } finally { working--; }
  };
}
