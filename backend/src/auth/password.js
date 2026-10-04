import { randomBytes, scrypt as nodeScrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { ApiError } from '../catalog.js';

const scrypt = promisify(nodeScrypt);
const options = { N: 131072, r: 8, p: 1, maxmem: 192 * 1024 * 1024 };
const format = /^scrypt\$131072\$8\$1\$([a-f0-9]{32})\$([a-f0-9]{128})$/;
// Missing/invalid hashes still incur the same KDF cost as a valid account.
const dummy = `scrypt$131072$8$1$${'00'.repeat(16)}$${'00'.repeat(64)}`;

export function normalizeEmail(value) {
  if (typeof value !== 'string') throw new ApiError(400, 'INVALID_INPUT');
  const email = value.trim().toLowerCase();
  if (email.length > 254 || !/^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,63}$/.test(email)) {
    throw new ApiError(400, 'INVALID_INPUT');
  }
  return email;
}

export function validateCredentials(value, newPassword = false, minimumLength = 15) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || Object.keys(value).some(key => !['email', 'password'].includes(key))) throw new ApiError(400, 'INVALID_INPUT');
  const email = normalizeEmail(value.email);
  if (typeof value.password !== 'string' || [...value.password].length < (newPassword ? minimumLength : 1)
      || [...value.password].length > 128 || value.password.includes('\0')) throw new ApiError(400, 'INVALID_INPUT');
  return { email, password: value.password }; // Never trim or normalize a password.
}

export async function hashPassword(password, minimumLength = 15) {
  validateCredentials({ email: 'validation@example.invalid', password }, true, minimumLength);
  const salt = randomBytes(16).toString('hex');
  const hash = await scrypt(password, Buffer.from(salt, 'hex'), 64, options);
  return `scrypt$131072$8$1$${salt}$${hash.toString('hex')}`;
}

export async function verifyPassword(password, stored) {
  const parsed = typeof stored === 'string' ? format.exec(stored) : null;
  const match = parsed ?? format.exec(dummy);
  const candidate = await scrypt(password, Buffer.from(match[1], 'hex'), 64, options);
  const equal = timingSafeEqual(candidate, Buffer.from(match[2], 'hex'));
  return Boolean(parsed && equal);
}
