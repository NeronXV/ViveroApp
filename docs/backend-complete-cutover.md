# Corte completo y conservación de datos

Actualización operativa del 3 de octubre: Web verificada contra el bundle del
VPS; app habitual actualizada a 1.0.2-vps; inventario de la sucursal principal
activado tras confirmar el conteo físico. Dos ventas locales UUID y cinco
contraseñas siguen pendientes. [Estado y alcance de esta continuación](vps-operational-cutover.md).

Actualización del 3 de octubre: migraciones 027–029 e importación de identidad,
catálogo, ventas/pagos e inventario completadas en VPS. Conciliación, OWNER,
HTTPS y respaldos comprobados. Imagen excluida por instrucción de Pedro.
[Evidencia actual y pendientes](backend-vps-data-cutover.md). Las referencias
inferiores al corte de datos pendiente son históricas; sigue pendiente la
aceptación operativa completa y no debe apagarse Supabase.

Actualización posterior: VPS contratado y despliegue privado realizado; Docker,
Web/API/MariaDB, HTTPS local y respaldo inicial verificados.
[Estado real y comandos](backend-vps-first-deployment.md). Las afirmaciones
de ausencia de servidor/despliegue que siguen son evidencia histórica.

Actualización de conservación: recibida la exportación privada y ensayada
localmente la importación conjunta de identidad, catálogo y ventas/pagos.
Ver [importación histórica](backend-history-import.md) y el
[ensayo conjunto de inventario](backend-inventory-import.md). Imágenes y
corte de datos en VPS siguen pendientes; las referencias anteriores a falta
de exportación son evidencia histórica.

Pedro autorizó coordinar la integración operativa de los tres proyectos sobre
la API oficial y confirmó que deben conservarse los datos actuales mediante
exportación/importación. Esta decisión no autoriza ejecutar comandos remotos,
publicar ni desplegar. No se declara terminada la migración.

## Estado vigente: integración Android/Web y ensayo local VPS (2026-10-02)

Esta sección sustituye el estado del 1 de octubre que se conserva abajo como
historial. **No está completado el corte ni se autoriza apagar Supabase.**

- Newsletter Web usa API/MariaDB (migración 025): confirmación y baja explícitas,
  snapshots cifrados, campañas idempotentes y lotes manuales. El bundle Web ya no
  incluye el SDK Supabase; los helpers históricos y dependencia se conservan.
  Evidencia actual: 468 pruebas Web, build/lint correctos; 68 backend, check correcto
  y 25 HTTP/SQL Docker. Correo simulado y sin datos importados.
  [Contratos y configuración](backend-newsletter.md).
- Recuperación e invitaciones Web usan API/MariaDB: enlaces de un uso, expiración,
  revocación de sesiones y cuentas invitadas sin rol/sucursal. Migración 024,
  configuración Resend solo en servidor. Pruebas locales del bloque anterior: 460 Web,
  build/lint correctos; 66 backend, check correcto y 24 HTTP/SQL Docker. Correo
  simulado; falta validar entrega real. [Contratos](backend-account-links.md).
- Mostrador Web usa cotización, envío y recuperación de la API; conserva cliente
  mediante `sales.customer_id` y una FK real. La migración 022 añade el retiro
  transaccional de claves rechazadas: devuelve la venta existente o impide que
  una solicitud tardía cree una venta después de retirar su clave. La UI no
  descarta un intento por un error de red ni por una recuperación 404.
- Android activa login, permisos, catálogo, consulta manual por código, carrito,
  cotización/envío, cobro e inventario en la API. Room 6 conserva los UUID históricos y guarda
  los nuevos intentos antes de HTTP; consumo del carrito e intento de venta son
  atómicos. Añade historial propio, detalle de comanda, comprobantes y cierre de
  claves de pago mediante migración 026; conserva pagos existentes e impide un
  cobro tardío con una clave retirada. APK debug y de pruebas compilan; 330 pruebas
  unitarias, SQLite y recorrido HTTP con el cliente Kotlin reales pasan. Backend:
  68 pruebas, `check` y 25 SQL/HTTP correctos en este bloque. Sin dispositivo/release.
  [Historial, cierre y comandos](backend-android-history.md).
  Inventario ahora tiene saldos, recepciones, conteos e historial nativos, con
  recuperación durable y consultas de resultado sin movimientos nuevos.
  Validación de este bloque: 343 unitarias correctas, HTTP Kotlin/MariaDB, SQLite
  3→4→5→6, 68 backend/check y SQL/HTTP de inventario correctos.
  [Alcance y comandos nuevos](backend-android-inventory.md). Los 25 SQL/HTTP
  anteriores son evidencia del bloque de historial. Reportes, administración,
  activación de stock y mínimos siguen mediante Web; sin paridad nativa completa. [Alcance y comandos](backend-android-workflow.md).
