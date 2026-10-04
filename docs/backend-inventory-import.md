# Importación histórica del inventario

`source-inventory.js` verifica cantidades con milésimas enteras (BigInt), sin
redondeos. Concilia el saldo por sucursal/producto con todos sus movimientos,
rechaza prefijos negativos y comprueba traspasos emparejados y conteos. Los
conteos sin ajuste conservan su historial y no generan movimientos nuevos.

La exportación inspeccionada contiene 23 saldos, 38 movimientos, 6 conteos y
11 pares de traspaso. No hay ubicaciones ni movimientos SALE/REFUND; esas
variantes se rechazan hasta implementar su correspondencia. No se resta el
historial de ventas otra vez: los saldos se explican por el ledger exportado.

## Una sola proyección y un solo ledger

`inventory-import.js` usa las correspondencias oficiales de identidad y catálogo.
Para cada saldo nuevo crea una fila de inventario con cantidad cero e inserta
los movimientos en orden cronológico. El trigger vigente calcula la proyección.
Antes de confirmar compara el resultado exacto con los saldos exportados y
conserva fechas UTC con microsegundos. No invoca recepciones, conteos o cobros HTTP.

El mínimo global antiguo de producto se aplica al inventario de cada sucursal.
Si un producto con mínimo positivo no tiene saldo registrado en una sucursal,
se crea únicamente una fila de cantidad cero para conservar ese mínimo. La
copia actual necesita dos de esas filas: quedan 25 filas de proyección para
23 saldos originales. No son saldos históricos adicionales ni generan movimientos.
Los mínimos cero sin saldo no necesitan filas nuevas.

La migración 029 amplía los tipos y signos del ledger para TRANSFER_IN/OUT y
añade una FK real entre sus movimientos contraparte. Añade tres tablas de
correspondencias con PK autoincremental y FK al saldo/movimiento/conteo destino.
Los UUID solo son referencias externas. Las referencias originales genéricas
se conservan como metadatos de origen; los vínculos operativos de traspaso y
conteo usan IDs enteros y claves foráneas reales. No hay ledger paralelo ni
permisos nuevos para el usuario API.

El importador rechaza filas de inventario existentes sin correspondencia. Una
repetición reutiliza el lote completo si origen y destino siguen intactos;
si hubo cambios operativos posteriores, aborta para revisión y no reemplaza
existencias. Todo fallo antes de confirmar revierte proyección, movimientos,
conteos y correspondencias. Una confirmación incierta se informa explícitamente.

Las sucursales no se activan automáticamente: antes del corte definitivo debe
revisarse la activación operativa de inventario sobre los saldos importados.
El origen no contiene `branch_inventory_activation`.

## Comando local

Aplicar primero las migraciones y la importación de identidad/catálogo en el
**entorno local confirmado**, usando la misma clave estable de origen. Preparar
una carpeta privada ignorada con el snapshot completo como `source.json`:

```powershell
$env:INVENTORY_IMPORT_DIR = (Resolve-Path tmp/inventory-import).Path
$sourceHash = (Get-FileHash "$env:INVENTORY_IMPORT_DIR/source.json" -Algorithm SHA256).Hash.ToLowerInvariant()
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm import-inventory --dry-run --source-key original-source --expected-sha256 $sourceHash
```

Después de revisar el informe, sustituir `--dry-run` por `--apply` para el ensayo
local. El snapshot se monta de solo lectura. No aplicar al VPS como parte de
este bloque. El reporte no muestra nombres, UUID, notas ni filas del origen.

## Evidencia de esta entrega

- 83 pruebas unitarias del backend correctas.
- Dos ensayos SQL correctos: sintético y copia privada real, en bases temporales.
- Ambos construyeron todas las migraciones: 55 tablas y 28 marcadores, hasta 029.
  Se omitieron GRANT/REVOKE del ensayo para no modificar permisos compartidos.
- Ensayo conjunto de identidad, catálogo, ventas/pagos e inventario: saldos y
  mínimos exactos, contraparte bidireccional de traspasos, conteos sin ajuste,
  reversión por fallo provocado, repetición sin duplicados y detección de cambios
  posteriores del destino. Las bases temporales se eliminaron al finalizar.
- Android: `testDebugUnitTest --tests ...BackendInventoryTest`, 11 pruebas
  correctas. El lector admite traspasos y rechaza signos incorrectos. Se compiló
  Kotlin; no se ejecutaron pruebas de dispositivo ni se generó un APK nuevo.

El informe privado de conciliación está en `tmp/source-inventory-reconciliation.json`,
ignorado por Git. No se modificaron datos ni servicios del VPS. No se aplicó la
029 al VPS. Quedan el archivo de imagen, corte con snapshot final, respaldos,
activación operativa y aceptación Web/Android antes de retirar Supabase.
