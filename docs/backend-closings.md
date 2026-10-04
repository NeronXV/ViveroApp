# Cortes de caja en Backend API / MariaDB

Bloque 016, validación local del 2026-09-30. Referencia del contrato vigente:
`supabase/migrations/202609080002_cashier_closings_refunds.sql`.

## Operación

Requiere Bearer, OPERATE_CASHIER y sucursal propia activa. Cada cajero incluye
solo sus cobros y sus devoluciones; no toda la actividad de la sucursal. No hay
excepción por rol global. Los recibos históricos solo se consultan por su actor
y sucursal actual. No cambia Web, Android, Supabase ni inventario.

- `GET /api/v1/cashier/closings/preview`: importes pendientes por método, cantidades
  de pagos/devoluciones y último corte del cajero. La previa es informativa;
  el POST recalcula las operaciones al confirmar.
- `POST /api/v1/cashier/closings`: crea un corte con `Idempotency-Key` de 64
  caracteres hexadecimales minúsculos, y cuerpo exacto:

```json
{"opening_cash_cents":10000,"counted_cash_cents":25000}
```

- `POST /api/v1/cashier/closings/recover`: cuerpo `{}` y misma clave para recuperar
  una respuesta perdida, sin exigir repetir los importes.
- `GET /api/v1/cashier/closings/:id`: recibo con importes e IDs exactos de pagos y
  devoluciones incluidos. La previa ofrece el ID del último corte; no hay listado
  general ni reporte administrativo en este bloque.

Primera confirmación: 201. Mismo actor, clave e importes: 200 con
`idempotent_replay: true`. Cambiar importes o sucursal con la misma clave devuelve
409 `CLOSING_IDEMPOTENCY_CONFLICT`. Corte sin operaciones pendientes: 409
`CLOSING_EMPTY`. Consulta/recuperación ajena o inexistente: 404.

Saldo esperado = fondo inicial + cobros CASH − devoluciones CASH.
Diferencia = efectivo contado − saldo esperado. CARD/TRANSFER y devoluciones no
efectivas se registran por separado, sin sumarlas al efectivo. Se usa el importe
debido del pago, excluyendo efectivo recibido de más y cambio. El fondo inicial
y efectivo contado son declaraciones del operador, no movimientos bancarios.
No se arrastra automáticamente el saldo del corte anterior.

Las sumas se calculan con BigInt y se devuelven como centavos enteros exactos.
Se rechaza cualquier agregado o diferencia fuera del rango entero seguro de JSON.
Se permite saldo esperado negativo (por ejemplo, devoluciones de pagos incluidos
en un corte anterior); no se ocultan faltantes mediante redondeo o límites a cero.

## Integridad y concurrencia

Migración `016_cashier_closings.sql`: `cashier_closings` con ID autoincremental y
dos relaciones necesarias, `cashier_closing_payments` y `cashier_closing_refunds`.
Estas usan el ID de la operación como PK: una operación solo pertenece a un corte.
Todas las referencias son FKs reales. Triggers comprueban cajero y sucursal de
cada vínculo. El rol SQL API tiene SELECT/INSERT; no UPDATE/DELETE en las tres
tablas. No se agregan índices ajenos a PK, claves únicas y FKs.

`auth.withAccess` bloquea el usuario en cada transacción. Las operaciones del
mismo actor se serializan incluso con sesiones distintas. Corte, pago y devolución
usan READ COMMITTED. Los IDs pendientes se leen una vez bajo bloqueo, se suman y
se asignan dentro de la misma transacción; no se usan intervalos de timestamps.
Operaciones posteriores quedan pendientes para el siguiente corte. Una devolución
posterior no modifica el corte que ya incluyó su pago.

Un fallo al insertar cualquier vínculo revierte encabezado, vínculos y clave de
idempotencia. El mismo intento puede repetirse. La integridad de las sumas con los
vínculos depende de esta transacción del backend; SQL comprueba fórmulas del
encabezado, identidad de operaciones y pertenencia única.

## Comandos desde la raíz

Preparar `.env` local conforme a la plantilla, sin credenciales operativas:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --build --rm migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
npm --prefix backend test
npm --prefix backend run check
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests
git diff --check
```

## Evidencia de esta sesión

- Migración 016 aplicada en el volumen sintético local existente, sin reset.
- 44 pruebas unitarias aprobadas con Node `--test-isolation=none`; sintaxis correcta.
- Integración específica de cortes aprobada: tres métodos, cambio excluido,
  devoluciones de cortes anteriores, saldo negativo, dos sesiones/reintento,
  aislamiento de actor/sucursal, recuperación, conflicto, corte vacío, reversión
  de fallo tardío, cobro y devolución simultáneos, unicidad y permisos SQL.
- Primera suite completa: 15/16; la prueba nueva esperaba 503 para un fallo de FK,
  pero la API responde correctamente 409 `REFERENCE_CONFLICT`. Se corrigió esa
  expectativa y pasó la prueba específica.
- Segunda suite completa: 15/16; cortes aprobado. La prueba preexistente de
  inventario produjo `ER_LOCK_DEADLOCK` en recepciones concurrentes. Repetida
  aislada: 1/1 aprobada. No se corrigió ni se considera resuelto el riesgo intermitente
  de inventario; revisar su bloqueo/reintento en el próximo bloque de inventario.
- Para repetir la suite se expiró únicamente el contador de login de la IP del
  contenedor sintético, conservando la política de login y las cuentas.
- 134 comprobaciones estáticas de Supabase aprobadas: validan los contratos
  conservados, no el SQL MariaDB. `git diff --check` correcto.
- Fixtures y trigger temporal eliminados. API local saludable con health 016.
  No se probó instalación desde volumen vacío, importación real, Android ni pgTAP:
  no cambian Android/PostgreSQL. No hubo commit, push ni despliegue.

## Archivos de este bloque

- `backend/src/closings.js`, `backend/src/app.js`.
- `database/mysql/migrations/016_cashier_closings.sql`.
- `backend/test/closings.test.js`, `backend/test/closings-integration.test.js`,
  `backend/test/integration.test.js` (conteo del esquema).
- `backend/package.json`, `infra/docker/compose.yaml`.
- Esta guía, `docs/backend-api-mariadb.md`, `docs/supabase-migration-map.md`.

Siguiente módulo recomendado: inventario por venta y reposición de devoluciones,
con activación gradual y revisión del interbloqueo observado. Los cortes ya están
implementados localmente; integrar los consumidores sigue pendiente.
