# Vivero

## Estado actual

Revisión del 9 de octubre de 2026 sobre `main`, HEAD
`a58fd614b28920d7b39fee7cef2b67f1c16b0531`, con cambios locales. El destino
operativo oficial es Backend API + MariaDB + Docker. El código Android local
prepara **1.0.8-vps, código 9**, candidata debug compatible con la 1.0.7,
con cámara QR/EAN/Code 128 desde catálogo,
consulta por la API y confirmación manual para agregar al carrito. Conserva
Room 6 y los contratos de venta, caja e inventario. El 4 de octubre se actualizó
el Samsung A22 conectado de 1.0.6 a **1.0.7-vps** mediante APK debug compatible:
respaldo privado previo, siete archivos persistentes idénticos después de instalar,
15 ventas históricas y Room 6 íntegros. No es todavía una entrega release firmada.

La consulta de solo lectura del 9 de octubre confirma seis cuentas activas,
cinco sin contraseña, MATRIZ con descuento de stock activo y CENTRO con
is_active=1/inventory_enabled=0. Se conservó esa configuración. La Web/API usan
`https://viverodulcinea.bajastack.network`; el ensayo nuevo usa MariaDB/API locales
aisladas. No se desplegó: producción aún no tiene la migración 030 de compras.

Pruebas nuevas: backend 87 unitarias/25 SQL-HTTP; Web 479 y recorrido HTTP real;
Android build/unitarias/lint y recorrido Kotlin HTTP real con respuestas perdidas.
La conciliación confirma una venta/pago/salida de stock por ensayo.
Evidencia, APK y límites: [candidata estable](docs/stable-candidate-20261009.md).

Pedro confirmó en este chat que las dos ventas UUID antiguas fallidas eran
**pruebas** y que la distribución será por **APK directo**. Sus registros se
conservan; no se reenvían ni se eliminan. Esto no resuelve automáticamente otros
intentos de venta/pago ni acredita recepción de dinero.

Pendientes de salida: aceptación completa en tablet/Web y de cámara física,
revisión de intentos actuales, acceso de cinco cuentas según la última evidencia,
correo real, validación operativa de CENTRO, respaldo cifrado externo con
restauración y firma compatible. No existe `key.properties` local. El APK release
antiguo y el debug actual tienen certificados distintos: no intercambiarlos para
actualizar una instalación sin comprobar su certificado.

Guía vigente de cierre: [candidata y aceptación pendiente](docs/stable-candidate-20261009.md).
Toni: [evaluación SMTP](docs/smtp-evaluation.md) y [guía de respaldos](docs/toni-backup-handoff.md).
La guía [pendientes del bloque anterior](docs/release-readiness.md) conserva evidencia histórica.
Preparación de APK: [firma y distribución](docs/production-release.md).
Supabase se conserva para revisión histórica. AppCliente requiere un alcance aparte;
Web se validó localmente y AppCliente quedó fuera de esta revisión. La exportación de
[seguimiento n8n](docs/automation/project-status.json) conserva su fecha anterior;
su contenido se actualizará al cerrar la sesión con la evidencia nueva y nunca
se interpreta como certificación del código actual.

## Registro histórico de migración y seguimiento

Las secciones siguientes conservan el trabajo documental previo. Sus versiones,
destinos, pruebas y pendientes describen etapas anteriores y se subordinan al
estado actual superior y a las guías fechadas de entrega.

