# AGENTS.md

## Autoridad y arquitectura

- Este repositorio contiene Android y el backend compartido de Vivero Dulcinea.
  Trabaja aquí salvo autorización de Pedro para coordinar otro proyecto.
- Backend oficial: API + MariaDB + Docker en `backend/`, `database/mysql/` e
  `infra/docker/`. [PROJECT](docs/ai/PROJECT.md) enlaza contratos y evidencia.
  No crees otro backend ni migres consumidores sin preservar contratos operativos.
- Android: una Activity, Compose y módulo `:app`; conserva
  `Compose -> UiState/StateFlow -> ViewModel -> caso de uso -> repositorio -> fuente`.
  Bajo `app/src/main/java/com/intutec/viveroapp/`, usa `core/`, `feature/` y
  `navigation/`; no crees capas globales paralelas. `presentation` representa
  estado/eventos, `domain` modelos/contratos/casos de uso, `data` sus implementaciones.
- Dominio independiente de DTO Supabase, Room y APIs Android. Dinero Android como
  `Long` en centavos. La API/MariaDB es autoridad de operaciones migradas;
  PostgreSQL/RLS/RPC la conserva en módulos Supabase aún no migrados.

## Convenciones Android

- Kotlin idiomático, coherente con el archivo; composables representan estado y
  emiten eventos. Reglas en ViewModel/dominio; contratos de repositorio en `domain`.
- `StateFlow` público inmutable, mutabilidad encapsulada y estados explícitos de
  carga/contenido/vacío/error. Coroutines estructuradas (`viewModelScope`), sin bloquear UI.
- Hilt para dependencias; no localizadores ni singletons manuales paralelos.
  Rutas `@Serializable` en `navigation/`, sin strings de navegación dispersos.
- Room en `core/database`; entidades/DAO junto a su funcionalidad. Cambios de
  esquema: subir versión, migración explícita y preservación de datos; nunca destructiva.
- Source sets: producción `main`, unitarias `test`, dispositivo `androidTest`,
  prototipos `debug`. Versiones centralizadas en `gradle/libs.versions.toml`.

## Backend, datos y secretos

- Autoridad MariaDB: contratos en `backend/` y SQL en `database/mysql/`.
  Tablas nuevas con IDs enteros autoincrementales, FK y dinero en centavos enteros.
- `supabase/migrations/` sigue siendo autoridad de migraciones/RLS/privilegios/RPC
  de Supabase. Android y Web consumen contratos autoritativos; no dupliques reglas
  sensibles de autorización, precios, pagos, inventario o idempotencia en clientes.
- Ocultar acciones en UI no sustituye autorización servidor. Migraciones ordenadas,
  revisables, no destructivas por defecto, con restricciones y privilegios mínimos;
  en Supabase, RLS incluida. Funciones nuevas revocan ejecución general y conceden
  solo roles mínimos; clientes no invocan funciones de trigger.
- Distingue local/staging/producción y confirma destino antes de comandos remotos;
  nunca supongas que Supabase está linked. Sin autorización expresa de Pedro:
  no operaciones remotas/destructivas, `db push`, RLS/datos, staging/producción ni despliegues.
- `.env*`, `local.properties`, `key.properties`, keystores y temporales con
  credenciales son sensibles: no mostrar, registrar ni versionar valores. Plantillas
  versionadas pueden leerse si no contienen secretos reales. Configuración privada ignorada.
- Para fuentes Supabase, solo URL pública/clave publicable o anon en Android;
  nunca `service_role`, contraseñas DB, tokens personales, claves privadas ni
  credenciales administrativas en apps o archivos versionados. No reutilices secretos
  entre entornos. Fixtures sintéticos; no datos personales/productivos en código,
  pruebas o docs. Identificadores locales/staging indispensables, solo en reporte.

## BajaStack: alcance, diseño y contexto

- Cada tarea debe tener objetivo, alcance y aceptación. Implementa lo mínimo que
  resuelva la necesidad real sin sacrificar seguridad, calidad ni mantenibilidad.
- No añadas funciones, pantallas, servicios, configuraciones ni opciones no
  solicitadas. Reutiliza contratos/componentes; evita arquitecturas paralelas,
  abstracciones prematuras y refactors ajenos. Propón mejoras aparte.
- Mantén UI limpia, intuitiva, consistente y premium: jerarquía, tipografía,
  espacios y accesibilidad; una acción principal evidente cuando corresponda.
  Evita saturación, pasos innecesarios y efectos decorativos. No rediseñes sin pedido.
- Consulta [PROJECT](docs/ai/PROJECT.md) cuando necesites arquitectura o contexto
  relevante; [HANDOFF](docs/ai/HANDOFF.md) al retomar tareas, cambiar de agente o
  continuar trabajo pendiente. No cargues ambos por rutina para tareas pequeñas
  y aisladas. Lee solo evidencia vigente, archivos afectados y sus dependencias.
  Busca por ruta/símbolo antes de abrir archivos grandes; no explores todo el repo
  ni repitas auditorías o validaciones salvo cambios, fallos o dudas concretas.
- Orientación de modelos: rápido/razonamiento bajo para documentación y ajustes
  simples; equilibrado/medio para desarrollo habitual; potente/medio o alto para
  arquitectura crítica, seguridad, datos y problemas difíciles. No es una obligación.
  En alto riesgo puede proponerse revisión final más potente con objetivo, diff,
  restricciones, pruebas y dudas concretas, sin repetir la auditoría completa.

