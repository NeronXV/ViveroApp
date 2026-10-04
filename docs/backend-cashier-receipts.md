# Historial de comprobantes y búsqueda de devoluciones

Estado posterior, 2026-10-02: Web ya usa API y Android activa historial y detalle
de comprobantes. [Integración Android y cierre de pagos](backend-android-history.md).
La evidencia siguiente conserva el alcance original de este bloque.

Implementado el 2026-10-01 como requisito para conectar la sesión Backend API y Caja Web sin perder la consulta de comprobantes ni la búsqueda por folio. La pantalla visible y AuthProvider siguen en Supabase. Este bloque no activa el cambio de proveedor.

## API oficial

- `GET /api/v1/cashier/receipts?limit=50&before_id=...`: pagos del cajero autenticado y su sucursal activa. IDs de pago en orden descendente, límite 1–100, cursor `next_before_id` o null. No es una bandeja de ventas pendientes ni un informe de todos los cajeros. No requiere conservar la clave usada al cobrar.
- `GET /api/v1/cashier/receipts/:paymentId`: venta pagada/entregada, pago, líneas guardadas, sucursal y devolución total si existe. No cobra ni modifica registros. Otro cajero o sucursal recibe 404, incluso un administrador; los informes mantienen sus contratos separados.
- `GET /api/v1/cashier/refunds/lookup?folio=...`: mismo preview que la consulta por ID, con importe original, estado de devolución y disponibilidad de reposición. Requiere `OPERATE_CASHIER` y `MANAGE_DISCOUNTS`, además de sucursal propia activa. Folio exacto sensible a mayúsculas; se recortan espacios exteriores, máximo 40 caracteres. No acepta parámetros repetidos/adicionales ni caracteres de control. Venta ausente/en otra sucursal: 404; venta no pagada: 409.

Todas las lecturas requieren sesión y autorización en servidor, usan parámetros SQL y el control de origen existente. No exponen hash de petición, clave idempotente ni token de reserva. El historial requiere solamente `OPERATE_CASHIER`. Las rutas rechazan mutaciones y consultas no admitidas.

La devolución no borra el comprobante: se informa por separado su importe y método. Los precios y cantidades proceden de las líneas guardadas; no se recalculan con el catálogo actual. La sucursal presenta su nombre vigente. Líneas anteriores sin código/precio de lista conservan null en esos campos: el consumidor histórico lo admite sin inventarlos; el detalle operativo mantiene su validación previa. Los recibos devueltos no implican que se pueda volver a entregar o cobrar la venta.

## Consumidores Web

`backend-cashier-service.ts` incorpora `receipts` y `receipt`, con validación estricta de IDs, orden, cursor, campos, identidad, sumas y cambio. `backend-cashier-operations-service.ts` incorpora `refundLookup`, comprobando primero identidad/sucursal y que el folio de respuesta coincida. Siguen dentro de las funcionalidades y rutas existentes; no se creó otro proveedor, API ni página.

## Validación nueva

- Web: `npm run lint`, `npm test` (427 pruebas / 33 archivos), `npm run build`: correctos.
- Backend en Docker: `npm test` (60 pruebas), `npm run check`: correctos.
- Integraciones específicas `test/cashier-integration.test.js` y `test/refunds-integration.test.js`: correctas. Incluyen exclusión de otro cajero/sucursal, paginación, parámetros inválidos, folio exacto y devolución previamente registrada, junto con los controles de cobro/rollback existentes.
- Runner Web `scripts/verify-backend-cashier-operations.mjs`: correcto contra Vite → API → MariaDB; consulta historial y comprobante antes/después de devolución y localiza por folio, además del recorrido previo de respuesta perdida y recuperación de corte. Limpieza sintética correcta. Sesión, almacenamiento y bloqueo son fixtures/adaptadores; no constituye prueba de UI o navegador nativo.
- Se usó `vivero-fresh-20261001c`, puerto 33002; al terminar se detuvo conservando volúmenes. El entorno anterior no se modificó.

Para repetir el runner, consultar `backend-web-cashier-operations.md` de ViveroWeb. Para las pruebas del backend desde ViveroApp, con un env local sintético y Compose aislado ya levantado:

```powershell
docker compose --env-file tmp/browser-validation-20261001.env -p vivero-fresh-20261001c -f infra/docker/compose.yaml --profile test run --build --no-deps --rm tests node --test test/cashier-integration.test.js test/refunds-integration.test.js
```

No hay migración SQL, nuevos índices, cambios Android ni cambios del flujo visible. Se omitió compilación Android/pruebas Supabase porque no cambió su código o esquema. No se repitió toda la suite de integración del backend: se ejecutaron las integraciones afectadas y toda la suite unitaria.

Archivos de este bloque: `backend/src/{app,cashier,refunds}.js`; `backend/test/{cashier-integration.test,refunds-integration.test,refunds.test,web-cashier-operations-fixture}.js`; esta guía y mapa de migración; en Web los dos servicios de Caja preparados, sus dos archivos de pruebas, runner y guía de devoluciones/cortes. Se preservó el trabajo anterior; no hubo commit, push, Supabase remoto ni despliegue.