Estado local de migración, 2026-10-02: Web incorpora mostrador, administración
completa de catálogo, compras/proveedores, recuperación, invitaciones y newsletter a los
consumidores API existentes. Boletín: [backend-newsletter.md](docs/backend-newsletter.md).
Contratos de correo de acceso: [backend-account-links.md](docs/backend-account-links.md).
Android activa login, catálogo, consulta manual de códigos, carrito y cobro mediante
la API oficial. Room 6 conserva los datos UUID anteriores; no los reenvía a MariaDB.
Android añade historial propio, comprobantes, detalle y cierre seguro de pagos
(migración 026). APK debug y recorrido HTTP Kotlin/MariaDB correctos. [Bloque de historial y cierre](docs/backend-android-history.md).
Android añade inventario nativo con recepción, conteo, historial y recuperación
durable de intentos. Validación nueva: 343 pruebas unitarias correctas y un HTTP
opcional omitido, ejecutado aparte con MariaDB.
[Inventario, archivos y comandos](docs/backend-android-inventory.md).
[Archivos, configuración, validaciones y límites](docs/backend-android-workflow.md).
VPS: Web/API/MariaDB publicadas en https://bajastack.network con certificado
público y cuenta inicial OWNER verificados. El 3 de octubre se aplicaron las
migraciones 027–029 e importaron identidad, catálogo, ventas/pagos e inventario:
15 productos, 14 ventas/pagos, seis cuentas y dos sucursales. OWNER conservado,
HTTPS y respaldos previo/posterior verificados. Imagen excluida por decisión de
Pedro; cinco cuentas requieren restablecimiento y stock operativo sigue desactivado.
[Importación, evidencia y pendientes](docs/backend-vps-data-cutover.md).
Docker local y el ensayo HTTPS del perfil VPS funcionan. **La migración completa
sigue pendiente**: entrega real de correo, integración completa de consumidores
Android/Cliente y validación operativa final.
Los datos importados están conciliados y la restauración del respaldo real ya
pasó en un proyecto aislado sin puertos públicos. Se estableció la administración
en `/srv/apps/vivero-dulcinea` y la red de proxy compartido, preservando datos y
servicios existentes. Falta la copia cifrada fuera del VPS.
[Guía para Pedro y Toni](docs/vps-multiproject-operations.md).
Consultar [estado y evidencia](docs/backend-complete-cutover.md) y
[preparación VPS](docs/backend-vps-preparation.md). Las verificaciones históricas
conservan su fecha y alcance originales.

## Arquitectura objetivo oficial: Backend API + MariaDB + Docker

La migración por módulos parte de `backend/`, `database/mysql/` e
`infra/docker/` en este repositorio y en la rama actual. Para levantar el entorno
local y trabajar en él, comenzar por [la guía de fase 1](docs/backend-api-mariadb.md).
El [mapa de dependencias](docs/supabase-migration-map.md) identifica lo que sigue
en Supabase. La API ofrece catálogo local e
[identidad y permisos propios](docs/backend-identity.md); Web y el recorrido Android
descrito arriba usan esos contratos. Los datos ya se importaron al VPS; el corte
operativo completo sigue pendiente de aceptación.
El arranque local, bootstrap, sesiones y actualización desde fase 1 ya tienen
[validación real con MariaDB](docs/backend-local-validation.md). La evidencia de fase
1 es histórica; el alcance actual de consumidores está en el resumen superior.
Las secciones siguientes conservan la evidencia de la aplicación existente;
no implican que esos módulos ya estén migrados a MariaDB.

Aplicación Android para apoyar la operación de un vivero. El proyecto se desarrolla en fases pequeñas y verificables.

## Estado documental anterior para INTUTEC

Esta sección conserva la fuente anterior del estado para INTUTEC. Su exportación para n8n es
[project-status.json](docs/automation/project-status.json); debe actualizarse a partir
de este estado, no como un seguimiento independiente. Revisión local sobre `main`,
HEAD `a4621f6d42deb735714c047b05789ee83760939a`. El HEAD identifica la revisión,
no acredita terminación, pruebas ni despliegue. La fecha exacta de esta revisión
figura en `updated_at` de la exportación, con zona horaria.

**Resumen interno:** Android tiene implementados los flujos de catálogo, escaneo,
carrito, comandas, caja e inventario y contratos backend compartidos. Hay pruebas
locales documentadas y reportes conservados; falta la validación integral en
dispositivos y del entorno de entrega. No hay evidencia suficiente para afirmar
que esta versión esté desplegada.

### Implementado y evidencia

La tabla siguiente describe la implementación histórica y sus contratos Supabase.
Las pantallas nativas activas y los límites de la integración API actual están en
[backend-android-workflow.md](docs/backend-android-workflow.md); presencia de fuente
histórica no significa que esa pantalla esté disponible en el grafo actual.

