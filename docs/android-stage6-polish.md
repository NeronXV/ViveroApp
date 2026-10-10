# ViveroApp — pulido visual y accesibilidad de etapa 6

Validación local del 9 de octubre de 2026. Esta entrega se detiene para revisión visual; no inicia integración completa ni aceptación física.

## Cambios de esta pasada

Solo tres archivos de producción:

- `app/src/main/java/com/intutec/viveroapp/ui/theme/Theme.kt`: controla el contraste de los iconos de estado y navegación según la luminosidad de los colores del tema efectivo. Se verifica tema claro y oscuro, sin cambiar el tema predeterminado.
- `app/src/main/java/com/intutec/viveroapp/feature/catalog/presentation/BackendCatalogScreen.kt`: fotografía con relación 1.35 frente a 1.15, espaciado de 4 dp frente a 6 y padding vertical de 10 dp frente a 12. Conserva fotos, nombre, precio, Agregar y Ver detalle con altura mínima de 48 dp. Ajusta el espacio disponible al teclado; la lista sigue desplazable.
- `app/src/main/java/com/intutec/viveroapp/feature/cart/presentation/BackendCartScreen.kt`: padding de tarjeta de 12 dp, separación de 8 dp y foto de 60 dp. Cantidad y subtotal pueden pasar a otra línea. Aumentar, disminuir y quitar conservan al menos 48 × 48 dp. Diálogo de cotización con lista desplazable de hasta 280 dp, total separado de la lista, título y aviso compactos; mantiene precios confirmados, sucursal, advertencia de cambio de importe y ambas acciones. El aviso conserva su anuncio accesible mediante live region.

Archivos nuevos:

- `app/src/androidTest/java/com/intutec/viveroapp/feature/sales/BackendSalesPolishTest.kt`: seis escenarios instrumentados de presentación y accesibilidad.
- Este informe.

No se modificaron ViewModels, backend, API, Room, inventario, pagos, capacidades, sucursales ni mecanismos de recuperación. Los bloques de éxito y recuperación del carrito permanecen exactamente iguales al inicio de esta pasada; solo se corrige el contraste del sistema que los rodea.

## Pruebas nuevas ejecutadas

