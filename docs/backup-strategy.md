# Estrategia de Backups — Vivero Dulcinea

**Fecha:** 5 de octubre de 2026  
**Estado:** SCRIPTS IMPLEMENTADOS — AUTOMATIZACIÓN Y OFFSITE PENDIENTES

---

## RESUMEN EJECUTIVO

| Qué | Implementación | Ubicación |
|---|---|---|
| **Backup BD + imágenes** | Script `backup.mjs` (Node) | `infra/docker/backup.mjs` |
| **Restauración verificada** | Script `restore.mjs` (Node) | `infra/docker/restore.mjs` |
| **Verificación de integridad** | `verifyBackup()` en `backup.mjs` | Hash SHA-256 + manifest |
| **Offsite (R2 + Restic)** | Script `offsite-backup.py` | `infra/host/offsite-backup.py` (preparado, no configurado) |
| **Wrapper VPS** | `viveroctl.sh backup` | `/srv/apps/vivero-dulcinea/ops/viveroctl.sh` |

---

## MARIADB

### Dump manual (usando script existente)
```bash
# Desde VPS (requiere sudo y Docker autorizado)
sudo /srv/apps/vivero-dulcinea/ops/viveroctl.sh backup --acknowledge-downtime
```
Esto:
1. Pausa `api` y `web` (30s timeout)
2. Ejecuta `mariadb-dump --single-transaction --routines --triggers --events --hex-blob --databases vivero`
3. Empaqueta `/data/catalog-images` en `catalog-images.tar.gz`
4. Genera `manifest.json` con SHA-256 de ambos archivos
5. Marca `COMPLETE` solo si todo OK
6. Reanuda `api` y `web`

**Salida:** Directorio en `/var/backups/vivero/vivero-<timestamp>-<uuid>/` con:
- `database.sql` — dump completo
- `catalog-images.tar.gz` — imágenes de catálogo
- `manifest.json` — `{schema_version:1, project, created_at, files:{database.sql:<sha256>, catalog-images.tar.gz:<sha256>}}`
- `COMPLETE` — marcador de finalización exitosa

### Backup automatizable (recomendación)
**Frecuencia sugerida:**
- **Diario:** 03:00 UTC (ventana baja)
- **Semanal:** Domingo 02:00 UTC (retener 4)
- **Mensual:** Día 1 01:00 UTC (retener 6)

**Implementación pendiente:** systemd timer o cron en host que invoque `viveroctl.sh backup --acknowledge-downtime`
> ⚠️ Requiere ventana de mantenimiento (API/Web caen ~30-60s). Coordinar con operación.

### Restauración
```bash
# SOLO EN ENTORNO AISLADO (nunca en producción directa)
# 1. Preparar proyecto Compose vacío con .env.restore y compose.restore.yaml
# 2. Ejecutar:
/srv/apps/vivero-dulcinea/ops/viveroctl.sh restore-isolated \
  /opt/vivero/maintenance/NUEVO_ENSAYO/.env.restore \
  vivero-restore-nuevo /var/backups/vivero/LOTE \
  /opt/vivero/maintenance/NUEVO_ENSAYO/compose.restore.yaml \
  --acknowledge-empty-target
```
Validaciones automáticas del script `restore.mjs`:
- Verifica `manifest.json` y hashes SHA-256
- Confirma que BD destino está vacía (solo tablas de referencia: roles, permissions, schema_migrations)
- Valida que `catalog-images.tar.gz` solo contiene archivos `.webp` con nombres hash
- Importa SQL en modo binario
- Extrae imágenes preservando permisos

### Validación de un backup
```bash
# Verificar integridad sin restaurar
node infra/docker/backup.mjs --verify /var/backups/vivero/LOTE
# Salida esperada: "Both backup files match their manifest. This does not replace a restore rehearsal."
```
> **Nota:** La verificación de hash NO sustituye un ensayo de restauración real. Hacer restore rehearsal mensual.

---

## ARCHIVOS (VOLÚMENES DOCKER)

