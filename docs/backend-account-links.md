# Recuperación e invitaciones por API

Los consumidores Web `PasswordRecoveryPage` y `StaffInvitation` usan la API
oficial. No invocan Supabase Auth ni Edge Functions. Android conserva su flujo
anterior hasta completar su integración. No se han enviado correos reales.

## Contratos

- `POST /api/v1/auth/recovery`, `{email}`: 202 `{accepted:true}` para cuentas
  existentes, inexistentes o inactivas. Solo se envía a cuentas activas con
  contraseña. Los límites persistentes usan un espacio separado del login:
  10 solicitudes por correo y 60 por dirección de conexión cada 15 minutos.
  No se confía en `X-Forwarded-For`; detrás del proxy este límite IP es compartido.
- `POST /api/v1/auth/password`, `{token,password}`: 200 `{password_changed:true}`.
  Contraseña de 15 a 128 caracteres Unicode, sin recortar espacios. No inicia una
  sesión automáticamente. Los enlaces inválidos, vencidos o consumidos devuelven
  400; se limita el cálculo simultáneo de scrypt a dos solicitudes.
- `POST /api/v1/admin/staff/invitations`, `{name,email}`: requiere sesión y
  `MANAGE_USERS`; 200 `{invited:true}` confirma aceptación por el proveedor,
  **no recepción del correo**. Crea cuenta activa sin contraseña, rol ni sucursal.
  El mismo correo pendiente puede reenviarse sin duplicar la cuenta; el nuevo
  enlace reemplaza al anterior. Una cuenta habilitada con contraseña o una
  asignación existente responde 409. Asignar rol y sucursal después de aceptar
  la invitación. Si ya se asignaron sin recibirla, usar el restablecimiento
  administrativo de contraseña existente.

La migración **024** crea `account_links` con ID entero, FK a users y hash SHA-256
del token aleatorio de 32 bytes. Vence en 30 minutos. Se bloquea el usuario antes
del enlace; completar contraseña, revocar sesiones/enlaces y registrar auditoría
ocurren en la misma transacción. Un cambio administrativo de contraseña o
activación también invalida los enlaces. Nunca se devuelve el enlace a la UI de
personal ni se registra en auditoría.

El correo apunta a `/recuperar#token=...`. El fragmento no llega al servidor HTTP;
Web lo elimina del historial al montar y mantiene el token solo en memoria.
Recargar requiere abrir de nuevo el correo. GET no consume enlaces. El proveedor
recibe el token en el texto, pero su clave de idempotencia usa solo su hash.

## Configuración del mismo Compose

Definir en el archivo privado usado con `--env-file`:

```dotenv
WEB_ORIGIN=https://vivero.example.invalid
RESEND_API_KEY=replace-with-private-resend-key
ACCOUNT_MAIL_FROM=Vivero Dulcinea <acceso@example.invalid>
```

En el overlay VPS, `WEB_ORIGIN` procede de `VIVERO_DOMAIN`. Usar remitente y
dominio verificados en Resend. Se reutiliza el proveedor del newsletter anterior
sin SDK nuevo: [contrato oficial de envío](https://resend.com/docs/api-reference/emails/send-email).
Las credenciales solo entran en API; nunca usar variables VITE ni subir `.env`.
Si falta configuración, ambas solicitudes de correo responden 503 sin escribir
cuentas/enlaces. Health no certifica el correo. No se requieren secretos para
las pruebas: se inyecta un transporte simulado en el servidor real contra MariaDB.

La recuperación pública responde sin esperar al proveedor, para que su latencia
no revele cuentas. Hay como máximo 32 envíos pendientes por proceso y timeout de
8 segundos. **No hay cola durable de correo**: un reinicio puede perder un envío;
el usuario debe solicitar otro enlace. El enlace emitido permanece hasta
vencer o ser reemplazado, porque un timeout puede corresponder a correo aceptado.
El resultado público no revela fallos de entrega. Invitaciones sí esperan y
devuelven 503 si no se confirma el proveedor; conservan la cuenta pendiente para
reintentar. No hay reintentos automáticos, enlaces en logs ni simulación activa
en producción. Antes del corte real falta comprobar entrega y recepción con
dominio/remitente propios.

## Validación local del 2 de octubre de 2026

Web: `npm test` (460), `npm run build` y `npm run lint` correctos.
Backend: `npm test` (66), `npm run check` correctos; 24 pruebas HTTP/SQL Docker,
incluida recuperación/invitación con usuario runtime de privilegios mínimos.
Se comprobó autorización, cuenta sin permisos, reenvío, hash de enlace, política
de contraseña, uso único concurrente, caducidad, revocación de sesiones,
desactivación/reactivación, cambio administrativo, rollback, rate limit,
configuración ausente y fallos simulados de correo. No es prueba visual de UI,
entrega real, Android ni VPS. Esquema local actualizado: 41 tablas y 23 marcadores.

Desde ViveroApp, con configuración privada de un entorno **local de pruebas**:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml run --rm --build migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build api
docker compose --env-file .env -f infra/docker/compose.yaml run --rm --build tests
```

Las pruebas crean y limpian datos sintéticos; no apuntarlas a una base operativa.
El ensayo de esta sesión utilizó el proyecto aislado `vivero-cutover-20261001` y
sus archivos locales ignorados de configuración/red. No se borraron volúmenes,
no hubo importación real, commit, push ni despliegue.

## Archivos de esta entrega

En ViveroApp: `backend/src/auth/account-links.js`, `mail.js`, `service.js`,
`backend/src/app.js`, `server.js`, `backend/package.json`,
`backend/scripts/verify-local-install.js`, `backend/test/account-links.test.js`,
`account-links-integration.test.js`, `integration.test.js`, `backup-fixture.js`,
`database/mysql/migrations/024_account_links.sql`, `infra/docker/compose.yaml`,
`vps.env.example`, `.env.example`, `README.md`, y documentación
`backend-account-links.md`, `backend-complete-cutover.md`,
`backend-vps-preparation.md`, `supabase-migration-map.md`.

En ViveroWeb: `src/features/auth/PasswordRecoveryPage.tsx`,
`account-links-service.ts`, `account-links-service.test.ts`,
`src/features/admin/StaffInvitation.tsx`, `README.md`, `docs/PROJECT_STATUS.md`.
Se preservaron los cambios previos de ambos repositorios, incluido Android/IDE.
No se ejecutó Gradle ni pruebas de dispositivo: no cambió código Android.
No cambió SQL Supabase; la validación nueva corresponde a MariaDB real local.
