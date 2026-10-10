import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { argumentsForBackup, backup, verifyBackup } from '../../infra/docker/backup.mjs';
import { restore, restoreArguments } from '../../infra/docker/restore.mjs';

async function fixture(t, failedOperation, running = ['db', 'api', 'web']) {
  const root = await mkdtemp(join(tmpdir(), 'vivero-backup-test-'));
  // Only the new directory returned by mkdtemp is removed, never the temp root.
  t.after(() => rm(root, { recursive: true, force: true }));
  const envFile = join(root, 'synthetic.env');
  await writeFile(envFile, '# synthetic; no passwords\n');
  const options = { envFile, project: 'synthetic-test', output: root, overlays: [], downtime: true };
  const calls = [];
  let stopped = false;
  const run = async (args, file) => {
    calls.push(args);
    if (args[0] === 'context') return JSON.stringify('unix:///var/run/docker.sock');
    const operation = args[args.indexOf('-p') + 2];
    if (operation === 'stop') stopped = true;
    if (operation === failedOperation) throw new Error('SYNTHETIC_FAILURE');
    if (operation === 'ps') return (stopped ? ['db'] : running).join('\n');
    if (file) await writeFile(file, operation === 'exec' ? '-- synthetic SQL\n' : 'synthetic image archive');
    return '';
  };
  return { root, options, run, calls };
}

test('backup requires explicit project, private env path, output and downtime acknowledgement', () => {
  const args = ['--env-file', 'fixture.env', '--project', 'vivero-test', '--output', 'backup'];
  assert.throws(() => argumentsForBackup(args));
  assert.equal(argumentsForBackup([...args, '--acknowledge-downtime']).project, 'vivero-test');
  assert.throws(() => argumentsForBackup([...args, '--acknowledge-downtime', '--project', 'other']));
  assert.throws(() => argumentsForBackup([...args, '--acknowledge-downtime', '--unknown']));
});

test('backup freezes API/Web, captures SQL and images, hashes files and resumes only prior services', async t => {
  const f = await fixture(t);
  const directory = await backup(f.options, f.run);
  const manifest = JSON.parse(await readFile(join(directory, 'manifest.json'), 'utf8'));
  assert.equal(manifest.project, 'synthetic-test');
  for (const [name, sha] of Object.entries(manifest.files)) {
    assert.equal(sha, createHash('sha256').update(await readFile(join(directory, name))).digest('hex'));
  }
  assert.ok((await readdir(directory)).includes('COMPLETE'));
  assert.ok(!(await readdir(directory)).includes('INCOMPLETE'));
  const operations = f.calls.filter(a => a[0] === 'compose').map(a => a[a.indexOf('-p') + 2]);
  assert.deepEqual(operations, ['config', 'ps', 'stop', 'ps', 'exec', 'run', 'start']);
  assert.deepEqual(f.calls.at(-1).slice(-3), ['start', 'api', 'web']);
  assert.deepEqual(await verifyBackup(directory), { schema_version: 1, verified_files: 2 });
  await writeFile(join(directory, 'database.sql'), 'modified');
  await assert.rejects(verifyBackup(directory), /BACKUP_HASH_MISMATCH/);
});

test('dump failure preserves an incomplete folder and resumes services', async t => {
  const f = await fixture(t, 'exec');
  await assert.rejects(backup(f.options, f.run), /SYNTHETIC_FAILURE/);
  const directory = (await readdir(f.root)).find(name => name.startsWith('vivero-'));
  assert.deepEqual(await readdir(join(f.root, directory)), ['INCOMPLETE']);
  assert.deepEqual(f.calls.at(-1).slice(-3), ['start', 'api', 'web']);
});

test('partially failed stop still attempts restart and an originally stopped API stays stopped', async t => {
  const f = await fixture(t, 'stop', ['db', 'web']);
  await assert.rejects(backup(f.options, f.run), /SYNTHETIC_FAILURE/);
  assert.deepEqual(f.calls.at(-1).slice(-2), ['start', 'web']);
});

test('an additional running writer blocks the backup before any service is stopped', async t => {
  const f = await fixture(t, undefined, ['db', 'api', 'migrate']);
  await assert.rejects(backup(f.options, f.run), /UNEXPECTED_RUNNING_SERVICES/);
  assert.equal(f.calls.some(args => args.includes('stop')), false);
});