- Administración Web de productos, categorías, imágenes y promociones usa API.
  El stock mínimo se consulta y modifica en `inventory.minimum_stock` de la
  sucursal activa, sin alterar cantidades; migración 023 de privilegios mínimos.
  Fechas administrativas proceden de MariaDB. Los porcentajes se convierten a
  puntos base enteros antes de enviarlos.
- Compras/proveedores Web usa API, con proveedor, fechas, presentaciones y conteos
  reales. Borradores y confirmaciones guardan cuerpo/clave antes de POST, comprueban
  la escritura local y usan Web Locks. Un borrador incierto se recupera desde el
  asistente mediante su cuerpo original. Un rechazo permanente no se descarta:
  su resolución administrativa/fence sigue pendiente. No reenviar con otra clave.
- Docker Desktop vuelve a arrancar. Se apartaron carpetas que contenían solamente
  sockets dañados (`Docker/run` y `docker-secrets-engine`) después de detenerlo;
  quedaron copias `*-stale-20261002*`. No se eliminaron bases ni volúmenes.
- El perfil VPS es un overlay del mismo Compose, con Web compilada, Caddy/HTTPS,
  API sin puerto publicado y MariaDB privada. El seed de ese perfil crea solo
  seis roles; no crea personas, sucursales, catálogo ni existencias demo.
  Guía y límites: [backend-vps-preparation.md](backend-vps-preparation.md).

Verificación anterior del 2 de octubre: Web build/lint y 456 pruebas; backend `check` y 64
pruebas; 23 pruebas SQL/HTTP en Docker después de las migraciones 022/023, con
asociación de cliente, carrera retiro/envío y stock mínimo real. Instalación VPS
local nueva: 40 tablas, 22 migraciones, seis roles y cero filas operativas/demo.
HTTPS local verificó certificado con su CA, `/login`, `/health` y catálogo vacío.
La CA no se instaló en Windows; no se deshabilitó la validación TLS en la prueba
exitosa. No equivale a validar certificados públicos ni un VPS real.

La construcción detectó avisos de dependencias de desarrollo; se actualizaron
solo `brace-expansion` y `js-yaml` en el lockfile Web (tres entradas).
`npm audit` y `npm audit --omit=dev` quedaron sin vulnerabilidades reportadas.
La primera repetición de build/tests/lint posterior al cambio del lockfile quedó
bloqueada por el límite de revisión de permisos. Tras la continuación autorizada
se ejecutó correctamente: compilación, 456 pruebas y lint Web.

Continuación del 2 de octubre: Pedro confirmó que aún no tiene VPS ni dominio y
considera Hostinger. Se documentó Ubuntu 24.04 LTS con Docker como recomendación.
Se añadieron `infra/docker/backup.mjs` y `restore.mjs`, con pausa/reanudación de
escritores, hashes, protección de copias incompletas y rechazo de destinos con
datos. Pasaron ocho pruebas locales y un ensayo Docker real de respaldo y
restauración en un segundo proyecto nuevo. Se conciliaron importes, existencias,
imagen, FK y trigger de inventario; funcionaron la API restaurada y el HTTPS del
origen reanudado. Ver detalles en [la guía VPS](backend-vps-preparation.md).
No se respaldaron ni importaron datos reales. El ensayo con el futuro snapshot
Supabase, su conciliación y copias cifradas fuera del host siguen pendientes.

Pendientes concretos para abandonar Supabase:

