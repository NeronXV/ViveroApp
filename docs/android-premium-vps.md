# Diseño premium en la app conectada al VPS

Continuación posterior: [cambio personal de contraseña en 1.0.5-vps](backend-personal-password.md).
Las verificaciones de 1.0.4 siguientes corresponden a la recuperación del diseño.

## Resultado verificado el 2026-10-03

La app habitual `com.intutec.viveroapp`, versión `1.0.4-vps`, código 5, reutiliza
el inicio premium original: logotipo Dulcinea, colores crema/verde/terracota,
tarjeta de venta con hojas y accesos adaptados al ancho de pantalla. No se
reactivó `HomeViewModel` ni los repositorios Supabase del inicio anterior.

`HomeDashboard` comparte la presentación original mediante un resumen sin IDs:
cantidad e importe del carrito. `BackendHomeScreen` usa sesión, permisos y
carrito API/Room actuales. No convierte identificadores MariaDB a UUID. El
resumen representa precios locales; la cotización oficial sigue determinando
el importe que se envía al servidor.

El catálogo recupera cuadrícula adaptable, tarjetas botánicas, colores, precios
destacados, categoría y acceso al carrito. Solo muestra fotos del VPS; si no
hay una, indica fotografía pendiente. No inventa disponibilidad: el contrato
de catálogo actual no entrega stock por sucursal. Caja mantiene su validación
oficial de existencias.

Carrito, caja, historial e inventario usan `ViveroCard` y encabezados de marca.
El carrito incluye resumen estimado. Sus acciones, confirmaciones, permisos,
claves de idempotencia y recuperación de intentos siguen usando los mismos
ViewModels y contratos. Reportes, productos y equipo permanecen en el portal
Web autorizado; no se presenta como restaurada toda la paridad nativa antigua.

## Validaciones nuevas

- `assembleDebug testDebugUnitTest`: aprobado, 350 casos, 349 aprobados,
  uno HTTP opcional omitido sin fixture, cero fallos/errores.
- Cinco pruebas del nuevo adaptador de inicio: los permisos no se deducen del
  nombre del rol; sesiones vencidas, identidades distintas y sucursales inactivas
  no habilitan operaciones; los enlaces administrativos requieren capacidad y URL.
- `connectedDebugAndroidTest`, limitado a `HomeScreenTest`: ocho pruebas
  existentes aprobadas, incluyendo carrito, navegación y distribución adaptable.
  Una expectativa antigua buscaba «Comenzar»; se actualizó a «Comenzar venta»,
  que ya era el texto del diseño original. No se cambió el botón para acomodar la prueba.
- APK firmada con la misma clave que la instalación anterior; instalación y
  versión 1.0.4-vps confirmadas en el dispositivo USB.
- `git diff --check` aprobado. No cambios SQL ni despliegue VPS en esta tarea.

Las capturas automáticas se tomaron después de cerrar las pruebas y muestran
el lanzador, por lo que no se usan como evidencia visual de la app. Falta la
revisión humana del inicio y catálogo con la cuenta real; las pruebas de layout
no sustituyen esa aceptación.

## Conservación de datos y precaución con instrumentación

Antes de instalar o ejecutar pruebas se copió la base con WAL/SHM a un directorio
privado ignorado por Git y se verificó su integridad SQLite.

La tarea de pruebas conectadas retiró automáticamente la aplicación del
dispositivo al finalizar. Se detectó mediante `pm path`; se reinstaló la APK
premium y se restauró el respaldo **solo después de comprobar que no existía
una base en la nueva instalación**. Todos los archivos de base respaldados
coincidieron byte por byte antes de abrir la app. Las 15 ventas históricas y
las dos pendientes permanecen en esa copia. No se declara preservado contenido
ajeno a ese respaldo de bases, como preferencias o cachés; se requiere login.

No repetir pruebas conectadas sobre el paquete operativo sin proteger todos
sus datos privados y revisar la limpieza automática. Preferir un paquete de
pruebas separado cuando se necesite nueva instrumentación. No borrar la app
habitual ni reenviar sus ventas antiguas para resolver una actualización.

La APK y copias privadas están en `tmp/vps-operational-cutover`, fuera de Git.
Continúa siendo una entrega debug; la firma definitiva de distribución sigue
pendiente. Supabase se conserva para conciliación histórica; el grafo activo
continúa usando el VPS y su dominio oficial.

## Archivos propios de esta tarea

- `app/build.gradle.kts`: únicamente versión 1.0.4-vps/código 5.
- `navigation/ViveroApp.kt`: conecta el inicio premium con la sesión API.
- `feature/home/presentation/HomeScreen.kt`: presentación original compartida.
- `feature/home/presentation/BackendHomeScreen.kt`: nuevo adaptador API.
- `feature/catalog/presentation/BackendCatalogScreen.kt`: cuadrícula y tarjetas.
- `feature/cart/presentation/BackendCartScreen.kt`: tarjetas y resumen.
- `feature/cashier/presentation/BackendCashierScreen.kt`: presentación de caja.
- `feature/mysales/presentation/BackendHistoryScreen.kt`: historial de marca.
- `feature/inventory/presentation/BackendInventoryScreen.kt`: inventario de marca.
- `app/src/test/.../feature/home/BackendDashboardTest.kt`: permisos del adaptador.
- `app/src/androidTest/.../feature/home/HomeScreenTest.kt`: una expectativa de texto.
- Este documento y enlace desde `docs/vps-canonical-domain.md`.

Las rutas de producción anteriores son relativas a
`app/src/main/java/com/intutec/viveroapp/`. Las modificaciones preexistentes,
incluida la integración API, quedaron conservadas. Rama `main`; sin commit ni push.
