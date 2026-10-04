# Corte operativo hacia VPS y MariaDB

Estado posterior: [dominio definitivo y app habitual 1.0.3-vps](vps-canonical-domain.md).
Esa continuación verificó HTTPS, acceso y conservación de MariaDB/montajes,
actualizó la tablet y deshabilitó reversiblemente la app de pruebas. Los resultados
de las secciones siguientes describen el corte anterior, no las pruebas nuevas.

Actualización posterior: publicado el ajuste Web de rechazo por inventario
insuficiente. Conserva el intento y distingue rechazo de incertidumbre; 471
pruebas Web aprobadas, lint/build y HTTPS correctos. Solo se recreó Web, con
respaldo verificado y reversión preparada; API, MariaDB y volúmenes intactos.
[Detalle de la corrección](../../ViveroWeb/docs/backend-web-cashier-inventory-rejection.md).

Estado verificado el 2026-10-03. Este documento describe esta continuación;
las evidencias anteriores están en [aceptación Web/Android](vps-acceptance-web-android.md)
y [corte de datos](backend-vps-data-cutover.md).

## Resultado de esta continuación

- La Web desplegada ya usa la API oficial y MariaDB. Los hashes de sus 13
  archivos JavaScript/CSS coinciden con el build local actual. HTTPS devuelve
  200 en `/health`. No fue necesario volver a desplegarla.
- Web: 468 pruebas aprobadas, lint y build correctos. El bundle no contiene
  los marcadores del SDK Supabase ni sus rutas de autenticación/PostgREST.
  Los helpers históricos y la dependencia siguen en las fuentes; no constituyen
  un proveedor alternativo para la sesión activa.
- Android habitual actualizado por USB, mediante `adb install -r`, a
  `com.intutec.viveroapp`, versión `1.0.2-vps`, código 3. La firma coincide
  con la aplicación anterior; no se desinstaló ni se borraron datos.
- La configuración local ignorada establece los orígenes API/Web HTTPS del VPS
  para debug y release. Los campos Supabase de BuildConfig están vacíos.
- La compilación normal debug funciona. Suite Android: 345 casos, 344 aprobados,
  uno HTTP opcional omitido sin fixture, cero fallos/errores. La evidencia anterior
  del ensayo HTTP Kotlin/VPS sigue en la guía de aceptación; no se repitió aquí.
- Respaldo privado de la base de la app anterior, con sus archivos WAL/SHM y APK.
  Integridad SQLite correcta; versión Room previa 3. Se preservó el archivo
  original antes de preparar la actualización hacia Room 6. Una segunda copia
  posterior a la instalación confirmó integridad, conservación de las 15 ventas
  antiguas y de las dos pendientes sin cambios; sigue en Room 3 hasta abrir el carrito.
- Se encontraron dos ventas UUID locales con sincronización fallida y sin
  coincidencia por ID de origen o folio en MariaDB. Se conservan y no se reenviaron;
  falta que Pedro indique si fueron ensayos o ventas reales pendientes.
- Pedro confirmó que el inventario físico está comprobado. Se activó mediante
  la API oficial el descuento de existencias en la sucursal principal. Nuevos
  cobros descuentan stock; insuficiencia de saldo rechaza el pago. No se
  reprocesaron ventas históricas ni se modificaron cantidades al activar.

## Alcance del uso diario

El grafo activo Android usa API/MariaDB para login, permisos, catálogo, códigos
manuales, carrito, envío, caja, historial/comprobantes e inventario. Reportes y
administración se abren en la Web oficial, que también usa API/MariaDB.
Las pantallas históricas Supabase permanecen fuera del grafo activo. Todavía no
hay paridad nativa completa ni integración del antiguo escáner de cámara.

La APK instalada es **debug**, firmada con la misma clave de desarrollo que la
versión anterior. No se ha creado ni sustituido una clave release. Falta
establecer una estrategia de firma definitiva compatible con las instalaciones
existentes; no resolverlo desinstalando o borrando sus bases locales.
La versión se mantiene en `app/build.gradle.kts`; los orígenes y credenciales de
firma permanecen en archivos locales ignorados.

## Pendientes que impiden declarar cerrado todo el corte

1. Comprobar login/carrito y migración Room en la app habitual actualizada.
   La instalación correcta no demuestra por sí sola esa migración.
2. Conciliar las dos ventas UUID pendientes; no convertirlas ni generar nuevas
   claves automáticamente. Conservar respaldo y tablas históricas.
3. Las cinco cuentas importadas distintas de la cuenta ya habilitada siguen
   sin contraseña. Recuperación/invitaciones por correo requieren la
   configuración Resend pendiente; el backend también ofrece restablecimiento
   administrativo autorizado, que no equivale a enviar correos.
4. La otra sucursal sigue con descuento de inventario desactivado. Activarla
   desde una cuenta habilitada con sucursal/capacidad correspondientes, usando
   el endpoint oficial y el conteo físico confirmado.
5. Finalizar la aceptación visual de cobro, comprobante y stock, con datos aislados.
   Las pruebas automáticas previas no sustituyen el recorrido humano.
6. Copia cifrada externa R2 y restauración aislada con un respaldo operativo
   reciente. No se activaron recursos de pago ni se configuró el correo.
7. AppCliente sigue demo. Su integración no se presenta como completada por
   actualizar la app de personal. Hace falta delimitar este bloque con Pedro.

Supabase se conserva sin modificar ni apagar: sirve para revisión/conciliación
mientras existan operaciones históricas pendientes. El uso operativo de los
consumidores nuevos apunta al VPS; esto no declara retirado todo el código legado.

## Archivos y verificaciones

- Cambio propio versionable: únicamente código/nombre de versión en
  `app/build.gradle.kts`, este documento y enlace desde la guía general de corte.
  El resto de modificaciones previas se conserva.
- Artefactos, copias privadas y scripts de esta continuación: directorio `tmp/`
  ignorado por Git, con acceso privado para el propietario y SYSTEM. No subirlos.
- Sin commit ni push. Hubo instalación Android y activación remota de inventario;
  no hubo recreación de servicios, migración SQL nueva, nueva importación de
  datos, despliegue Web ni cambios en Supabase.

Caddy continúa dentro del proyecto Compose de Vivero. Debe independizarse antes
de incorporar la segunda aplicación, siguiendo la
[guía de operación](vps-multiproject-operations.md). Este corte no duplica el proxy.