| Volumen | Contenido | Incluido en backup | Notas |
|---|---|---|---|
| `vivero-vps_mariadb_data` | Datos MariaDB (`/var/lib/mysql`) | **SÍ** (via `mariadb-dump`) | No copiar archivos raw; usar dump lógico |
| `vivero-vps_catalog_images` | Imágenes productos (`/data/catalog-images`) | **SÍ** (tar.gz) | ~0-10 MB actual; crecerá con catálogo |
| `vivero-vps_caddy_data` | Certificados TLS, OCSP, Caddy state | **NO** | Respaldar por separado (ver abajo) |
| `vivero-vps_caddy_config` | Config Caddy runtime | **NO** | Respaldar por separado (ver abajo) |

### Volúmenes NO cubiertos por backup automático
- **TLS/Caddy:** `/var/backups/vivero` no incluye certificados Let's Encrypt ni config Caddy
- **Secrets/Config:** `.env.vps`, `.env.bootstrap`, `offsite.json`, claves SSH
- **Acción:** Incluir en backup offsite cifrado (Restic) o respaldo manual separado

---

## CONFIGURACIÓN

### Archivos que DEBEN respaldarse (fuera de Git)
| Archivo | Ubicación | Secreto | Frecuencia |
|---|---|---|---|
| `.env.vps` | `/opt/vivero/shared/.env.vps` | **SÍ** | Cada cambio |
| `.env.bootstrap` | `/opt/vivero/shared/.env.bootstrap` | **SÍ** | Cada cambio |
| `offsite.json` (R2 config) | `/opt/vivero/shared/offsite.json` | **SÍ** | Cada cambio |
| `offsite.password` (Restic) | `/opt/vivero/shared/offsite.password` | **SÍ** | Cada cambio |
| `key.properties` (Android) | Local de Toni/Pedro | **SÍ** | Cada cambio |
| `keystore.jks` (Android) | Local de Toni/Pedro | **SÍ** | Cada cambio |
| Claves SSH privadas | `~/.ssh/` de cada admin | **SÍ** | Rotación anual |

### Archivos que NO deben subirse a Git
- Cualquier `.env` real (`.env`, `.env.vps`, `.env.bootstrap`, `.env.local`)
- `key.properties`, `keystore.jks`
- `offsite.json`, `offsite.password`
- Backups (`/var/backups/vivero/*`)
- APKs firmados
- `local.properties` con URLs reales

### Manejo de `.env` y secretos
- **Plantillas versionadas:** `.env.example`, `vps.env.example` (solo marcadores `replace-with-`)
- **Valores reales:** Solo en archivos ignorados por Git, modo 600, fuera del repo
- **Entrega a Toni:** Pedro comparte vía gestor de contraseñas o canal privado; **nunca** por chat/Git/email
- **Rotación:** Cambiar `MARIADB_PASSWORD`, `RESEND_API_KEY`, `NEWSLETTER_LINK_KEY` tras incidente o salida de personal

---

## VPS

### Almacenamiento externo (RECOMENDACIÓN)
**Regla 3-2-1 adaptada:**
- **3 copias:** 1 local (volumen Docker), 1 en VPS (`/var/backups/vivero`), 1 offsite (R2)
- **2 medios:** Disco local + almacenamiento objeto (R2/S3)
- **1 offsite:** **Obligatorio** — R2 en región distinta al VPS

### Frecuencia recomendada
| Tipo | Frecuencia | Retención | Ubicación |
|---|---|---|---|
| **Backup completo (BD + imágenes)** | Diario | 7 días | `/var/backups/vivero` + R2 |
| **Backup semanal** | Domingo | 4 semanas | R2 |
| **Backup mensual** | Día 1 | 6 meses | R2 |
| **Config/Secrets (Restic)** | Cada cambio | 12 meses | R2 (cifrado) |

### Procedimiento ante caída total del servidor
1. **Provisionar VPS nuevo** (Ubuntu 24.04, Docker, Compose)
2. **Restaurar configuración:** `/opt/vivero/shared/.env.vps`, `.env.bootstrap`, `offsite.json`, `offsite.password`, Caddyfile, TLS
3. **Clonar repositorios** en `/opt/vivero/releases/<timestamp>/`
4. **Restaurar BD + imágenes** desde último backup COMPLETE verificado:
   ```bash
   # En VPS nuevo, con Docker corriendo y DB vacía
   cd /opt/vivero/releases/<timestamp>/ViveroApp/infra/docker
   node restore.mjs --env-file /opt/vivero/shared/.env.vps \
     --project vivero-restore --backup-dir /ruta/al/backup \
     --acknowledge-empty-target
   ```
