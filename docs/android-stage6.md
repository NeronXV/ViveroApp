# ViveroApp Android — etapa 6

Fecha: 2026-10-09. Implementación local para revisión visual; aceptación presencial pendiente. No hubo commit, push, despliegue, migraciones ni operaciones de producción.

## Mejoras implementadas

- **Vender:** sucursal activa, búsqueda y acceso a cámara juntos, categorías horizontales, cuadrícula adaptable, fotografía del catálogo, alternativa botánica durante carga/error o ausencia de imagen, nombre, precio y «Agregar». Código manual y ficha completa quedan en diálogos secundarios. El escaneo sigue buscando, sin agregar automáticamente.
- **Carrito:** productos primero, fotografías pequeñas cuando se pueden resolver con el catálogo autorizado, precio unitario, cantidad, controles de 48 dp, subtotal y confirmación antes de quitar. Vaciar carrito pasa a un menú. Total estimado y «Enviar a caja» permanecen en el pie fijo.
- **Confirmación:** el botón principal solicita la cotización existente. El diálogo muestra productos y precios del servidor, total vigente, aviso si el importe cambió y confirmación explícita. Una operación en curso deshabilita confirmación y salida del diálogo.
- **Resultado:** «Venta enviada a Caja», folio y total del diario original, estado confirmado, «Nueva venta» y «Mis ventas» por capacidad. No se calcula el resultado desde el carrito consumido ni desde precios actuales.
- **Recuperación:** aviso visible «Estamos comprobando el envío», consulta del resultado original y bloqueo de otra cotización mientras haya un pendiente. Se comprueban tanto el diario reciente como la lista de pendientes existente, para no omitir un envío antiguo fuera de las últimas 100 entradas. Si el diario no se puede leer, no se habilita envío. El panel previo de recuperación, reintento y cierre de intento se conserva.
- **Navegación e historial:** entrada «Vender», carrito y Mis ventas existentes; vuelta al catálogo sin acumular pantallas de carrito. Historial con folio, estado e importe más visibles y sin la introducción extensa.

## Reutilización y límites del cambio

Se reutilizan Material 3, tema verde/crema y tipografía ya presentes, `ViveroTopAppBar`, `ViveroCard`, `StatusPill`, Coil, CameraX/ML Kit, Hilt, rutas tipadas, `SessionStore`, repositorios, cotizador, emisor, sincronizador y Room.

No hay dependencias nuevas ni otro backend. No se modificaron contratos, transporte, persistencia, esquema Room, claves, sincronización, pagos, inventario o capacidades. Los nuevos campos de UiState solo representan información existente. Las consultas opcionales de fotos buscan por nombre mediante el gateway actual y exigen coincidencia exacta del ID; ignoran sus precios. No se repiten al cambiar únicamente cantidades. Si hay más de 100 coincidencias y el producto no aparece, o la red falla, se usa la alternativa visual. No se inventa una imagen ni se persiste metadato nuevo.

Las verificaciones de usuario/sucursal se conservan; la confirmación y las fotos se descartan al cambiar sesión. Gerencia, inventario, administración, autenticación y Caja Android conservan sus funciones y permisos. No se trasladó lógica de Caja Web a Android ni se añadió selector de sucursal que el servidor no autorice.

## Archivos de esta etapa

Prefijo de producción: `app/src/main/java/com/intutec/viveroapp/`.

Modificados:

- `feature/catalog/presentation/BackendCatalogScreen.kt`
- `feature/catalog/presentation/BackendCatalogViewModel.kt`
- `feature/cart/presentation/BackendCartScreen.kt`
- `feature/cart/presentation/BackendCartViewModel.kt`
- `feature/cart/presentation/BackendPendingSalesPanel.kt`
- `feature/home/presentation/BackendHomeScreen.kt`
- `feature/home/presentation/HomeScreen.kt` — solo la descripción de la acción de venta.
- `feature/mysales/presentation/BackendHistoryScreen.kt`
- `navigation/ViveroApp.kt`

Nuevos:

- `core/designsystem/SalesComponents.kt`: foto, aviso y estado vacío compartidos.
- `app/src/test/java/com/intutec/viveroapp/feature/cart/BackendSalesPresentationTest.kt`
- `app/src/androidTest/java/com/intutec/viveroapp/feature/sales/BackendSalesUiTest.kt`
- `app/src/androidTest/java/com/intutec/viveroapp/feature/sales/BackendSalesPersistenceTest.kt`
- Este reporte.

## Pruebas de esta sesión

