import { access } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { docker, verifyBackup } from './backup.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const fail = code => { throw new Error(code); };
export function restoreArguments(args) {
  const result = { overlays: [] };
  for (let i = 0; i < args.length; i++) {
    const flag = args[i];
    if (flag === '--acknowledge-empty-target') {
      if (result.acknowledged) fail('DUPLICATE_ARGUMENT');
      result.acknowledged = true;
    } else {
      const key = { '--env-file': 'envFile', '--project': 'project', '--backup-dir': 'directory', '--overlay': 'overlay' }[flag];
      const value = args[++i];
      if (!key || !value || value.startsWith('--')) fail('RESTORE_ARGUMENTS_REQUIRED');
      if (key === 'overlay') result.overlays.push(resolve(value));
      else { if (result[key]) fail('DUPLICATE_ARGUMENT'); result[key] = key === 'project' ? value : resolve(value); }
    }
  }
  if (!result.acknowledged || !result.envFile || !result.directory || !/^[a-z0-9][a-z0-9_-]{0,62}$/.test(result.project ?? '')) fail('RESTORE_ARGUMENTS_REQUIRED');
  return result;
}

export async function restore(options, run = docker) {
  await verifyBackup(options.directory);
  await access(options.envFile);
  for (const overlay of options.overlays) await access(overlay);
  const endpoint = process.env.DOCKER_HOST || JSON.parse(await run(['context', 'inspect', '--format', '{{json .Endpoints.docker.Host}}']));
  if (typeof endpoint !== 'string' || !/^(unix:\/\/|npipe:\/\/)/.test(endpoint)) fail('LOCAL_DOCKER_REQUIRED');
  const compose = ['compose', '--env-file', options.envFile, '-f', join(here, 'compose.yaml'), '-f', join(here, 'compose.vps.yaml'), ...options.overlays.flatMap(path => ['-f', path]), '-p', options.project];
  const call = (args, input) => run([...compose, ...args], undefined, input);
  const query = sql => call(['exec', '-T', 'db', 'sh', '-c', 'export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"; exec mariadb --user=root --batch --skip-column-names --database=vivero --execute="$1"', 'restore-guard', sql]);
  await call(['config', '--quiet']);
  const running = (await call(['ps', '--services', '--status', 'running'])).trim().split(/\s+/).filter(Boolean);
  if (running.length !== 1 || running[0] !== 'db') fail('ONLY_TARGET_DB_MAY_RUN');
  const tables = (await query("SELECT table_name FROM information_schema.tables WHERE table_schema='vivero' ORDER BY table_name")).trim().split(/\s+/);
  if (!tables.includes('users') || !tables.includes('sales') || tables.some(name => !/^[a-z][a-z0-9_]*$/.test(name))) fail('TARGET_SCHEMA_INVALID');
  const reference = ['roles', 'permissions', 'role_permissions', 'schema_migrations'];
  // Migration 031 initializes this singleton even on a target with no operations.
  // An allocated, missing or additional counter row is never an empty target.
  if (tables.includes('sale_folio_counter')) {
    const pristine = (await query('SELECT IF(COUNT(*)=1 AND MIN(id)=1 AND MAX(id)=1 AND MIN(last_value)=0 AND MAX(last_value)=0,1,0) FROM sale_folio_counter')).trim();
    if (pristine !== '1') fail('TARGET_CONTAINS_DATA');
    reference.push('sale_folio_counter');
  }
  const operational = tables.filter(name => !reference.includes(name));
  const total = (await query('SELECT ' + operational.map(name => `(SELECT COUNT(*) FROM ${name})`).join('+'))).trim();
  if (total !== '0') fail('TARGET_CONTAINS_DATA');
  await call(['run', '--rm', '-T', '--no-deps', '--entrypoint', 'sh', 'api', '-c', 'test -z "$(find /data/catalog-images -mindepth 1 -print -quit)"']);
  const archive = join(options.directory, 'catalog-images.tar.gz');
  const entries = (await call(['run', '--rm', '-T', '--no-deps', '--entrypoint', 'tar', 'api', '-tzf', '-'], archive)).trim().split(/\r?\n/);
  if (!entries.length || entries.some(name => name !== './' && !/^\.\/[a-f0-9]{64}\.webp$/.test(name))) fail('UNSAFE_IMAGE_ARCHIVE');
  const types = (await call(['run', '--rm', '-T', '--no-deps', '--entrypoint', 'tar', 'api', '-tvzf', '-'], archive)).trim().split(/\r?\n/);
  if (types.some(line => !/^[-d]/.test(line))) fail('UNSAFE_IMAGE_ARCHIVE');
  // This imports SQL only after proving the selected target has no operational rows.
  // Restore trusted backups made by this application, not arbitrary SQL archives.
  await call(['exec', '-T', 'db', 'sh', '-c', 'export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"; exec mariadb --user=root --binary-mode'], join(options.directory, 'database.sql'));
  await call(['run', '--rm', '-T', '--no-deps', '--entrypoint', 'tar', 'api', '--no-same-owner', '--no-same-permissions', '-C', '/data/catalog-images', '-xzf', '-'], archive);
  return { restored_files: 2 };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    await restore(restoreArguments(process.argv.slice(2)));
    console.log('SQL and images restored to the empty target. API/Web remain stopped. Reconcile data before starting them.');
  } catch (error) {
    console.error(/^[A-Z_]+$/.test(error?.message ?? '') ? error.message : 'RESTORE_FAILED_KEEP_TARGET_OFFLINE');
    process.exitCode = 1;
  }
}
