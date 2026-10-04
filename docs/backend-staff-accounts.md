# Cuentas del personal en Backend API

## Estado y alcance

Implementado y validado localmente el 2026-10-01. Complementa la administración
de personal existente. Web y Android todavía usan su sesión de Supabase; esta
entrega no cambia sus consumidores ni importa usuarios reales.

La migración SQL `021_staff_accounts.sql` extiende la restricción de auditoría y
los privilegios por columna del usuario interno de API. No crea tablas: siguen
siendo 39 tablas y ahora 20 marcadores de migración, desde 002 hasta 021.

## Contrato

- `POST /api/v1/admin/staff`: cuerpo exacto con `email`, `password`, `full_name`,
  `role_name`, `branch_id` e `is_active`. Devuelve 201 con `schema_version: 1` y
  `staff`, usando el mismo formato seguro de la lista de personal.
- `POST /api/v1/admin/staff/:id/password`: cuerpo exacto con `password`.
  Devuelve 200 con `staff_id`, `password_reset: true`, `sessions_revoked: true`
  y `schema_version: 1`.

Ambos requieren sesión activa, MANAGE_USERS y ASSIGN_ROLES, y rol OWNER o ADMIN.
ADMIN no crea OWNER ni restablece contraseñas de OWNER. El alta exige una sucursal
activa. IDs enteros y relaciones existentes se conservan. El correo se normaliza
a minúsculas; un duplicado devuelve 409, sin sobrescribir otra cuenta. El nombre
tiene entre 2 y 160 caracteres y el estado debe ser booleano explícito.

La contraseña tiene entre 15 y 128 caracteres, se preserva sin recortar y rechaza
marcadores `replace-with-`. Se guarda únicamente el hash scrypt con sal individual,
usando el mecanismo de identidad existente. Respuestas y auditoría no incluyen
contraseña, hash ni correo. Los eventos USER_CREATE y USER_PASSWORD registran
actor, usuario destino y cambios mínimos.

El bloqueo administrativo existente serializa las escrituras, incluido el cálculo
del hash. Alta/restablecimiento y auditoría se confirman juntos. El trigger de
identidad revoca todas las sesiones cuando cambia el hash; si falla la auditoría,
se revierten contraseña y revocación. Restablecer una cuenta inactiva no la activa.
Se conservan las restricciones sobre el último OWNER y los demás permisos por
columna: la API no puede actualizar correo o nombre directamente.

No hay registro público, recuperación por correo, envío de mensajes, importación
de hashes de Supabase ni pantalla cliente nueva en esta entrega. Antes de migrar
la sesión Web habrá que coordinar sus servicios operativos e IDs; cambiar solamente
AuthProvider dejaría las RPC de Supabase sin su sesión válida.

## Comandos locales

Desde la raíz, con `.env` local previamente configurado y sin secretos versionados:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests npm test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests npm run test:integration
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests npm run check
```

Las integraciones necesitan la base Docker local aislada y crean solamente datos
sintéticos. No ejecutarlas contra datos operativos. Aplicar migraciones antes de
recrear API: health requiere el marcador 021.

## Evidencia de esta entrega

En `vivero-fresh-20261001c`, con configuración local ignorada: migración incremental
021 aplicada; prueba específica de cuentas aprobada; 58 unitarias y 22 integraciones
aprobadas; comprobación de sintaxis aprobada. Las integraciones incluyen alta
concurrente con correo duplicado, jerarquía, login, cuenta inactiva, revocación y
rollback de auditoría. No se repitieron builds Android/Web porque no cambiaron.

Intento adicional desde volumen vacío en `vivero-accounts-fresh-20261001` bloqueado
antes del arranque: Docker informó `all predefined address pools have been fully
subnetted`. No confirma una instalación nueva con 021. Se conservaron redes y
volúmenes; no se cambió la configuración de Docker ni se ejecutó prune/down -v.
La evidencia de instalación vacía anterior hasta 020 sigue en
`backend-fresh-install.md`; no se presenta como una prueba nueva de 021.

Archivos de esta entrega: `backend/src/app.js`, `backend/src/administration.js`,
`backend/package.json`, `backend/scripts/verify-local-install.js`,
`backend/test/administration.test.js`, `backend/test/accounts-integration.test.js`,
`backend/test/administration-integration.test.js`, `backend/test/integration.test.js`,
`database/mysql/migrations/021_staff_accounts.sql`, `infra/docker/compose.yaml`,
esta guía y enlaces en los documentos de arquitectura, identidad y administración.

Siguiente paso: integración coherente de sesión y consumidores Web con la API,
preservando catálogo, pedidos y atención administrativa. VPS sigue pendiente.
