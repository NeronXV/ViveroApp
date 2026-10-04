# Preparación del catálogo desde la exportación real

El adaptador `backend/scripts/source-catalog.js` valida el snapshot completo y
prepara los campos que consume el importador oficial de catálogo. No escribe
en MariaDB. El comando `prepare-source-catalog.js` genera dos archivos privados:

- `catalog.json`: categorías/productos con los UUID de origen como referencias
  externas. El importador existente asigna IDs enteros y guarda sus relaciones.
- `metadata.json`: hash del origen, fechas originales y mínimos de inventario
  en milésimas enteras. Estos valores todavía no se aplican a MariaDB.

Los precios en centavos se convierten solamente si caben exactamente en el
contrato entero seguro del backend. Los importes fuera de rango se rechazan;
nunca se redondean. Las columnas desconocidas detienen la adaptación.
Los mínimos no pertenecen a `products` en MariaDB: se aplicarán por sucursal
durante la importación de inventario. Las imágenes requieren sus archivos aparte.
El snapshot original permanece intacto y conserva el historial completo.

## Preparar archivos privados

Desde la raíz del repositorio, con un JSON ya validado y una carpeta de salida
nueva dentro de `tmp/` (ignorada por Git):

```powershell
$sourcePath = (Resolve-Path tmp/source-export.json).Path
$sourceHash = (Get-FileHash $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
node backend/scripts/prepare-source-catalog.js --file $sourcePath --source-key original-source --expected-sha256 $sourceHash --output-dir tmp/catalog-import-ready
```

El comando no sobrescribe carpetas existentes. No compartir los archivos ni
abrirlos en Excel. Conservar `metadata.json` junto al catálogo para el paso de
inventario; no tomar su generación como una importación de esos mínimos.

Para el importador de catálogo existente, en el **entorno local confirmado**:

```powershell
$env:CATALOG_IMPORT_FILE = (Resolve-Path tmp/catalog-import-ready/catalog.json).Path
docker compose --env-file .env -f infra/docker/compose.yaml --profile tools run --rm import-catalog --dry-run
```

Para aplicar en el ensayo local, consultar el hash del **catalog.json** y usar
`--apply --expected-sha256 <hash>` según `backend-catalog-import.md`.
La clave estable `original-source` debe mantenerse al preparar identidad,
catálogo y los próximos módulos. No confundir el hash del snapshot completo
con el del archivo de catálogo derivado.

## Validación de este bloque

- Adaptación real: 2 categorías, 15 productos y 15 mínimos conservados aparte.
- Dos pruebas nuevas del adaptador: exactitud monetaria, fechas/mínimos,
  rechazo de columnas nuevas, precisión no representable y hash incorrecto.
- Ensayos SQL sintético y real en bases temporales: importación completa,
  fallo provocado con reversión, repetición sin duplicados y comprobación
  exacta de precios minoristas y mayoristas por producto.
- Los ensayos también comprobaron rechazo del cambio del precio de origen.
- Ambas bases se eliminaron al terminar; no se importó catálogo al VPS.

El [ensayo histórico posterior](backend-history-import.md) ya comprobó la
importación conjunta de identidad, catálogo y ventas/pagos en bases temporales.
Quedan saldos/movimientos y mínimos por sucursal, archivos de imágenes y
conciliación final. No se aplicó esta importación al VPS.
