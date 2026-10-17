# Catalogación móvil permanente

## Entrega 1 — contratos locales para Android (14 de octubre de 2026)

Esta sección describe las correcciones locales; no acredita un despliegue. El
resto del documento conserva la entrega histórica 034. El destino acordado es
ViveroApp como única aplicación Android del personal y ViveroWeb para
administración y Caja. No se implementaron pantallas Android en esta entrega.

Se reutilizan `cataloging_drafts`, `cataloging_events`, la transacción de sesión
y el esquema MariaDB actual, incluida la migración **035 ya presente en el trabajo
local**. No se añadió ni modificó una migración en esta entrega. `/health` exige
034 y 035; no basta con encontrar una de las dos.

### Operaciones para los clientes

- Todas las rutas requieren sesión, `MANAGE_PRODUCTS`, sucursal activa y los
  encabezados `X-Expected-Actor-Id` / `X-Expected-Branch-Id`.
- Crear: `POST /api/v1/cataloging`, `{original_name, fields}`.
- Guardar: `PATCH /api/v1/cataloging/{id}`, `{revision, action:"SAVE", fields}`.
  Los campos omitidos se conservan; para vaciar se envía `""` o `null` según su
  tipo. `suggested_price_cents` e `initial_quantity` son propuestas guardadas,
  no modifican precio ni inventario del catálogo.
- Foto: `PUT /api/v1/cataloging/{id}/photo`, bytes JPEG/PNG/WEBP,
  `Content-Type` y `X-Cataloging-Revision`. Mantiene límites y normalización
  existentes. La foto confirmada se transfiere al producto al publicarlo.
- Vincular: mismo PATCH, `action:"LINK"`, `product_id` y `fields`. Requiere
  `PUBLISH_CATALOGING` o la autorización histórica del propietario. Funciona
  desde DRAFT y REVIEW; no modifica el producto existente ni sus existencias.
- Publicar: `POST /api/v1/cataloging/{id}/publish`,
  `{revision, fields?, price_cents?, initial_quantity?}`; requiere además
  `PUBLISH_CATALOGING`. Se pueden completar directamente DRAFT o REVIEW sin
  una nueva aprobación. La capacidad existente corresponde a MANAGER, ADMIN y
  OWNER en 035; se verifica en servidor y no se deduce del nombre en el cliente.

SAVE y foto sobre REVIEW requieren publicación. SUBMIT solo sale de DRAFT: no
permite eludir esa restricción reenviando una ficha que ya está en REVIEW.
RETURN/PREPARE conservan el contrato histórico del propietario. LINKED y PREPARED
son terminales para nuevas escrituras sobre el borrador; un reintento confirmado
puede recuperar su recibo aunque la ficha haya avanzado después.

Publicar asigna un SKU si está vacío y comprueba también las colisiones del
código de barras. Producto, foto vinculada, precio, movimiento, conteo inicial,
preparación por sucursal, estado y recibo se confirman en una transacción. Con
precio positivo y existencia positiva queda listo; si falta alguno, queda
registrado e inactivo. Cero existencias no inventa un movimiento ni confirma un
conteo inicial en este contrato. Las propuestas guardadas no sustituyen la
confirmación explícita de precio/existencia en el cuerpo de publicación.

### Reintentos y recuperación

Cada escritura lleva `Idempotency-Key` persistida **antes** de enviar. Repetir
exactamente ruta, cuerpo/bytes, revisión, actor y sucursal recupera el resultado.
No generar otra clave ante una respuesta incierta. La misma clave con otro
contenido da conflicto. Cambiar la clave tampoco permite publicar dos veces el
mismo borrador. Los permisos actuales se siguen exigiendo al recuperar.

El recibo de publicación añade `publication` con `branch_id`, `price_cents`,
`initial_quantity` y `ready`. Es el resultado de esa operación, no una consulta
del stock actual. Los recibos anteriores siguen aceptados sin ese campo; los
clientes deben consultar la ficha del producto para conocer su disponibilidad.
Para recuperar existencias históricas se comprueba la sucursal del conteo exacto
de esa publicación. Un GET de borrador puede devolver una revisión posterior al
recibo: no sobrescribirla silenciosamente con el formulario anterior.

