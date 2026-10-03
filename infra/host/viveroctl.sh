#!/bin/sh
set -eu
app=/srv/apps/vivero-dulcinea
source=$(readlink -f "$app/current")/ViveroApp
compose() {
  docker compose --env-file "$app/shared/.env.vps" -p vivero-vps \
    -f "$source/infra/docker/compose.yaml" -f "$source/infra/docker/compose.vps.yaml" \
    -f "$app/ops/compose.host.yaml" "$@"
}
maintenance() {
  script=$1
  shift
  docker run --rm --user 0 \
    --mount type=bind,src=/srv,dst=/srv \
    --mount type=bind,src=/opt/vivero,dst=/opt/vivero \
    --mount type=bind,src=/var/backups/vivero,dst=/var/backups/vivero \
    --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
    --mount type=bind,src=/usr/bin/docker,dst=/usr/bin/docker,readonly \
    --mount type=bind,src=/usr/libexec/docker/cli-plugins,dst=/usr/libexec/docker/cli-plugins,readonly \
    --workdir "$source" node:24-bookworm-slim node "infra/docker/$script" "$@"
}
case ${1:-status} in
  status) compose ps ;;
  validate) compose config --quiet ;;
  logs)
    case ${2:-} in api|web|db) compose logs --tail 100 "${2}" ;; *) echo 'Specify api, web or db' >&2; exit 2 ;; esac ;;
  proxy-check) docker exec vivero-vps-web-1 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile ;;
  proxy-reload)
    docker exec vivero-vps-web-1 caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
    docker exec vivero-vps-web-1 caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile ;;
  backup|verify-backup)
    action=$1
    if [ "$action" = verify-backup ]; then
      [ "$#" = 2 ] || { echo 'Specify absolute backup directory' >&2; exit 2; }
      candidate=$(readlink -f "$2")
      case "$candidate" in /var/backups/vivero/vivero-*) ;; *) echo 'Backup path outside this project' >&2; exit 2 ;; esac
      set -- --verify "$candidate"
    else
      [ "${2:-}" = --acknowledge-downtime ] || { echo 'Backup pauses API/Web; pass --acknowledge-downtime' >&2; exit 2; }
      set -- --env-file "$app/shared/.env.vps" --project vivero-vps --output "$app/backups" \
        --overlay "$app/ops/compose.host.yaml" --acknowledge-downtime
    fi
    # Runtime and Docker are already installed. Secrets remain on this host.
    maintenance backup.mjs "$@" ;;
  restore-isolated)
    [ "$#" = 6 ] && [ "$6" = --acknowledge-empty-target ] || { echo 'Use restore-isolated ENV PROJECT BACKUP OVERLAY --acknowledge-empty-target' >&2; exit 2; }
    case "$3" in vivero-restore-*) ;; *) echo 'Only an isolated vivero-restore-* project is allowed' >&2; exit 2 ;; esac
    envfile=$(readlink -f "$2")
    overlay=$(readlink -f "$5")
    candidate=$(readlink -f "$4")
    for privatefile in "$envfile" "$overlay"; do
      case "$privatefile" in /opt/vivero/maintenance/*) ;; *) echo 'Restore configuration must be in project maintenance' >&2; exit 2 ;; esac
    done
    case "$candidate" in /var/backups/vivero/vivero-*) ;; *) echo 'Backup path outside this project' >&2; exit 2 ;; esac
    maintenance restore.mjs --env-file "$envfile" --project "$3" --backup-dir "$candidate" --overlay "$overlay" --acknowledge-empty-target ;;
  *) echo 'Use status, validate, logs SERVICE, proxy-check, proxy-reload, backup --acknowledge-downtime, verify-backup PATH or restore-isolated' >&2; exit 2 ;;
esac
