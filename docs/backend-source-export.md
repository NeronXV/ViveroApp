# Exportación del origen para conservar datos

## Esquema inspeccionado

Pedro confirmó que el proyecto inspeccionado es el origen de Web y Android.
El inventario proporcionado contiene 30 tablas públicas. Faltan:
`branch_inventory_activation`, `sale_refunds`, `cashier_closings`,
`cashier_closing_payments`, `cashier_closing_refunds`,
`newsletter_subscribers`, `newsletter_campaigns`, `newsletter_deliveries`.

Para este esquema usar `database/migration/export-supabase-legacy.sql`.
Es una variante de la misma exportación de datos, no otro backend.
El archivo original `export-supabase.sql` sigue destinado al esquema completo.

La variante usa formato 2: declara `absent_tables` y omite esas tablas de
`tables`. Una ausencia no equivale a cero filas. La consulta aborta si alguna
de esas ocho tablas ya existe, para evitar excluir datos recién incorporados.
Las otras tablas son obligatorias; no se ignoran errores de permisos o tablas
faltantes. Incluye claves primarias y foráneas del origen.

## Obtener la copia privada

1. Confirmar el proyecto original en el panel de Supabase.
2. Abrir SQL Editor y reemplazar la consulta anterior con **todo** el contenido
   de `export-supabase-legacy.sql`, incluyendo BEGIN y COMMIT.
3. Ejecutar una sola vez. La transacción es de solo lectura y no crea tablas,
   usuarios ni permisos. Si falla, no ejecutar fragmentos ni crear tablas para
   hacerla pasar; conservar el mensaje de error sin datos personales.
4. Guardar el valor completo de la columna `migration_snapshot` en un archivo
   UTF-8 privado, por ejemplo `tmp/source-export.json` (carpeta ignorada).
   El verificador espera el objeto JSON, no CSV ni el array envolvente de
   «Copy as JSON». Si se descargó CSV o se copió ese envoltorio, conservarlo
   intacto e indicar su ruta/formato para convertirlo sin Excel ni redondeos.
5. No pegar el contenido en el chat ni incluirlo en Git. Compartir solo la ruta.

Desde la raíz del repositorio:

```powershell
node backend/scripts/preflight-source-export.js --file tmp/source-export.json
```

El reporte contiene hash, conteos, tablas ausentes y códigos de error; nunca
filas ni credenciales. Comprueba claves, referencias e importes exactos. No
conecta a bases de datos ni aplica la importación. Una validación correcta no
certifica compatibilidad con MariaDB ni constituye el corte definitivo.

## Pendientes y límites

La consulta adaptada aún debe ejecutarse y comprobarse contra el origen real.
Las pruebas locales del verificador usan datos sintéticos. La verificación
estática de migraciones no valida la ejecución de esta consulta PostgreSQL.
No se ejecutaron pruebas pgTAP: no hay un PostgreSQL local confirmado activo.

Los archivos binarios de Storage se exportan por separado; este JSON solo
incluye referencias de imágenes. Las cuentas importadas requieren un flujo de
restablecimiento de contraseña: no se exportan contraseñas ni sesiones.

Antes de escribir en MariaDB faltan el mapeo de identidad/sucursal existente,
la importación histórica y la conciliación de ventas, pagos, existencias y
folios. No apagar Supabase ni reenviar operaciones históricas como nuevas.
