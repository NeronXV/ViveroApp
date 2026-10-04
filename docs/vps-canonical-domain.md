# Entrada operativa de Vivero Dulcinea

Actualización posterior: [diseño premium y versión Android 1.0.4-vps](android-premium-vps.md).
Los datos de versión 1.0.3 siguientes documentan el cambio de dominio anterior.

Verificado el 2026-10-03, con autorización de Pedro para el nombre definitivo.

La Web oficial es https://viverodulcinea.bajastack.network. Hostinger tiene
registros A y AAAA del subdominio hacia el VPS existente, TTL 300. No se compró
un dominio ni se contrató otro servicio.

La app habitual `com.intutec.viveroapp` quedó instalada por USB en la tablet
como `1.0.3-vps`, código 4. API y Web apuntan al mismo origen HTTPS. Se confirmó
la coincidencia de firma con la APK anterior y se actualizó con `adb install -r`.
Continúa siendo una APK debug; la firma de distribución definitiva sigue pendiente.

## Datos y acceso anterior

Solo hay una base operativa: MariaDB del proyecto Compose `vivero-vps`. Se
recrearon API y Web para actualizar sus variables de dominio, sin recrear MariaDB,
mover datos ni cambiar los montajes persistentes. Antes se creó y verificó por
hashes un respaldo completo de base e imágenes.

La entrada principal de `bajastack.network` redirige temporalmente con HTTP 307
al subdominio nuevo. `/caja`, `/login`, `/assets/*`, `/api/*` y `/health` permanecen
disponibles en el origen anterior para recuperar operaciones guardadas allí.
El navegador aísla localStorage por origen: la redirección no transfiere carritos,
sesiones ni claves de intentos de pago. No borrar ese almacenamiento ni repetir
una venta con otra clave para resolver un cobro antiguo.

El backend admite el origen principal y una lista explícita `WEB_ORIGIN_ALIASES`.
No se habilitó CORS ni un comodín; solicitudes con origen no autorizado o
metadatos cross-site siguen rechazándose. Los enlaces de correo futuros usarán
solo `WEB_ORIGIN`, que corresponde al subdominio definitivo.

Una vez conciliadas todas las operaciones del navegador antiguo, retirar su
excepción de recuperación de Caddy y `WEB_ORIGIN_ALIASES`, dejando únicamente
la redirección. La API antigua puede mantenerse como compatibilidad para las
instalaciones Android pendientes de actualizar: apunta a la misma base.

## Tablet

Se respaldaron las bases de ambas apps antes de consolidar el acceso. La app
de pruebas `com.intutec.viveroapp.vpsvalidation` tiene dos envíos sincronizados,
sin operaciones backend pendientes ni artículos en carrito. Quedó deshabilitada
con `pm disable-user --user 0`, conservando todos sus datos. Para revisar su
historial puede habilitarse de nuevo con:

```powershell
adb shell pm enable com.intutec.viveroapp.vpsvalidation
```

El respaldo de la app habitual conserva las 15 ventas históricas y las dos
pendientes sin alteraciones; SQLite íntegro, todavía Room 3 antes de iniciar
el carrito en esa app. Falta comprobar con Pedro su login y la migración Room 6
al utilizarla. No se reenviaron las ventas antiguas automáticamente.
Una segunda copia posterior a instalar 1.0.3-vps volvió a confirmar integridad
SQLite, las 15 ventas y las dos pendientes idénticas; continuaba en Room 3.

## Verificaciones de esta continuación

- HTTPS público: raíz y health del subdominio responden 200; raíz anterior
  responde 307; caja anterior responde 200.
- Login y contexto OWNER/sucursal, catálogo, inventario, historial, comprobantes
  y revocación de sesión comprobados mediante la API HTTPS del subdominio.
- Orígenes principal y anterior aceptados; origen ajeno rechazado con 403.
- Backend: 84 pruebas aprobadas y sintaxis revisada. Incluye tres pruebas de
  origen, con autenticación todavía obligatoria y ausencia de CORS.
- Android: `assembleDebug testDebugUnitTest` aprobado; 345 casos, 344 aprobados,
  uno HTTP opcional omitido sin fixture; cero errores o fallos.
- Firma Android idéntica a la instalación anterior y versión instalada confirmada.
- Identidad del contenedor MariaDB y montajes API/Web/DB iguales antes y después.
- Caddy validado y recargado al conservar los encabezados de seguridad también
  en el acceso antiguo. API y DB saludables; Web activo; solo 80/443 publicados.

La compra nueva de dos artículos está en MariaDB, enviada a caja y sin pago.
Una línea carece de existencias; el rechazo de inventario evita registrar el
cobro entero. No se aumentaron saldos ni se desactivó el control para eludirlo.

## Reversión y pendientes

Evidencia y copias privadas del despliegue en
`/opt/vivero/maintenance/20261003-vivero-domain-attempt2`, con imágenes
`vivero-vps-api:before-domain-20261003` y
`vivero-vps-web:before-domain-20261003`. Contiene configuración privada: no copiar
a Git ni mostrar sus valores. Para revertir, restaurar las fuentes y configuración
guardadas, etiquetar esas imágenes con los nombres usados por Compose y recrear
solo API/Web, conservando los volúmenes. Las bases locales/APK anteriores están
en `tmp/vps-operational-cutover`, privado e ignorado por Git.

Supabase se conserva para conciliación histórica; no se usa en el grafo operativo
activo. No se declara terminada toda la migración: siguen pendientes las dos
ventas antiguas, las cinco cuentas sin contraseña, la activación de inventario
de la otra sucursal, aceptación humana del cobro completo, R2 y correo. La app
de clientes sigue demo y debe delimitarse aparte. Caddy continúa dependiendo
del proyecto de Vivero: independizarlo antes del segundo proyecto según la guía
de operación. No hubo commit ni push en esta continuación.
