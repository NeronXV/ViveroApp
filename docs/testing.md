# Pruebas

Las pruebas locales cubren el dashboard, restauración e inicio de sesión, matriz de permisos, búsqueda/filtros del catálogo, normalización de códigos, supresión de lecturas duplicadas y reglas monetarias/del carrito mediante repositorios falsos y modelos de dominio.

Ejecutar desde la raíz:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Pruebas de seguridad de Supabase

Las 18 suites de base de datos declaran 499 aserciones pgTAP. Cubren seguridad/RLS, pagos, contratos de Caja y Administración, RPC públicos, imágenes, roles, inventario, historial de comandas, promociones, pedidos web, escaneo, compras a proveedores, checkout presencial, cortes y devoluciones.

El 21 de septiembre de 2026 se aplicaron las 35 migraciones desde cero y pasaron las 499 aserciones en PostgreSQL 17.6 de Supabase, con las migraciones oficiales de Auth y Storage. También pasaron cinco escenarios de concurrencia real de caja, incluyendo comprobación de inventario. La CLI Windows quedó bloqueada por Control de aplicaciones, por lo que se utilizó directamente la imagen oficial de `pg_prove`; no se modificó esa política. Esto valida la base de datos, no los servicios HTTP, el correo ni los recorridos de navegador y Android, que siguen pendientes.

Con Docker Desktop iniciado y las imágenes indicadas en el script ya descargadas, repetir desde PowerShell 7:

```powershell
pwsh -NoProfile -File supabase\tests\run_docker_database_tests.ps1
```

El script exige el motor local `desktop-linux`, crea una red interna sin puertos publicados y una base nueva, ejecuta las migraciones y pruebas y detiene esa base al terminar. Conserva su contenedor, volumen y logs bajo `tmp/database-validation-*` para inspección; no modifica volúmenes anteriores ni consulta proyectos remotos. `-KeepRunning` deja la base de prueba encendida. No equivale a iniciar el stack completo de Supabase. Ver [evidencia y límites](database-validation.md).

El piloto Android de inventario tiene pruebas unitarias para carga y filtro, recepción, validación de conteo y reutilización de la clave idempotente al reintentar. El módulo de reportes Android cuenta con pruebas unitarias para el parsing de los contratos RPC, validación de rangos de fecha y acceso por permisos.

Mis comandas cuenta con pruebas unitarias de estructura JSON estricta, importes, estados, fechas, orden, paginación, cursor, cancelación y parámetros enviados al RPC. La ejecución pgTAP real de `get_my_recent_sales` pasó junto con las demás pruebas de base.

La prueba manual pendiente debe cubrir gerente con sucursal activa, recepción duplicada por reconexión, conteo al alza y a la baja, historial y rechazo por rol o sucursal. Para reportes, verificar la visibilidad según el permiso VIEW_REPORTS y la consistencia de los totales contra la operación real.

La verificación estática complementaria puede ejecutarse sin base de datos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1
```

La cámara y el reconocimiento visual requieren una prueba instrumental en un dispositivo físico: conceder y denegar permiso, abrir configuración tras una denegación permanente, leer cada formato admitido, validar poca luz y confirmar que una lectura repetida no dispara dos consultas. La entrada manual permite validar el flujo de producto encontrado/no encontrado en emulador.

Para probar el carrito manualmente: inicia sesión con una cuenta `SALES`, abre Catálogo, agrega varias plantas, cambia cantidades, cierra y vuelve a abrir la app para comprobar el borrador Room, asocia un cliente y envía la orden. Confirma que aparece un folio y que un segundo toque no genera otra venta.

En fases posteriores se añadirán pruebas de puntos, transiciones de cobro, repositorios remotos y UI crítica. Antes de cerrar cada fase se ejecutarán compilación y pruebas, y se revisarán advertencias relevantes.
