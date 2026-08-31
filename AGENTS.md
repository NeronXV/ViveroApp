# AGENTS.md

## Propósito y alcance

- Este repositorio contiene la aplicación Android de Vivero Dulcinea y la fuente
  autoritativa de sus contratos y migraciones de Supabase.
- Trabaja solo dentro de este repositorio salvo que Pedro autorice expresamente
  coordinar cambios con otro proyecto.
- Prioriza cambios pequeños, verificables y compatibles con la arquitectura actual.


## Modo actual: MVP funcional

- La prioridad vigente es completar recorridos utilizables de extremo a extremo y obtener retroalimentación real.
- Lee `docs/PROJECT_STATUS.md`, `docs/ROADMAP.md`, `docs/MVP_RULES.md` y `docs/tasks/current.md` antes de planear trabajo funcional.
- Clasifica los hallazgos como **bloqueante**, **importante** o **deuda técnica**.
- Corrige de inmediato sólo los bloqueantes del flujo y los riesgos de pérdida de datos, autorización, secretos, cobros o contratos incompatibles.
- Documenta lo demás y continúa; no amplíes la tarea por posibilidades hipotéticas.
- La validación debe ser proporcional al cambio. Empieza por la prueba específica y el camino principal; reserva suites exhaustivas para cambios críticos o checkpoints integrales.
- No uses esta prioridad para retirar controles ya implementados ni para debilitar autenticación, RLS, privilegios, idempotencia, totales autoritativos o protección de secretos.

## Mapa del repositorio

- `app/`: único módulo Gradle Android.
- `app/src/main/java/com/intutec/viveroapp/`: código Kotlin de producción.
- `core/`: utilidades comunes, modelos compartidos, Room, diseño, DI, red,
  seguridad y sesión.
- `feature/<funcionalidad>/`: funcionalidades organizadas en `presentation`,
  `domain` y `data` cuando esas capas son necesarias.
- `navigation/`: rutas tipadas y grafo de navegación de la única Activity.
- `app/src/test/`: pruebas unitarias; `app/src/androidTest/`: pruebas instrumentadas.
- `app/src/debug/`: prototipos y utilidades exclusivos de debug.
- `supabase/migrations/`: historial SQL ordenado y autoritativo del backend.
- `supabase/tests/`: pruebas pgTAP y verificación estática de migraciones.
- `docs/`: arquitectura, base de datos, permisos, pruebas y configuración.
- `gradle/libs.versions.toml`: catálogo central de versiones y dependencias.

## Arquitectura vigente

- La app usa una sola Activity, Jetpack Compose y un único módulo `:app`.
- Conserva el flujo `Compose -> UiState/StateFlow -> ViewModel -> caso de uso ->
  repositorio -> fuente local o remota`.
- La organización es por funcionalidad; no crees carpetas globales paralelas que
  dupliquen `core`, `navigation` o las capas de cada `feature`.
- `presentation` contiene pantallas, estado, eventos y ViewModels; `domain`
  contiene modelos, contratos y casos de uso; `data` implementa repositorios,
  mappers y fuentes locales o remotas.
- Los modelos de dominio no deben depender de DTO de Supabase, entidades Room ni
  APIs de Android.
- Los importes monetarios se representan como `Long` en centavos. PostgreSQL es
  la autoridad final de operaciones críticas de caja, inventario y permisos.

## Convenciones Android y Kotlin

- Escribe Kotlin idiomático, explícito en límites de capa y coherente con el estilo
  del archivo modificado; evita abstracciones o dependencias sin necesidad actual.
- Mantén los composables centrados en representar estado y emitir eventos. Las
  reglas de negocio pertenecen a ViewModels, casos de uso o dominio.
- Expón estado observable como `StateFlow` inmutable; conserva el estado mutable
  encapsulado y usa estados explícitos de carga, contenido, vacío y error.
- Ejecuta trabajo asíncrono con coroutines estructuradas y scopes del ciclo de vida,
  como `viewModelScope`; no bloquees el hilo principal.
- Usa Hilt para construir dependencias y enlazar contratos con implementaciones;
  no introduzcas localizadores de servicios ni singletons manuales paralelos.
- Mantén contratos de repositorio en `domain` e implementaciones en `data`.
- Usa las rutas `@Serializable` y el grafo de `navigation/`; no disperses strings de
  rutas o argumentos sin tipar por las pantallas.
- Para Room, ubica la base y sus migraciones en `core/database`, y entidades/DAO
  junto a la fuente local de la funcionalidad correspondiente.
- Todo cambio de esquema Room debe aumentar la versión, incluir una migración
  explícita y preservar datos; no uses migración destructiva como atajo.
- Conserva los source sets: producción en `main`, pruebas locales en `test`, pruebas
  de dispositivo en `androidTest` y prototipos no productivos en `debug`.

## Autoridad y seguridad de Supabase