1. Web: newsletter, recuperación e invitaciones ya usan API/MariaDB. Falta validar
   correo real y transición de consentimientos/enlaces/envíos históricos. Falta
   retirar de forma segura los intentos de compras rechazados. El SDK ya no aparece
   en el bundle generado; se conserva la dependencia y helpers históricos.
2. Android operativo: activar y completar repositorios/pantallas API, permisos,
   caja, inventario y administración; conservar/resolver outbox UUID anterior.
   AppCliente sigue demo y necesita conectar catálogo/carrito/pedidos reales.
3. Datos: obtener exportación privada y binarios; completar el importador de
   identidades e historial y conciliar dinero, inventario y referencias. La
   revisión de exportación existente no importa datos. No se recibió la copia.
4. Operación: respaldo/restauración con datos reales y copia cifrada externa,
   acceso inicial/importación en perfil
   sin demo, ensayos completos de UI y dispositivo, dominio/servidor/correo reales.
   VPS y dominio todavía no están contratados; no se ejecutó despliegue remoto.

No hubo cambios Android/Cliente, pruebas de dispositivo ni importación real en
esta sesión. Se conservaron los cambios preexistentes. Sin commit, push o despliegue.

## Estado histórico del código local (2026-10-01)


ViveroWeb utiliza la API oficial para login/sesión, catálogo público, pedidos
públicos, atención administrativa de pedidos, cobro de Caja, clientes,
cortes/devoluciones, activación de inventario, sucursales, personal, roles,
informes y movimientos de inventario. La sesión permanece en memoria y no
restaura credenciales de Supabase. Los módulos migrados no cambian de proveedor
ante errores de la API. El backend devuelve metadatos reales de Caja y pedidos
(sucursal, creador, revisión, reclamación, venta vinculada y reloj del servidor).

Los pedidos administrativos envían la revisión mostrada; un conflicto o
resultado incierto bloquea otra acción hasta actualizar. Cortes y devoluciones
persisten el cuerpo y clave originales antes de enviar, bloquean otra operación
pendiente y permiten recuperarla sin sustituir sus datos. Los intentos y
carritos antiguos permanecen bajo sus claves originales; sus UUID no se envían
como IDs de MariaDB. Una operación rechazada permanentemente todavía puede
requerir revisión para retirar su clave de forma segura; no se borra por un
simple error ni por un resultado ausente.

**La migración completa sigue pendiente y este estado no está listo para uso
operativo ni despliegue.** Mostrador, edición administrativa de catálogo e
imágenes/promociones, compras, newsletter, invitaciones y recuperación de
contraseña todavía conservan consumidores Supabase. El nuevo login no concede
una sesión Supabase a esos módulos. Deben migrarse antes del corte operativo;
no se debe confundir una compilación correcta con su funcionamiento completo.
El servicio temporal de newsletter está limitado a newsletter; las operaciones
financieras ya no comparten ese transporte RPC.

Android conserva el login y los consumidores operativos anteriores, junto con
sus consumidores API y outbox preparados. ViveroAppCliente continúa con sus
repositorios demo. No se cambiaron ni validaron ambas apps Android en esta
sesión. El usuario confirmó que aún no tiene exportación; la importación real
no se ejecutó y el importador completo de historial sigue pendiente.

## Docker local de esta sesión

Docker agotó las subredes automáticas debido a redes de validaciones anteriores.
No se eliminaron redes ni volúmenes existentes. Se verificaron las redes Docker
y las rutas locales, y se creó un override IPAM privado para el entorno aislado
`vivero-cutover-20261001` (API `127.0.0.1:33003`, subredes `10.241.10.0/24`
y `10.241.11.0/24`). La configuración sintética está ignorada en `tmp/`.
El Compose oficial continúa en `infra/docker/compose.yaml`.

```powershell
docker compose --env-file tmp/cutover-validation-20261001.env -f infra/docker/compose.yaml -f tmp/cutover-networks.yaml -p vivero-cutover-20261001 up -d --build db api
docker compose --env-file tmp/cutover-validation-20261001.env -f infra/docker/compose.yaml -f tmp/cutover-networks.yaml -p vivero-cutover-20261001 --profile test run --rm -T --build tests
```

