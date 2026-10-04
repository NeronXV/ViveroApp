# Aceptación Web y Android en el VPS

Estado del 3 de octubre de 2026, America/Mazatlan. La aceptación manual todavía
está pendiente. Supabase se conserva; R2 y correo siguen pendientes. No usar
el resultado automático como declaración de migración terminada.

## Fase 1: lectura y carrito contra Vivero operativo

APK: `tmp/tablet-test/Vivero-VPS-pruebas.apk`, nombre **Vivero VPS pruebas**,
paquete `com.intutec.viveroapp.vpsvalidation`, destino público
`https://bajastack.network`. SHA-256:
`070a4231110763ad7b57a2bbff2a3a9f0e949cea2cc15ecfadfb89da8706e5a0`.
La APK se instala junto a la app habitual; no la reemplaza ni convierte sus datos.

1. Copiar la APK a Descargas de la tablet y abrirla desde Archivos. Autorizar a
   esa aplicación la instalación cuando Android lo solicite. Conservar la app
   habitual y sus datos. Si aparece un error, registrar su texto antes de seguir.
2. Abrir **Vivero VPS pruebas** e iniciar sesión con una cuenta habilitada del VPS.
   La cuenta OWNER existente conserva su acceso. Las otras cinco cuentas aún
   requieren contraseña: el correo pendiente no permite recuperarlas todavía.
3. Comprobar rol y sucursal. Comparar con la misma cuenta en Web
   `https://bajastack.network/login`, sin enviar ventas ni cambiar inventario.
4. Abrir **Catálogo y consulta de códigos**. Revisar nombres, códigos y precios
   de dos productos en ambos consumidores. La ausencia de fotos es un pendiente
   conocido: Pedro decidió subirlas manualmente después.
5. Agregar un producto al carrito, dejar dos unidades y anotar producto,
   cantidad y total orientativo. No pulsar **Confirmar y enviar a caja**.
6. Cerrar por completo la APK y abrirla de nuevo. El proceso nuevo requiere
   iniciar sesión: es comportamiento vigente, no fallo de persistencia. Con
   la misma cuenta y sucursal, el carrito Room debe conservarse. Registrar
   cualquier pérdida o mezcla entre cuentas/sucursales; no borrar datos para
   ocultar un fallo. La cotización posterior es la autoridad del precio actual.

Registrar por paso: aprobado/falló, mensaje exacto, hora y versión/modelo Android.
No compartir contraseñas, tokens ni capturas con datos personales innecesarios.

## Fase 2: operaciones con datos aislados en el VPS

Proyecto Compose **`vivero-acceptance-20261003`**, configuración privada en
`/opt/vivero/maintenance/20261003-acceptance/`, también accesible por el enlace
de maintenance de `/srv/apps/vivero-dulcinea`. Usa las imágenes ya desplegadas
de API/Web y el esquema oficial: 55 tablas, 28 migraciones hasta 029.
La base se inició vacía con el seed de roles, sin importar datos reales.

Sus contenedores, volúmenes y redes tienen nombre propio. DB/API sin puertos
publicados; Web solo `127.0.0.1:38003` en el VPS. No conecta a `platform_proxy`,
no modifica DNS ni el Caddy operativo. HTTP loopback solo en este ensayo debug,
transportado entre computadora y VPS por SSH. API usa NODE_ENV=development
para permitir ese origen local; producción conserva HTTPS y su configuración.

Se crearon cuatro cuentas OWNER sintéticas y sucursales propias: AUTO, KOTLIN,
TABLET y WEB. Producto `ENSAYO-500`, **Planta de ensayo VPS**, precio 500 centavos
($5,00), diez unidades iniciales por sucursal; control de stock activado solo en
esas sucursales sintéticas. No hay datos de personas ni ventas operativas.

Las cuentas manuales están en `tmp/vps-acceptance/fixture.private.json`, ignorado
por Git: usar `accounts.tablet` para Android y `accounts.web` para Web. Sus
contraseñas no se incluyen en este documento. No iniciar sesión aquí con cuentas
reales. El fixture del cliente Kotlin se conserva en otro archivo privado.

### Acceso al ensayo

En esta computadora se abrió un túnel SSH con el usuario personal de Pedro.
Si se cierra, abrir en PowerShell y mantener esa consola activa:

```powershell
& 'C:\Windows\System32\OpenSSH\ssh.exe' -N -L 127.0.0.1:38003:127.0.0.1:38003 -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -i "$env:USERPROFILE\.ssh\vivero_vps" pedro@179.236.238.111
```

Web del ensayo en la computadora: `http://127.0.0.1:38003/login`.
Ese origen no es la Web operativa; sus cuentas/datos son distintos.

APK del ensayo: `tmp/vps-acceptance/Vivero-VPS-ensayo.apk`, nombre **Vivero VPS
ensayo**, paquete `com.intutec.viveroapp.vpsacceptance`. Firma debug verificada,
orígenes API/Web `http://127.0.0.1:38003`. SHA-256:
`0d0ba57ce2f28c129fe4fbd96b9c0b374ffe8aaeae82169f0c7c27a23f64fb27`.
Es otra aplicación independiente; no usar **Vivero VPS pruebas** para estas ventas.

