# Etiquetas QR de productos

La etiqueta codifica únicamente el `internal_code` exacto del producto. El nombre,
precio, promoción e inventario se consultan en Supabase al escanear; por eso pueden
cambiar sin reimprimir el QR.

## Generar una hoja de prueba

El archivo `qr-labels-sample.csv` contiene los productos del catálogo demo. Para
productos remotos, copia el archivo y sustituye cada fila por códigos internos que
ya existan en Supabase. El formato es:

```csv
code,name,presentation
PL-001,Monstera deliciosa,Maceta de 6 pulgadas
```

Genera el HTML imprimible con:

```powershell
.\gradlew.bat :app:generateQrLabels
```

El resultado queda en `app/build/qr-labels/labels.html`. Puede abrirse en el
navegador e imprimirse en A4 al 100 %, sin ajustar la escala. Cada etiqueta mide
50 × 30 mm y muestra también el código legible para captura manual.

Para usar otro CSV o destino:

```powershell
.\gradlew.bat :app:generateQrLabels `
  -PqrLabelsFile="C:\ruta\mis-productos.csv" `
  -PqrLabelsOutput="C:\ruta\etiquetas.html"
```

## Prueba recomendada

1. Inicia sesión en la app con un trabajador asignado a una sucursal.
2. Escanea primero una etiqueta desde pantalla y luego la misma etiqueta impresa.
3. Confirma nombre, precio, promoción y existencia de esa sucursal.
4. Repite con poca luz, a 20–60 cm y con la etiqueta ligeramente inclinada.
5. Escanea un código inexistente y confirma que la app permita intentar de nuevo.

Un producto y presentación comparten QR. Los lotes, ubicaciones y ejemplares únicos
usarán prefijos y contratos separados cuando se implemente la siguiente etapa de
trazabilidad.
