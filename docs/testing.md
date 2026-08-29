# Pruebas

Las pruebas locales cubren el dashboard, restauración de sesión, acceso demo, matriz de permisos, búsqueda/filtros del catálogo, normalización de códigos, supresión de lecturas duplicadas y reglas monetarias/del carrito mediante repositorios falsos y modelos de dominio.

Ejecutar desde la raíz:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Pruebas de seguridad de Supabase

Las seis suites de base de datos declaran 251 aserciones pgTAP: 66 de seguridad/RLS, 51 de pagos, 60 del contrato Web de Caja, 20 de RPC públicos, 18 de imágenes públicas y 36 del contrato Web de Administración.

Las pruebas están creadas, pero su repetición actual está pendiente porque requiere un entorno PostgreSQL/Supabase local compatible. También permanecen pendientes la aplicación de las doce migraciones desde cero y los recorridos integrales de navegador. Deben aprobarse antes de considerar Caja o Administración desplegables; el resultado histórico 215/215 no sustituye esta ejecución actual.

La verificación estática complementaria puede ejecutarse sin base de datos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1
```

La cámara y el reconocimiento visual requieren una prueba instrumental en un dispositivo físico: conceder y denegar permiso, abrir configuración tras una denegación permanente, leer cada formato admitido, validar poca luz y confirmar que una lectura repetida no dispara dos consultas. La entrada manual permite validar el flujo de producto encontrado/no encontrado en emulador.

Para probar el carrito manualmente: entra en demostración, abre Catálogo, agrega varias plantas, cambia cantidades, cierra y vuelve a abrir la app para comprobar el borrador Room, asocia el cliente demo y envía la orden. Confirma que aparece un folio y que un segundo toque no genera otra venta.

En fases posteriores se añadirán pruebas de puntos, transiciones de cobro, repositorios remotos y UI crítica. Antes de cerrar cada fase se ejecutarán compilación y pruebas, y se revisarán advertencias relevantes.