Estos archivos privados son de esta máquina y no se distribuyen al clonar.
Tony debe seguir la instalación oficial de `.env.example` y
[backend-fresh-install.md](backend-fresh-install.md). Si requiere un override
IPAM, debe elegir subredes libres en su equipo; no copiar contraseñas de aquí.

## Exportación preparada, sin ejecutar

Actualización: el origen inspeccionado tiene 30 tablas públicas. Para ese esquema
usar la [guía de exportación adaptada](backend-source-export.md), que registra
ocho tablas ausentes explícitamente. El procedimiento siguiente corresponde al
esquema completo.


`database/migration/export-supabase.sql` prepara una consulta bajo transacción
de solo lectura con snapshot consistente. Incluye las 39 tablas de origen
identificadas en las migraciones y sus claves primarias/foráneas. De `auth.users`
exporta únicamente ID y correo. Excluye contraseñas, sesiones, tokens de
reclamación de Caja y tokens de newsletter. Los campos `*_cents` se exportan
como strings enteros para preservar bigint sin redondeo JavaScript.

Antes de ejecutarla se debe confirmar el proyecto de origen y autorizar
expresamente la lectura remota. No se ejecutó la consulta ni se obtuvieron
datos personales en esta entrega. El resultado debe guardarse de forma privada,
fuera de Git; puede utilizarse `tmp/source-export.json`, carpeta ya ignorada.
No compartir el resultado, correos, contactos ni contraseñas en logs.

Para revisar un archivo guardado localmente, desde este repositorio:

```powershell
node backend/scripts/preflight-source-export.js --file tmp/source-export.json
```

La revisión no se conecta a bases de datos ni escribe en MariaDB. Comprueba
tablas completas, cobertura de claves primarias, duplicados, referencias
exportadas incluidas las compuestas, centavos exactos y ausencia de credenciales.
El reporte contiene SHA-256, conteos y códigos de error; nunca filas o nombres.
Una revisión correcta **no equivale a una importación ni demuestra compatibilidad
con MariaDB**. Las restricciones comprobadas proceden del snapshot exportado.

## Condiciones para completar el corte

1. Completar los consumidores visibles sobre los contratos oficiales, incluyendo
   login, catálogo, ventas, cobro, recibos, pedidos, inventario y administración.
2. Completar el importador de identidad, sucursales e historial con IDs enteros
   nuevos y correspondencias persistentes con el origen. Catálogo/promociones/
   imágenes ya tienen importadores específicos; no reinterpretar UUID como IDs.
3. Conservar folios, importes y referencias históricas; conciliar conteos,
   totales, pagos, devoluciones, cierres y existencias por sucursal. No ejecutar
   de nuevo operaciones históricas como si fueran ventas nuevas.
4. Resolver intents pendientes en Supabase antes del snapshot final. Las sesiones
   y reclamaciones no se importan. Las cuentas requieren contraseñas nuevas con
   el mecanismo oficial; no copiar hashes Supabase al esquema scrypt.
5. Copiar y verificar archivos de imágenes privados. Los metadatos exportados
   no contienen los binarios. Conservar consentimiento de newsletter sin
   reactivar bajas ni reenviar campañas históricas.
6. Verificar instalación Docker limpia y recorridos completos Web/Android;
   retirar dependencias Supabase solo al no quedar consumidores activos.

No se borra el historial SQL ni se apaga Supabase mientras existan datos,
operaciones pendientes o consumidores que dependan de él. El despliegue VPS
queda pendiente del servidor y de autorización expresa.

## Verificaciones nuevas

- Web: `npm run build`, `npm run test -- --run` (451 pruebas) y `npm run lint`
  correctos. Las pruebas anteriores de presentación ahora simulan su adaptador;
  pruebas adicionales verifican HTTP administrativo e intentos de pedidos.
- Backend: `npm run check` y `npm test` correctos (64 pruebas, incluidas cuatro
  nuevas de revisión de exportaciones). También se ejecutaron esas cuatro con
  `node --test --test-isolation=none test/source-export-preflight.test.js`.
