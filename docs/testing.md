# Pruebas

Las pruebas locales cubren el dashboard, restauración de sesión, acceso demo, matriz de permisos, búsqueda/filtros del catálogo, normalización de códigos, supresión de lecturas duplicadas y reglas monetarias/del carrito mediante repositorios falsos y modelos de dominio.

Ejecutar desde la raíz:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Pruebas de seguridad de Supabase

`supabase/tests/database/security_rls.test.sql` contiene 25 pruebas pgTAP para capacidades por rol, aislamiento entre sucursales, protección del perfil, bandeja de Caja, idempotencia, rechazo atómico de tickets y jerarquía entre `OWNER` y `ADMIN`.

Las pruebas están creadas pero pendientes de ejecución porque requieren un entorno PostgreSQL/Supabase local compatible. Deben aprobarse con `supabase test db` en un entorno de pruebas antes de promover cualquier estructura o versión a producción. No se instalará Docker ni PostgreSQL como parte de esta fase.

La verificación estática complementaria puede ejecutarse sin base de datos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1
```

La cámara y el reconocimiento visual requieren una prueba instrumental en un dispositivo físico: conceder y denegar permiso, abrir configuración tras una denegación permanente, leer cada formato admitido, validar poca luz y confirmar que una lectura repetida no dispara dos consultas. La entrada manual permite validar el flujo de producto encontrado/no encontrado en emulador.

Para probar el carrito manualmente: entra en demostración, abre Catálogo, agrega varias plantas, cambia cantidades, cierra y vuelve a abrir la app para comprobar el borrador Room, asocia el cliente demo y envía la orden. Confirma que aparece un folio y que un segundo toque no genera otra venta.

En fases posteriores se añadirán pruebas de puntos, transiciones de cobro, repositorios remotos y UI crítica. Antes de cerrar cada fase se ejecutarán compilación y pruebas, y se revisarán advertencias relevantes.
