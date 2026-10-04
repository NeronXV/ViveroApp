import mysql from 'mysql2/promise';

export async function openAdminDb({ multipleStatements = false } = {}) {
  // These tools intentionally cannot target a remote host/VPS by accident.
  if (process.env.NODE_ENV !== 'development' || process.env.DB_HOST !== 'db'
      || process.env.DB_NAME !== 'vivero' || process.env.DB_USER !== 'root') {
    throw new Error('Maintenance commands require the local Compose tools profile');
  }
  return mysql.createConnection({
    host: 'db', database: 'vivero', user: 'root', password: process.env.DB_PASSWORD,
    multipleStatements, timezone: 'Z', charset: 'utf8mb4',
  });
}