Web conserva el intento también para fotos y ante errores de autenticación,
permisos, timeout, saturación o servidor. Ofrece «Comprobar guardado» en la lista
y el asistente. «Guardar y salir» guarda datos comerciales y después foto con la
revisión devuelta; no sale si una operación falla. El diario utiliza el
almacenamiento existente del navegador: si no cabe la foto, se avisa y **no se
envía**; debe elegirse una más pequeña. No se cambió el flujo de foto de productos
ya existentes ni se añadió sincronización offline general.

### Validación local reproducible

`backend/test/cataloging-integration.test.js` comprueba las operaciones reales
con cuentas y plantas sintéticas, concurrencia, respuesta perdida, fallo forzado
después de las escrituras, reintento, permisos, sucursales y compatibilidad del
flujo anterior. El mensaje `Request failed` del fallo forzado es esperado.

Desde la raíz, con binarios portables MariaDB 11.4 ya disponibles:

```powershell
./backend/scripts/test-cataloging-local.ps1 -MariaDbBin C:/ruta/mariadb/bin
```

El script crea una carpeta nueva en `tmp/plants-contract-*`, rechaza un puerto
43389 ocupado, escucha solo en 127.0.0.1 y apaga su proceso al terminar. La prueba
rechaza una base `vivero` preexistente. No lee `.env`, no conecta al VPS y no
reutiliza datos reales. Conserva artefactos sintéticos ignorados por Git.

Antes de implementar Android: aprobar el contrato anterior, conservar las claves
y bytes de intentos en almacenamiento durable nativo y validar el flujo con la
gerente. La aceptación visual/manual y las pruebas en dispositivo no forman
parte de la evidencia automatizada de esta entrega. Un despliegue de 035/API/Web
requiere una autorización independiente; no se realizó aquí.

### Resultado verificado de esta entrega

- Backend: `npm test`, 116 pruebas aprobadas; pruebas dirigidas
  `node --test test/cataloging.test.js test/inventory-preparation.test.js`, 12
  aprobadas; comprobación sintáctica `npm run check:cataloging` y de `src/app.js`.
- Integración real: script local anterior, 19 pruebas aprobadas con MariaDB
  11.4.13. Cada ejecución creó datos sintéticos nuevos y apagó su proceso.
- Web: Vitest general con `--exclude 'artifacts/**' --experimental.fsModuleCache
  --maxWorkers 1`, 652 aprobadas y una omitida por no proporcionar el fixture
  opcional de flujo HTTP completo. Tras los últimos ajustes, Vitest dirigido a
  `src/features/admin/plants`, 52 aprobadas. Los conteos se solapan; no sumarlos.
- Web: `npm run build` y ESLint de los seis archivos de Plantas tocados, correctos.
- `git diff --check` correcto en ambos repositorios. No se compila Android porque
  no se modificó; no se ejecutaron pruebas de cámara/dispositivo ni aceptación
  manual del asistente. No se modificó SQL, por lo que no se ejecutó pgTAP ni la
  verificación histórica de migraciones Supabase.

Archivos editados por esta entrega, sobre los cambios locales preexistentes:

| Repositorio | Archivos |
|---|---|
| ViveroApp | `backend/src/cataloging.js`, `backend/src/app.js`, `backend/test/cataloging-integration.test.js`, `backend/scripts/test-cataloging-local.ps1` (nuevo), `docs/mobile-cataloging.md` |
| ViveroWeb | `src/features/admin/plants/cataloging-service.ts`, `cataloging-service.test.ts`, `CatalogingWizard.tsx`, `AdminPlants.tsx`, `cataloging-wizard-model.ts`, `cataloging-wizard-model.test.ts` (los seis en la misma carpeta) |

Los asistentes/modelos Web y las migraciones 035/036 ya estaban sin versionar al
iniciar. Se conservaron también los cambios de Recepción, permisos de inventario,
menús, estilos, configuración del IDE y documentación ajenos a esta entrega.
Los repositorios continúan con cambios locales sin commit. No hubo commit, push,
despliegue ni conexión a producción.

## Historial de la entrega 034

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
