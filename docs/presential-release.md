# Cierre de versión presencial — 8 septiembre 2026

> Registro histórico. Para el estado vigente consultar [README.md](../README.md#estado-actual)
> y [database-validation.md](database-validation.md). Los pendientes de pgTAP y
> concurrencia de este registro fueron resueltos localmente el 21 de septiembre
> de 2026 (499 aserciones y cinco escenarios). La indicación histórica de no iniciar
> Docker no describe aquella ejecución posterior ni autoriza nuevas ejecuciones.
> HTTP, correo, piloto y despliegue siguen sin validación integral acreditada.

Sitio confirmado por Pedro: https://vivero-dulcinea.netlify.app

## Cambios preparados
- Migraciones autoritativas 202609080001–003: enlace único de pedido web a venta en Caja, bloqueo de entrega sin pago, cancelación previa al cobro sin una toma activa de caja, activación explícita de inventario por sucursal, devoluciones totales y cortes por cajero, suscripciones confirmadas y campañas.
- Web: enviar pedidos confirmados/listos a Caja; mostrar su folio VD para cobrar desde Web o Android; cortes y devoluciones en Caja web; activación en Inventario.
- Web: asistente basado en fichas reales del catálogo y test por luz/clima con productos reales. No usa un modelo de IA ni diagnostica enfermedades.
- Web: alta de boletín con consentimiento y confirmación, baja por enlace y campañas por lotes de hasta 20 destinatarios.
- Recuperación de contraseña e invitación de personal mediante Supabase Auth; asignación de rol y sucursal sigue siendo administrativa.
- Android: reconoce rechazo por inventario insuficiente y permite configurar redirección web de recuperación.
- Reportes existentes muestran ventas cobradas antes de devoluciones; el corte muestra pagos, devoluciones y neto.

## Configuración pendiente (no ejecutada)
1. Revisar y probar todas las migraciones en un Supabase local con datos sintéticos antes de autorizar publicación. Pedro indicó no iniciar Docker: pgTAP queda preparado, sin ejecución.
2. En Supabase Auth configurar Site URL a https://vivero-dulcinea.netlify.app y permitir exactamente https://vivero-dulcinea.netlify.app/recuperar como redirect URL. Verificar plantillas de invitación y recuperación conservan el enlace de confirmación.
3. Para Android, establecer AUTH_REDIRECT_URL=https://vivero-dulcinea.netlify.app/recuperar en local.properties ignorado. No contiene un secreto. Mantener el destino de pruebas separado cuando se pruebe contra otro Supabase.
4. Crear cuenta Resend y verificar un dominio/remitente propio. Configurar solo en secretos de Edge Functions: APP_ORIGIN=https://vivero-dulcinea.netlify.app, RESEND_API_KEY y NEWSLETTER_FROM con el remitente verificado. APP_ORIGIN no lleva barra final. Las claves administrativas nunca van en Netlify VITE_* ni en Android.
5. Configurar SMTP de Supabase Auth para entrega real y límites adecuados de invitaciones/recuperación. La integración Resend del boletín no cambia por sí misma el SMTP de Auth.
6. Tras autorización separada, aplicar migraciones y publicar newsletter e invite-staff. newsletter valida JWT y MANAGE_SETTINGS internamente para campañas aunque su endpoint admite altas públicas.
7. Publicar la web desde su repositorio ViveroWeb. Este cambio local no actualiza Netlify por sí solo.

## Validación y límites
- Web: lint, build y pruebas unitarias; pruebas específicas de recomendaciones y contratos de cortes.
- Android: assembleDebug y testDebugUnitTest, usando JDK 21 existente en caché de Gradle.
- Migraciones: verificación estática. Nuevas pruebas presential_release, cashier_closings_refunds, ampliación de web_orders y corrección de gradual_inventory_trigger.
- Pendiente: pgTAP, pruebas de concurrencia real de caja, prueba de navegador autenticado y envío/recepción real de correo. No se enviaron mensajes ni se tocó producción.
- Primer corte incluye todos los pagos históricos del cajero sin corte. Revisar ese alcance antes del primer cierre.
- Devolución básica total: requiere OPERATE_CASHIER y MANAGE_DISCOUNTS, registra dinero devuelto presencialmente. No hay devolución parcial ni movimiento bancario automático. Reponer stock solo devuelve unidades descontadas originalmente.
- Cortes y devoluciones se administran en Web; no se añadió pantalla nativa Android para estas operaciones.
- Si una respuesta de devolución se pierde, consultar el ticket antes de repetir una entrega de dinero. El servidor impide una segunda devolución de la misma venta.
- Activar inventario solo tras captura/conteo inicial completo. No se descuentan ventas históricas.
- Resend acepta la solicitud de correo; esto no demuestra llegada a bandeja. Revisar entregas/rebotes en su panel. Ante límite de envío, esperar y reintentar el lote. Envíos inciertos mayores a 23 horas requieren conciliación manual para evitar duplicados fuera de la ventana de idempotencia.
- La administración editorial permanece deshabilitada; no representa una función operativa de esta entrega.
- Pasarela de pagos fuera de alcance por decisión de Pedro.

## Referencias de configuración
- https://supabase.com/docs/guides/auth/redirect-urls
- https://supabase.com/docs/reference/javascript/auth-admin-inviteuserbyemail
- https://resend.com/docs/api-reference/emails/send-email
- https://resend.com/docs/dashboard/emails/idempotency-keys

## Trabajo preservado
El repositorio Android ya tenía cambios de interfaz, catálogo, escáner, QR y compras a proveedores, además de archivos IDE. Se conservaron. Los ajustes propios que se solapan son app/build.gradle.kts, ReportsScreen.kt y verify_migrations.ps1. No se hizo git add, commit, push ni despliegue.

## Resultado de comprobaciones locales
- ViveroWeb: npm run lint, npm test (328 pruebas en 22 archivos), npm run build: correctos.
- ViveroApp: .\\gradlew.bat assembleDebug testDebugUnitTest: BUILD SUCCESSFUL con JAVA_HOME al JDK 21 existente.
- powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase/tests/verify_migrations.ps1: 133 comprobaciones correctas.
- node supabase/tests/edge-functions.test.cjs C:/Users/GAMER/AndroidStudioProjects/ViveroWeb/node_modules/typescript: 7 comprobaciones de límites HTTP/autorización sin red. No reemplazan deno check ni pruebas de integración con Supabase/Resend.
- git diff --check sin cambiar la configuración de saltos de línea: correcto en ambos repositorios.
