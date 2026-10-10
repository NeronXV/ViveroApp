# Android: piloto de catalogación del 10 de octubre de 2026

**Evidencia histórica.** La variante `pilot` se consolidó en la aplicación principal. Para el uso actual desde Android Studio, consultar [Android principal](android-main-consolidation.md). Los APK y resultados de esta entrega anterior se conservan; sus comandos de variante ya no aplican a la configuración actual.

APK de prueba: **1.0.10-piloto**, código **11**, paquete habitual `com.intutec.viveroapp`.
Destino API y Web: `https://viverodulcinea.bajastack.network`.

## Alcance de la entrega

| Función | Disponibilidad en esta APK |
| --- | --- |
| Login/logout, permisos, rol y sucursal | API MariaDB; sesión propia del usuario, sin credenciales integradas |
| Catálogo, búsqueda, categorías, detalles y fotos | Nativo, consulta del catálogo comercial actual |
| Consulta por código | Nativa, con permiso SCAN_PRODUCTS; cámara pendiente de aceptación física |
| Existencias e historial por sucursal | Nativo, solo consulta en el piloto |
| Los 174 borradores, completar fichas, fotos y revisión del propietario | Portal Web, desde **Catalogar plantas** |
| Conteos físicos, aprobación y operaciones de inventario | Portal Web con los mecanismos de 033 y permisos actuales |
| Nuevas ventas, pagos y movimientos nativos | Deshabilitados en esta variante de piloto |
| Reportes, equipo, administración de precios/productos y funciones adicionales | Portal Web, según permisos |

El grafo operativo de Android usa BackendAuthViewModel y repositorios de la API. Los módulos Supabase/demo legados permanecen en el repositorio, pero no se navega a ellos desde el grafo operativo. SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY están vacíos. No hay fallback a datos simulados durante el login o las consultas del backend.

El rol/capacidades y sucursal provienen de `/api/v1/auth/me`; no se asignan por nombre ni por dispositivo. Se revalidan al volver a la app. Un rol sin permisos o una sucursal inactiva impiden las operaciones correspondientes. El token vive en memoria, se oculta en toString y vence; no se entrega con la APK. Si el personal de gerencia o propiedad no tiene MANAGE_PRODUCTS, la APK no les concede catalogación: debe revisarse su acceso mediante el panel existente del propietario, fuera de esta preparación.

## Compatibilidad corregida

- El dashboard de inventario valida el objeto `permissions` que la migración 033/API actual agregaron; el cliente anterior rechazaba esa respuesta por contener campos nuevos.
- Se retiró la acción nativa de conteo nuevo. El gateway la rechaza antes de enviar HTTP. La consulta del resultado de un intento histórico conserva su clave y contrato, sin reinterpretarlo como un conteo nuevo aprobado.
- Recepciones nativas se limitan al propietario en variantes operativas; la variante de este piloto deshabilita todas las escrituras nativas de inventario.
- Modo seguro del piloto en navegación, presentación y transporte: las rutas nativas de carrito/Caja y sus botones no se habilitan y no se transmiten ventas, pagos, ajustes, recepciones ni conteos nuevos. No se borran carritos ni intentos guardados. Las excepciones del transporte son acceso de cuenta, consulta de códigos y recuperación de resultados históricos de inventario.
- **Catalogar plantas** y **Abrir conteos en Web** abren `/admin` en el navegador externo. No usan WebView, no adjuntan tokens ni transfieren la sesión Android. El portal conserva sus propias comprobaciones de identidad y autorización.
- Variante `pilot` no depurable, sin las actividades de prototipo de debug, con nombre visible **Vivero Dulcinea · Piloto**. Usa el certificado de pruebas existente; no sustituye una firma release definitiva. La verificación confirmó compatibilidad con la candidata anterior 1.0.9/código 10. El APK release sigue requiriendo su configuración de firma existente; no se eludió esa comprobación.

Los borradores de 034 no son productos comerciales. La consulta pública actual conserva 15 productos y dos categorías y su formato se verificó con el parser Kotlin. Los 174 borradores se consultan y completan en la Web; no se crearon variantes ni tablas Android para duplicar la funcionalidad. El precio y conteo del producto se gestionan después de la revisión del propietario mediante el flujo Web vigente.

## Validación de esta sesión

