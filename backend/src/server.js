import mysql from 'mysql2/promise';
import { createApp } from './app.js';
import { createImageStore } from './images.js';
import { browserOrigin } from './browser-origin.js';
import { createAccountMailer, createResendSender } from './auth/mail.js';

for (const key of ['DB_HOST', 'DB_NAME', 'DB_USER', 'DB_PASSWORD']) {
  if (!process.env[key] || process.env[key].startsWith('replace-with-')) throw new Error(`Configure ${key}`);
}
const port = Number(process.env.PORT ?? 3001);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid PORT');
const db = mysql.createPool({
  host: process.env.DB_HOST,
  database: process.env.DB_NAME,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  connectionLimit: 5,
  queueLimit: 20,
  connectTimeout: 5000,
  supportBigNumbers: true,
  bigNumberStrings: true,
  timezone: 'Z',
  charset: 'utf8mb4',
});
const imageStore = createImageStore(process.env.IMAGE_STORAGE_DIR);
await imageStore.check();
const webOrigin = browserOrigin(process.env.WEB_ORIGIN, process.env.NODE_ENV === 'development');
const webOriginAliases = (process.env.WEB_ORIGIN_ALIASES ?? '').split(',').filter(Boolean)
  .map(value => browserOrigin(value, process.env.NODE_ENV === 'development'));
const accountMailer = createAccountMailer({ apiKey: process.env.RESEND_API_KEY, from: process.env.ACCOUNT_MAIL_FROM, origin: webOrigin });
const newsletterConfig = { send: createResendSender({ apiKey: process.env.RESEND_API_KEY }), from: process.env.NEWSLETTER_FROM, origin: webOrigin, linkKey: process.env.NEWSLETTER_LINK_KEY };
const server = createApp({ db, imageStore, webOrigin, webOriginAliases, accountMailer, newsletterConfig });
server.listen(port, '0.0.0.0', () => console.log(`Vivero API listening on port ${port}`));
let stopping = false;
function stop() {
  if (stopping) return;
  stopping = true;
  const deadline = setTimeout(() => process.exit(1), 10000).unref();
  server.close(async () => {
    await db.end();
    clearTimeout(deadline);
  });
}
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