- `ViveroApp/supabase/migrations` es la autoridad de migraciones, RLS, privilegios,
  RPC y contratos compartidos de Supabase.
- Android y Web consumen esos contratos autoritativos. No dupliques en clientes
  reglas sensibles de autorización, precios, pagos, inventario o idempotencia.
- La UI puede ocultar acciones por capacidad, pero RLS y RPC deben hacer cumplir la
  autorización en el servidor.
- Toda migración nueva debe ser ordenada, revisable y no destructiva por defecto;
  debe incluir restricciones, RLS y privilegios mínimos en la misma entrega.
- Toda función nueva debe revocar ejecución general y concederla solo a los roles
  mínimos necesarios. Los clientes no invocan funciones de trigger directamente.
- Mantén separados local, staging y producción. Verifica siempre el destino antes
  de cualquier comando de Supabase y nunca presupongas que un proyecto está linked.
- No ejecutes operaciones remotas, destructivas, `db push`, despliegues, cambios de
  RLS/datos ni acciones sobre staging o producción sin autorización expresa de Pedro.
- Tampoco hagas `push`, publicación o despliegue del repositorio sin esa autorización.

## Credenciales y datos sensibles

- Trata `.env`, `.env.local`, `local.properties`, `key.properties`, keystores y
  archivos temporales con credenciales como sensibles: no muestres, registres ni
  versiones sus valores.
- Puedes inspeccionar plantillas versionadas como `.env.example` cuando sea
  necesario, siempre que confirmes que no contienen secretos reales.
- La app solo puede recibir la URL pública y la clave publicable/anon mediante
  configuración local ignorada por Git.
- Nunca distribuyas `service_role`, contraseñas de PostgreSQL, tokens personales,
  claves privadas ni credenciales administrativas en Android o archivos versionados.
- En ejemplos y fixtures usa datos sintéticos. Si una validación exige citar un
  identificador de local o staging, limita su exposición al reporte; nunca copies
  datos personales ni datos de producción a código, pruebas o documentación.

## Flujo obligatorio de trabajo

1. Confirma la raíz del repositorio, rama, HEAD y `git status --short`.
2. Lee las instrucciones y la evidencia local relevante antes de asumir el diseño.
3. Identifica y conserva todos los cambios preexistentes del usuario.
4. Expón un plan proporcional al cambio y delimita archivos, riesgos y validaciones.
5. Implementa el cambio mínimo; no hagas refactors, limpiezas ni correcciones ajenas.
6. Valida primero lo específico y después las comprobaciones amplias justificadas.
7. Revisa el diff completo, ejecuta `git diff --check` y vuelve a consultar el estado.
8. Reporta lo realizado, validaciones, limitaciones y cambios preexistentes separados.

## Validación respaldada por el repositorio

- Compilación debug: `.\gradlew.bat assembleDebug`
- Pruebas unitarias: `.\gradlew.bat testDebugUnitTest`
- Ambas cuando aplique: `.\gradlew.bat assembleDebug testDebugUnitTest`
- Verificación estática de migraciones:
  `powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1`
- Pruebas de base de datos: `supabase test db`, únicamente en un entorno local de
  pruebas confirmado y cuando la tarea autorice iniciar sus dependencias.
- Las pruebas instrumentadas y de cámara requieren emulador o dispositivo; no los
  inicies salvo que la tarea lo autorice.
- Elige validaciones según el alcance, pero explica toda omisión. Un cambio SQL exige
  verificación estática y, cuando esté autorizado el entorno local, pruebas pgTAP.

## Protección del trabajo existente

- No reviertas, sobrescribas, formatees ni incluyas cambios que no sean tuyos.
- No uses comandos destructivos ni `git reset --hard` o `git checkout --` para limpiar.
- No hagas `git add`, commit, push, pull, merge o rebase salvo petición expresa.
- No edites archivos generados, IDE, cachés o artefactos para ocultar un estado sucio.
- Si un cambio requerido se solapa con trabajo existente y no puede preservarse con
  seguridad, detente y solicita dirección.

## Definición de terminado

- El comportamiento solicitado está implementado con el menor alcance razonable.
- La arquitectura, contratos backend, seguridad y datos existentes se preservan.
- Las validaciones pertinentes pasan, o sus bloqueos quedan documentados con evidencia.
- El diff no contiene secretos, datos temporales, archivos accidentales ni cambios ajenos.
- La documentación se actualiza solo cuando el comportamiento o contrato cambió.

## Reporte final

- Resume el resultado y enumera los archivos modificados.
- Indica comandos ejecutados y resultado; distingue pruebas omitidas o bloqueadas.
- Separa claramente cambios preexistentes de los producidos por la tarea.
- Declara riesgos, supuestos y cualquier trabajo deliberadamente fuera de alcance.
- Incluye el estado Git final y confirma que no hubo commit, push ni despliegue.
