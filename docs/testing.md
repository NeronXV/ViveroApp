# Pruebas

Las pruebas locales cubren el dashboard, restauración e inicio de sesión, matriz de permisos, búsqueda/filtros del catálogo, normalización de códigos, supresión de lecturas duplicadas y reglas monetarias/del carrito mediante repositorios falsos y modelos de dominio.

Ejecutar desde la raíz:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Pruebas de seguridad de Supabase

Las catorce suites de base de datos declaran 420 aserciones pgTAP: 66 de seguridad/RLS, 51 de pagos, 60 del contrato Web de Caja, 20 de RPC públicos, 18 de imágenes públicas, 36 del contrato Web de Administración, 36 de administración de roles, 23 del endurecimiento de inventario, clientes, promociones y reportes, 12 de la proyección de existencia del catálogo por sucursal, 20 del piloto de inventario, 23 del historial propio de comandas, 14 del descuento gradual de inventario, 20 de promociones de catálogo y 21 de pedidos web reales.

Las pruebas están creadas, pero su ejecución actual está pendiente porque requiere un entorno PostgreSQL/Supabase local compatible. También permanecen pendientes la aplicación de las veintinueve migraciones desde cero y los recorridos integrales de navegador y Android. Deben aprobarse antes de considerar Caja, Inventario, Administración, Mis comandas, Pedidos Web o los módulos MVP nuevos desplegables; las pruebas unitarias de cliente no sustituyen esta ejecución de base de datos.

El piloto Android de inventario tiene pruebas unitarias para carga y filtro, recepción, validación de conteo y reutilización de la clave idempotente al reintentar. El módulo de reportes Android cuenta con pruebas unitarias para el parsing de los contratos RPC, validación de rangos de fecha y acceso por permisos.

Mis comandas cuenta con pruebas unitarias de estructura JSON estricta, importes, estados, fechas, orden, paginación, cursor, cancelación y parámetros enviados al RPC. La ejecución pgTAP real de `get_my_recent_sales` continúa pendiente junto con las demás pruebas de base.

La prueba manual pendiente debe cubrir gerente con sucursal activa, recepción duplicada por reconexión, conteo al alza y a la baja, historial y rechazo por rol o sucursal. Para reportes, verificar la visibilidad según el permiso VIEW_REPORTS y la consistencia de los totales contra la operación real.

La verificación estática complementaria puede ejecutarse sin base de datos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1
```

La cámara y el reconocimiento visual requieren una prueba instrumental en un dispositivo físico: conceder y denegar permiso, abrir configuración tras una denegación permanente, leer cada formato admitido, validar poca luz y confirmar que una lectura repetida no dispara dos consultas. La entrada manual permite validar el flujo de producto encontrado/no encontrado en emulador.

Para probar el carrito manualmente: inicia sesión con una cuenta `SALES`, abre Catálogo, agrega varias plantas, cambia cantidades, cierra y vuelve a abrir la app para comprobar el borrador Room, asocia un cliente y envía la orden. Confirma que aparece un folio y que un segundo toque no genera otra venta.

En fases posteriores se añadirán pruebas de puntos, transiciones de cobro, repositorios remotos y UI crítica. Antes de cerrar cada fase se ejecutarán compilación y pruebas, y se revisarán advertencias relevantes.
