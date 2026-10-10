# Catalogación móvil permanente

Reutiliza el catálogo móvil premium de ViveroWeb y la preparación comercial /
inventario 033. Un producto por presentación.

**Publicada el 10 de octubre de 2026:** versión `20261010-825a14bddbc6`,
034 → API → Web, HTTPS saludable en `https://viverodulcinea.bajastack.network`.
Respaldo productivo 033 nuevo, cifrado y restaurado en un clon aislado:
63 tablas, 671 registros, 114 relaciones y cuatro cuentas técnicas verificadas.
MariaDB, proxy y Catering conservaron contenedores/configuración; Catering 200.
Los 174 borradores no se importaron: bandeja productiva vacía. La aceptación
autenticada desde celulares reales queda pendiente; las pruebas de sesiones,
permisos, guardado/fotos y rechazo de venta pendiente se ejecutaron exclusivamente
con cuentas sintéticas en el clon. No se reutilizó la sesión manual de Pedro.

Reporte de publicación, evidencia y recuperación: `tmp/deploy-cataloging-034/`.
La copia cifrada externa se conserva en
`tmp/pilot-backup/ViveroDulcinea-cataloging-033-20261010-184721.aesgcm`;
clave DPAPI separada, ligada al perfil Windows. Imágenes anteriores conservadas;
retroceder imágenes no revierte 034. El resto de este documento conserva el
historial de entrega y sus condiciones anteriores a esta publicación.

## Uso

En Administración → Plantas, abrir «Revisión de plantas y nuevas fichas».
«Nueva planta» permite guardar un nombre original aunque falten identidad,
presentación, categoría y SKU. Los borradores son compartidos entre usuarios con
MANAGE_PRODUCTS y sucursal activa. El original y la evidencia importada son
inmutables; propuestas y datos originales permanecen separados.

Completar los datos disponibles, guardar avances, tomar foto o elegirla de la
galería, guardar fotografía y enviar a revisión. Las fotos se normalizan como
WEBP, sin metadatos, con los límites existentes de 5 MiB/16 megapíxeles/2048 px.
Los borradores no tienen precios comerciales, stock ni presencia en Caja.

El propietario puede devolver el borrador, vincular explícitamente un producto
existente sin modificarlo, o confirmar identidad/presentación/categoría/SKU y
preparar un producto nuevo. Este último nace INACTIVO, PENDING, precio cero sin
confirmar y sin inventario. La foto confirmada acompaña su ficha. No se interpretan
medidas B/M ni se asignan automáticamente nombres, categorías o SKU.

En la ficha del producto se confirma el precio con MANAGE_PRICES y se registra
el conteo mediante las observaciones existentes. El propietario aprueba el
conteo; la habilitación continúa bajo las capacidades/requisitos de 033.
Se conserva el catálogo anterior, incluida la compatibilidad LEGACY.

## Guardados y colaboración

La migración 034 añade únicamente cataloging_drafts, cataloging_events y un
contador de revisión en products, incrementado por trigger ante cualquier edición.
No cambia saldos, ventas, precios ni asignaciones de roles/capacidades.

Cada guardado de borrador exige la revisión leída y una clave idempotente.
Actor, payload y resultado quedan auditados. Una respuesta perdida se recupera
con el mismo intento, guardado en un diario local por usuario/sucursal antes
del envío. No se admite otro guardado hasta resolverlo. Los eventos históricos
no se actualizan ni borran. El responsable se muestra por su ID y hora del servidor.

Un conflicto conserva el formulario y obliga a consultar/comparar la versión
guardada. No hay merge automático ni sincronización en tiempo real.
Las fotos guardadas con respuesta incierta se consultan antes de reenviarlas;
no se persiste el archivo original en el diario de texto.

Las fichas existentes usan If-Match y una lectura consistente de versión/datos.
Sus fotos se cambian con un único PUT protegido por versión, conservando imágenes
anteriores en la galería. Las rutas antiguas y respuestas de productos/Caja no
cambian; clientes anteriores siguen admitidos y sus ediciones incrementan revisión.

Contratos nuevos (sesión + MANAGE_PRODUCTS; actor/sucursal esperados en borradores):

