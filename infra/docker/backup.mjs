import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { access, mkdir, open, writeFile, rename, stat, lstat, readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const fail = code => { throw new Error(code); };

export function argumentsForBackup(args) {
  const options = { overlays: [] };
  for (let i = 0; i < args.length; i++) {
    const flag = args[i];
    if (flag === '--acknowledge-downtime') {
      if (options.downtime) fail('DUPLICATE_ARGUMENT');
      options.downtime = true;
    } else if (['--env-file', '--project', '--output', '--overlay'].includes(flag)) {
      const value = args[++i];
      if (!value || value.startsWith('--')) fail('ARGUMENT_VALUE_REQUIRED');
      if (flag === '--overlay') options.overlays.push(resolve(value));
      else {
        const key = { '--env-file': 'envFile', '--project': 'project', '--output': 'output' }[flag];
        if (options[key]) fail('DUPLICATE_ARGUMENT');
        options[key] = key === 'project' ? value : resolve(value);
      }
    } else fail('UNKNOWN_ARGUMENT');
  }
  if (!options.downtime || !options.envFile || !options.output ||
      !/^[a-z0-9][a-z0-9_-]{0,62}$/.test(options.project ?? '')) fail('BACKUP_ARGUMENTS_REQUIRED');
  return options;
}

// No shell interpolation on the host. Never relay Docker stderr, env or SQL rows.
export async function docker(args, outputFile, inputFile) {
  const file = outputFile ? await open(outputFile, 'wx', 0o600) : null;
  try {
    return await new Promise((resolveRun, reject) => {
      const child = spawn('docker', args, { windowsHide: true, stdio: [inputFile ? 'pipe' : 'ignore', file ? file.fd : 'pipe', 'ignore'] });
      if (inputFile) {
        const input = createReadStream(inputFile);
        input.on('error', () => { child.kill(); reject(new Error('BACKUP_READ_FAILED')); });
        child.stdin.on('error', () => input.destroy());
        input.pipe(child.stdin);
        child.on('close', () => input.destroy());
      }
      let output = '', tooLarge = false;
      child.stdout?.on('data', chunk => {
        output += chunk.toString('utf8');
        if (output.length > 1_000_000) { tooLarge = true; child.kill(); }
      });
      child.on('error', () => reject(new Error('DOCKER_START_FAILED')));
      child.on('close', code => code === 0 && !tooLarge ? resolveRun(output) : reject(new Error('DOCKER_COMMAND_FAILED')));
    });
  } finally { await file?.close(); }
}

async function digest(path) {
  if (!(await stat(path)).size) fail('EMPTY_BACKUP_FILE');
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest('hex');
}

export async function verifyBackup(directory) {
  for (const name of ['COMPLETE', 'manifest.json', 'database.sql', 'catalog-images.tar.gz']) {
    if (!(await lstat(join(directory, name))).isFile()) fail('BACKUP_FILE_INVALID');
  }
  try { await access(join(directory, 'INCOMPLETE')); fail('BACKUP_INCOMPLETE'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const manifest = JSON.parse(await readFile(join(directory, 'manifest.json'), 'utf8'));
  if (manifest.schema_version !== 1 || !manifest.files ||
      Object.keys(manifest.files).sort().join(',') !== 'catalog-images.tar.gz,database.sql') fail('BACKUP_MANIFEST_INVALID');
  for (const name of ['database.sql', 'catalog-images.tar.gz']) {
    if (!/^[a-f0-9]{64}$/.test(manifest.files[name]) || await digest(join(directory, name)) !== manifest.files[name]) fail('BACKUP_HASH_MISMATCH');
  }
  return { schema_version: 1, verified_files: 2 };
}

export async function backup(options, run = docker) {
  await access(options.envFile);
  for (const overlay of options.overlays) await access(overlay);
  const endpoint = process.env.DOCKER_HOST || JSON.parse(await run(['context', 'inspect', '--format', '{{json .Endpoints.docker.Host}}']));
  if (typeof endpoint !== 'string' || !/^(unix:\/\/|npipe:\/\/)/.test(endpoint)) fail('LOCAL_DOCKER_REQUIRED');
  const compose = ['compose', '--env-file', options.envFile, '-f', join(here, 'compose.yaml'), '-f', join(here, 'compose.vps.yaml'),
    ...options.overlays.flatMap(path => ['-f', path]), '-p', options.project];
  const call = (args, outputFile) => run([...compose, ...args], outputFile);
  await call(['config', '--quiet']);
  const running = (await call(['ps', '--services', '--status', 'running'])).trim().split(/\s+/).filter(Boolean);
  if (!running.includes('db') || running.some(service => !['db', 'api', 'web'].includes(service))) fail('UNEXPECTED_RUNNING_SERVICES');
  const writers = running.filter(service => ['api', 'web'].includes(service));
  await mkdir(options.output, { recursive: true, mode: 0o700 });
  const directory = join(options.output, `vivero-${new Date().toISOString().replace(/[:.]/g, '-')}-${randomUUID()}`);
  await mkdir(directory, { mode: 0o700 });
  await writeFile(join(directory, 'INCOMPLETE'), 'Do not restore an incomplete backup.\n', { mode: 0o600, flag: 'wx' });
  let resume = false, backupFailure;
  try {
    // Mark before stop so partial stop failures also attempt to restore prior state.
    resume = writers.length > 0;
    if (resume) await call(['stop', '--timeout', '30', ...writers]);
    const stillRunning = (await call(['ps', '--services', '--status', 'running'])).trim().split(/\s+/).filter(Boolean);
    if (stillRunning.length !== 1 || stillRunning[0] !== 'db') fail('WRITERS_STILL_RUNNING');
    await call(['exec', '-T', 'db', 'sh', '-c',
      'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb-dump --user=root --single-transaction --routines --triggers --events --hex-blob --databases vivero'], join(directory, 'database.sql'));
    await call(['run', '--rm', '-T', '--no-deps', '--entrypoint', 'tar', 'api', '-C', '/data/catalog-images', '-czf', '-', '.'], join(directory, 'catalog-images.tar.gz'));
    const files = {};
    for (const name of ['database.sql', 'catalog-images.tar.gz']) files[name] = await digest(join(directory, name));
    await writeFile(join(directory, 'manifest.json'), JSON.stringify({ schema_version: 1, project: options.project, created_at: new Date().toISOString(), files }, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
    await writeFile(join(directory, 'INCOMPLETE'), 'Backup files complete. Restore rehearsal still required.\n', { mode: 0o600 });
    await rename(join(directory, 'INCOMPLETE'), join(directory, 'COMPLETE'));
  } catch (error) { backupFailure = error; }
  finally {
    if (resume) {
      try { await call(['start', ...writers]); }
      catch { fail('SERVICE_RESTART_FAILED_CHECK_PROJECT'); }
    }
  }
  if (backupFailure) throw backupFailure;
  return directory;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  let interrupted = false;
  const interrupt = () => { interrupted = true; };
  // Finish the in-flight operation, then resume services in finally before exiting.
  process.on('SIGINT', interrupt);
  process.on('SIGTERM', interrupt);
  try {
    if (process.argv[2] === '--verify' && process.argv.length === 4) {
      await verifyBackup(resolve(process.argv[3]));
      console.log('Both backup files match their manifest. This does not replace a restore rehearsal.');
    } else {
      const directory = await backup(argumentsForBackup(process.argv.slice(2)), (args, file) => {
        const resuming = args[0] === 'compose' && args[args.indexOf('-p') + 2] === 'start';
        if (interrupted && !resuming) fail('BACKUP_INTERRUPTED');
        return docker(args, file);
      });
      console.log(`Backup saved: ${directory}`);
      console.log('Contains private operational data. Keep encrypted copies outside the VPS and rehearse restoration.');
    }
  } catch (error) {
    const safe = /^[A-Z_]+$/.test(error?.message ?? '') ? error.message : 'BACKUP_FAILED';
    console.error(safe);
    process.exitCode = 1;
  } finally {
    process.removeListener('SIGINT', interrupt);
    process.removeListener('SIGTERM', interrupt);
    if (interrupted) process.exitCode = 130;
  }
}