Para la tablet, conectar USB, habilitar Depuración USB en Opciones de desarrollador
y aceptar la autorización de esta computadora. Si ese menú no existe, habilitarlo
desde Información del dispositivo pulsando siete veces Número de compilación;
los nombres pueden variar por fabricante. Después, desde esta computadora:

```powershell
& 'C:\Users\GAMER\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices
& 'C:\Users\GAMER\AppData\Local\Android\Sdk\platform-tools\adb.exe' reverse tcp:38003 tcp:38003
& 'C:\Users\GAMER\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r 'C:\Users\GAMER\AndroidStudioProjects\ViveroApp\tmp\vps-acceptance\Vivero-VPS-ensayo.apk'
```

Si hay varios dispositivos, añadir `-s SERIAL` del elegido. `adb reverse` depende
de USB y puede necesitar repetirse al reconectar. La tablet llega al ensayo mediante
USB → computadora → túnel SSH → VPS. No requiere publicar un puerto en Internet.
En esta sesión ADB no detectó dispositivos: instalación y reverse no se ejecutaron.

### Venta y cobro manuales: un recorrido por consumidor

1. Entrar con la cuenta sintética correspondiente. Confirmar `ENSAYO-TABLET` o
   `ENSAYO-WEB`, precio $5,00 y saldo inicial 10. Si no coincide, detener el recorrido.
2. Agregar dos unidades, comprobar borrador/persistencia y cotizar: total $10,00.
3. Confirmar **en el ensayo** el envío a caja. Anotar folio; comprobar una comanda
   en Caja. El stock debe seguir en 10 hasta el cobro.
4. Reservar/cobrar en efectivo, recibido $12,00. Esperar pago confirmado y cambio
   $2,00. No generar otra comanda ante una respuesta incierta: consultar resultado
   o reintentar el mismo intento con las acciones de recuperación existentes.
5. Abrir el comprobante: folio, dos unidades, debido $10,00, recibido $12,00,
   cambio $2,00. Confirmar que la comanda ya no está pendiente de cobro.
6. Consultar inventario e historial: saldo 8 y una salida SALE de −2 asociada a
   esa venta. Revisar el mismo comprobante otra vez: no debe descontar de nuevo.

Después de cada recorrido, conciliar SQL: exactamente una venta/pago de esa
sucursal y una salida de dos unidades. Conservar folios y evidencias del ensayo.
No reiniciar saldos ni borrar registros para repetir. Para otra vuelta se puede
crear otro fixture/sucursal aislados, sin sobreescribir lo probado.

## Evidencia ejecutada y fallos

- Automática HTTP a través del Caddy de pruebas: login/rol/sucursal, cotización,
  envío a caja, recuperación/repetición con la misma clave, reserva, rechazo de
  cobro insuficiente, cobro, comprobante, recuperación/repetición de pago y SQL.
  Resultado: 1000/1200/200 centavos; stock 10→8, una venta, un pago, una salida.
- Cliente Kotlin real mediante la prueba HTTP opcional: login, catálogo/código,
  recepción y conteo, envío, recuperación, caja, cobro, comprobante, cierre de
  intentos y logout. Un caso ejecutado, cero omitidos/fallos/errores. Sucursal
  KOTLIN terminó con una venta/pago y stock 8; recepción/conteo previos conciliados.
- Sucursales manuales TABLET/WEB siguen en 10, cero ventas/pagos al preparar el ensayo.
- APK del ensayo compilada, paquete/nombre/orígenes/firma verificados. Login Web
  del ensayo mostrado en navegador sin errores visibles. Esto no equivale al
  recorrido visual autenticado ni a validar Room en la tablet.
- Producción continúa saludable por HTTPS; sus conteos agregados coinciden con
  la inspección anterior: seis cuentas, dos sucursales, quince productos,
  catorce ventas/pagos, veinticinco saldos y treinta y ocho movimientos.

Fallo encontrado: sintaxis inválida en un bloque del Caddyfile **del ensayo**,
que provocó reinicios de esa Web. Se corrigió el bloque multilínea, se validó
Caddy y se reinició solo ese contenedor; health y pruebas posteriores pasaron.
No se detectó un fallo funcional de API/Kotlin en los recorridos automáticos.
Faltan las observaciones manuales de Web y tablet para detectar problemas visuales,
persistencia real, interacción y recuperación de red. No declarar aceptación completa.

Fuentes nuevas de esta preparación: `backend/scripts/vps-acceptance.mjs` y esta
guía; la prueba `BackendAndroidHttpIntegrationTest.kt` se amplió mínimamente para
permitir el túnel loopback 38003 solo con proyecto explícito y fixture etiquetado.
Mantiene el destino local 33003 y el producto anterior como valor predeterminado.
Las configuraciones temporales, APK, contraseñas y resultados privados están en
`tmp/` local y maintenance en VPS, fuera de Git. No hubo commit ni push en este bloque.
No cambió SQL, backend de producción ni lógica Android de producción; no se
repitieron pgTAP ni las suites completas anteriores. El ensayo permanece activo
para la aceptación manual; no se borraron volúmenes ni se alteró Supabase.