Se usó el JDK 21 existente en `C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`; no se cambió configuración versionada. El intento inicial sin JAVA_HOME y el JBR incompleto de Android Studio no permitieron compilar; se resolvió usando ese JDK instalado.

| Ejecución | Resultado |
| --- | --- |
| `gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest` | Correcto. |
| `gradlew.bat testDebugUnitTest` final | 370 aprobadas, 1 HTTP opcional omitida, cero fallos/errores. Incluye 7 casos nuevos de presentación/ViewModels. |
| `gradlew.bat lintDebug` | Correcto: cero errores, 34 advertencias. No se corrigieron advertencias ajenas a esta etapa. |
| ADB, `BackendSalesUiTest` + `BackendSalesPersistenceTest` | 7 aprobadas en emulador API 35. |
| ADB, `BackendSalesUiTest`, tablet con texto ampliado | 6 aprobadas; repetidas después de mejorar la espera de carga de imágenes. |
| ADB, `BackendSalesUiTest`, teléfono, capturas finales | 6 aprobadas. |
| `git diff --check` | Correcto. |

Las capturas y pruebas de interfaz se ejecutaron en `emulator-5560`, AVD `Mochilapp_Pixel6_API35`, iniciado con `-read-only -no-snapshot-save -no-window`. Se actualizaron los APK únicamente en esa copia temporal, después de confirmar la misma firma. No se instaló en el dispositivo físico. La prueba de persistencia creó un archivo Room sintético con nombre único; nunca abrió ni limpió la base operativa.

Las pantallas capturadas ejecutan los mismos composables de producción con estados y callbacks sintéticos. La imagen de Monstera es un recurso existente usado como fixture; la app normal utiliza las URLs del catálogo. **No representan ventas reales ni una sesión conectada al VPS.** La confirmación capturada es un caso de interfaz; la recuperación real del emisor se cubre por las pruebas unitarias existentes y nuevas.

## Matriz de verificaciones solicitadas

| Caso | Evidencia ejecutada y alcance |
| --- | --- |
| 1. Búsqueda | Interacción Compose y ViewModel real con gateway de prueba; conserva debounce y filtros enviados. |
| 2. Categorías | Interacción de chips, selección y petición filtrada del ViewModel. |
| 3. Con/sin fotografías | Coil carga el recurso de ensayo; alternativa visible para producto sin imagen. Capturas esperan fin de carga. |
| 4. Código/escáner | Código manual en Compose y pruebas existentes del ViewModel/decodificador. Permiso de cámara denegado mantiene entrada manual. Escaneo físico pendiente. |
| 5. Agregar | Callback de Agregar y regresión del ViewModel/capacidades. Escanear no agrega automáticamente. |
| 6. Cantidades | +/− en interfaz, cálculos existentes y persistencia real de dos unidades en Room sintético. |
| 7. Quitar | No elimina hasta confirmar; luego muestra vacío. |
| 8. Carrito vacío | Composable real y captura, con acceso a buscar productos. |
| 9. Cotización | ViewModel conserva cotización del emisor; el diálogo usa precios del servidor de prueba. |
| 10. Cambio de precio | Aviso de importe actualizado en Compose y regresión existente `SALE_PRICE_CHANGED` del emisor. |
| 11. Sin existencia | Rechazo del gateway simulado conserva borrador y no envía. No se ejecutó un caso nuevo de inventario cero contra MariaDB. |
| 12. Éxito | Folio/total originales del diario en ViewModel y confirmación Compose. No es envío HTTP real de esta etapa. |
| 13. Respuesta perdida | Emisor/sincronizador unitarios y nuevo caso ViewModel; bloquea otra cotización y consulta el ID original. |
| 14. Recuperación | Consulta original sin segundo send, diario persistido y aviso visible. |
| 15. Cierre/reapertura | Cierre y reapertura de Room real sintético preservan clave, importe, cantidad, pendiente y ámbito. Recreación del ViewModel muestra el pendiente. No se ejecutó force-stop/reapertura de la app con una venta HTTP real. |
| 16. Folios | Regresión existente de folios cortos/históricos y resultado `VD-0090` desde el diario, sin renumerar. |
| 17. Roles | Suite existente de capacidades y revocación; se conserva guard de navegación y visibilidad por capacidad. |
| 18. Sucursal | Sesión autorizada de prueba restablece filtros; suites previas verifican ámbito; Room sintético no expone datos a otra sucursal. No se asignó ni activó una sucursal real. |
| 19. Red | Rechazos simulados, diario ilegible y estados inciertos; captura de error. HTTP real nuevo pendiente. |
| 20. Tamaños/accesibilidad | Teléfono 1080×2400, densidad 420; tablet 1600×2560, densidad 320 y font_scale 1.3. Controles de cantidad de 48 dp, nombres accesibles, avisos con live region. TalkBack y dispositivo económico físico pendientes. |

