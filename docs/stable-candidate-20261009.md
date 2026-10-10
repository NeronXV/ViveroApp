# Candidata estable Web/Android — 9 de octubre de 2026

Alcance autorizado por Pedro/Toni: ViveroWeb y ViveroApp, dejando AppCliente fuera.
Pruebas nuevas en un entorno local aislado; producción consultada solo en modo
lectura. Supabase, datos históricos, pendientes y respaldos existentes conservados.
La aceptación conjunta y la lectura óptica física siguen pendientes.

## Resultado y correcciones

- Docker local recuperado: sockets residuales de Ingest/Secrets Engine conservados
  en carpetas hermanas `*-stale-20261009-*`, sin borrar volúmenes ni configuración.
  El motor respondió con versión 29.7.2. Persistía además agotamiento de los pools
  automáticos de red: ensayo con subredes explícitas libres, sin prune ni cambios
  de redes ajenas. El problema de sockets puede repetirse al arrancar; recuperación
  puntual verificada, no reparación permanente del software Docker.
- Proyecto nuevo `vivero-stable-20261009`, MariaDB vacía, API loopback 33003,
  redes 10.243.60.0/24 y 10.243.61.0/24, volúmenes propios. El ensayo del día 5
  se conservó. Los límites de login acumulados de ese ensayo causaban 429 en las
  reejecuciones; se usó una BD nueva, sin debilitar límites ni borrar datos reales.
- Compras Web: el contrato de retiro seguro (migración 030, preparado en el bloque
  anterior) distingue COMMITTED/RETIRED. Recupera compras guardadas y conserva el
  cuerpo original antes de liberar un intento sin confirmar. Esta continuación
  repone proveedor, fecha, referencia, términos, archivo y renglones en el formulario
  tras reabrir y resolverlo; una clave cambiada por otra pestaña impide el retiro.
  Si otra pestaña ya liberó el intento, tampoco se interpreta como retiro confirmado.
  Un diario corrupto queda guardado y produce un mensaje seguro, sin enviar datos.
- Prueba HTTP opcional Web añadida fuera del código del navegador, con destino
  cerrado al loopback y fixture sintético nuevo. No instala dependencias.
- Android: candidata identificable 1.0.8-vps/código 9, conservando la firma existente.

## Pruebas realizadas y resultados

| Validación nueva | Resultado / alcance |
|---|---|
| `npm test` backend, Node 24 local | 87 aprobadas |
| `npm run check` backend | Correcto |
| Compose local `run --rm -T --build tests` | 25 SQL/HTTP aprobadas; esquema 56 tablas, migración 030, permisos, compra/recepción atómica, devoluciones, cierres, concurrencia e inventario |
| `npm run test:backup` backend | 8 aprobadas, simuladas; no acredita copia externa |
| Web `npm test`, lint, build | 479 aprobadas, una HTTP opcional omitida sin fixture; lint/build correctos |
| Web HTTP con fixture propio | Recorrido real de servicios cliente contra API/MariaDB aprobado; pérdida de respuestas, claves persistidas, recuperación con servicios reconstruidos, comprobante, compra rechazada/corregida/recibida, devolución y cierre |
| Android `assembleDebug testDebugUnitTest lintRelease` | Correcto; suite general de 361 casos con una HTTP opcional omitida, cero fallos; lint cero errores/32 advertencias |
| Kotlin HTTP con fixture propio | Aprobado, no omitido: login/catálogo/consulta de código, recepción/conteo, cotización, venta, pago, historial/comprobante, recuperación de respuestas perdidas, reintento y logout |
| Conciliación SQL de los dos fixtures | Una venta/pago/salida de stock por fixture, cobro 1000 y cambio 200; Android stock 8, Web 11 tras compra de una unidad y devolución de dos. Una compra recibida, devolución y cierre en el ensayo Web |
| Interfaz Web local, navegador | Login de cuenta ficticia, consulta por código, carrito de dos unidades, cotización, envío a caja, reserva, cobro $10/recibido $12/cambio $2, comprobante, recarga/login/reimpresión, devolución con reposición y corte esperado/contado/diferencia $0; SQL: una venta/pago/devolución/corte y stock final 10 |
| `verify_backend_outbox_sql.py` | Room 3→6, UUID, FK, cierre atómico y recuperación de inventario correctos en SQLite local |
| Verificador APK | Mismo paquete/firma que 1.0.7, código superior; firma criptográfica válida |

