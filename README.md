# Vivero Dulcinea

## Estado actual — 3 de octubre de 2026

El destino oficial es Backend API + MariaDB + Docker. La Web operativa está en
https://viverodulcinea.bajastack.network y la app habitual usa la misma API y cuenta.
La aplicación Android tiene versión 1.0.6-vps (código 7), conserva sus datos en
Room 6 y ofrece inicio premium, catálogo, carrito, envío a caja, cobro, historial,
comprobantes e inventario. Desde el perfil permite cambiar la contraseña personal
con mínimo de 6 caracteres y cierra las sesiones al confirmarlo.

La aceptación operativa completa sigue pendiente. Supabase se conserva para
conciliación histórica; no es el destino del recorrido operativo activo. AppCliente
sigue fuera de este corte. No hay entrega por correo ni copia cifrada externa
configuradas. Las cinco cuentas importadas requieren activación de acceso.
Caddy todavía pertenece al proyecto Compose de Vivero; antes de incorporar otra
aplicación se debe separar el proxy según la guía operativa, preservando HTTPS.

### Fuentes y configuración

- `backend/`: API, contratos, scripts de importación y pruebas. Node 24 y lockfile npm.
- `database/mysql/`: esquema, semillas sintéticas y migraciones hasta 029.
- `infra/docker/`: Compose, Caddy y respaldo/restauración; MariaDB no publica puertos.
- `app/`: cliente Android; URLs públicas en `local.properties`, ignorado por Git.
- `.env.example` y `infra/docker/vps.env.example`: marcadores sin credenciales.

En Android configurar `BACKEND_API_URL` y `BACKEND_WEB_URL` para debug;
para release, `RELEASE_BACKEND_API_URL` y `RELEASE_BACKEND_WEB_URL` y la firma
existente en `key.properties`. Nunca incluir claves o contraseñas en el APK.
Los archivos reales de entorno, APK, respaldos y exportaciones permanecen fuera de Git.

### Guías de trabajo y evidencia

- [Arranque local con MariaDB](docs/backend-fresh-install.md).
- [Arquitectura oficial](docs/backend-api-mariadb.md) y [mapa de migración](docs/supabase-migration-map.md).
- [Operación del VPS para Pedro y Toni](docs/vps-multiproject-operations.md).
- [Corte operativo y pendientes](docs/vps-operational-cutover.md).
- [Dominio definitivo y recuperación de intentos del dominio anterior](docs/vps-canonical-domain.md).
- [Diseño Android premium](docs/android-premium-vps.md).
- [Cambio personal de contraseña y versión 1.0.6](docs/backend-personal-password.md).
- [Aceptación controlada Web/Android](docs/vps-acceptance-web-android.md).

Validaciones locales de esta entrega: `assembleDebug testDebugUnitTest` correcto,
355 pruebas Android aprobadas y una HTTP opcional omitida; backend, 87 pruebas
aprobadas y `npm run check`; verificación SQLite de migraciones Room 3→4→5→6
correcta. La Web pasó lint, build y 471 pruebas. La prueba HTTP/SQL aislada de
contraseña de 6 caracteres pasó antes de actualizar la API. Los informes enlazados
distinguen despliegues anteriores, ensayos técnicos y aceptación humana pendiente.
No se reejecutaron pruebas instrumentadas, PostgreSQL ni pruebas completas de
MariaDB en este checkpoint de Git.

## Referencia de la implementación anterior

La documentación siguiente describe el recorrido original con Supabase y sus
pruebas históricas. No implica que esas pantallas estén activas en el grafo API.

Aplicación Android para apoyar la operación de un vivero. El proyecto se desarrolla en fases pequeñas y verificables.

## Estado anterior con Supabase

