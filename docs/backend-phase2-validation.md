# Validación de identidad/permisos — 2026-09-29

> Registro histórico previo a recuperar Docker. La ejecución real de MariaDB,
> migración, bootstrap y sesiones se completó después; consultar
> [validación local](backend-local-validation.md). Los resultados originales se conservan.

## Resultado y límite

La API incorpora login, contexto actual, logout, sesiones revocables, permisos de
catálogo y bootstrap local del primer propietario. Se retiró el token compartido.
**La validación real de SQL/HTTP con MariaDB sigue bloqueada porque el motor
Docker Desktop no está disponible. No se declara migrado el login de Web/Android.**

## Validación ejecutada en esta sesión

| Comando | Resultado |
|---|---|
| `npm test` en backend | 9 pruebas aprobadas: hashing/verificación real de contraseñas, validación, permisos/precios, alcance de sucursal, rollback, HTTP y rechazo del token anterior |
| `npm run check` en backend | Sintaxis de API, autenticación y herramientas administrativas correcta |
| `node --check backend/test/integration.test.js` | Sintaxis correcta; no significa ejecución de integración |
| `docker compose --env-file .env.example -f infra/docker/compose.yaml config --quiet` | Configuración válida, sin imprimir valores |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase/tests/verify_migrations.ps1` | 134 comprobaciones correctas del backend Supabase conservado |
| `docker info --format '{{.ServerVersion}}'` | No existe el pipe dockerDesktopLinuxEngine; no se pudo conectar al motor |
| `git diff --check` y revisión de archivos nuevos | Sin errores de whitespace |

Las pruebas Node se ejecutaron con el permiso necesario para procesos hijos en
este equipo. La evidencia del fallo `sailor-ingest.sock` pertenece al intento de
inicio de fase 1; en esta sesión se comprobó que el motor sigue inaccesible, sin
afirmar que se volvió a diagnosticar la misma causa interna. No se cambiaron
permisos/sockets/configuración global de Docker ni se iniciaron otros servicios.

## Implementación preparada para validar con MariaDB

- Migración incremental `002_identity` sobre baseline preservado; no borra datos.
- Contexto vigente bajo bloqueo: permisos y cambio de catálogo dentro de la misma
  transacción; MANAGE_PRICES solo si cambia el precio o se crea el producto.
- Tokens aleatorios de una hora almacenados como hash; revocación por logout,
  cambio de contraseña o cambio de estado activo.
- Límite persistente de login por email/dirección de conexión y máximo de dos
  verificaciones scrypt simultáneas por instancia.
- Bootstrap explícito, sin contraseñas por defecto, que rechaza repetición y
  conserva cualquier cuenta existente.
- Cuenta SQL de ejecución sin escritura sobre usuarios/roles/capacidades y sin
  permisos sobre ventas/pagos. Root solo en herramientas de mantenimiento/test.

La suite `test/integration.test.js` contiene cuatro pruebas de integración:
CRUD de catálogo con sesión real; restricciones/grants; ciclo de sesión y
permisos vigentes; límites persistentes de login. Los fixtures son sintéticos y
se limpian. No se ejecutaron en esta sesión. Tampoco se verificaron contra el
motor los triggers, el bootstrap, el migrador, el arranque de imágenes ni la
actualización de un volumen de fase 1. Son comprobaciones pendientes, no aprobadas.

El verificador estático de Supabase no interpreta MariaDB. La revisión estática
de la migración nueva comprobó tipos de ID, FKs, permisos mínimos, cardinalidades
y correspondencia de las capacidades; no sustituye ejecutarla en MariaDB.

No se ejecutaron Gradle, emuladores, build/lint/test Web ni pgTAP porque no cambió
código Android/Web ni SQL PostgreSQL. No hubo consultas ni cambios remotos.

## Archivos de esta fase

Nuevos:

- `backend/src/auth/password.js`, `backend/src/auth/service.js`.
- `backend/scripts/admin-db.js`, `backend/scripts/migrate.js`,
  `backend/scripts/bootstrap-owner.js`.
- `backend/test/auth.test.js`.
- `database/mysql/migrations/002_identity.sql`.
- `docs/backend-identity.md`, este informe.

Actualizados:

- `backend/src/app.js`, `backend/src/server.js`.
- `backend/test/unit.test.js`, `backend/test/integration.test.js`.
- `backend/package.json`, `backend/Dockerfile`.
- `infra/docker/compose.yaml`, `.env.example`.
- `README.md`, `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`.

No cambian dependencias ni package-lock.json. Se preservan schema.sql, seed.sql,
grants.sql y el informe histórico de fase 1. No se modificó ViveroWeb ni
ViveroAppCliente en esta fase.

## Trabajo preexistente y Git

Todos los archivos de fase 1 ya estaban presentes, sin commit. También se
conservaron los cambios anteriores en `.idea/misc.xml`, la eliminación de
`.idea/planningMode.xml`, AGENTS.md, README.md y docs/presential-release.md, y los
archivos/carpetas sin versionar de auditorías, adjuntos, release, automatización
y output. Se tomó una copia local ignorada de los archivos de fase 1 para
distinguir el diff de este módulo; no se leyó ni cambió el `.env` del usuario.
Solo las adiciones de identidad indicadas arriba modifican ese punto de partida.

Rama `main`, HEAD `a4621f6d42deb735714c047b05789ee83760939a`, sin cambios de rama ni
HEAD; modificaciones sin staging y archivos nuevos sin versionar. No hubo
commit, push, publicación, despliegue ni integración n8n. La documentación y el
código no equivalen a un módulo desplegado ni a una migración de cuentas reales.

## Continuación

Resolver el inicio de Docker Desktop, ejecutar instalación nueva y actualización
desde fase 1, bootstrap y pruebas HTTP/SQL con datos sintéticos siguiendo
[backend-identity.md](backend-identity.md). Después completar el catálogo operativo
y la correspondencia de IDs/pedidos antes de cambiar un consumidor Web/Android.
