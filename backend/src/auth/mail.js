import { ApiError } from '../catalog.js';
import { sessionDigest } from './service.js';

// Server-only credentials; never log provider responses or link tokens.
export function createResendSender({ apiKey, fetcher = fetch }) {
  if (!apiKey) return null;
  if (apiKey.startsWith('replace-with-')) throw new Error('Invalid mail configuration');
  return async (payload, key) => {
    try {
      const response = await fetcher('https://api.resend.com/emails', {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(8000),
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', 'Idempotency-Key': key },
        body: JSON.stringify(payload),
      });
      if (!response.ok || typeof (await response.json()).id !== 'string') throw new Error('Mail rejected');
    } catch { throw new ApiError(503, 'MAIL_UNAVAILABLE'); }
  };
}
export function createAccountMailer({ apiKey, from, origin, fetcher = fetch }) {
  if (!apiKey || !from || !origin) return null;
  if (/[\r\n]/.test(from)) throw new Error('Invalid account mail configuration');
  const send = createResendSender({ apiKey, fetcher });
  return async ({ email, token, purpose }) => {
    try { await send({ from, to: [email], subject: purpose === 'INVITE' ? 'Invitación a Vivero Dulcinea' : 'Recuperar acceso a Vivero Dulcinea',
      text: `Establece tu contraseña en ${origin}/recuperar#token=${token}\nEl enlace vence en 30 minutos y solo puede usarse una vez. Si no solicitaste este correo, ignóralo.` }, `account-${sessionDigest(token).toString('hex')}`); }
    catch { throw new ApiError(503, 'MAIL_UNAVAILABLE'); }
  };
}