Comandos Gradle ejecutados con el JDK 21 local existente:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
.\gradlew.bat assembleDebugAndroidTest lintDebug
```

Se repitieron las validaciones pertinentes después de los ajustes. Compilaciones correctas; 371 pruebas unitarias: 370 correctas, cero fallos/errores y una prueba HTTP opcional omitida por falta de fixture aislado. Lint final: cero errores, 34 advertencias; ninguna señala los cuatro archivos Kotlin de esta pasada. No se corrigieron advertencias ajenas a este alcance.

Las pruebas instrumentadas se ejecutaron mediante `adb -s emulator-5560 shell am instrument -w -e class ... com.intutec.viveroapp.test/androidx.test.runner.AndroidJUnitRunner`, seleccionando `BackendSalesUiTest`, `BackendSalesPolishTest` y `BackendSalesPersistenceTest` en los tamaños de teléfono y tablet. En pantalla pequeña se seleccionaron las seis nuevas y la regresión de confirmación/precio cambiado.

| Configuración final | Resultado |
| --- | --- |
| Teléfono: 1080 × 2400, densidad 420, texto 100 % | 13 correctas |
| Tablet: 1600 × 2560, densidad 320, texto 130 % | 13 correctas |
| Pantalla pequeña: 720 × 1280, densidad 360 (320 dp de ancho), texto 150 % | 7 correctas |

Son 13 casos únicos en el conjunto completo; las repeticiones por tamaño no se cuentan como casos adicionales. Todas las ejecuciones finales concluyeron sin fallos.

Las seis pruebas nuevas comprueban:

1. Iconos oscuros en tema claro y claros en tema oscuro, leyendo el estado real del controlador de la ventana.
2. Áreas de al menos 48 × 48 dp para aumentar, disminuir, quitar y enviar en el carrito.
3. Pedido de 20 productos: todos pueden leerse desplazando la lista; total y acciones permanecen disponibles; navegar por la lista no envía ni cancela.
4. Código manual con teclado real visible: Buscar y Volver se ven, y Buscar entrega el código correcto.
5. Precio cambiado: producto, cantidades, precios, advertencia, total y confirmación visibles, también con texto ampliado.
6. Búsqueda con teclado real: se puede desplazar hasta Agregar y Ver detalle; ambos mantienen tamaño táctil, y Agregar emite una única acción.

Se repitieron las siete regresiones existentes de etapa 6: catálogo/fotos/entrada manual, carrito y eliminación confirmada, resultado incierto que bloquea otro envío, éxito con folio y total originales, confirmación bloqueada mientras trabaja, error y alternativa manual de cámara, y reapertura de una base Room sintética que conserva borrador y clave original. No se alteró la base operativa.

La inspección visual detectó que la primera versión del diálogo ocultaba el producto cuando el aviso de precio crecía con texto al 150 %. Se compactó ese aviso y se añadió la prueba específica antes de repetir las ejecuciones finales. También se corrigió un selector ambiguo en la nueva prueba de teclado: ahora distingue la lista vertical de la fila de categorías. Los registros finales están en `tmp/android-stage6-polish/*-final-tests.txt`.

## Capturas actualizadas

Capturas directas de la pantalla del emulador API 35, renderizando los componentes de producción con estados sintéticos y recursos de ensayo. No son operaciones contra el VPS ni comprobación de fotografías del catálogo remoto. Se revisaron visualmente teléfono, tablet, tema oscuro y pantalla pequeña con teclado. Los archivos originales no fueron retocados.

- [Catálogo de teléfono](../tmp/android-stage6-polish/captures/phone/vender-catalogo.png)
- [Carrito de teléfono](../tmp/android-stage6-polish/captures/phone/carrito-accesible.png)
- [Confirmación compacta y precio cambiado](../tmp/android-stage6-polish/captures/phone/confirmacion-precio-accesible.png)
- [Confirmación con texto al 150 %](../tmp/android-stage6-polish/captures/small/confirmacion-precio-accesible.png)
- [Código manual con teclado en pantalla pequeña](../tmp/android-stage6-polish/captures/small/codigo-con-teclado.png)
- [Agregar con teclado en pantalla pequeña](../tmp/android-stage6-polish/captures/small/catalogo-agregar-con-teclado.png)
- [Pedido largo y último producto](../tmp/android-stage6-polish/captures/small/confirmacion-ultimo-producto.png)
- [Tema oscuro en tablet](../tmp/android-stage6-polish/captures/tablet/catalogo-oscuro.png)
- [Confirmación de venta preservada](../tmp/android-stage6-polish/captures/phone/venta-confirmada.png)
- [Aviso de recuperación preservado](../tmp/android-stage6-polish/captures/phone/envio-por-comprobar.png)

Galerías completas en `tmp/android-stage6-polish/captures/phone`, `small` y `tablet`. Las capturas históricas de `tmp/android-stage6/captures` siguen preservadas.

## Conservación y estado Git

Referencia inicial guardada en `tmp/android-stage6-polish/baseline`: diff, estado, copias de los tres archivos solapados y hashes de todos los archivos preexistentes sin seguimiento. La comparación final verificó 29 diffs preexistentes ajenos al pulido exactamente iguales y 24 archivos preexistentes sin seguimiento idénticos byte por byte. Los dos archivos de pantallas ya modificados conservan sus avances; el diff respecto a las copias iniciales contiene únicamente los ajustes visuales descritos. Se verificaron además los bloques de éxito y recuperación sin cambios.

`git diff --check` final correcto. Rama `main`, HEAD `a58fd614b28920d7b39fee7cef2b67f1c16b0531`, conservados. El árbol continúa con todos los cambios previos sin commit más los cinco archivos de esta pasada. No hubo git add, commit, push, despliegue ni cambios de producción. No se modificó ViveroWeb ni ViveroAppCliente.

Se instaló con `-r` exclusivamente en el emulador, después de comprobar coincidencia del certificado debug instalado y compilado: SHA-256 `54aae00dc34e2e48e287effbb513afb47e448e3e83470ccecb4b42851acf9cb6`. Sin desinstalación, cambio de firma, versión o configuración de destino. El APK histórico de etapa 6 permanece intacto; el APK de compilación actualizado está en `app/build/outputs/apk/debug/app-debug.apk` y no es una entrega definitiva.

El emulador se inició con `-read-only -no-snapshot-save`; al terminar se restauraron tamaño, densidad y texto 100 %, y se cerró solo `emulator-5560`. No se instaló en el teléfono físico conectado.

## Límites y revisión pendiente

- Pendiente revisión visual de Pedro/Toni antes de iniciar integración completa.
- Verificaciones de accesibilidad realizadas: semántica, tamaños táctiles, anuncios del aviso, contraste del sistema, texto ampliado, desplazamiento y teclado real. No se ejecutó una auditoría completa con Accessibility Scanner ni una sesión manual de TalkBack; deben revisarse con usuarios antes de aceptación física.
- Solo API 35 y los tamaños/escalas de la tabla fueron ejecutados. Otras versiones Android, escalas superiores al 150 %, orientación horizontal y combinaciones extremas de textos/importes no quedan certificadas por esta pasada.
- No se probó escaneo físico, aceptación en dispositivo económico ni integración real Android–API–Caja en esta tarea. Los resultados sintéticos y unitarios no sustituyen ese cierre.
- No se ejecutaron pruebas SQL o contra MariaDB/Supabase: no hubo cambios de contratos o base de datos. No se ejecutó toda la suite instrumentada de módulos ajenos al pulido.

No se declara cerrada la aceptación de etapa 6 ni iniciada la etapa posterior.
