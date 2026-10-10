# Cancelación segura de ventas pendientes — etapa 4

Implementación local, 9 de octubre de 2026. No desplegada. Conserva comprobantes
y folios de etapas 2/3, IDs, partidas, importes y trabajo previo sin commit.
No incluye simplificación general de Caja, rediseño Android ni AppCliente.

## Política y contratos inspeccionados

Se revisaron schema.sql, migraciones 012/013/014/015/031, permisos, transacciones
de autenticación, cobro/reconciliación, checkout y estados/historial de pedidos.
La política exige ambas capacidades existentes: OPERATE_CASHIER y
MANAGE_DISCOUNTS, cuenta activa y la misma sucursal activa. No se modifican
roles, asignaciones ni capacidades de cuentas.

- SENT_TO_CASHIER: cancelable únicamente sin pago, movimiento de inventario,
  cobro abierto o pedido COMPLETED asociado.
- PAID/DELIVERED o pago registrado: requiere devolución; no se cancela.
- PAYMENT_PENDING, otros estados o cobro abierto (también vencido): primero
  conciliación. El vencimiento no habilita automáticamente la cancelación.
- CANCELLED: no vuelve a ser cobrable. La misma clave original recupera el
  resultado; otro usuario/intento recibe un rechazo, sin otra auditoría.

La incertidumbre de red actual también vive en el intento local del navegador;
no es un estado SQL nuevo. La UI impide cancelar durante confirmación o resultado
incierto. El backend rechaza todo cobro abierto, incluso vencido. La liberación
explícita pertenece al flujo existente y exige revisar previamente el resultado
y cualquier dinero recibido presencialmente. No se agregó una conciliación
automática ni se presupone conexión a un procesador bancario.

## Migración y operación atómica

`database/mysql/migrations/032_pending_sale_cancellations.sql` agrega
sale_cancellations: PK entera autoincremental, venta única, sucursal, responsable,
motivo obligatorio de 3–240 caracteres, hora UTC del servidor y hashes de intento
y solicitud. La API solo recibe SELECT/INSERT en esta auditoría, sin UPDATE o
DELETE. FKs RESTRICT preservan referencias. No borra ni modifica datos existentes.

Se amplía la transición de sale_status_history para SENT_TO_CASHIER→CANCELLED.
El trigger de venta rechaza reabrir una cancelada y exige condiciones seguras y
su registro de cancelación para cambiar a CANCELLED. El guard de pedidos admite
únicamente la cancelación vinculada a una venta ya cancelada y auditada; conserva
la protección de completar pedidos pagados/devueltos de la migración 015.
La API nueva exige 032 en health; instalación esperada: 61 tablas/31 migraciones.

Dentro de auth.withAccess READ COMMITTED se bloquean pedido web (si existe) y
venta, en ese orden, igual que checkout. Cobro bloquea la misma venta. Se verifican
permisos/sucursal y condiciones nuevamente al confirmar. Solo una operación puede
completar la transición. La cancelación inserta auditoría, cambia estado y agrega
historial de venta; para el pedido, cambia a CANCELLED, aumenta revisión y agrega
historial con el responsable/motivo, todo en una transacción.

No inserta pagos ni movimientos, no repone existencias y no elimina partidas,
relaciones o folios. Checkout conserva su relación única y devuelve la misma
venta CANCELLED al reintentar; no genera otra venta cobrable. Los endpoints de
cobro/claim existentes rechazan su estado.

## API compatible

Nuevos endpoints, sin cambiar campos de contratos anteriores:

- GET `/api/v1/cashier/sales/:id/cancel-options`: OPERATE_CASHIER; autorización
  autoritativa `can_cancel` y motivo de bloqueo. No reserva la cancelación.
- POST `/api/v1/cashier/sales/:id/cancel`: ambas capacidades, body `{reason}` y
  clave Idempotency-Key de 64 caracteres hexadecimales. Primera respuesta 201;
  reintento idéntico 200 con la misma auditoría y fecha.
- POST `/api/v1/cashier/sales/:id/cancellation-result`: mismas capacidades,
  body vacío y clave original; recupera únicamente el resultado del actor/sucursal.

