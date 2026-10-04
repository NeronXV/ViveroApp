# Importación histórica de ventas y pagos

El bloque admite el historial presente en la exportación inspeccionada: ventas
PAID, cantidades enteras, sin clientes asociados ni descuentos/promociones,
un pago por venta y reclamaciones cerradas. Si otra exportación introduce estados,
descuentos o reclamaciones abiertas, aborta para completar su mapeo; no los omite.

`source-sales.js` concilia partidas/subtotales/totales, recibido/cambio, reclamación
del pago y la cadena DRAFT → SENT_TO_CASHIER → PAID. Calcula importes con BigInt y
devuelve solamente hash, conteos y totales por índice de sucursal.

`history-import.js` usa las correspondencias persistentes de identidad y catálogo
con la misma clave de origen. Inserta el historial en una transacción administrativa,
sin invocar HTTP, RPC, confirmación de pagos o movimientos de inventario. Rechaza
folios ya existentes sin correspondencia y cambios de origen o destino al repetir.

La migración 028:

- Añade cinco tablas necesarias de correspondencias, una por tipo de registro,
  con IDs autoincrementales, unicidad del origen y claves foráneas reales.
- Conserva fechas con microsegundos mediante TIMESTAMP(6) en ventas/pagos y
  `sale_items.created_at`. No cambia valores ni importes de registros existentes.
- No concede permisos nuevos al usuario de la API.

## Información conservada y límites

Se conservan folios, nombres/códigos de producto del ticket, cantidades, precios,
importes, sucursal, actores, estados, observaciones y fechas UTC. Las 15
reclamaciones cerradas se conservan como historial; se generan tokens nuevos
inertes por su estado cerrado. Los tokens originales no fueron exportados.

Las claves operativas antiguas no habilitan reenvíos: los pagos reciben una clave
determinista de mantenimiento por origen/registro y `request_hash=NULL`; las
ventas históricas mantienen hashes operativos nulos. Las claves originales
permanecen en el snapshot privado. No constituyen una migración de intentos
pendientes de clientes ni de sesiones. Los descuentos de esta copia son cero.

Los mínimos, saldos y movimientos de inventario se importan por separado. No
recalcular existencias restando estas ventas otra vez.

## Comandos locales

Primero aplicar migraciones e importar identidad y catálogo en el **entorno local
confirmado**, usando la misma clave estable `original-source`. Preparar una carpeta
privada ignorada `tmp/history-import` con `source.json`, el snapshot completo.
El origen queda montado de solo lectura.

```powershell
$env:HISTORY_IMPORT_DIR = (Resolve-Path tmp/history-import).Path
$sourceHash = (Get-FileHash "$env:HISTORY_IMPORT_DIR/source.json" -Algorithm SHA256).Hash.ToLowerInvariant()
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm import-history --dry-run --source-key original-source --expected-sha256 $sourceHash
```

Tras revisar el informe, usar `--apply` para el ensayo local. Si la confirmación de
transacción resulta incierta, el comando lo declara con `import_applied:null`;
revisar con el mismo archivo/clave, sin inventar nuevas claves ni asumir reversión.
No aplicar estos comandos al VPS como parte de este bloque.

## Evidencia local

La copia real concilió 14 ventas, 18 partidas, 14 pagos, 42 cambios de estado y
15 reclamaciones cerradas. El informe privado está en
`tmp/source-sales-reconciliation.json` y no se versiona.

El ensayo conjunto construyó bases MariaDB temporales independientes, una con
datos sintéticos y otra con la exportación real. Aplicó identidad, catálogo y
historial usando solo esas bases. Verificó:

- Fallo provocado durante el pago: reversión completa de ventas, partidas,
  reclamaciones, pagos y correspondencias históricas.
- Repetición: todos los registros reutilizados, sin duplicados.
- Totales exactos de pagos, recibido y cambio; folios y fechas con microsegundos.
- Conservación de la cuenta OWNER existente con un marcador de contraseña
  sintético; la contraseña real del VPS no se leyó ni copió.
- Cero cambios en inventario y cero reclamaciones activas.
- Rechazo de modificaciones posteriores en destino.

La prueba no aplica GRANT de los archivos a la base compartida: prueba su DDL en
la base temporal y conserva los permisos locales existentes. Las bases temporales
se eliminaron al finalizar. No certifica arranque limpio ni despliegue de la 028.

Validaciones finales: 80 pruebas unitarias del backend correctas, dos ensayos
SQL conjuntos correctos y configuración Compose validada con `config --quiet`.
Las comprobaciones de sintaxis y `git diff --check` también pasaron.

Pruebas nuevas:

```powershell
node --test --test-isolation=none backend/test/source-sales.test.js
```

El ensayo SQL requiere el perfil local de pruebas y montaje de `/database`;
`npm run test:history-import` usa `history-import-integration.test.js`. Para el
ensayo privado se montan exclusivamente el snapshot, el inventario mínimo del
destino y las resoluciones, sin publicar sus filas en logs.

La verificación estática de Supabase no ejecuta este DDL MariaDB. No hay cambios
de PostgreSQL que requieran ejecutar pgTAP. El [ensayo posterior de inventario](backend-inventory-import.md) ya comprobó
localmente la importación conjunta con saldos/movimientos y mínimos. Quedan
archivos de imágenes, copia final y conciliación tras detener operaciones del
origen. No se modificó el VPS ni se apagó Supabase.
