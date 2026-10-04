# Clientes en Backend API / MariaDB — bloque 018

Implementado y validado localmente el 2026-09-30. Se conservan los permisos del
contrato autoritativo de `202608290009_mvp_backend_hardening.sql`: CREATE_SALES
o MANAGE_USERS para buscar/crear; solo MANAGE_USERS para editar clientes existentes.
Requiere sesión activa. Los clientes son globales, como en Supabase; no se inventa
pertenencia a una sucursal ni una capacidad MANAGE_CUSTOMERS nueva.

## Endpoints

- `GET /api/v1/customers?search=Demo&limit=10`: solo clientes activos, búsqueda de
  2–80 caracteres por nombre, correo o teléfono; límite 1–50, default 10. Orden
  por nombre/ID. `%`, `_` y `!` son texto literal, no comodines SQL. Correo y
  consulta se normalizan a minúsculas. Respuesta `{schema_version:1,items:[...]}`.
- `GET /api/v1/customers/:id`: un registro; inactivos solo visibles a MANAGE_USERS.
- `POST /api/v1/customers`: alta, respuesta 201 con `{schema_version:1,customer:...}`.
- `PATCH /api/v1/customers/:id`: edición completa de los cuatro campos, respuesta
  200. ID inexistente devuelve 404; no se crea silenciosamente.
- `DELETE /api/v1/customers/:id`: desactivación, conserva datos. Solo MANAGE_USERS.
  Reactivar mediante PATCH con `is_active:true`. No hay borrado físico en la API.

Cuerpo completo para POST/PATCH:

```json
{"full_name":"Cliente Demo","email":"demo@example.invalid","phone":"12345678","is_active":true}
```

Nombre 2–160 caracteres, correo válido de hasta 254 caracteres o null, teléfono
8–20 caracteres o null. Valores se recortan; correo/teléfono vacíos se convierten
a null. No se exige un contacto, coherente con el contrato previo. Se rechazan
campos adicionales, controles, cadenas Unicode inválidas y estados no booleanos.
Ventas puede crear un registro inactivo conforme al contrato Supabase existente;
después solo administración puede consultarlo o modificarlo.

Los IDs son enteros autoincrementales. `created_by` y `updated_by` son FKs a users,
calculados del actor, no enviados por el cliente. Fechas UTC y auditoría inicial
se preservan al editar. Correo normalizado es único, incluso tras desactivación;
varios registros sin correo son válidos. Conflicto de correo devuelve 409
`DUPLICATE` sin modificar parcialmente el registro.

No hay Idempotency-Key para altas: como en el contrato anterior, repetir un POST
sin correo puede crear otro cliente. Buscar/conciliar antes de repetir una alta
cuya respuesta se perdió. PATCH reemplaza los campos y conserva la semántica
de última actualización, sin revisión optimista en este bloque.

## Esquema y límites

Migración `018_customers.sql`: tabla customers con PK, correo único, FKs de
auditoría y checks de nombre/contactos/estado. No se crean índices avanzados ni
tablas intermedias. SQL API dispone de SELECT/INSERT y UPDATE solo de campos
editables, actor de actualización y fecha; no DELETE ni modificación de created_by.
Las capacidades se cumplen en Backend API; el usuario SQL nunca se distribuye a
los consumidores.

No se asocian automáticamente pedidos o ventas con clientes, ni se convierten
contactos de pedidos en clientes. No se importaron datos reales. Web/Android
siguen usando Supabase; no cambia login, caja ni sus contratos.

## Validación de esta sesión

- Migración aplicada sobre el volumen sintético existente, sin reset.
- 47 pruebas unitarias y 18 pruebas de integración aprobadas en ejecución final.
- Clientes: autenticación/capacidades, acceso global entre sucursales, normalización
  y unicidad concurrente de correo, búsquedas literales y límites, autorización de
  edición, reversión de conflicto, desactivación/reactivación, FKs y privilegios.
- Sintaxis y 134 comprobaciones estáticas Supabase aprobadas. Estas últimas validan
  contratos conservados y no el SQL MariaDB. `git diff --check` correcto.
- Primera suite paralela: 17/18; fallo 503 intermitente en pago simultáneo con corte.
  Prueba aislada aprobada y sin deadlock registrado en InnoDB. Las suites instalan
  y eliminan triggers de fallo en tablas compartidas: posible interferencia DDL,
  sin afirmar una causa SQL no demostrada. Se configura `--test-concurrency=1`
  entre archivos para aislar esas mutaciones. Se conservan Promise.all y todas
  las pruebas concurrentes dentro de cada suite; ejecución completa final 18/18.
  No se cambió la lógica productiva de caja/cortes para ese fallo.
- Se expiró solo el contador de login de la IP del contenedor de pruebas para la
  suite completa, sin alterar cuentas ni la política de autenticación.
- Fixtures eliminados y API saludable con health 018. No hubo commit, push ni
  despliegue; rama main y trabajo preexistente preservados.

No se probaron volumen vacío, importación real, consumidores, Android ni pgTAP:
no cambian Android/PostgreSQL. La instalación nueva integral sigue pendiente.

## Comandos desde la raíz

Con `.env` local según plantilla, sin secretos operativos:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
npm --prefix backend test
npm --prefix backend run check
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
git diff --check
```

Archivos de este bloque: `backend/src/customers.js`, `backend/src/app.js`,
`database/mysql/migrations/018_customers.sql`, `backend/test/customers.test.js`,
`backend/test/customers-integration.test.js`, `backend/test/integration.test.js`,
`backend/package.json`, `infra/docker/compose.yaml`, esta guía,
`docs/backend-api-mariadb.md` y `docs/supabase-migration-map.md`.

Siguiente módulo recomendado: administración de personal y sucursales, conservando
las restricciones de OWNER/ADMIN y revocación de sesiones.