La clave se vincula al actor; el hash de solicitud incluye venta, sucursal y motivo.
Mismo intento con otros datos se rechaza. Errores tardíos revierten todo y permiten
reintentar con la misma clave. El comprobante de cancelación contiene venta,
auditoría y referencia de pedido; no expone los hashes. short-v1 mantiene el folio
corto; sin cabecera conserva el original. Android ya acepta CANCELLED en su parser;
no se modificó código Android en esta etapa. Web antigua mantiene respuestas y
no ve esta acción nueva. Web nueva oculta la acción si una API anterior no la anuncia.

## Caja y recuperación

Acción secundaria «Cancelar venta pendiente», sin cambiar la acción principal de
cobro. Solo aparece tras respuesta positiva del backend y antes de abrir un cobro.
Muestra folio e importe, motivo y confirmación obligatoria de que no se recibió
dinero. Si ya se abrió cobro, se debe conciliar/liberar mediante el flujo existente.

Reutiliza el diario de operaciones de Caja, sus claves aleatorias, bloqueo entre
pestañas, almacenamiento previo al envío, validadores e identidad/sucursal. Una
cancelación pendiente impide otros cobros en esta página y aparece al reabrir
Caja aunque la venta haya salido de la fila. Recupera primero por clave; solo
tras NOT_FOUND reenvía el mismo cuerpo y clave. Errores de red, respuesta inválida,
autorización o error desconocido conservan el intento; únicamente rechazos
explícitos seguros liberan ese pendiente. El resultado validado se conserva hasta
cerrarlo mediante el mecanismo existente. Motivo/hash no se reemplazan al reintentar.

Selecciones obsoletas muestran mensajes de venta cancelada, cobrada o pendiente
de conciliación sin habilitar cobro. Un comprobante público recuperado con estado
CANCELLED muestra «Pedido cancelado» y deja de invitar a pagar. Las copias locales
anteriores siguen siendo comprobantes del pedido al registrarse; no se reescriben
ni se consultan públicamente otras operaciones para actualizar su estado.

## Verificación de esta sesión

| Comprobación | Resultado |
|---|---|
| Backend npm test / npm run check | 89 aprobadas / sintaxis correcta |
| Suite Compose SQL/HTTP | 34 aprobadas en la suite final completa |
| Cancelación ampliada | 8 aprobadas: incluye cuerpo HTTP descartado después del commit y recuperación/reintento sin duplicar auditoría |
| Dos trabajadores | 8 carreras cancelación/cancelación y 8 apertura de cobro/cancelación; un ganador por venta |
| Cobro/cancelación simultáneos | Cobro con reserva activa gana; cancelación rechazada, sin doble transición |
| Pago incierto/pagado | Rechazo de PAYMENT_PENDING y cobros abiertos/vencidos; PAID requiere devolución; no puede cobrarse después de cancelar |
| Auditoría e inventario | Responsable/sucursal/motivo/hora correctos, un evento, comparación exacta de existencias/movimientos/pagos antes/después |
| Pedido vinculado | Misma relación, estado CANCELLED y revisión/historial; error tardío revierte pedido/venta/auditoría |
| Permisos/sucursal | CASHIER sin MANAGE_DISCOUNTS y SALES rechazados; otra sucursal sin acceso; runtime no puede editar auditoría |
| Migración 031→032 final | Aprobada en base aislada: datos de negocio, alias y role_permissions exactamente iguales antes/después |
| Web npm test | 496 aprobadas y una HTTP opcional omitida sin fixture |
| Web lint / build | Correctos, sin avisos de lint |
| Supabase estático | 134 checks aprobados; Supabase no modificado |

La prueba Web nueva cubre pérdida de respuesta, recarga/lectura del diario,
recuperación sin otra escritura, reenvío del mismo intento tras NOT_FOUND,
rechazo de cambios de motivo/identidad/sucursal/estado, formulario obligatorio,
API anterior y mensajes de comprobante cancelado/selección obsoleta.

Se corrigieron durante los ensayos: contacto faltante de un fixture, permiso
del guard de pedido para cancelación auditada y conservación de su condición
de devolución. Los fallos intermedios no se presentan como pruebas aprobadas.
El sandbox bloqueó Vite realpath y loopback de algunos tests (EPERM/EACCES);
las mismas comprobaciones pasaron ejecutándose con autorización fuera de él.

