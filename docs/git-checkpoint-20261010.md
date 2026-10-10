# Checkpoint Git del 10 de octubre de 2026

Este checkpoint organiza los avances locales en `main` para su sincronización con GitHub. No realiza despliegues, operaciones sobre bases de datos ni publicación de APK. Las guías anteriores describen sus respectivas sesiones; sus menciones a archivos sin commit son históricas.

## Alcance

- Backend: preparación de inventario y migración 033, seguida de catalogación y fotografías de borradores con migración 034.
- Android: piloto `1.0.11-vps`, consulta nativa y acceso a catalogación Web. Las ventas nativas continúan bloqueadas deliberadamente.
- Documentación técnica, referencias visuales y verificador de recuperación cifrada. No se incluyen respaldos, llaves ni exportaciones reales.

Los cambios funcionales eran preexistentes. Esta sesión sustituyó referencias personales por cargos genéricos en las guías operativas y, con autorización de Pedro, ajustó únicamente `BackendWorkflowTest` y `BackendFolioNegotiationTest` al modo de consulta. Se conserva la comprobación de permisos y del transporte de folios cortos y heredados.

Las guías actuales usan cargos genéricos. Dos commits preexistentes pendientes de sincronización conservan nombres de pila del equipo en sus versiones históricas (`e248e8d` y `239411c`). El historial no se reescribe; subirlo requiere autorización explícita para conservar esas referencias.

## Validación de esta sesión

- `gradlew.bat assembleDebug testDebugUnitTest --console=plain`: compilación satisfactoria; 376 pruebas, cero fallos, dos omitidas por sus condiciones existentes.
- Backend: `npm run check`, `npm run check:cataloging`, `npm test` (95 aprobadas) y `npm run test:cataloging` (4 aprobadas).
- Verificador estático de Supabase: 134 comprobaciones aprobadas. No equivale a una prueba de integración MariaDB de las migraciones 033/034.
- ViveroWeb: lint, 562 pruebas aprobadas y una omitida; build satisfactorio.
- Revisión de diferencias, `git diff --check` y búsqueda de secretos en los archivos candidatos y commits previamente pendientes de push. Las contraseñas de fixtures revisadas son sintéticas.

No se ejecutaron integraciones con bases de datos ni pruebas de cámara/dispositivo. Las evidencias de despliegues y pruebas integrales anteriores permanecen identificadas como históricas en sus guías.

## Exclusiones y relación con producción

Se conservan fuera de los commits los cambios de `.idea/misc.xml` y `.idea/planningMode.xml`. También quedan excluidos `tmp/`, configuración local, credenciales, APK, respaldos y datos reales, mediante selección explícita de archivos y las reglas existentes de Git.

El usuario confirmó personalmente la navegación administrativa productiva. La última versión Web documentada es `20261010-navigation-4488b9dc39b5`. Las correcciones del carrito guardadas en ViveroWeb permanecen pendientes de publicación; `main` no debe interpretarse como una etiqueta de producción. No se cambió producción en esta sesión.
