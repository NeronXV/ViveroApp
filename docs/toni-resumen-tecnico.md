# Vivero Dulcinea: resumen técnico para Toni

**Fecha de revisión:** 5 de octubre de 2026, America/Chihuahua.
**Entrega:** diagnóstico de solo lectura; no cambia servicios, datos ni accesos.
**Manual complementario:** [Manual técnico para Toni](toni-manual-tecnico.md).

## Qué construimos

Vivero tiene una Web para catálogo, pedidos y operación administrativa, y una app
Android para el personal: catálogo, carrito, envío a caja, cobro, comprobantes e
inventario. Ambos recorridos operativos utilizan una API compartida con MariaDB.
La Web está en **https://viverodulcinea.bajastack.network**. El dominio anterior
redirige a ella, conservando temporalmente rutas de recuperación de pagos.

La API y las migraciones viven en ViveroApp; ViveroWeb contiene el cliente React.
Docker Compose administra el despliegue en un VPS Hostinger con Ubuntu. Caddy
sirve la Web, termina HTTPS y reenvía solicitudes a la API. MariaDB no publica
puertos. Los datos y archivos utilizan volúmenes persistentes.

## Qué migramos desde Supabase

Se importaron identidad, sucursales, catálogo, ventas/pagos e inventario, manteniendo
correspondencias con los IDs de origen. MariaDB usa IDs enteros y dinero en centavos.
Las cuentas nuevas no reciben las contraseñas de Supabase: necesitan establecerlas.
Los UUID y operaciones históricas de Android se conservan; no se reenvían a ciegas.

La imagen de producto del origen se excluyó por decisión de Pedro; la carga manual
sigue pendiente. Supabase se conserva para conciliación y validación final. Su
código y dependencias históricas todavía existen, aunque el recorrido operativo
Web/Android revisado usa la API. No hay doble escritura automática entre motores.

## Comprobado en esta revisión (5 octubre 2026)

- GitHub y los checkouts coinciden en `main`: ViveroApp `3369bbb` y ViveroWeb `62d027e`.
- Web activa; API y MariaDB saludables; HTTPS `/`, `/login` y `/health` responden 200.
- Base `vivero`: **56 tablas**, **29 migraciones** hasta `030_purchase_draft_retirement`, seis cuentas,
  15 productos, 16 ventas y 14 pagos. Cinco cuentas no tienen contraseña.
- MATRIZ tiene control de inventario activo; CENTRO lo tiene inactivo.
- La app instalada es `com.intutec.viveroapp`, **1.0.7-vps, código 8**. Incluye diseño
  premium, cámara QR/EAN/Code 128, cambio personal de contraseña desde seis caracteres. Es una APK debug.
- **Validación actual:** Web 475 tests + lint + build ✅; Backend integration tests (13) ✅; Android testDebugUnitTest + assembleDebug + lintDebug ✅.
- Ocho lotes completos de respaldo local; SQL e imágenes coinciden con sus hashes.
  Restic está instalado, pero no existen configuración ni contraseña de destino externo.
- Usuarios SSH personales `pedro` y `toni`, con llave y sudo limitado al wrapper.
  Toni aún debe comprobar la conexión desde su computadora.
- API en ejecución coincide con las fuentes del release. Los 19 archivos bajo
  `/srv/assets` coinciden con el último build local revisado.

Hay una diferencia de reproducibilidad pendiente: el `package-lock.json` Web del
VPS coincide con la copia local modificada, pero difiere del commit publicado en
tres dependencias de desarrollo. La plantilla VPS de ejemplo también está atrasada
respecto a Git. No se corrigieron archivos del servidor para preparar esta entrega.

## AppCliente es un proyecto diferente

`ViveroAppCliente` es una demostración Android de catálogo, carrito, seguimiento,
club y diagnóstico de plantas con respuestas locales. Está instalada como versión
1.0 en el dispositivo consultado, pero no está conectada a la API ni desplegada
como servicio del VPS. Su carpeta local no tiene `.git`; no se acreditó remoto,
commit, publicación en tienda ni persistencia real de pedidos o diagnóstico.

## Qué falta para cerrar Vivero

1. Aceptar un recorrido humano completo en Web y Android: acceso, sucursal,
   catálogo/precios, carrito persistente, envío, cobro, comprobante y stock.
   Los ensayos automáticos anteriores no sustituyen esa aceptación.
2. Conciliar dos ventas antiguas fallidas de Room y los intentos de cobro pendientes.
   Las ventas 15 y 16 siguen enviadas a caja y sin pago; no se borran ni cobran desde herramientas.
3. Habilitar las cinco cuentas pendientes, **configurar correo (Resend) y revisar CENTRO**.
4. Conectar R2, copiar respaldos cifrados y probar recuperación desde esa copia.
   Los respaldos dentro del VPS no protegen frente a perder todo el servidor.
5. Definir firma y distribución Android definitiva, preservando la compatibilidad
   con instalaciones y datos existentes. Resolver la diferencia del lockfile Web.
6. Delimitar AppCliente y retirar Supabase solo después de cerrar conciliación y aceptación.

Para incorporar un segundo proyecto, además hay que **independizar Caddy**.
`/srv/proxy` ya existe, pero su Caddyfile es un enlace al de Vivero; `platform_proxy`
solo conecta su Web. No existe todavía un Compose independiente `platform-proxy`.
Parar o actualizar Web de Vivero aún puede afectar la entrada HTTPS compartida.

## Evidencia y límites

Pruebas anteriores de esta versión: backend 87 aprobadas; Android 355 aprobadas y
una HTTP opcional omitida; Web 475 aprobadas, lint y build correctos. Hay ensayos
documentados de HTTP/SQL, revocación de sesiones y restauración aislada del respaldo
real. Para esta entrega se revisaron resultados y documentación; no se reejecutaron
ventas, cambios de contraseña, restauraciones ni pruebas instrumentadas.

**Validación actual (5 octubre 2026):**
- Web: 475 tests Vitest + lint + build ✅
- Backend: 13 integration tests (integration + purchases + sales + cashier) ✅
- Android: testDebugUnitTest + assembleDebug + lintDebug ✅
- Migración 030 aplicada (tabla `purchase_draft_retirements`, 56 tablas totales)
- APK debug 1.0.7-vps, versionCode 8 generada

No se accedió a Supabase ni Cloudflare para comprobar su estado remoto, ni se probó
la llave privada de Toni. El manual identifica comandos, permisos, ubicaciones,
procedimientos históricos verificados y pasos que siguen siendo una propuesta.

## Primeros pasos de Toni

1. Clonar ViveroApp y ViveroWeb como carpetas hermanas y revisar los commits indicados.
2. Seguir el entorno local del manual con datos sintéticos y credenciales propias.
3. Conectar con `ssh toni@179.236.238.111` y ejecutar los comandos de estado autorizados.
4. Leer el manual, la guía operativa y los pendientes antes de proponer cambios al VPS.