La pérdida de respuesta se inyectó **después de una respuesta exitosa del servidor**,
para simular la incertidumbre tras commit. Reiniciar los servicios cliente y releer
sus diarios comprueba recuperación técnica; no equivale a cerrar una app física.
Las pruebas instrumentadas del Samsung (44 aprobadas el día 4) son evidencia
histórica, no una ejecución nueva del día 9. ADB no detectó dispositivo este día.
No se repitió pgTAP: no cambió SQL Supabase ni se inició su entorno local.
La migración MariaDB 030 se validó en arranque/integración local; sigue sin desplegar.
La revisión de interfaz anterior fue Web local con datos ficticios; no es aceptación
conjunta en los dispositivos/impresora de Pedro/Toni ni lectura óptica de cámara.
No se imprimió físicamente el ticket. Se corrigió además un texto antiguo del
panel que atribuía el catálogo a Supabase; sus controles y contratos no cambiaron.

Evidencia privada/ignorada: `tmp/stable-20261009/` (ensayo actual) y
`tmp/stable-20261005/` (logs del bloque anterior y compilación general Android).
No compartir `.private.json`, env ni respaldos: contienen credenciales/datos.

## APK para Pedro y Toni

- Ruta: `tmp/stable-20261009/Vivero-Dulcinea-1.0.8-candidata.apk`.
- Versión **1.0.8-vps**, código **9**, variante **debug**, paquete com.intutec.viveroapp.
- API/Web comprobadas en BuildConfig: `https://viverodulcinea.bajastack.network`.
- Certificado SHA-256: `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6`.
- APK SHA-256: `6f5a2af2b92f53cd0e66789024e8941fb015cb678ce8abaea909fc793b292ea4`.
- Compatible por paquete/firma/código con la 1.0.7 de pruebas instalada el día 4.
  Verificación contra ese archivo; no se instaló 1.0.8 ni comprobó otro dispositivo.
  Actualizar sobre la instalación existente con respaldo; no desinstalar ni borrar datos.
- Esta APK apunta al VPS operativo. Probar acceso/catálogo/cámara sin enviar ventas
  ficticias a producción. Venta completa física requiere un ensayo confirmado.

APK definitiva: disponer/custodiar la clave de firma elegida y key.properties privado,
mantener compatibilidad con instalaciones existentes o planificar transición con
preservación de datos, destino release HTTPS explícito, `verifyReleaseConfiguration`,
verificador de firma/versión y actualización física sobre datos respaldados. La
firma del antiguo APK release difiere de debug; no intercambiarlos a ciegas.
Ver [firma y entrega](production-release.md). No se generó release sin su clave.

## Cuentas y sucursales: consulta actual de solo lectura

| Cuenta (ID interno, sin datos personales) | Rol / sucursal | Situación |
|---|---|---|
| 1 | OWNER / MATRIZ | Hash de contraseña presente; no se hizo login humano nuevo |
| 2 | OWNER / MATRIZ | Sin contraseña |
| 3 | SALES / MATRIZ | Sin contraseña |
| 4 | ADMIN / CENTRO | Sin contraseña |
| 5 | SALES / MATRIZ | Sin contraseña |
| 6 | OWNER / MATRIZ | Sin contraseña |

Las seis están marcadas activas en BD. Los roles/capacidades se comprobaron con
datos sintéticos y pruebas de autorización; no se presume que Toni/Pedro ya hayan
probado todas las cuentas reales. MATRIZ: is_active=1, inventory_enabled=1.
CENTRO: **is_active=1, inventory_enabled=0**. Se conservó esa configuración;
no se activó el descuento de stock ni se cambiaron responsables/roles/contraseñas.
Las cinco cuentas sin contraseña requieren incorporación individual segura.
La conexión SSH de Toni usando la llave disponible aquí fue rechazada; pendiente
que Toni pruebe su propio acceso. La llave administrativa existente permitió SELECT.

No se reprocesaron ventas antiguas o pendientes. La consulta no concilia ventas
del VPS/navegadores; las dos UUID históricas clasificadas como pruebas se conservan.