| ID | Avance implementado | Evidencia local | Verificación |
|---|---|---|---|
| android-base | Base MVVM, navegación, autenticación y roles | `navigation/`, `core/` y `feature/auth/` bajo `app/src/main/java/com/intutec/viveroapp/`; `docs/architecture.md` | Código presente; pruebas unitarias agregadas indicadas abajo; Auth remoto y permisos en piloto pendientes |
| catalog-scan | Catálogo, administración de productos, imágenes, precios y consulta por código; escáner CameraX/ML Kit con entrada manual | `feature/catalog/`, `feature/scanner/`; RPC `get_product_by_scan_code` en la fuente remota de catálogo | Código presente; cámara física y recorrido remoto pendientes |
| sales-cashier | Carrito persistente, outbox idempotente, historial propio y caja con confirmación y recuperación | `feature/cart/`, `feature/mysales/`, `feature/cashier/`; RPC `submit_sale_to_cashier` y `confirm_sale_payment` | Pruebas locales de base y concurrencia registradas; flujo completo Android/Web pendiente |
| inventory-reports | Recepción, conteo e historial de inventario; consulta de clientes, reportes y gestión de personal | `feature/inventory/`, `feature/customer/`, `feature/reports/`, `feature/staff/` | Código presente y pruebas locales agregadas; conciliación operativa y permisos en dispositivo pendientes |
| backend-contracts | 35 migraciones: permisos/RLS, ventas, inventario, promociones, compras a proveedores, pedidos web, cortes, devoluciones y boletín | `supabase/migrations/`, `supabase/tests/database/`, `docs/database-validation.md` | 18 suites/499 aserciones locales correctas; no certifica HTTP, correo ni despliegue remoto |
| release-preparation | Versión configurada 1.0.1, código 2; validación de configuración/firma y preparación de entrega | `app/build.gradle.kts`, `docs/production-release.md` | Compilación/lint documentados; no acredita un nuevo APK release ni su distribución |

Las rutas `feature/` de la tabla parten de
`app/src/main/java/com/intutec/viveroapp/`. La presencia de código no equivale a
aceptación funcional. La Web se conoce aquí por contratos y documentación local;
no se revisó su repositorio ni se verificó su publicación.

### Probado: alcance de la evidencia

- `docs/production-release.md` registra el 21 de septiembre de 2026:
  `assembleDebug testDebugUnitTest lintRelease` correcto, 226 pruebas sin fallos,
  cero errores y 30 advertencias de lint. Los 43 XML conservados en
  `app/build/test-results/testDebugUnitTest/` suman 226 pruebas, cero fallos,
  errores u omisiones. Son resultados previos, no una ejecución nueva ni una
  certificación completa del HEAD actual.
- Ese informe registra `assembleDebugAndroidTest` correcto: compila las pruebas,
  no demuestra su ejecución en dispositivo. Registra también 134 comprobaciones
  estáticas correctas de migraciones.
- `docs/database-validation.md` y los archivos locales de
  `tmp/database-validation-20260921-130841-c9c812a9/` registran 35 migraciones desde
  cero, 18 suites/499 aserciones y cinco escenarios concurrentes correctos.
  Se revisaron `result.json`, `pgtap.log` y `concurrency.log`. Los hashes actuales
  de las 35 migraciones, 18 suites y fixture SQL concurrente coinciden con el
  manifiesto. Los dos scripts PowerShell ejecutores difieren; el informe documenta
  la incorporación posterior del requisito PowerShell 7. No se reejecutaron.
- Los reportes locales en `app/build/` y `tmp/` no son evidencia portable garantizada
  en un checkout nuevo; los informes versionados conservan el resumen y límites.

### Desplegado

No confirmado para esta entrega. Un sitio mencionado en documentación y un APK
preexistente con firma validada no demuestran qué versión está instalada, publicada
o aplicada en Supabase. No se consultaron servicios remotos. La nueva migración de
privilegios está documentada como pendiente de despliegue autorizado.

### Pendientes y bloqueos