| Ruta `/api/v1/` | Operación |
|---|---|
| GET `cataloging?status=ALL&limit=100&after_id=…&search=…&category_id=…` | Bandeja central, con cursor; estados DRAFT/REVIEW/LINKED/PREPARED. |
| POST `cataloging` | `{original_name,fields}`; Idempotency-Key. |
| GET `cataloging/{id}` | Datos, revisión, responsable y evidencia. |
| PATCH `cataloging/{id}` | `{revision,action,fields}`; SAVE/SUBMIT o RETURN/PREPARE/LINK del propietario. LINK añade product_id. |
| GET/PUT `cataloging/{id}/photo` | Foto privada; PUT exige X-Cataloging-Revision e Idempotency-Key. |
| GET `products/{id}/edit-version` | Contador para edición protegida. |
| PUT `products/{id}/catalog-photo` | Carga atómica con If-Match. |

fields admite approved_name, presentation, internal_code, category_id,
scientific_name, description y barcode. Rechaza precio, cantidad, activación,
source_evidence y otros campos. No existe conversión automática de borrador a venta.

## Precarga preparada, sin aplicar

El archivo privado `tmp/conciliacion-catalogo-20261010-etapa2/cataloging-drafts.preload.json`
contiene 174 BORRADORES DE FUENTE, no 174 productos: 96 agrupaciones del cuaderno
y 78 partidas cotizadas. Cada source_id conserva su PLA/COT. Todos tienen fields
vacío. Presentaciones, categorías sugeridas, candidatos, costos y cantidades
quedan únicamente como evidencia sin confirmar.

Generador offline, sin conexión ni sobrescritura de salidas:

```powershell
node backend/scripts/prepare-cataloging-drafts.js INPUT.csv OUTPUT.json dulcinea-review-20261010
```

El importador separado exige manifiesto vacío de campos aprobados, origen/ID
estable, actor propietario autorizado y hash del archivo. Simula con transacción
READ ONLY. Aplicación local transaccional, bloqueo compartido con importadores,
repetición por source_key/source_id y rechazo de evidencia original cambiada.
Las ediciones humanas posteriores se conservan al repetir la misma fuente.
La CLI mantiene la restricción local de openAdminDb: no se apunta al VPS ni se
eluden sus validaciones. Una precarga productiva requiere autorización separada
y un procedimiento administrativo controlado; no está incluida en publicar la UI.

## Validación realizada y límites

- Backend: check/check:cataloging y 21 pruebas unitarias focalizadas aprobadas.
- MariaDB nativa 11.4.13: 8 pruebas SQL/HTTP aprobadas usando privilegios reales
  de catalog_api, sesión/login reales, upgrade 033→034, conservación de productos
  e inventario, concurrencia, fotos, idempotencia, roles y preparación inactiva.
  Caja cotiza/cobra un producto LEGACY una sola vez, conserva saldo con control
  deshabilitado y rechaza productos pendientes. Datos exclusivamente sintéticos.
- Ensayo anterior en MariaDB 13.0.2: 7 pruebas SQL/HTTP aprobadas. No sustituye
  la validación posterior 11.4.13. Docker no pudo iniciarse; 11.4.13 se obtuvo como
  ZIP portátil oficial con SHA-256 comprobado, sin instalación ni servicio Windows.
- Web: build/lint y 49 pruebas focalizadas de catálogo, diario, conteos y Caja
  aprobadas en seis archivos. También pasó el smoke final 11.4.13 de colisión y
  rollback de borrador, con las fuentes definitivas.
- Chrome real, viewport 390×844, contra API/MariaDB sintéticas: login gerente y
  propietario, guardado, foto, revisión, producto inactivo y conteo cero enviado
  como observación. Sin errores JavaScript ni desbordamiento horizontal. Capturas
  en tmp/cataloging-mobile-20261010/screenshots. Este recorrido se ejecutó contra
  MariaDB 13.0.2; las pruebas SQL/HTTP posteriores usan 11.4.13.
- Verificación estática Supabase: 134 checks aprobados, sin modificar PostgreSQL.
  No es una validación SQL de MariaDB; esa evidencia es el ensayo 11.4.13 anterior.

