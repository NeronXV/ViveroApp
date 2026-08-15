# Changelog

## 0.5.0 - 2026-08-08

### Añadido

- Carrito persistente con Room 2.8.4 y acceso protegido por rol.
- Alta desde catálogo, detalle y escáner; cantidades limitadas por existencia y eliminación de partidas.
- Cálculos monetarios en centavos para subtotal, descuentos autorizados y total.
- Asociación opcional de cliente demo preparada para el módulo de clientes.
- Guardado de borrador, cancelación confirmada y envío a caja protegido contra doble toque.
- Ticket con UUID interno, folio comercial, estados auditables y marca de sincronización pendiente.
- Migración Supabase de ventas, partidas, historial, RLS y función idempotente de envío.
- Pruebas de cálculos, cantidades, stock y casos de uso del carrito.

## 0.4.1 - 2026-08-08

### Cambiado

- Identidad oficial de Vivero Dulcinea integrada en splash, acceso, recuperación e inicio.
- Nuevo sistema cromático esmeralda, jade, ámbar y marfil inspirado en la experiencia web.
- Jerarquía tipográfica editorial, esquinas más suaves y componentes de marca reutilizables.
- Catálogo alineado visualmente con “Nuestra colección” y nombre público actualizado.

## 0.4.0 - 2026-08-08

### Añadido

- Escáner premium con CameraX 1.6.1 y ML Kit Barcode Scanning 17.3.0.
- Lectura limitada a QR, EAN-13, EAN-8 y Code 128 con análisis del último fotograma.
- Flujo de permiso de cámara con explicación de privacidad, acceso a configuración y alternativa manual.
- Búsqueda local por código y respaldo en Supabase cuando está configurado.
- Resultado con fotografía, precio efectivo, existencia, promoción y acceso al detalle.
- Supresión temporal de lecturas duplicadas y reinicio explícito para escanear otra unidad.
- Pruebas unitarias del control de duplicados y normalización de códigos.

## 0.3.0 - 2026-08-08

### Añadido

- Catálogo premium adaptable con fotografías demo optimizadas.
- Búsqueda por nombre, nombre científico, código interno y código de barras.
- Filtros por categoría y disponibilidad con estados vacío/error reintentables.
- Tarjetas con stock, promociones, precio efectivo y precio original.
- Detalle del producto con descripción, existencia y guía de cuidados.
- Modelos de dominio, contrato de repositorio y casos de uso del catálogo.
- Migración PostgreSQL para categorías, productos, imágenes, índices y RLS.
- Pruebas unitarias para búsqueda, categorías y disponibilidad.

## 0.2.0 - 2026-08-08

### Añadido

- Supabase Auth/PostgREST 3.2.6 con configuración local segura y motor Ktor OkHttp; es la línea compatible con Kotlin 2.2 del proyecto.
- Restauración, inicio y cierre de sesión, recuperación de contraseña y modo demo separado.
- Perfiles, sesiones, roles y matriz central de permisos.
- Navegación protegida y dashboard adaptado al rol.
- Primera migración PostgreSQL para roles, sucursales, perfiles, asignaciones, índices y RLS.
- Pantalla de perfil funcional y pruebas de autenticación/permisos.
- Sistema visual premium responsivo con identidad botánica, panel editorial y componentes de marca.

## 0.1.0 - 2026-08-08

### Añadido

- Base MVVM por funcionalidades con contratos, caso de uso y repositorio simulado.
- Hilt, StateFlow y estados de carga, éxito, vacío y error.
- Navegación Compose tipada: splash, acceso simulado, inicio, catálogo y perfil.
- Tema Material 3 verde/tierra con preparación para modo oscuro.
- Dashboard adaptable a teléfonos y tablets con datos de demostración.
- Pruebas unitarias iniciales y documentación del proyecto.

### Compatibilidad

- Hilt 2.59.2 se eligió porque es la primera línea estable con soporte nativo para AGP 9; Hilt 2.57.1 falla con la nueva DSL de AGP 9.2.
- KSP 2.3.10 evita el uso de `kotlin.sourceSets` incompatible con el Kotlin integrado de AGP 9.
- `compileSdk` sube de 36.1 a 37 porque Core 1.19 y Lifecycle 2.11 ya presentes lo requieren; `targetSdk` permanece en 36.