Ensayo final: vivero-cancellations-release, API loopback 33016, redes locales
10.243.80.0/24 y 10.243.81.0/24. Actualización final: vivero-cancellations-upgrade,
redes 10.243.82.0/24 y 10.243.83.0/24; inicializado con migraciones hasta 031 y
fixtures sintéticos antes de aplicar 032 por protocolo SQL. El arranque fresco
también aplicó la fuente final hasta 032. Ensayos previos conservados en sus
volúmenes, no son evidencia de la fuente final. Se detuvieron los contenedores
iniciados para estos ensayos al terminar; no se eliminaron volúmenes.
`git diff --check` pasó en ambos repositorios; permanecen main y sin commit.

No se ejecutaron pgTAP (sin cambios Supabase), compilación Android (sin cambios
Android de esta etapa), pruebas físicas, aceptación humana, SMTP o producción.
La regresión de impresión conserva portal/CSS/formatos y pruebas de etapa 2;
los PDF previamente validados son evidencia histórica, no impresiones físicas nuevas.

## Archivos modificados por etapa 4

App/backend:

- `database/mysql/migrations/032_pending_sale_cancellations.sql` (nuevo).
- `backend/src/sale-cancellations.js` (nuevo), `backend/src/app.js`.
- `backend/test/sale-cancellations.test.js`, `sale-cancellations-integration.test.js`,
  `sale-cancellation-migration-upgrade.test.js` (nuevos).
- `backend/test/integration.test.js` (solo expectativa de tablas).
- `backend/package.json` (scripts), `backend/scripts/verify-local-install.js`.
- `infra/docker/compose.yaml` (prueba adicional).
- `docs/backend-pending-cancellations.md` (este reporte).

Web:

- `src/features/cashier/CashierSaleCancellation.tsx`,
  `cashier-cancellation-service.ts`, `cashier-cancellation.test.tsx` (nuevos).
- `src/features/cashier/CashierPage.tsx`, `cashier-service.ts`,
  `cashier-operations-service.ts`, `backend-cashier-operations-service.ts`.
- `src/features/public-orders/PublicOrderTicket.tsx` (solo texto según CANCELLED).

La base inicial se registró en tmp/cancellations-stage4/baseline: status y diff
de ambos repositorios y copias de archivos solapados. App sigue main/
a58fd614b28920d7b39fee7cef2b67f1c16b0531; Web main/
5f9c4853e42cace586cdaeec13998668b3d204aa. Todos los cambios de IDE, README,
Gradle/versión, compras, catálogo, PanelPage, Android, folios y documentación
anterior permanecen. En archivos compartidos se agregaron solo las piezas de
esta etapa; no se mezclaron mediante staging/commit. tmp/artefactos siguen ignorados.

## Riesgos pendientes y entrega futura

Pedro y Toni deben aceptar en sus equipos la acción secundaria, confirmar el
motivo, reabrir Caja tras un corte de red, ver la venta desaparecer y comprobar
los mensajes al intentar abrir una venta cancelada desde otra sesión.
Concilien presencialmente cualquier dinero recibido antes de liberar un cobro.

Antes de desplegar: respaldo/restauración verificados y destino confirmado,
aplicar migraciones faltantes en orden (incluidas 030/031/032) durante mantenimiento,
comprobar datos/permisos/health, desplegar API y después Web bajo autorización.
No reprocesar ventas ni asignar capacidades automáticamente. DDL MariaDB no es
una transacción global: si falla una migración, inspeccionar estado parcial,
no resetear ni repetir ciegamente.

Los navegadores antiguos no entienden un intento local nuevo de cancelación y
deben fallar de forma cerrada sin borrarlo; no degradar Web con un pendiente sin
resolver. Si cambian permisos/sucursal o se revoca la cuenta, la recuperación
debe esperar a restablecer acceso autorizado; no se omiten controles. La prueba
automática no acredita manejo físico de dinero ni aceptación de interfaz.

Sin commit, push, despliegue, datos de producción, cambios de firma/APK,
activación de CENTRO ni avance a etapas posteriores.
