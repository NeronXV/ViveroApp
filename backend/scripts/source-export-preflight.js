import { createHash } from 'node:crypto';
import { SOURCE_TABLES, LEGACY_ABSENT_TABLES } from './source-export-tables.js';

export class SourceExportError extends Error {
  constructor(code) { super(code); this.code = code; }
}
const fail = code => { throw new SourceExportError(code); };
function record(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail('EXPORT_SHAPE_INVALID');
  return value;
}
function columns(value) {
  if (!Array.isArray(value) || value.length === 0 || value.some(c => typeof c !== 'string' || !/^[a-z][a-z0-9_]*$/.test(c)) || new Set(value).size !== value.length) fail('EXPORT_CONSTRAINT_INVALID');
  return value;
}
function tuple(row, cols) {
  if (cols.some(c => !Object.hasOwn(row, c))) fail('EXPORT_COLUMN_MISSING');
  return cols.map(c => row[c]);
}
// This checks a private source snapshot only. It neither connects nor writes to a database.
export function inspectSourceExport(bytes) {
  if (!Buffer.isBuffer(bytes) || bytes.length === 0 || bytes.length > 500 * 1024 * 1024) fail('EXPORT_SIZE_INVALID');
  let root;
  try { root = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)); } catch { fail('EXPORT_JSON_INVALID'); }
  record(root);
  if (![1, 2].includes(root.schema_version) || root.authority !== 'supabase-export' || typeof root.exported_at !== 'string' || !/^\d{4}-\d{2}-\d{2}T/.test(root.exported_at) || !Number.isFinite(Date.parse(root.exported_at))) fail('EXPORT_VERSION_INVALID');
  const tables = record(root.tables), keys = Object.keys(tables);
  const absent = root.schema_version === 2 ? root.absent_tables : [];
  if (!Array.isArray(absent) || new Set(absent).size !== absent.length || absent.some(name => !LEGACY_ABSENT_TABLES.includes(name) || Object.hasOwn(tables, name)) || (root.schema_version === 1 && Object.hasOwn(root, 'absent_tables'))) fail('EXPORT_ABSENT_TABLES_INVALID');
  const required = SOURCE_TABLES.filter(name => !absent.includes(name));
  if (keys.length !== required.length || required.some(name => !Object.hasOwn(tables, name))) fail('EXPORT_TABLES_INCOMPLETE');
  for (const name of required) {
    const rows = tables[name];
    if (!Array.isArray(rows)) fail('EXPORT_SHAPE_INVALID');
    for (const row of rows) {
      record(row);
      for (const [column, value] of Object.entries(row)) {
        if (['encrypted_password', 'password_hash', 'access_token', 'refresh_token', 'claim_token', 'confirmation_token', 'unsubscribe_token'].includes(column)) fail('EXPORT_CREDENTIALS_FORBIDDEN');
        if (column.endsWith('_cents') && value !== null && (typeof value !== 'string' || !/^-?\d+$/.test(value) || BigInt(value) < -9223372036854775808n || BigInt(value) > 9223372036854775807n)) fail('EXPORT_MONEY_INVALID');
      }
    }
  }
  if (tables.auth_users.some(row => Object.keys(row).length !== 2 || !Object.hasOwn(row, 'id') || !Object.hasOwn(row, 'email'))) fail('EXPORT_IDENTITY_INVALID');
  if (!Array.isArray(root.primary_keys) || !Array.isArray(root.foreign_keys)) fail('EXPORT_CONSTRAINT_INVALID');
  const primary = [{ table: 'auth_users', columns: ['id'] }, ...root.primary_keys];
  const covered = new Set();
  for (const constraint of primary) {
    record(constraint);
    if (!Object.hasOwn(tables, constraint.table) || covered.has(constraint.table)) fail('EXPORT_CONSTRAINT_INVALID');
    covered.add(constraint.table);
    const cols = columns(constraint.columns), seen = new Set();
    for (const row of tables[constraint.table]) {
      const values = tuple(row, cols), key = JSON.stringify(values);
      if (values.some(v => v === null || typeof v === 'object') || seen.has(key)) fail('EXPORT_PRIMARY_KEY_INVALID');
      seen.add(key);
    }
  }
  if (required.some(name => !covered.has(name))) fail('EXPORT_PRIMARY_KEYS_INCOMPLETE');
  const indexes = new Map();
  for (const constraint of root.foreign_keys) {
    record(constraint);
    const cols = columns(constraint.columns), referenced = columns(constraint.referenced_columns);
    if (cols.length !== referenced.length || !Object.hasOwn(tables, constraint.table) || !Object.hasOwn(tables, constraint.referenced_table)) fail('EXPORT_CONSTRAINT_INVALID');
    const indexName = JSON.stringify([constraint.referenced_table, referenced]);
    let index = indexes.get(indexName);
    if (!index) { index = new Set(tables[constraint.referenced_table].map(row => JSON.stringify(tuple(row, referenced)))); indexes.set(indexName, index); }
    for (const row of tables[constraint.table]) {
      const values = tuple(row, cols);
      // Authoritative source FKs use MATCH SIMPLE: any null omits the lookup.
      if (!values.includes(null) && !index.has(JSON.stringify(values))) fail('EXPORT_FOREIGN_KEY_ORPHAN');
    }
  }
  return { schema_version: root.schema_version, absent_tables: absent, input_sha256: createHash('sha256').update(bytes).digest('hex'),
    source_validated: true, import_applied: false,
    tables: required.map(name => ({ name, row_count: tables[name].length })),
    primary_keys_checked: primary.length, foreign_keys_checked: root.foreign_keys.length,
    pending: ['MariaDB mapping and compatibility validation', 'Private image files and hashes', 'Password reset for imported accounts', 'Final snapshot after pending operations are resolved'] };
}