1. Ejecutar pruebas instrumentadas de Room, caja, inventario y cámara; completar
   el piloto con datos sintéticos, roles/sucursales, pérdida de red y reinicio.
2. Confirmar destino y firma de release; generar/verificar el artefacto final y
   probar instalación y actualización sin pérdida de datos en tablets. El último
   intento documentado de `verifyReleaseConfiguration` se bloqueó por falta de
   `RELEASE_SUPABASE_URL`; la configuración actual no fue inspeccionada.
3. Verificar con autorización el entorno remoto, migraciones/RLS, Auth/SMTP,
   invitaciones y recuperación. Probar HTTP y correo real; incluir el boletín
   solo si se confirma su alcance y configuración.
4. Verificar usuarios, roles, sucursales, precios y conteo inicial antes de activar
   inventario; revisar pagos históricos sin corte y conciliar reportes.
5. Documentar respaldo/restauración del backend, responsable de incidencias,
   canal de distribución y recuperación; obtener autorización del destino antes
   de desplegar o distribuir.

No hay bloqueos actuales confirmados en esta revisión documental. La falta de
destino/firma del último intento y los problemas de CLI/Docker son limitaciones
históricas, conservadas en `historical_limitations`, no en `blockers`. La validación
integral sigue pendiente; falta evidencia actual para clasificarla como bloqueo.

Las pruebas técnicas, verificación de firma/destino, validación remota autorizada,
conciliación, documentación de respaldo y revisión del borrador corresponden al
seguimiento interno (`internal_actions_needed`). `client_input_needed` queda vacío:
no hay responsables del cliente acreditados para los pendientes. Confirmar quién
decide el alcance comercial, sitio, canal de distribución, ventana del piloto y
responsabilidad operativa antes de asignarlos; figuran en `needs_confirmation`.

### Acuerdos del chat e información por confirmar

En el contexto visible de este chat se solicitó preparar seguimiento para INTUTEC
mediante n8n y aprovechar documentos existentes. Se autorizó después crear y probar
solo el workflow manual interno de Vivero mediante MCP, sin publicar ni activar,
sin cambiar permisos/configuración de n8n y conservando DEMO y Metal Madera.
No modificar código, usar IA ni servicios externos, enviar mensajes, hacer commit
o push. El borrador para el cliente permanece
sin aprobación. No aparecen acuerdos comerciales, fechas de entrega ni aceptación
del cliente final en este chat.

`docs/presential-release.md` atribuye a decisiones anteriores la URL del sitio y
la exclusión de pasarela de pagos. Se conservan como antecedentes documentados,
no como acuerdos comprobados en esta conversación. Confirmar su vigencia y:

- Destino de entrega, canal, disponibilidad de firma y versión realmente instalada.
- Responsable y ventana del piloto; no interpretar «piloto del lunes» como fecha acordada.
- Alcance de boletín, cortes/devoluciones en Web y reportes antes de devoluciones.
- Respaldo, responsable operativo y configuración real de correo.
- Vigencia de descripciones antiguas: `CHANGELOG.md` llega a 0.5.0 y
  `docs/architecture.md` conserva texto de una fase previa del escáner/inventario;
  no deben usarse como estado actual ni como evidencia de despliegue.

Identificador interno indicado en este chat: `vivero-dulcinea`. El campo
`report_component` de la exportación separa el componente
`android-supabase-contracts` (Android y contratos backend Supabase). Solo se revisó
el repositorio `ViveroApp`; la Web aparece como contexto documental y contratos
compartidos, sin revisión de `ViveroWeb` ni de su despliegue.

### Borrador de actualización para el cliente — no aprobado

La aplicación cuenta con funciones de catálogo, lectura de códigos, preparación
de ventas, caja e inventario. Los registros de pruebas locales muestran 226 pruebas
de la aplicación sin fallos y 499 comprobaciones de base de datos correctas, además
de cinco pruebas de cobros simultáneos. Aún falta probar el recorrido completo en
las tablets y confirmar la configuración de entrega. No está confirmada la
publicación de esta versión.

## Requisitos

