# Pruebas

Las pruebas locales cubren el dashboard, restauración de sesión, acceso demo, matriz de permisos, búsqueda/filtros del catálogo, normalización de códigos, supresión de lecturas duplicadas y reglas monetarias/del carrito mediante repositorios falsos y modelos de dominio.

Ejecutar desde la raíz:

```powershell
.\gradlew.bat testDebugUnitTest
```

La cámara y el reconocimiento visual requieren una prueba instrumental en un dispositivo físico: conceder y denegar permiso, abrir configuración tras una denegación permanente, leer cada formato admitido, validar poca luz y confirmar que una lectura repetida no dispara dos consultas. La entrada manual permite validar el flujo de producto encontrado/no encontrado en emulador.

Para probar el carrito manualmente: entra en demostración, abre Catálogo, agrega varias plantas, cambia cantidades, cierra y vuelve a abrir la app para comprobar el borrador Room, asocia el cliente demo y envía la orden. Confirma que aparece un folio y que un segundo toque no genera otra venta.

En fases posteriores se añadirán pruebas de puntos, transiciones de cobro, repositorios remotos y UI crítica. Antes de cerrar cada fase se ejecutarán compilación y pruebas, y se revisarán advertencias relevantes.
