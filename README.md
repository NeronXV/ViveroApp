# Vivero

Aplicación Android para apoyar la operación de un vivero. El proyecto se desarrolla en fases pequeñas y verificables.

## Estado actual

| Módulo | Estado |
|---|---|
| Base MVVM, tema y navegación | Fase 1 implementada |
| Autenticación y roles | Integración Supabase real con fallback demo explícito |
| Catálogo | Integración Supabase real con búsqueda, filtros, detalle e imágenes; fallback demo explícito |
| Escáner inteligente | Fase 4 implementada con CameraX, ML Kit y entrada manual |
| Carrito y envío a caja | Outbox Room y sincronización idempotente mediante `submit_sale_to_cashier` |
| Caja y cobro | Integración real con claims, confirmación y recuperación; validación integral actual pendiente |
| Administración esencial | Contrato backend de presentación implementado; cliente Web pendiente |
| Inventario, clientes, promociones y reportes | Pendiente o parcial según capacidad |

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
5. En la pantalla de acceso pulsa **Explorar demostración** o configura Supabase para iniciar con una cuenta real.

También puedes compilar desde la terminal integrada:

```powershell
.\gradlew.bat assembleDebug
```

## Pruebas

Desde Android Studio, haz clic derecho en `app/src/test` y elige **Run Tests**. Desde terminal:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Acceso de demostración

Pulsa **Explorar demostración**. No requiere contraseña ni representa una cuenta real.

## Supabase

La URL pública y la clave publicable/`anon key` se configuran únicamente en `local.properties`, que está ignorado por Git. Consulta [docs/setup-supabase.md](docs/setup-supabase.md).

Se usa Supabase Kotlin 3.2.6 por compatibilidad binaria con Kotlin 2.2.10. Las ramas 3.7.x requieren Kotlin 2.4 y se evaluarán en una actualización futura del toolchain.

## Documentación

- [Arquitectura](docs/architecture.md)
- [Modelo de base de datos](docs/database.md)
- [Roles y permisos](docs/roles-and-permissions.md)
- [Pruebas](docs/testing.md)
- [Preparación de Supabase](docs/setup-supabase.md)

## Catálogo

Incluye búsqueda por nombre, nombre científico, código interno y código de barras; filtros por categoría y disponibilidad; precios en centavos y detalle de cuidados. Las sesiones remotas consumen Supabase y el modo de exploración mantiene un repositorio demostrativo claramente separado.

## Escáner

Reconoce QR, EAN-13, EAN-8 y Code 128 mediante CameraX y ML Kit. Solicita la cámara con una explicación de privacidad, evita lecturas repetidas y ofrece captura manual para emuladores o equipos sin cámara. Puedes probar con `750100000001`, `750100000014`, `750100000022` o con los códigos internos `PL-001`, `PL-014` y `PL-022`.

## Carrito y envío a Caja

Agrega plantas desde catálogo, detalle o escáner. El borrador se conserva en Room aunque cierres la app. En sesión remota, el envío genera un UUID idempotente, conserva un outbox local y llama a `submit_sale_to_cashier`; el backend recalcula precios y es la autoridad. El modo demo no representa persistencia ni cobro real.

Las doce migraciones, 251 aserciones pgTAP declaradas y los recorridos integrales deben repetirse desde cero cuando vuelva a estar disponible la infraestructura local. Hasta entonces Caja y Administración no se consideran desplegables.