- `gradlew.bat assembleDebug testDebugUnitTest assemblePilot`: correcto. 375 pruebas debug pasaron; una integración HTTP con entorno sintético propio se omitió por no disponer de su fixture. No se sustituyó por producción.
- `gradlew.bat testPilotUnitTest --tests '*AndroidPilotSafetyTest' --tests '*BackendAuthRemoteTest' --tests '*BackendAuthRepositoryTest' --tests '*BackendInventoryTest' --tests '*BackendPublicCatalogContractTest' lintPilot`: correcto. 37 pruebas focalizadas de la variante piloto pasaron, sin omisiones. Lint sin errores; 32 advertencias sobre versiones de dependencias, recursos y estilo, conservadas sin refactors ajenos.
- `gradlew.bat assemblePilot` final: correcto.
- Verificador APK existente: firma válida, paquete habitual, código creciente, versión esperada y debuggable=false.
- Inspección del DEX y manifiesto de la APK: destinos HTTPS productivos, PILOT_SAFE_MODE=true, claves Supabase vacías, backup de aplicación deshabilitado y prototipos debug ausentes.
- Solo se consultaron `/health`, catálogo público y categorías por HTTPS. La prueba del contrato público reproduce esas respuestas sin volver a conectarse ni autenticarse. No se inició sesión productiva desde herramientas ni se pidieron contraseñas.
- No se iniciaron emuladores, no se instaló en dispositivos y no se ejecutaron pruebas de ventas/inventario/fotos sobre datos reales. La primera aceptación física del APK queda para el personal; las fotos y catalogación Web ya fueron confirmadas por el usuario en producción.

El JBR de Android Studio no era utilizable; se usó el JDK 21 existente en las cachés de Gradle, sin reinstalar Java ni modificar el IDE. Gradle necesitó acceso a sus cachés normales. Las pruebas específicas del piloto se habilitaron mediante [HostTestBuilder de AGP](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/HostTestBuilder), conservando debuggable=false.

## Archivo y pasos para instalar

APK: `C:\Users\GAMER\AndroidStudioProjects\ViveroApp\tmp\android-pilot-20261010\ViveroDulcinea-1.0.10-piloto-20261010.apk`.

SHA-256: `4f79223591bb3d22d068aef59f14fc5c8791ee9db321429a12b2b4048c22d55c`.
Tamaño: 43,517,572 bytes. Android mínimo: 7.0 (API 24).

1. Copiar/enviar manualmente el APK al celular y abrirlo desde el gestor de archivos o navegador.
2. Autorizar la instalación desde esa fuente cuando Android lo solicite e instalar/actualizar. Conservar la instalación y datos anteriores; no desinstalar ni borrar almacenamiento como procedimiento de actualización.
3. Abrir **Vivero Dulcinea · Piloto** e iniciar sesión con la cuenta habitual. Confirmar que Mi cuenta muestra el rol y sucursal correctos.
4. Consultar catálogo/inventario y probar búsqueda y detalles. El catálogo nativo muestra productos comerciales, no los borradores.
5. Pulsar **Catalogar plantas**. En el navegador, comprobar la cuenta Web e iniciar sesión si corresponde: ambas sesiones son independientes.
6. Entrar en **Administración → Plantas → Revisión de plantas y nuevas fichas**, seleccionar **Por completar / Todas**, abrir **Completar ficha**, revisar **Documento original y dudas**, completar solo datos reales y guardar avances/fotografía antes de enviar a revisión.
7. Para conteos reales de productos preparados/vinculados, usar la ficha y flujo de inventario Web existente con su revisión del propietario. No hacer ventas ficticias, movimientos de prueba ni sustituir cantidades cotizadas por existencias.

La firma de actualización se comparó con el APK anterior archivado, no con cada teléfono del personal. Esta entrega es un piloto acotado de consulta y catalogación; la habilitación comercial nativa requiere su propia aceptación posterior.

Evidencia temporal: `tmp/android-pilot-20261010/`, con logs, snapshots públicos, resultados de firma y configuración/manifiesto empaquetados. Archivos reales/snapshots y APK ignorados por Git. No se modificaron producción, MariaDB, Web, los 174 borradores, Catering Oculto, permisos ni migraciones. No hubo commit, push o despliegue. Se preservaron los cambios preexistentes de backend/IDE/documentación.

## Archivos de esta tarea

- `app/build.gradle.kts`: variante, versión, comprobación de destino y pruebas del piloto.
- `core/network/BackendApiTransport.kt`: protección de escrituras del piloto.
- `feature/home/presentation/BackendHomeScreen.kt`: módulos habilitados y acceso de catalogación.
- `feature/catalog/presentation/BackendCatalogScreen.kt` y `BackendCatalogViewModel.kt`: catálogo de consulta en piloto.
- `feature/inventory/data/remote/BackendInventoryRemoteDataSource.kt`: contrato 033 y bloqueo del conteo obsoleto.
- `feature/inventory/presentation/BackendInventoryScreen.kt` y `BackendInventoryViewModel.kt`: inventario de consulta y acceso al flujo Web.
- `navigation/ViveroApp.kt`: apertura del portal y protección de rutas nativas.
- `app/src/test/java/.../feature/inventory/BackendInventoryTest.kt`: actualización del contrato y prueba de bloqueo antes de HTTP.
- `app/src/test/java/.../core/network/AndroidPilotSafetyTest.kt` y `.../feature/catalog/BackendPublicCatalogContractTest.kt`: pruebas nuevas.
- Esta guía. Las rutas Kotlin de producción anteriores están dentro de `app/src/main/java/com/intutec/viveroapp/`.

Estado Git final: cambios Android y guía sin commit; los cambios preexistentes de IDE, backend, migraciones y otras guías permanecen separados e intactos. `git diff --check` pasó; avisos de conversión LF/CRLF, sin errores de espacios.