5. **Levantar servicios:**
   ```bash
   docker compose --env-file /opt/vivero/shared/.env.vps -p vivero-vps \
     -f compose.yaml -f compose.vps.yaml -f /srv/apps/vivero-dulcinea/ops/compose.host.yaml \
     up -d --wait
   ```
6. **Verificar:** `curl --fail https://viverodulcinea.bajastack.network/health`
7. **Actualizar DNS** si IP cambió
8. **Probar flujos críticos:** login, catálogo, venta, cobro, inventario

---

## SCRIPTS EXISTENTES (NO MODIFICAR SIN COORDINACIÓN)

| Script | Propósito | Ubicación |
|---|---|---|
| `backup.mjs` | Backup completo BD + imágenes + manifest + verificación | `infra/docker/backup.mjs` |
| `restore.mjs` | Restauración validada a destino vacío | `infra/docker/restore.mjs` |
| `offsite-backup.py` | Subida/descarga cifrada a R2 (Restic) | `infra/host/offsite-backup.py` |
| `viveroctl.sh` | Wrapper sudo para Toni/Pedro (backup, restore, logs, status) | `/srv/apps/vivero-dulcinea/ops/viveroctl.sh` |

### Uso directo (sin wrapper, solo admin con Docker)
```bash
# Backup
node infra/docker/backup.mjs --env-file /opt/vivero/shared/.env.vps \
  --project vivero-vps --output /var/backups/vivero --acknowledge-downtime

# Verificar
node infra/docker/backup.mjs --verify /var/backups/vivero/vivero-2026-10-05T03-00-00-xxx

# Restaurar (SOLO destino vacío/aislado)
node infra/docker/restore.mjs --env-file /opt/vivero/shared/.env.vps \
  --project vivero-restore --backup-dir /var/backups/vivero/vivero-... \
  --acknowledge-empty-target
```

---

## CHECKLIST DE BACKUP PARA TONI

- [ ] Entender: `backup.mjs` pausa API/Web, dumpea BD, empaqueta imágenes, hashea, marca COMPLETE
- [ ] Saber ejecutar: `sudo viveroctl.sh backup --acknowledge-downtime`
- [ ] Saber verificar: `node backup.mjs --verify <dir>`
- [ ] Saber restaurar: `viveroctl.sh restore-isolated ... --acknowledge-empty-target` (solo ensayo)
- [ ] Confirmar: `/var/backups/vivero` tiene lotes recientes con `COMPLETE` y `manifest.json` válido
- [ ] Configurar: R2 + Restic (`offsite-backup.py`) para copia externa cifrada
- [ ] Probar: Restore rehearsal mensual en entorno aislado
- [ ] Documentar: Cada backup manual con timestamp, motivo y hash en bitácora operativa

---

## RIESGOS Y MITIGACIONES

| Riesgo | Mitigación |
|---|---|
| Backup falla a medio camino (API/Web caídos) | Script reanuda servicios en `finally`; `INCOMPLETE` marca lote incompleto |
| Volumen `mariadb_data` corrupto | Dump lógico (`mariadb-dump`) no depende de archivos binarios |
| Imágenes no respaldadas | `catalog-images.tar.gz` incluido; verificar `manifest.json` |
| Secrets perdidos (TLS, `.env.vps`) | Incluir en Restic offsite; backup manual separado |
| R2 no configurado | **Prioridad alta** — sin offsite, pérdida total de VPS = pérdida total de datos |
| Restore en producción por error | `restore.mjs` exige `--acknowledge-empty-target` y BD vacía; falla si hay datos operativos |

---

## PRÓXIMOS PASOS

1. **Configurar R2 + Restic** (credenciales, bucket, `offsite.json`, `offsite.password`)
2. **Primer envío offsite** y restore rehearsal desde copia externa
3. **Automatizar** con systemd timer (diario) + retención (7/4/6)
4. **Documentar** procedimiento de disaster recovery completo (incluye DNS, TLS, secrets)