### Prueba interna de seguimiento n8n

Workflow separado: [INTUTEC — Vivero — Seguimiento interno](http://localhost:5678/workflow/Jr06gUa2GuUJLRTh).
Configurado con disparador manual, lectura del archivo real en cada ejecución,
validación de campos y resumen determinista sin IA ni envíos. Sin publicar ni activar.

La ejecución manual `29`, del 22 de septiembre de 2026 a las 06:15:11 UTC, falló
en la lectura: `Access to the file is not allowed. Allowed paths: C:\Users\GAMER/.n8n-files`.
El archivo fuente está fuera de esa ruta permitida. No se cambiaron permisos ni
configuración y no se copiaron datos a otra ubicación. La validación y el resumen
no llegaron a ejecutarse; no hay resumen generado por n8n. La estructura de los
cuatro nodos pasó la validación del MCP. DEMO y Metal Madera no fueron modificados.

Posteriormente se autorizó una copia en la carpeta ya permitida. La ejecución
manual `30`, del 22 de septiembre de 2026 a las 06:22:06 UTC, completó lectura,
validación y resumen correctamente; `client_update_approved=false`. Se cambió
únicamente `fileSelector` del nodo de lectura. El workflow sigue sin publicar ni activar.

- Fuente autoritativa: `C:\Users\GAMER\AndroidStudioProjects\ViveroApp\docs\automation\project-status.json`.
- Copia leída por n8n: `C:\Users\GAMER\.n8n-files\intutec\vivero-dulcinea\project-status.json`.
- SHA-256 idéntico al copiar: `09D198CF67A3113A4F55BBCA1B311F376115C4466B751C10D36D87A1311E2B4B`.

**No existe sincronización automática.** Cada cambio del original requiere
actualizar la copia y comprobar su hash antes de ejecutar el workflow. n8n lee
la copia del disco en cada ejecución; no incorpora el reporte dentro de los nodos.
La carpeta permitida es `GAMER\.n8n-files`, no `GAMER.n8n-files`.
No se ampliaron permisos ni se modificó la configuración de n8n.

#### Sincronización manual del reporte

Después de actualizar el original, ejecutar desde PowerShell (requiere PowerShell
7; no usar Windows PowerShell 5.1). Comando con el ejecutable comprobado en este equipo:

```powershell
& 'C:\Users\GAMER\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File 'C:\Users\GAMER\AndroidStudioProjects\ViveroApp\docs\automation\sync-project-status.ps1'
```

Si `pwsh` está en PATH, equivale a:

```powershell
pwsh -NoProfile -File 'C:\Users\GAMER\AndroidStudioProjects\ViveroApp\docs\automation\sync-project-status.ps1'
```

El script toma el JSON junto a él y lo copia a la ruta permitida indicada arriba.
Valida JSON estricto, campos y listas del workflow, fecha ISO con zona horaria,
identificador/schema y aprobación booleana `false` antes de tocar el destino.
Una validación fallida termina con error y conserva la copia anterior. Mantiene
abierto el origen sin permitir modificaciones durante la copia, prepara un temporal
en el directorio de destino, verifica SHA-256 y reemplaza atómicamente con respaldo.
Comprueba el hash final y restaura el respaldo si falla esa comprobación; si falla
la restauración, conserva el respaldo y muestra su ruta. Requiere permisos de
escritura/reemplazo en el destino; no modifica permisos ni configuración de n8n.

Copia los bytes sin reserializar: no cambia `updated_at`, aprobación ni avances.
No inicia workflows, envía mensajes ni programa tareas. Los parámetros opcionales
`-SourcePath` y `-DestinationPath` permiten probar con archivos aislados.

Comprobación de esta entrega: creación y reemplazo correctos; rechazo de JSON
malformado, aprobación `true`, lista interna ausente y fecha inválida, conservando
el hash anterior en los cuatro casos. La sincronización real conservó el SHA-256
`09D198CF67A3113A4F55BBCA1B311F376115C4466B751C10D36D87A1311E2B4B`,
la fecha `2026-09-22T05:16:53+00:00` y `client_update_approved=false`.
La prueba de reemplazo requirió acceso fuera del sandbox de edición; no se cambió
la política de acceso de n8n. La validación se basa en el contrato del workflow
inspeccionado durante su creación; si cambia ese contrato, revisar también el script.

### Entorno Android

- Android Studio compatible con AGP 9.2.
- JDK 17 o superior (Android Studio incluye uno compatible).
- Android SDK 37 instalado (el `targetSdk` permanece en 36).
- Emulador o dispositivo con Android 7.0 (API 24) o superior.

## Abrir y ejecutar

1. En Android Studio, selecciona **Open** y abre esta carpeta.
2. Espera a que termine **Gradle Sync**.
3. Selecciona un emulador o dispositivo.
4. Pulsa **Run 'app'**.
5. Configura Supabase e inicia sesión con una cuenta real.

También puedes compilar desde la terminal integrada:

```powershell
.\gradlew.bat assembleDebug
```

## Pruebas

Desde Android Studio, haz clic derecho en `app/src/test` y elige **Run Tests**. Desde terminal:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Supabase

La URL pública y la clave publicable/`anon key` se configuran únicamente en `local.properties`, que está ignorado por Git. Consulta [docs/setup-supabase.md](docs/setup-supabase.md).

Se usa Supabase Kotlin 3.2.6 por compatibilidad binaria con Kotlin 2.2.10. Las ramas 3.7.x requieren Kotlin 2.4 y se evaluarán en una actualización futura del toolchain.

## Documentación

- [Arquitectura](docs/architecture.md)
- [Modelo de base de datos](docs/database.md)
- [Roles y permisos](docs/roles-and-permissions.md)
- [Pruebas](docs/testing.md)
- [Preparación de la entrega Android](docs/production-release.md)
- [Preparación de Supabase](docs/setup-supabase.md)

## Catálogo

Incluye búsqueda por nombre, nombre científico, código interno y código de barras; filtros por categoría y disponibilidad; precios en centavos y detalle de cuidados. Las sesiones autenticadas consumen exclusivamente Supabase.

## Escáner

Reconoce QR, EAN-13, EAN-8 y Code 128 mediante CameraX y ML Kit. Solicita la cámara con una explicación de privacidad, evita lecturas repetidas y ofrece captura manual para emuladores o equipos sin cámara. Puedes probar con `750100000001`, `750100000014`, `750100000022` o con los códigos internos `PL-001`, `PL-014` y `PL-022`.

## Carrito y envío a Caja

Agrega plantas desde catálogo, detalle o escáner. El borrador se conserva en Room aunque cierres la app. El envío genera un UUID idempotente, conserva un outbox local y llama a `submit_sale_to_cashier`; el backend recalcula precios y es la autoridad.

## Inventario piloto

Las cuentas con `MANAGE_INVENTORY` y sucursal activa disponen de un tablero operativo. La gerente puede registrar recepciones y conciliar un conteo físico con motivo; PostgreSQL genera movimientos auditables y mantiene el saldo. Los reintentos conservan una clave idempotente durante el intento abierto. No existe edición directa del saldo.

Las 35 migraciones se aplicaron desde cero en una base local aislada y pasaron las 499 aserciones pgTAP de las 18 suites, además de cinco escenarios concurrentes de caja con verificación de inventario. Antes de producción siguen pendientes los recorridos completos de Android/Web y la configuración del destino y firma de release. Consulta la [evidencia de base de datos](docs/database-validation.md).

## Instalación local integral verificada (2026-10-01)

Guía del backend oficial para Tony: [instalación desde cero](docs/backend-fresh-install.md). Incluye Docker/MariaDB, bootstrap OWNER, pruebas y persistencia. Web/Android todavía usan Supabase; no hubo publicación ni despliegue.

## Entrega técnica para Toni (3 de octubre de 2026)

- [Resumen técnico](docs/toni-resumen-tecnico.md).
- [Manual técnico y operación](docs/toni-manual-tecnico.md).

Revisión de solo lectura con estado comprobado y pendientes; los documentos
antiguos conservan su evidencia histórica.
