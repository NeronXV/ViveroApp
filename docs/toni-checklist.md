# Checklist de Onboarding — Toni

**Fecha:** 5 de octubre de 2026  
**Versión:** 1.0.7-vps / código 8  
**Backend:** `https://viverodulcinea.bajastack.network`

---

## ✅ CHECKLIST PASO A PASO

### 1. Clonar repositorios
```bash
# En carpeta de desarrollo (ej. ~/dev/vivero)
git clone https://github.com/NeronXV/ViveroApp.git
git clone https://github.com/NeronXV/ViveroWeb.git
# Verificar que queden como hermanos:
ls -la
# ViveroApp/  ViveroWeb/
```
- [ ] ViveroApp HEAD = `3369bbb` (main)
- [ ] ViveroWeb HEAD = `62d027e` (main)
- [ ] Leer `ViveroApp/AGENTS.md` y `ViveroWeb/AGENTS.md`

---

### 2. Configurar `.env` local (ViveroApp)
```bash
cd ViveroApp
cp infra/docker/vps.env.example infra/docker/.env
# Editar .env con valores NUEVOS (nunca copiar de producción):
# - MARIADB_ROOT_PASSWORD=<nuevo hex 32 bytes>
# - MARIADB_PASSWORD=<nuevo hex 32 bytes>
# - WEB_ORIGIN=http://localhost:5173
# - BOOTSTRAP_EMAIL=toni@local.invalid
# - BOOTSTRAP_PASSWORD=<nueva pass>
# - BOOTSTRAP_FULL_NAME=Toni Local
# - BOOTSTRAP_BRANCH_CODE=LOCAL
# - BOOTSTRAP_BRANCH_NAME=Sucursal Local
```
- [ ] `.env` creado con contraseñas aleatorias propias
- [ ] `WEB_ORIGIN=http://localhost:5173` (coincide con Vite dev server)

---

### 3. Levantar Docker/MariaDB + API
```bash
cd ViveroApp/infra/docker
docker compose --env-file .env -p vivero-toni-local \
  -f compose.yaml config --quiet
docker compose --env-file .env -p vivero-toni-local \
  -f compose.yaml up -d --wait db
docker compose --env-file .env -p vivero-toni-local \
  -f compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -p vivero-toni-local \
  -f compose.yaml up --build -d --wait
curl --fail http://127.0.0.1:3001/health
```
- [ ] `docker compose ps` → `db` healthy, `api` healthy
- [ ] `curl http://127.0.0.1:3001/health` → `{"status":"ok",...}`
- [ ] `curl http://127.0.0.1:3001/api/v1/categories` → 200

---

### 4. Ejecutar migraciones (verificar 030 aplicada)
```bash
docker compose --env-file .env -p vivero-toni-local \
  -f compose.yaml exec db mariadb -u root -p$MARIADB_ROOT_PASSWORD vivero \
  -e "SELECT version FROM schema_migrations WHERE version='030_purchase_draft_retirement';"
```
- [ ] Migración `030_purchase_draft_retirement` presente
- [ ] `SHOW TABLES;` → 56 tablas

---

### 5. Levantar ViveroWeb (local)
```bash
cd ../ViveroWeb
cp .env.example .env.local 2>/dev/null || true
# Editar .env.local:
# BACKEND_PROXY_TARGET=http://127.0.0.1:3001
npm ci
npm run dev -- --host localhost
```
- [ ] `npm run lint` → sin errores
- [ ] `npm test` → 475 tests ✅
- [ ] `npm run build` → build ✅
- [ ] Web abre en `http://localhost:5173` → login funciona contra API local

---

### 6. Compilar ViveroApp (Android)
```bash
cd ../ViveroApp
# Requiere: JDK 17 (Temurin en ~/.gradle/jdks/), Android SDK 37
# Configurar local.properties (ya existe con URLs de producción; para debug local cambiar):
# BACKEND_API_URL=http://10.0.2.2:3001
# BACKEND_WEB_URL=http://10.0.2.2:5173
$env:JAVA_HOME="~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
$env:ANDROID_HOME="~/AppData/Local/Android/Sdk"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```
- [ ] `testDebugUnitTest` → BUILD SUCCESSFUL
- [ ] `assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (53.4 MB)
- [ ] `lintDebug` → sin errores
- [ ] APK: `versionName=1.0.7-vps`, `versionCode=8`, `applicationId=com.intutec.viveroapp`

---

### 7. Probar APK en dispositivo/emulador
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Abrir app → login con cuenta bootstrap (toni@local.invalid)
# Verificar: catálogo, carrito, envío a caja, cobro, recuperación
```
- [ ] Login exitoso contra API local (`10.0.2.2:3001`)
- [ ] Catálogo carga productos
- [ ] Carrito persiste al cerrar/reabrir
- [ ] Flujo caja → cobro → comprobante funciona
- [ ] **Recovery:** Simular pérdida de red (modo avión) tras enviar pago → reabrir → recuperar con misma clave

