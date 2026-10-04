# Validación local de fase 1 — 2026-09-29

> Registro histórico del primer intento. El bloqueo de Docker se resolvió y
> las pruebas reales posteriores están en [validación local](backend-local-validation.md).
> Se conserva aquí lo que se ejecutó y lo que estaba pendiente en aquel momento.

## Resultado

La base oficial de API/MariaDB/Docker está implementada. Se verificaron lógica
HTTP, validación, autorización local, sintaxis y configuración Compose.
**El arranque completo y las pruebas SQL/HTTP contra MariaDB están bloqueados
por Docker Desktop; no se declaran aprobados.** Ningún cliente operativo fue
migrado ni se ejecutó una operación remota de Supabase.

## Comandos y evidencia de esta sesión

| Comando | Resultado |
|---|---|
| `npm install mysql2 --save-exact --ignore-scripts --no-audit --no-fund` en backend | Instalado 3.24.5, 12 paquetes, lockfile generado |
| `npm test` en backend | 3 pruebas aprobadas; datos inválidos, autorización previa a SQL, cierre sin token, errores sin detalles internos, JSON/tipo/tamaño HTTP |
| `npm run check` en backend | Sintaxis correcta de server.js, app.js y catalog.js |
| `docker compose --env-file .env.example -f infra/docker/compose.yaml config --quiet` | Correcto; no muestra valores |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase/tests/verify_migrations.ps1` | 134 comprobaciones correctas; aplica al backend Supabase intacto, no prueba el SQL nuevo |
| `docker version` / `docker info` | Cliente 29.7.2 disponible; motor desktop-linux inaccesible |
| `docker desktop start` y arranque del ejecutable Desktop en segundo plano | Backend de Docker termina antes de estar listo |
| `docker compose --env-file tmp/backend-phase1-20260929.env -f infra/docker/compose.yaml up --build -d --wait` | Falla antes de crear servicios por ausencia del pipe dockerDesktopLinuxEngine |
| `git diff --check` en ambos repositorios | Sin errores de whitespace |

El primer `npm test` fue bloqueado por el sandbox con `spawn EPERM`. Se repitió
con ejecución autorizada y pasó; también se repitió tras añadir el caso 413.
La consulta inicial de npm estuvo bloqueada por el modo de caché de red; la
instalación autorizada sí terminó. No se confunden esos intentos con fallos de
las pruebas de aplicación.

El log local de Docker de esta sesión identifica:
`initializing Ingest server ... sailor-ingest.sock ... El sistema no tiene acceso al archivo`.
Desktop informa `backend exited before becoming ready`. No se modificaron
permisos, sockets, volúmenes ni configuración global para eludir el fallo.
El CLI de inicio que seguía esperando fue interrumpido. No quedaron servicios
de esta API ejecutándose. Se generaron únicamente credenciales aleatorias de
prueba en `tmp/` ignorado; no se leyó ni sobrescribió el `.env` del usuario.

## Pruebas pendientes

Después de reparar el inicio local de Docker Desktop, seguir la guía y ejecutar
el perfil `test`. `backend/test/integration.test.js` cubre:

- health real; alta de categoría/producto, actualización, baja lógica repetible;
- paginación, precio entero, duplicados, categoría inexistente e inactivos;
- solicitudes no autorizadas y ausencia de permisos SQL fuera del catálogo;
- FKs, restricciones de importes y cantidades, unicidad de inventario y pago;
- limpieza de los registros HTTP sintéticos y rollback de fixtures SQL.

Estas dos pruebas de integración **no se ejecutaron**. Tampoco se validaron
construcción de imagen, instalación en contenedor, inicialización SQL, seed,
grants, persistencia tras reinicio ni conectividad efectiva por el puerto 3001.
No se afirma que el proyecto haya levantado completamente en este equipo.

No se ejecutaron Gradle, emulador/cámara, build/lint/test Web ni pgTAP: no cambió
código Android, código Web ni migraciones PostgreSQL. El SQL MariaDB no puede
probarse con pgTAP. El verificador Supabase se ejecutó como protección estática
del contrato anterior, y se dejaron pruebas específicas del motor nuevo.

## Archivos producidos

En ViveroApp:

- `.env.example`.
- `backend/package.json`, `backend/package-lock.json`, `backend/Dockerfile`,
  `backend/.dockerignore`, `backend/.gitignore`.
- `backend/src/app.js`, `backend/src/catalog.js`, `backend/src/server.js`.
- `backend/test/unit.test.js`, `backend/test/integration.test.js`.
- `database/mysql/schema.sql`, `database/mysql/seed.sql`, `database/mysql/grants.sql`.
- `infra/docker/compose.yaml`.
- `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`, este informe.
- Adiciones acotadas en `README.md` y `AGENTS.md` para la dirección oficial.

En ViveroWeb: solo adición documental en `README.md`, con enlaces a la guía.
ViveroAppCliente: inspección sin modificaciones.

## Trabajo preexistente conservado

ViveroApp ya tenía cambios en `.idea/misc.xml`, eliminación de
`.idea/planningMode.xml`, modificaciones de `AGENTS.md`, `README.md` y
`docs/presential-release.md`. Ya eran no versionados `.codex-remote-attachments/`,
`VIVERO_DULCINEA_CURRENT_ARCHITECTURE_AUDIT.md`,
`VIVERO_DULCINEA_ECOSYSTEM_TECHNICAL_HANDOFF.md`, `app/release/`,
`docs/automation/` y `output/`. Se conservaron; las adiciones de esta fase a
README/AGENTS no sustituyen sus modificaciones previas.

ViveroWeb ya tenía sin versionar
`docs/VIVERO_DULCINEA_WEB_CURRENT_ARCHITECTURE_AUDIT.md`; se conservó.
No se modificó seguimiento de automatización ni se añadieron integraciones n8n.

Ramas/HEAD conservados: ViveroApp `main` / `a4621f6d42deb735714c047b05789ee83760939a`;
ViveroWeb `main` / `d4db2b666e4d726d2887d133e64fa34794535ef6`.
Estado final observado: cambios anteriores más los archivos de esta entrega,
sin staging. No hubo commit, push, publicación, despliegue ni importación de datos.