Docker local estaba detenido: `docker version` no encontró el pipe `dockerDesktopLinuxEngine`. La prueba HTTP opcional requiere un fixture aislado y no se ejecutó contra el VPS como alternativa. No se iniciaron contenedores, se hicieron migraciones ni se eliminaron volúmenes de otros proyectos. Los resultados de etapas anteriores se usaron como contexto, no se contabilizaron como pruebas nuevas.

## Capturas reales

Todas están en la carpeta ignorada `tmp/android-stage6/captures/`, con versiones `phone/` y `tablet/`:

- [Vender y catálogo](../tmp/android-stage6/captures/phone/vender-catalogo.png)
- [Carrito con artículos](../tmp/android-stage6/captures/phone/carrito-articulos.png)
- [Confirmación de precios](../tmp/android-stage6/captures/phone/confirmar-precios.png)
- [Venta enviada](../tmp/android-stage6/captures/phone/venta-confirmada.png)
- [Carrito vacío](../tmp/android-stage6/captures/phone/carrito-vacio.png)
- [Envío por comprobar](../tmp/android-stage6/captures/phone/envio-por-comprobar.png)
- [Error de catálogo](../tmp/android-stage6/captures/phone/catalogo-error.png)
- [Carrito en tablet](../tmp/android-stage6/captures/tablet/carrito-articulos.png)

## APK de revisión

- Copia preservada: `C:/Users/GAMER/AndroidStudioProjects/ViveroApp/tmp/android-stage6/apk/ViveroApp-etapa6-debug.apk`.
- Salida Gradle: `app/build/outputs/apk/debug/app-debug.apk`.
- Variante debug, paquete `com.intutec.viveroapp`, versión conservada `1.0.8-vps`, versionCode 9, minSdk 24 (Android 7). Solo se ejecutó en API 35.
- Certificado debug existente SHA-256: `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6`; coincide con la instalación del emulador. No se cambió firma ni se desinstaló para actualizar.
- SHA-256 del APK: `0c6022b30ed7b5ddc66236b40a3512b04fc19fc48f681ca3f7175fa4bc177414`.
- Conserva la configuración previa de destino. No se utilizó para operar datos reales. No se verificó compatibilidad de firma con instalaciones físicas/release: no desinstalar para forzar instalación. No es una APK definitiva de distribución.

## Conservación, riesgos y siguiente revisión

Antes de editar se guardaron diff y estado de ambos repositorios en `tmp/android-stage6/baseline/`. Al cierre se comprobaron exactamente iguales los 22 diffs preexistentes App y los 26 Web. Los archivos Android modificados en esta etapa estaban limpios al inicio. Backend, Web, migraciones, configuración de firma y los archivos anteriores de folios permanecen intactos. Nuevos archivos y cambios de etapa 6 siguen sin commit, junto con el trabajo previo.

HEAD/rama conservados: App `main` / `a58fd614b28920d7b39fee7cef2b67f1c16b0531`; Web `main` / `5f9c4853e42cace586cdaeec13998668b3d204aa`.

Antes de aceptar presencialmente: revisar el diseño, restablecer un API aislado para repetir el recorrido Android completo (incluyendo precio cambiado, cero existencias y respuesta perdida), probar cierre/reapertura real con ese envío, escaneo físico, TalkBack, tablet y teléfono de gama económica. Confirmar la firma instalada antes de cualquier actualización física. La automatización actual comprueba funciones y persistencia por partes; no sustituye ese recorrido completo ni la aceptación del equipo operativo.

El historial y Home recibieron ajustes puntuales; no se ejecutó una revisión visual instrumentada completa de todas las pantallas gerenciales. Se conserva su implementación y permisos. Las fotos del carrito son enriquecimiento opcional de red y pueden mostrar la alternativa; nunca bloquean cotización o envío. Las advertencias existentes de lint quedan documentadas en `app/build/reports/lint-results-debug.html`.

La implementación se detiene en etapa 6 para revisión. No se inició aceptación presencial, distribución, rediseño de otras aplicaciones ni ninguna etapa posterior.

Se restauraron tamaño/densidad del emulador y escala de texto, y se cerró únicamente `emulator-5560` al terminar. Las capturas y el APK de revisión se conservaron; el AVD original no se guardó con los datos del ensayo.