No se probó cámara física ni aceptación de usuarios reales. Android no cambió;
no se ejecutaron builds Android ni la auditoría integral prevista con Astra.

## Publicación coordinada pendiente de autorización

Preparar imágenes API/Web nuevas a partir del paquete congelado. El Docker
local no está disponible: las imágenes OCI de esta entrega todavía no se construyeron.
El paquete fuente/bundle y su manifiesto permiten construirlas en un entorno
Docker autorizado sin incluir tmp, credenciales ni manifiestos reales.

Después de autorización expresa de Pedro, confirmar VPS/sitio/Compose efectivos,
marcador 033 y usuarios OWNER operativos. Registrar IDs, imágenes, inicios y
configuración de DB, proxy y Catering para comparar al finalizar. Usar únicamente
la pila de Vivero, sin modificar/rediseñar proxy, redes, volúmenes o Catering.

1. Fijar imágenes API/Web y guardar imágenes anteriores. Verificar hashes del paquete.
2. Cerrar escritores de Vivero y tomar un respaldo PRODUCTIVO NUEVO de SQL,
   fotos, cuentas/grants/definers y configuración, con copia cifrada externa.
   Restaurar/verificarlo en destino aislado. El respaldo histórico 032 no sirve
   como respaldo actual de 033 y este ensayo local no acredita una copia productiva.
3. Sólo con recuperación verificada, aplicar ÚNICAMENTE 034 mediante migrador
   canónico y SQL congelado. Comprobar preservación de las tablas existentes.
4. Publicar API y Web de Vivero con los mismos Compose efectivos + overlay nuevo:
   `compose run --rm -T --no-deps --pull never migrate`, luego
   `compose up -d --no-deps --no-build --pull never api web`.
   Aquí compose representa la invocación completa verificada de la pila vigente,
   no un docker compose genérico. No recrear DB/proxy/Catering.
5. Verificar health/034, bundle servido, login/sucursal/permisos, catálogo,
   guardado/foto/conteo y Caja. Utilizar pruebas productivas acordadas, sin crear
   ventas ficticias ni cargar las listas reales automáticamente.
6. Comparar que DB/proxy/Catering conservan contenedores/configuración y salud;
   reabrir Vivero únicamente tras aceptar el resultado.

Si falla backup/restauración, no migrar. Si el DDL queda parcial, detenerse con
escritores cerrados; no reejecutar a ciegas ni borrar tablas. Revertir imágenes
no revierte SQL; cualquier recuperación SQL exige aprobación y conciliación.

No se ejecutó ninguna operación remota, backup productivo nuevo, precarga real,
commit, push ni despliegue. Se preservó el trabajo local previo de ambos repositorios.

## Archivos de esta entrega

ViveroApp: nuevos backend/src/cataloging.js; backend/scripts/cataloging-import.js,
import-cataloging-drafts.js y prepare-cataloging-drafts.js; backend/test/cataloging.test.js
y cataloging-integration.test.js; database/mysql/migrations/034_cataloging_drafts.sql;
este documento. Ampliaciones puntuales en backend/src/app.js, images.js,
backend/package.json y backend/scripts/verify-local-install.js.

ViveroWeb: nuevos src/features/admin/plants/CatalogingBoard.tsx,
cataloging-service.ts y cataloging-service.test.ts; tests/cataloging-browser.mjs.
Ampliaciones en AdminPlants.tsx, PlantDetail.tsx, plants-service.ts, plants.css y
src/features/admin/admin-catalog-types.ts. Los archivos de plantas y cambios 033
ya estaban presentes sin commit; se ampliaron sin reemplazar el trabajo previo.

La precarga real, evidencia privada y artefactos quedan exclusivamente en tmp
ignorado. El paquete catalogacion-movil-034.zip y manifest-sha256.json están en
tmp/cataloging-mobile-20261010/release-final; contienen backend/SQL y bundle Web, sin
credenciales, base de datos, fotos reales ni precarga. ZIP/hash verificados.
No se generaron imágenes Docker. Ambas ramas/HEAD permanecen en main:
ViveroApp 239411caeefc715feb55c9a8fa21163c218a9fde; ViveroWeb
f208ffb1dabf8db5d740a5c17cab7a09d33236d6.
