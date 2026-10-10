# Android principal: ejecución diaria desde Android Studio

Proyecto único: `C:\Users\GAMER\AndroidStudioProjects\ViveroApp`.
Módulo único: `app`. Application ID: `com.intutec.viveroapp`.
Versión: **1.0.11-vps**, `versionCode=12`. Nombre visible: **Vivero Dulcinea**.

La variante `pilot` se retiró. Se conservan las variantes normales `debug` y `release`, con la misma implementación y restricciones operativas. Debug significa herramientas de desarrollo habilitadas, no datos ficticios. Run instala la aplicación principal; no exige construir ni transferir un APK manualmente.

## Uso desde Android Studio

1. Abrir el proyecto anterior y esperar la sincronización de Gradle.
2. En Build Variants, usar **app → debug**. En la barra superior, seleccionar la configuración **app**.
3. Conectar el celular con depuración USB habilitada, autorizarlo en el dispositivo y seleccionarlo en la barra.
4. Pulsar **Run ▶ app**. La configuración existente utiliza la actividad principal, compila con Gradle y mantiene `CLEAR_APP_STORAGE=false`.
5. Iniciar sesión con la cuenta habitual y comprobar rol/sucursal en Mi cuenta.

Android Studio se abrió durante esta tarea; su ventana confirmó `ViveroApp – MainActivity.kt [ViveroApp.app.main]`. Se usó el JDK 21 existente para su arranque y las comprobaciones Gradle, sin modificar la instalación del IDE ni ajustes globales. La validación CLI equivale a configurar y compilar el proyecto; no se pulsó Run ni se instaló en un teléfono desde herramientas.

## Conexión y funciones

Origen API: `https://viverodulcinea.bajastack.network`. Los recursos se agregan como `/api/v1/...`: login `/api/v1/auth/login`, identidad `/api/v1/auth/me`, catálogo `/api/v1/products`, inventario `/api/v1/inventory/dashboard`. No añadir `/api/v1` a la URL base de local.properties.

Los orígenes públicos de API/Web continúan en `local.properties`, ignorado por Git. Debug comprueba el destino productivo antes de compilar. Release mantiene su destino HTTPS explícito y su verificación de firma. No se integraron contraseñas, tokens ni claves privadas.

- Login/logout real mediante API MariaDB, permisos efectivos y sucursal autorizada. Se revalida la identidad al volver a la app; errores de sesión/permisos bloquean el recorrido operativo.
- Catálogo comercial real: categorías, búsqueda, detalles, fotografías y consulta por código según capacidad.
- Inventario e historial de la sucursal en modo consulta, compatible con el objeto `permissions` de 033.
- **Catalogar plantas** abre `/admin` en el navegador externo. Allí: **Plantas → Revisión de plantas y nuevas fichas**. Los 174 borradores y sus avances permanecen en Web. La sesión es independiente; comprobar la cuenta del navegador. No se transfieren tokens.
- **Abrir conteos en Web** lleva al mismo portal para usar el flujo de observación/revisión de 033. Precios, preparación/activación y otros módulos administrativos permanecen en Web, según permisos.

No hay navegación a módulos Supabase ni pantallas demo en el grafo principal; las claves Supabase empaquetadas continúan vacías. El código legado se conserva sin refactors ajenos ni fallback operativo.

## Restricciones compartidas de Debug y Release

`NATIVE_OPERATIONS_ENABLED=false` es la configuración común, independiente de DEBUG. Presentación, navegación y transporte bloquean ventas, cobros y modificaciones nativas de inventario. El acceso de cuenta, consultas y lectura de resultados históricos de inventario permanecen disponibles. El gateway continúa rechazando conteos nuevos al endpoint retirado antes de transmitir HTTP.

No se habilita la escritura por cambiar de variante, usuario o rol. No se borran carritos ni intentos guardados. Su habilitación futura requiere compatibilidad y autorización específicas. La app no asigna permisos nuevos al personal de operación, propiedad o gerencia: usa las capacidades del servidor.

## Firma y actualización

La APK Debug generada con esta configuración tiene el mismo applicationId y certificado que la APK 1.0.10-piloto; el código sube de 11 a 12. El verificador existente confirmó que puede actualizar esa instalación sin requerir desinstalación. Se preservan el esquema Room y sus migraciones existentes; no se hicieron cambios de base local ni borrados de datos.

Run usa la firma Debug existente. Es adecuada para esta ejecución desde Android Studio. La firma Release definitiva aún requiere `key.properties` y su almacén de firma; esa comprobación se conserva, y no se necesita para Run Debug. No se generó Release ni se asumió compatibilidad de un certificado distinto.

## Validación de esta tarea

- Configuración/sincronización equivalente: `gradlew.bat help` correcta.
- `assembleDebug`: correcto.
- Pruebas focalizadas de autenticación, catálogo, inventario, permisos y navegación: **57 aprobadas**, cero fallos, errores u omisiones. Incluyen el snapshot público leído en la preparación anterior como prueba del parser, sin nuevas operaciones productivas.
- Firma/paquete/versión Debug frente a la APK anterior: PASS (`-AllowDebug`).
- URL productiva empaquetada y restricciones verificadas mediante BuildConfig y las pruebas de la variante Debug normal.
- Health público HTTPS: correcto. No se autenticaron herramientas con cuentas reales.
- `git diff --check`: correcto. No se ejecutaron lint ni suites amplias adicionales; se respetó el alcance focalizado solicitado. No se iniciaron emuladores ni pruebas físicas de cámara o instalación.

Comando focalizado ejecutado:

```powershell
.\gradlew.bat help assembleDebug testDebugUnitTest --tests '*AndroidOperationalSafetyTest' --tests '*BackendAuthRemoteTest' --tests '*BackendAuthRepositoryTest' --tests '*BackendCatalogRemoteTest' --tests '*BackendCatalogViewModelTest' --tests '*BackendPublicCatalogContractTest' --tests '*BackendInventoryTest' --tests '*BackendInventoryViewModelTest' --tests '*BackendDashboardTest' --console=plain
```

Evidencia temporal: `tmp/android-main-20261010/`. APK generada por el build normal: `app/build/outputs/apk/debug/app-debug.apk`; Run realiza esta generación/instalación automáticamente.

## Cambios y estado Git

Esta consolidación ajustó `app/build.gradle.kts`, el transporte API, las condiciones de catálogo/home/inventario/navegación, la prueba de restricciones renombrada a `AndroidOperationalSafetyTest.kt`, la prueba de conservación de intentos de inventario y estas guías. Se conservaron los demás avances del piloto y los cambios preexistentes del backend, IDE y documentación. No se alteraron producción, MariaDB, los 174 borradores ni Catering Oculto. No hubo commit, push o despliegue.