---

### 8. Configurar correo (Resend) — PENDIENTE
```bash
# En ViveroApp/infra/docker/.env (local) o /opt/vivero/shared/.env.vps (VPS)
# RESEND_API_KEY=re_...
# ACCOUNT_MAIL_FROM=Vivero Dulcinea <acceso@tudominio.com>
# NEWSLETTER_FROM=Vivero Dulcinea <boletin@tudominio.com>
# NEWSLETTER_LINK_KEY=<64 hex chars>
```
- [ ] Cuenta Resend creada y dominio verificado
- [ ] 4 variables configuradas en `.env.vps` (VPS) y `.env` (local)
- [ ] Prueba: `POST /api/v1/auth/recovery` → 202 → email recibido
- [ ] Prueba: `POST /api/v1/admin/staff/invitations` → 200 → email recibido
- [ ] Documentar: [backend-email-config.md](backend-email-config.md)

---

### 9. Ejecutar/validar backup
```bash
# En VPS (requiere sudo Docker)
sudo /srv/apps/vivero-dulcinea/ops/viveroctl.sh backup --acknowledge-downtime
# Verificar:
node ViveroApp/infra/docker/backup.mjs --verify /var/backups/vivero/<ultimo-lote>
```
- [ ] Backup completado sin errores
- [ ] Lote tiene `COMPLETE`, `manifest.json`, `database.sql`, `catalog-images.tar.gz`
- [ ] Verificación de hash OK
- [ ] **Restore rehearsal mensual** en entorno aislado (ver `backup-strategy.md`)

---

### 10. Confirmar acceso VPS
```bash
ssh toni@179.236.238.111
# Confirmar huella ED25519 con Pedro: SHA256:/WRISCh2bIoTLHSRwzQY8niIR+z46J6Y7789J9Y8RC8
sudo -n /srv/apps/vivero-dulcinea/ops/viveroctl.sh status
sudo -n /srv/apps/vivero-dulcinea/ops/viveroctl.sh validate
sudo -n /srv/apps/vivero-dulcinea/ops/viveroctl.sh proxy-check
```
- [ ] SSH conecta con llave propia
- [ ] `status` → 3 servicios healthy
- [ ] `validate` → config OK
- [ ] `proxy-check` → Caddy OK
- [ ] `logs api|web|db` → legibles (sanitizar antes de compartir)

---

### 11. Qué NO modificar sin coordinación con Pedro

| Área | Qué no tocar | Por qué |
|---|---|---|
| **VPS Producción** | `docker compose down -v`, `prune`, recrear volúmenes, restaurar BD | Pérdida de datos operativos |
| **Secrets** | `.env.vps`, `.env.bootstrap`, `offsite.json`, `offsite.password`, `key.properties`, keystore | Credenciales de producción |
| **Git** | `push` a `main`, `force push`, `rebase` publicado, `tag` release | Requiere autorización expresa |
| **DNS/SSL** | Caddyfile en `/srv/proxy`, certificados Let's Encrypt | Afecta HTTPS público |
| **Base de datos** | SQL directo `DELETE`/`UPDATE`/`DROP` en tablas operativas | Usar migraciones o scripts validados |
| **Android Release** | Generar/firmar APK release sin `key.properties` acordado | Firma incompatible = no actualiza |
| **Supabase** | Desactivar proyecto, borrar migraciones, cambiar RLS | Conciliación histórica pendiente |
| **ViveroAppCliente** | Integrar a backend sin definición de alcance/versionado | Proyecto demo sin Git acreditado |

---

## 📋 RESUMEN DE VERIFICACIÓN FINAL

| Check | Comando | Esperado |
|---|---|---|
| API Health | `curl http://127.0.0.1:3001/health` | `{"status":"ok"}` |
| Web Build | `cd ViveroWeb && npm run build` | `✓ built in Xs` |
| Android Build | `.\gradlew.bat assembleDebug` | `BUILD SUCCESSFUL` |
| Tests Web | `cd ViveroWeb && npm test` | `475 passed` |
| Tests Backend | `docker compose --profile test run --rm tests` | `13 passed` |
| Tests Android | `.\gradlew.bat testDebugUnitTest` | `BUILD SUCCESSFUL` |
| Migración 030 | `SELECT * FROM schema_migrations WHERE version='030...'` | 1 fila |
| Backup Verify | `node backup.mjs --verify <lote>` | `Both backup files match...` |
| VPS Status | `sudo viveroctl.sh status` | `api, db, web running healthy` |

---

## 📞 CONTACTOS Y ESCALACIÓN

- **Pedro** — Decisiones de arquitectura, secrets, deploy, DNS, firma Android
- **Toni** — Desarrollo, tests, backup, diagnóstico, documentación
- **Canal acordado** — Para compartir secrets (gestor contraseñas), no chat/email/Git

---

**Firma/fecha Toni:** _________________________  
**Revisión Pedro:** _________________________