test('restart failure is reported even after creating valid backup files', async t => {
  const f = await fixture(t, 'start');
  await assert.rejects(backup(f.options, f.run), /SERVICE_RESTART_FAILED_CHECK_PROJECT/);
  const directory = (await readdir(f.root)).find(name => name.startsWith('vivero-'));
  await verifyBackup(join(f.root, directory));
});

test('restore requires explicit empty-target acknowledgement', () => {
  const args = ['--env-file', 'fixture.env', '--project', 'new-target', '--backup-dir', 'backup'];
  assert.throws(() => restoreArguments(args));
  assert.equal(restoreArguments([...args, '--acknowledge-empty-target']).project, 'new-target');
});

test('restore refuses occupied or active targets and unsafe archives before importing SQL', async t => {
  const f = await fixture(t);
  const directory = await backup(f.options, f.run);
  const options = { ...f.options, directory, acknowledged: true };
  for (const scenario of ['occupied', 'active', 'unsafe', 'valid']) {
    let imported = false;
    const run = async (args, _output, input) => {
      if (args[0] === 'context') return JSON.stringify('unix:///var/run/docker.sock');
      const op = args[args.indexOf('-p') + 2];
      if (op === 'ps') return scenario === 'active' ? 'db\napi' : 'db';
      if (op === 'exec' && args.at(-1).includes('information_schema')) return 'users\nsales\nroles\nschema_migrations';
      if (op === 'exec' && args.at(-1).startsWith('SELECT ')) return scenario === 'occupied' ? '1' : '0';
      if (args.includes('-tzf')) return scenario === 'unsafe' ? '../unexpected' : './';
      if (args.includes('-tvzf')) return 'drwx------ 0/0 0 2026-10-02 00:00 ./';
      if (op === 'exec' && input) imported = true;
      return '';
    };
    if (scenario === 'valid') {
      assert.deepEqual(await restore(options, run), { restored_files: 2 });
      assert.equal(imported, true);
    } else {
      await assert.rejects(restore(options, run), /TARGET_CONTAINS_DATA|ONLY_TARGET_DB_MAY_RUN|UNSAFE_IMAGE_ARCHIVE/);
      assert.equal(imported, false);
    }
  }
});

test('restore accepts only the pristine migration 031 counter and still rejects operations', async t => {
  const f = await fixture(t);
  const directory = await backup(f.options, f.run);
  for (const scenario of ['pristine', 'allocated', 'missing', 'extra', 'occupied']) {
    let imported = false;
    const run = async (args, _output, input) => {
      if (args[0] === 'context') return JSON.stringify('unix:///var/run/docker.sock');
      const op = args[args.indexOf('-p') + 2];
      if (op === 'ps') return 'db';
      if (op === 'exec' && args.at(-1).includes('information_schema')) return 'users\nsales\nroles\nschema_migrations\nsale_folio_counter\nsale_folio_aliases';
      if (op === 'exec' && args.at(-1).includes('MIN(last_value)')) {
        assert.match(args.at(-1), /COUNT\(\*\)=1 AND MIN\(id\)=1 AND MAX\(id\)=1/);
        assert.match(args.at(-1), /MIN\(last_value\)=0 AND MAX\(last_value\)=0/);
        return ['pristine', 'occupied'].includes(scenario) ? '1' : '0';
      }
      if (op === 'exec' && args.at(-1).startsWith('SELECT ')) {
        assert.ok(!args.at(-1).includes('FROM sale_folio_counter'));
        assert.ok(args.at(-1).includes('FROM sale_folio_aliases'));
        return scenario === 'occupied' ? '1' : '0';
      }
      if (args.includes('-tzf')) return './';
      if (args.includes('-tvzf')) return 'drwx------ 0/0 0 2026-10-02 00:00 ./';
      if (op === 'exec' && input) imported = true;
      return '';
    };
    const options = {...f.options, directory, acknowledged:true};
    if (scenario === 'pristine') {
      assert.deepEqual(await restore(options, run), {restored_files:2});
      assert.equal(imported, true);
    } else {
      await assert.rejects(restore(options, run), /TARGET_CONTAINS_DATA/);
      assert.equal(imported, false);
    }
  }
});
