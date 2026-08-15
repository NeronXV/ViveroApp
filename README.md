# Vivero

Aplicación Android para apoyar la operación de un vivero. El proyecto se desarrolla en fases pequeñas y verificables.

## Estado actual

| Módulo | Estado |
|---|---|
| Base MVVM, tema y navegación | Fase 1 implementada |
| Autenticación y roles | Fase 2 implementada; conexión real se activa con claves locales |
| Catálogo | Fase 3 implementada con datos demo, búsqueda, filtros y detalle |
| Escáner inteligente | Fase 4 implementada con CameraX, ML Kit y entrada manual |
| Carrito y envío a caja | Fase 5 implementada con borrador local Room y ticket pendiente de sincronización |
| Caja, inventario, clientes y promociones | Pendiente |

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

## Catálogo de demostración

Incluye búsqueda por nombre, nombre científico, código interno y código de barras; filtros por categoría y disponibilidad; precios en centavos; promociones y detalle de cuidados. El repositorio actual es simulado y será sustituido por fuentes Room/Supabase conservando el mismo contrato.

## Escáner

Reconoce QR, EAN-13, EAN-8 y Code 128 mediante CameraX y ML Kit. Solicita la cámara con una explicación de privacidad, evita lecturas repetidas y ofrece captura manual para emuladores o equipos sin cámara. Puedes probar con `750100000001`, `750100000014`, `750100000022` o con los códigos internos `PL-001`, `PL-014` y `PL-022`.

## Carrito de demostración

Agrega plantas desde catálogo, detalle o escáner. El borrador se conserva en Room aunque cierres la app. Permite cantidades limitadas por existencia, eliminación, cliente demo opcional, subtotal, descuentos, total, cancelación y envío a caja. El envío genera un UUID y un folio comercial independiente; en modo demo queda marcado como pendiente de sincronización y no representa un cobro.
