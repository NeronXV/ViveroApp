# Reportes de ventas y productos: bloque 021

API oficial de solo lectura; no necesita tablas, índices, grants ni migración
nueva. Health conserva el requisito `020_supplier_purchases`. Consumidores
Web/Android continúan en Supabase hasta coordinar su conexión.

## Contrato

GET `/api/v1/reports/daily-sales` y `/api/v1/reports/top-products` requieren
Bearer y VIEW_REPORTS. VIEW_ALL_SALES permite todas las sucursales o filtrar una
con branch_id; sin esa capacidad solo se consulta la sucursal propia y pedir otra
devuelve 403. No hay bypass por nombre de rol. Usuarios sin sucursal propia
necesitan VIEW_ALL_SALES. Las sucursales inactivas conservan historial consultable.

Parámetros únicos: branch_id entero, start_date y end_date YYYY-MM-DD,
limit. Fechas UTC reales; final inclusivo convertido al siguiente día exclusivo.
Rango máximo 366 días. Diario por defecto últimos 30 días hasta el momento de
consulta, limit 200 (máximo 500). Si el resultado supera limit, has_more=true:
acotar fechas/sucursal para obtener las filas restantes. No se pretende exportar
un historial ilimitado desde este endpoint.

Top por defecto todo el historial, limit 10 (máximo 100). Opcionalmente admite
fechas: mismo rango UTC, y un extremo omitido usa 30 días anteriores al final o
el instante actual. Orden descendente por cantidad, con desempate estable por
producto/nombre/código. Agrupa por snapshots de producto, nombre y código; un
producto renombrado puede ocupar varias filas históricas. Cantidades decimales
se devuelven como strings exactos. Dinero y conteos como enteros JSON; overflow
devuelve 409 REPORT_TOTAL_INVALID, sin redondear ni perder centavos.

Diario devuelve items con branch_id, branch_name, day, sales_count,
revenue_cents y discount_cents. Usa fecha y amount_due_cents de cashier_payments,
sin filtrar el estado actual de venta, conforme al RPC vigente tras promociones.
No usa efectivo entregado ni resta cambio: ingreso es el importe cobrado.
Incluye el cobro histórico aunque luego haya devolución; no es ingreso neto.
Descuento = descuento de cabecera + suma por línea de
(list_price_cents - unit_price_cents) * quantity. Para líneas históricas sin
precio de lista se usa unit_price_cents. No consulta precios/promociones actuales.

Top devuelve product_id, product_name, product_code, total_quantity,
total_revenue_cents. Conserva el RPC anterior: solo ventas PAID, cantidades y
line_total_cents de snapshots, sin asignar descuentos de cabecera a productos.
El filtro opcional de fecha usa creación de venta, no fecha de pago. Por eso no
debe compararse su suma directamente con ingreso diario. Ventas devueltas o
entregadas dejan de figurar si ya no tienen estado PAID; no se cambia esa regla
histórica silenciosamente. Reportes netos/contables son otro contrato pendiente.

Respuestas incluyen schema_version:1, timezone:UTC, branch_id, start y
end_exclusive; null en fechas del top sin filtro. Sin datos personales ni claves.

## Archivos y comandos

Archivos de este bloque: backend/src/reports.js, backend/src/app.js,
backend/test/reports.test.js, backend/test/reports-integration.test.js,
backend/package.json, infra/docker/compose.yaml, esta guía,
docs/backend-api-mariadb.md y docs/supabase-migration-map.md.

Desde la raíz con `.env` local según plantilla:

```powershell
docker desktop start
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests npm test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests
npm --prefix backend run check
git diff --check
```

No Kotlin/PostgreSQL modificados; compilación Android y pgTAP no aplican.
Volumen vacío, consumidores y datos reales aún pendientes. Sin commit/push/despliegue.

## Validación de esta sesión: 2026-10-01

55/55 pruebas unitarias aprobadas localmente con Node 24 y
`--test-isolation=none` (evita spawn EPERM del sandbox Windows). Check de sintaxis
aprobado. No se cambió el comando normal de npm test ni su aislamiento en Docker.

Integración nueva preparada con fixtures sintéticos y limpieza: fecha de cobro
frente a creación, límite inclusivo/exclusivo, descuentos exactos sin duplicar
pagos por líneas, snapshots, aislamiento por sucursal, ranking PAID y overflow.
**Primer intento bloqueado**: Docker Desktop no exponía dockerDesktopLinuxEngine. El log
identifica fallo de arranque al retirar el socket residual
`AppData/Local/Docker/run/sailor-ingest.sock`; backend detenido. Inicio por CLI
no recuperó el motor. Retirar únicamente ese socket falló por acceso denegado
en PowerShell y Node incluso fuera del sandbox. No se modificaron ACL, servicios,
WSL ni volúmenes. API anterior tampoco pudo comprobarse por health esta sesión.
En ese intento la integración quedó pendiente; no se consideraron las 20 pruebas
del bloque anterior como una validación nueva.

### Recuperación y validación final (2026-10-01)

Docker recuperado sin alterar ACL, WSL, configuración ni volúmenes. Con backend
detenido se conservaron las carpetas de sockets de comunicación bajo otros
nombres; se verificó que solo contenían sockets vacíos. Docker regeneró su run.
El siguiente arranque identificó otro socket residual en docker-secrets-engine,
cuya carpeta también contenía únicamente engine.sock vacío y se conservó.
Respaldos locales fuera del repositorio, bajo AppData/Local:
Docker/run-stale-20261001, Docker/run-stale-20261001-2 y
docker-secrets-engine-stale-20261001-2. No se borraron archivos ni se importaron
datos. Este procedimiento se documenta como reparación puntual verificada,
no como instrucción para mover carpetas que puedan contener datos en otro equipo.

Motor Docker 29.7.2 disponible; API reconstruida y healthy con el volumen existente.
Prueba de reportes HTTP/SQL aprobada. Suite completa final **21/21 integraciones**
y **55/55 pruebas unitarias** en Docker Node 24 aprobadas en esta continuación,
sin cambiar código ni relajar pruebas. Check de sintaxis y git diff --check
aprobados. El bloqueo de integración de reportes queda resuelto.

Próximo paso recomendado: validación de instalación integral desde un volumen
nuevo y aislado, conservando el actual; después coordinar conexión de consumidores.