| Módulo | Estado |
|---|---|
| Base MVVM, tema y navegación | Fase 1 implementada |
| Autenticación y roles | Integración Supabase real |
| Catálogo | Integración Supabase real con búsqueda, filtros, detalle, imágenes y proyección de existencia por sucursal |
| Escáner inteligente | Fase 4 implementada con CameraX, ML Kit y entrada manual |
| Carrito y envío a caja | Outbox Room y sincronización idempotente mediante `submit_sale_to_cashier` |
| Mis comandas | Integración real local con historial propio, estado de cobro y paginación estable |
| Caja y cobro | Integración real con claims, confirmación y recuperación; validación integral actual pendiente |
| Administración esencial | Contratos backend implementados; integración Web parcial |
| Inventario, clientes, promociones y reportes | Piloto de inventario alineado en Android y Web para recepción, conteo e historial; validación integral pendiente |

## Requisitos

- Android Studio compatible con AGP 9.2.
- JDK 17 o superior (Android Studio incluye uno compatible).
- Android SDK 37 instalado (el `targetSdk` permanece en 36).
- Emulador o dispositivo con Android 7.0 (API 24) o superior.

## Abrir y ejecutar

1. En Android Studio, selecciona **Open** y abre esta carpeta.
2. Espera a que termine **Gradle Sync**.
3. Selecciona un emulador o dispositivo.
4. Pulsa **Run 'app'**.
5. Configura Supabase e inicia sesión con una cuenta real.

También puedes compilar desde la terminal integrada:

```powershell
.\gradlew.bat assembleDebug
```

## Pruebas

Desde Android Studio, haz clic derecho en `app/src/test` y elige **Run Tests**. Desde terminal:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Supabase

La URL pública y la clave publicable/`anon key` se configuran únicamente en `local.properties`, que está ignorado por Git. Consulta [docs/setup-supabase.md](docs/setup-supabase.md).

Se usa Supabase Kotlin 3.2.6 por compatibilidad binaria con Kotlin 2.2.10. Las ramas 3.7.x requieren Kotlin 2.4 y se evaluarán en una actualización futura del toolchain.

## Documentación

- [Arquitectura](docs/architecture.md)
- [Modelo de base de datos](docs/database.md)
- [Roles y permisos](docs/roles-and-permissions.md)
- [Pruebas](docs/testing.md)
- [Preparación de la entrega Android](docs/production-release.md)
- [Preparación de Supabase](docs/setup-supabase.md)

## Catálogo

Incluye búsqueda por nombre, nombre científico, código interno y código de barras; filtros por categoría y disponibilidad; precios en centavos y detalle de cuidados. Las sesiones autenticadas consumen exclusivamente Supabase.

## Escáner

Reconoce QR, EAN-13, EAN-8 y Code 128 mediante CameraX y ML Kit. Solicita la cámara con una explicación de privacidad, evita lecturas repetidas y ofrece captura manual para emuladores o equipos sin cámara. Puedes probar con `750100000001`, `750100000014`, `750100000022` o con los códigos internos `PL-001`, `PL-014` y `PL-022`.

## Carrito y envío a Caja

Agrega plantas desde catálogo, detalle o escáner. El borrador se conserva en Room aunque cierres la app. El envío genera un UUID idempotente, conserva un outbox local y llama a `submit_sale_to_cashier`; el backend recalcula precios y es la autoridad.

## Inventario piloto

Las cuentas con `MANAGE_INVENTORY` y sucursal activa disponen de un tablero operativo. La gerente puede registrar recepciones y conciliar un conteo físico con motivo; PostgreSQL genera movimientos auditables y mantiene el saldo. Los reintentos conservan una clave idempotente durante el intento abierto. No existe edición directa del saldo.

Las 35 migraciones se aplicaron desde cero en una base local aislada y pasaron las 499 aserciones pgTAP de las 18 suites, además de cinco escenarios concurrentes de caja con verificación de inventario. Antes de producción siguen pendientes los recorridos completos de Android/Web y la configuración del destino y firma de release. Consulta la [evidencia de base de datos](docs/database-validation.md).