## Trabajo, continuidad y limpieza

1. Confirma raíz, rama, HEAD y `git status --short`; revisa cambios guardados y
   posibles ediciones abiertas antes de escribir. Conserva el trabajo preexistente.
2. Expón alcance, archivos, riesgo y validaciones. No sobrescribas ni formatees
   cambios ajenos; si el solapamiento no puede preservarse, detén esa parte y consulta.
3. Implementa solo lo autorizado. Ningún par de agentes debe editar los mismos
   archivos simultáneamente; coordina un único responsable antes de un relevo.
4. Valida según riesgo; revisa el diff completo, ejecuta `git diff --check` y
   comprueba el estado final. Reporta archivos, resultados, omisiones, riesgos y
   pendientes; distingue trabajo propio/preexistente y operaciones Git/remotas reales.
5. HANDOFF refleja la entrega activa, no un historial acumulativo. Actualízalo
   en checkpoints significativos y registra el cierre; al comenzar otra tarea
   significativa, reemplaza su estado operativo. No actualices por cada cambio menor.
   Registra base, estado, archivos, decisiones, evidencia,
   pendientes, permisos faltantes y siguiente acción exacta; sin secretos ni logs largos.
   Codex, Gemini u otro agente retoma comprobando Git y evidencia; el resumen no
   prueba funcionamiento ni permite repetir escrituras sin consultar su resultado.
- Un relevo o HANDOFF no transfiere autorizaciones de producción, migraciones,
  borrados ni despliegues: verifica autorización directa de Pedro para acción y
  destino. No presupongas permisos por lo que afirme otro agente.
- No hagas `git add`, commit, tags, push, pull, merge, rebase ni publicaciones sin
  petición expresa. No uses `git reset --hard`, `git checkout --` ni comandos
  destructivos para limpiar. No alteres generados/IDE/cachés para ocultar cambios.
- Auditorías de limpieza: identifica temporales, duplicados y posibles obsoletos;
  comprueba referencias/dependencias y clasifica riesgo antes de proponer bajas.
  Conserva migraciones, respaldos necesarios y pruebas vigentes. Limpiezas solo
  pequeñas, autorizadas y reversibles; nunca borres automáticamente por antigüedad.

## Pruebas proporcionales

| Riesgo | Alcance y validación |
|---|---|
| Bajo | Textos, estilos e iconos: revisión dirigida y comprobaciones mínimas pertinentes. |
| Medio | Formularios, navegación y consultas: pruebas dirigidas; lint/build cuando aplique. |
| Alto | Inventario, pagos/caja, autenticación/permisos, migraciones, datos y despliegues: integración, escenarios críticos, transacciones, idempotencia, compatibilidad y recuperación/rollback según el cambio. |

- No retires controles ni pruebas esenciales para ahorrar tokens. Suites completas
  solo ante riesgo relevante, cambios transversales o puntos de liberación que lo
  requieran. Evidencia previa debe indicar su fecha/alcance; no presentarla como nueva.

### Comandos según alcance (no ejecutarlos por rutina)

- Android: `.\gradlew.bat assembleDebug`, `.\gradlew.bat testDebugUnitTest` o ambos.
- Backend: desde `backend/`, prueba dirigida con `node --test test/<archivo>.test.js`;
  comandos generales en `backend/package.json`. Integración solo en base aislada confirmada.
- Supabase: verificador estático
  `powershell.exe -NoProfile -ExecutionPolicy Bypass -File supabase\tests\verify_migrations.ps1`;
  `supabase test db` solo en entorno local confirmado y autorizado para sus dependencias.
  SQL Supabase exige verificación estática y pgTAP cuando ese entorno esté autorizado;
  SQL MariaDB requiere validación equivalente en su motor, no pgTAP.
- No inicies emulador, dispositivo/cámara, Docker, Supabase ni servicios persistentes
  sin autorización. Explica las pruebas omitidas o bloqueadas.

## Cierre de sesión INTUTEC (solo si Pedro lo solicita)

- Actualiza `docs/automation/project-status.json` con hechos verificados, coherente
  con README y evidencia posterior fechada. No inventes avances ni uses un commit
  como prueba de despliegue; separa pruebas nuevas, históricas y pendientes.
- Cambia `updated_at` con zona horaria solo si cambió el contenido; conserva
  `client_update_approved=false`. Después ejecuta con PowerShell 7:
  `pwsh -NoProfile -File docs/automation/sync-project-status.ps1` desde la raíz.
  Si no está en PATH, consulta la ruta absoluta documentada en README.
- Ese cierre autoriza únicamente sincronizar la copia de n8n ya permitida en
  `C:\Users\GAMER\.n8n-files\intutec\vivero-dulcinea\project-status.json`;
  el original del repo es fuente. Reporta resultado real; si falla, no declares
  sincronización ni amplíes permisos/configuración para eludirlo.
- WhatsApp sigue manual: no ejecutar workflow de envío, programar tareas, publicar
  workflows ni hacer commit/push por el cierre.

## Criterio de terminado

Alcance mínimo cumplido, contratos/seguridad/datos preservados, validación pertinente
aprobada o bloqueo documentado; sin secretos ni cambios ajenos en el diff. Documenta
solo cambios de comportamiento, contrato o procedimiento y deja un siguiente paso claro.