## Correo y respaldos

SMTP no está implementado: hoy solo hay transporte HTTPS Resend. No se configuró
proveedor ni se envió correo real. [Evaluación SMTP](smtp-evaluation.md) describe
datos necesarios, adaptador faltante, límites, tokens de un uso y las cuentas
preasignadas que no pueden habilitarse solo configurando correo.

Toni recibe [guía de respaldos](toni-backup-handoff.md): scripts/rutas, inclusión y
exclusión, estado real de programación/retención, cifrado externo, restore aislado
y detección/aviso de errores. Ocho COMPLETE locales encontrados; configuración
externa ausente. Cero cron root/timers relacionados en las fuentes revisadas.
No se configuró R2 ni automatización; respaldo externo pendiente de copia real
fuera del VPS y restauración real desde ella.

## Lo que deben probar Pedro y Toni

1. Actualización física desde 1.0.7, datos/carrito/historial conservados; acceso de
   cada cuenta con su rol/sucursal y cambio personal de contraseña.
2. Cámara real: QR/EAN/Code 128, ceros iniciales, permiso denegado/ajustes,
   cancelación, producto correcto y carrito sin agregado automático.
3. En un entorno de ensayo: Android envía y Web cobra (y recorrido inverso si
   corresponde), comprobante, compra/recepción, devolución y cierre. Comparar
   saldos/totales; desconectar internet y cerrar/reabrir Android y navegador.
4. Revisar físicamente CENTRO y responsables antes de activar stock.
5. Con proveedor elegido: entrega/recepción real de invitación/recuperación;
   copia externa y restauración, recepción de un aviso de fallo de respaldo.

## Bloqueantes y mejoras que pueden esperar

Bloqueantes de entrega definitiva: aceptación física conjunta y escaneo; acceso
individual de cinco cuentas; correo/adaptador SMTP y recepción; respaldo externo
restaurado; firma definitiva compatible. La corrección de compras requiere un
despliegue separado y autorizado de migración 030/API/Web: SELECT confirmó que
producción aún no tiene el marcador 030. No se desplegó como parte de estas pruebas.
CENTRO puede quedar pendiente si la primera operación se limita a MATRIZ.

Pueden esperar: AppCliente, refactors, advertencias de lint sin defecto observado,
reportes avanzados, pago en línea y retirada de Supabase. No borrar historial ni
intentos para adelantar el cierre. Una reparación permanente de los sockets Docker
queda como seguimiento del entorno; el ensayo local sí pudo ejecutarse.

## Archivos y Git

Cambios de esta continuación (día 9):

- ViveroApp: `app/build.gradle.kts`, README, `docs/release-readiness.md`, esta guía,
  `docs/smtp-evaluation.md`, `docs/toni-backup-handoff.md`.
- ViveroWeb: `src/features/admin/purchases/purchases-service.ts`,
  `PurchaseDraftWizard.tsx`, `purchases-api.test.ts`, `tests/backend-workflow-http.test.ts`,
  `src/features/internal-home/PanelPage.tsx`, README, `docs/PROJECT_STATUS.md`
  y `docs/tasks/current.md`.
- APK, fixtures y recuperación Docker solo en rutas temporales/ignoradas. El
  verificador `scripts/verify-android-apk.ps1` ya existía sin seguimiento al retomar.

Al retomar, los cambios del bloque anterior ya estaban en commits creados fuera
de esta continuación: ViveroApp main/a58fd614b28920d7b39fee7cef2b67f1c16b0531;
ViveroWeb main/5f9c4853e42cace586cdaeec13998668b3d204aa. Se conservaron.
Preexistentes no tocados: `.idea/misc.xml`, eliminación de `.idea/planningMode.xml`
y carpeta scripts sin seguimiento en ViveroApp; Web estaba limpio.
No se hizo commit, push ni despliegue. Operación remota: consultas de solo lectura.
Revisión final: `git diff --check` correcto en ambos repositorios; los avisos de
conversión LF/CRLF no son errores de whitespace. Al confirmar después de la
interrupción de Codex, el motor Docker estaba apagado y no había servidor temporal
en el puerto 5173. Se conservaron fixtures, volúmenes y APK; no se eliminaron datos.