- La consulta PostgreSQL preparada no se ejecutó; su sintaxis y comportamiento
  en una base real siguen pendientes de validación local.
- Docker: 23 pruebas SQL/HTTP correctas, incluyendo proyecciones operativas de
  Caja y pedidos, autorización por sucursal y liberación real de reclamación.
- Sin exportación/importación real, prueba visual de extremo a extremo Web,
  compilación Android nueva, pruebas de dispositivo o despliegue.

## Archivos de esta entrega

En ViveroApp:

- `backend/src/administration.js`: devuelve la fecha real de creación de sucursales.
- `backend/package.json`: incorpora las comprobaciones nuevas.
- `backend/scripts/source-export-tables.js`, `source-export-preflight.js` y
  `preflight-source-export.js`: inventario y revisión local sin escritura.
- `backend/test/source-export-preflight.test.js`: cuatro pruebas sintéticas.
- `database/migration/export-supabase.sql`: exportación de solo lectura preparada.
- `docs/backend-complete-cutover.md` y `docs/supabase-migration-map.md`: estado real.

En ViveroWeb:

- `src/features/public-catalog/catalog-service.ts`, `catalog-types.ts` y
  `catalog-image.ts`: lectura API, IDs enteros e imágenes del backend.
- `src/features/public-catalog/CartDrawer.tsx` y
  `src/features/public-orders/PublicCartProvider.tsx`: cotización/confirmación,
  protección de respuestas tardías y carrito separado del origen anterior.
- `src/features/public-orders/backend-order-coordinator.ts` y su prueba:
  persistencia previa, bloqueo entre pestañas y recuperación con clave original.
- `src/features/auth/backend-runtime.ts` y `useBackendSession.ts`: puente de lectura
  al controlador existente y comprobación de identidad, sin almacenar otro token.
- `src/lib/backend-http.ts`: transporte administrativo autenticado y validadores.
- `src/features/admin/backend-admin-request.ts` y su prueba: contratos HTTP.
- `src/features/admin/admin-service.ts`, `admin-parser.ts` y
  `admin-authority.test.ts`: adaptación a API como única autoridad administrativa.
- `src/features/admin/admin-boundary-service.test.ts`, `admin-staff-role.test.ts`
  y `admin-user-active.test.ts`: contratos de presentación sobre el adaptador.

Los cambios Android, de IDE, configuración, API preparada y documentación que
ya existían antes se conservaron; no son nuevas validaciones de esta entrega.
ViveroAppCliente no se modificó. Los repositorios siguen en `main` con cambios
locales; no se realizó commit, push, publicación o despliegue.

## Archivos adicionales modificados en esta sesión

- Backend: `src/app.js`, `src/cashier.js`, `src/web-order-admin.js`,
  `test/cashier-integration.test.js` y `test/web-order-checkout-integration.test.js`.
- Web sesión: `features/auth/AuthProvider.tsx`, `backend-session.ts`,
  `useBackendSession.ts`; administración: `admin-service.ts`,
  `admin-authority.test.ts`, `admin-customers-service.ts`,
  `admin-customers-service.test.ts`, `admin-customers-parser.ts`,
  `admin-boundary-service.test.ts`.
- Web Caja: `cashier-service.ts`, `backend-cashier-service.ts`,
  `cashier-parser.ts`, `cashier-payment-state.ts`, `cashier-receipts.ts`,
  `cashier-operations-service.ts`, `cashier-operations-api.test.ts`,
  `CashierOperations.tsx`.
- Web pedidos: `web-order-service.ts`, `web-order-service.test.ts`,
  `web-order-types.ts`, `admin/AdminOrders.tsx`.
- Web administración: `InventoryActivation.tsx`, `AdminCustomers.tsx`,
  `AdminDirectories.tsx`, `AdminPage.tsx`; newsletter: `AdminNewsletter.tsx`
  y `newsletter-legacy-service.ts` conservan su dependencia pendiente.

Los cambios previos de Android, IDE, API y catálogo/carrito/exportación se
preservaron. Los archivos nuevos siguen sin seguimiento y los cambios locales
no están confirmados. No hubo commit, push, despliegue ni cambio remoto.
