#!/usr/bin/env python3
"""Manual encrypted upload of one verified Vivero backup; never deletes snapshots."""
import json
import os
from pathlib import Path
import stat
import subprocess
import sys

APP = Path('/srv/apps/vivero-dulcinea')
CONFIG = APP / 'shared/offsite.json'


def private_file(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != 0 or info.st_mode & 0o077:
        raise ValueError('PRIVATE_ROOT_FILE_REQUIRED')


def run():
    if os.geteuid() != 0:
        raise ValueError('ROOT_REQUIRED')
    if len(sys.argv) < 2 or sys.argv[1] not in ('init', 'upload', 'check'):
        raise ValueError('USE_INIT_UPLOAD_OR_CHECK')
    private_file(CONFIG)
    config = json.loads(CONFIG.read_text())
    if set(config) != {'repository', 'password_file', 'environment'}:
        raise ValueError('INVALID_CONFIG')
    repository = config['repository']
    # Deliberately reject local repositories and credentials embedded in S3 URLs.
    if not isinstance(repository, str) or 'replace' in repository:
        raise ValueError('EXTERNAL_DESTINATION_REQUIRED')
    if not (repository.startswith('sftp:') or
            (repository.startswith('s3:https://') and '@' not in repository)):
        raise ValueError('SFTP_OR_HTTPS_S3_REQUIRED')
    password = Path(config['password_file'])
    if password.parent.resolve() != (APP / 'shared').resolve():
        raise ValueError('PASSWORD_PATH_OUTSIDE_SHARED')
    private_file(password)
    if not password.read_bytes().strip():
        raise ValueError('EMPTY_ENCRYPTION_PASSWORD')
    extra = config['environment']
    allowed = {'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN',
               'AWS_DEFAULT_REGION'}
    if not isinstance(extra, dict) or set(extra) - allowed or any(
            not isinstance(v, str) for v in extra.values()):
        raise ValueError('INVALID_ENVIRONMENT')
    env = {'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root',
           'RESTIC_REPOSITORY': repository, 'RESTIC_PASSWORD_FILE': str(password),
           **extra}
    action = sys.argv[1]
    if action == 'init':
        if sys.argv[2:] != ['--acknowledge-new-repository']:
            raise ValueError('ACKNOWLEDGE_NEW_REPOSITORY_REQUIRED')
        args = ['init']
    elif action == 'check':
        if len(sys.argv) != 2:
            raise ValueError('INVALID_ARGUMENTS')
        args = ['check', '--read-data']
    else:
        if len(sys.argv) != 3:
            raise ValueError('SPECIFY_COMPLETE_BACKUP_DIRECTORY')
        backup = Path(sys.argv[2]).resolve(strict=True)
        if backup.parent != Path('/var/backups/vivero') or not backup.name.startswith('vivero-'):
            raise ValueError('BACKUP_OUTSIDE_PROJECT')
        subprocess.run([str(APP / 'ops/viveroctl.sh'), 'verify-backup', str(backup)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        args = ['backup', '--host', 'vivero-vps', '--tag', 'vivero-dulcinea', str(backup)]
    # Restic can report repository details; never relay raw output to chat/logs.
    subprocess.run(['/usr/bin/restic', *args], env=env, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print('PASS: offsite ' + action + '. No credentials or backup contents printed.')


if __name__ == '__main__':
    try:
        run()
    except (OSError, ValueError, TypeError, subprocess.SubprocessError):
        print('FAIL: check private configuration, permissions, Restic and destination on the VPS.',
              file=sys.stderr)
        sys.exit(1)
