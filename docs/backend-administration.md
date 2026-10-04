# Personal y sucursales: bloque 019

Backend oficial, sin cambios en consumidores Web/Android ni datos de Supabase.
Health requiere `019_staff_branches`. No incluye altas de cuentas, invitaciones,
recuperación de contraseñas ni importación de personal real.

## Contrato

Todas las rutas requieren sesión Bearer. Prefijo `/api/v1/admin`:

| Método y ruta | Cuerpo / permiso |
| --- | --- |
| GET /branches | MANAGE_BRANCHES o MANAGE_USERS |
| POST /branches | `{code,name}`, MANAGE_BRANCHES |
| PATCH /branches/:id | `{code,name}`, MANAGE_BRANCHES |
| PATCH /branches/:id/active | `{is_active:boolean}`, MANAGE_BRANCHES |
| GET /staff | MANAGE_USERS |
| GET /roles | ASSIGN_ROLES y rol OWNER/ADMIN |
| PATCH /staff/:id/role | `{role_name}`, ASSIGN_ROLES y OWNER/ADMIN |
| PATCH /staff/:id/active | `{is_active:boolean}`, MANAGE_USERS |
| PATCH /staff/:id/branch | `{branch_id:integer}`, MANAGE_USERS |

Listados ordenados por ID con `limit` 1–100 (50 por defecto), `after_id`,
`include_inactive=true|false`; personal admite `search` literal por nombre y
`branch_id`. Respuesta `items`, `next_after_id`, `schema_version:1`.
Este contrato usa cursor por ID; no reproduce el cursor por nombre de Supabase.
No se exponen correos ni hashes en listados administrativos.

ADMIN no puede conceder OWNER ni cambiar rol, estado o sucursal de un OWNER.
El último OWNER **activo** no puede ser desactivado ni perder el rol. Los cambios
de rol/sucursal requieren personal activo y la sucursal destino debe estar activa.
No se permite desactivar sucursales con personal activo, ventas pendientes de
caja o pedidos PENDING/CONFIRMED/READY. El bloqueo por pedidos amplía la protección
del contrato previo. Código único normalizado a mayúsculas; no hay borrado físico.

Cada cambio real de acceso elimina todas las sesiones del usuario dentro de la
misma transacción. Repetir el mismo rol/estado/sucursal devuelve
`idempotent_replay:true` y conserva sesiones. El trigger previo de contraseña y
activación se conserva; cambios directos SQL de rol/sucursal no usan esta ruta
ni garantizan su revocación. Las credenciales SQL son exclusivas del servidor.

`administration_changes` tiene ID entero, FKs, actor, acción y valores anteriores
y nuevos limitados a rol/estado/sucursal/código/nombre. Sin secretos. Runtime
puede leer e insertar auditoría, pero no modificarla ni eliminarla. Sus permisos
UPDATE de usuarios se limitan a rol, sucursal, estado y fecha; no correo/password.
Si falla la auditoría, se revierten cambio y revocación.

Escrituras administrativas toman `GET_LOCK('vivero_staff_branch_admin',5)` antes
de bloquear sesión/actor, usan READ COMMITTED y liberan el lock después de commit
o rollback. Esto evita carreras entre administradores y pérdida simultánea de
OWNER. Si no se obtiene: 409 ADMINISTRATION_BUSY. No coordina escrituras SQL de
mantenimiento/bootstrap; no ejecutarlas simultáneamente con administración API.

## Verificación 2026-09-30

Migración aplicada sin reiniciar el volumen de pruebas sintéticas
`vivero-validation-20260929`; API saludable. Prueba administrativa real aprobada:
permisos, filtros, jerarquía, sucursales ocupadas/inactivas, revocación, no-op,
fallo de auditoría y demociones simultáneas. Si existen OWNER previos, no se
alteran para forzar el caso de último OWNER; la unidad lo comprueba siempre.

Suite completa final: 19/19 integraciones. El primer intento falló por una
expectativa histórica que negaba cualquier UPDATE de rol; se actualizó para
comprobar el nuevo permiso y mantener denegados correo/password/borrado de audit.
Pruebas unitarias en Docker Node 24: 51/51. Ejecución Windows inicialmente
bloqueada por spawn EPERM; se usó Docker. Check de sintaxis y verificación
estática Supabase (134 controles) aprobados. No se cambiaron dependencias.

No se probaron volumen vacío, importación real, Android ni pgTAP: este bloque
no modifica Kotlin ni PostgreSQL. La instalación integral desde cero y el cambio
de consumidores siguen pendientes. No hubo commit, push ni despliegue.

## Comandos desde la raíz

Usar `.env` local a partir de la plantilla, sin secretos versionados:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests npm test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests
npm --prefix backend run check
git diff --check
```

Archivos: src/administration.js, src/app.js, src/auth/service.js en backend;
test/administration.test.js, test/administration-integration.test.js,
test/auth.test.js, test/integration.test.js; backend/package.json;
database/mysql/migrations/019_staff_branches.sql; infra/docker/compose.yaml;
esta guía, backend-api-mariadb.md y supabase-migration-map.md.

Siguiente módulo recomendado: compras y proveedores.

## Actualización: cuentas de personal (2026-10-01)

La API permite alta administrativa de personal y restablecimiento de contraseñas con auditoría y revocación transaccional. Migración SQL 021. Consultar [backend-staff-accounts.md](backend-staff-accounts.md) para contratos, permisos y evidencia. Web y Android conservan sus sesiones Supabase hasta la integración coordinada de sus consumidores.
