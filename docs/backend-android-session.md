# Sesión Backend API preparada en Android

Estado posterior, 2026-10-02: el recorrido Android principal ya usa la API.
Consultar [backend-android-workflow.md](backend-android-workflow.md) para el alcance
actual, configuración y pruebas nuevas. La evidencia siguiente conserva el
alcance original de este bloque; no acredita paridad nativa completa.

Fecha: 2026-10-01. Bloque preparatorio del camino oficial Backend API + MariaDB.
El login visible, catálogo, ventas y sincronizador Android continúan en Supabase.
No se activó una pantalla ni se alteraron Room, sus pendientes o los UUID existentes.

## Archivos y contrato

- `core/session/BackendSession.kt`: identidad con IDs enteros Long, contexto y estados explícitos; token redactado en toString.
- `core/session/SessionStore.kt`: sesión API en memoria dentro del almacén existente, con revisión para invalidar respuestas tardías. La sesión Supabase conserva su contrato.
- `core/network/BackendApiTransport.kt`: login anónimo limitado al endpoint oficial y GET autenticado, además del POST de ventas existente.
- `core/di/BackendNetworkModule.kt`: enlaces Hilt para fuente remota y contrato BackendAuthGateway, sin sustituir AuthRepository.
- `feature/auth/data/remote/BackendAuthRemoteDataSource.kt`: POST `/api/v1/auth/login`, GET `/api/v1/auth/me`, POST `/api/v1/auth/logout`. Verifica token opaco de 43 caracteres, TTL 3600 segundos, schema_version 1, roles, IDs, sucursal y capacidades; rechaza respuestas incompatibles.
- `feature/auth/domain/repository/BackendAuthGateway.kt` y `feature/auth/data/repository/BackendAuthRepository.kt`: acceso, refresco, cierre y observación de vencimiento.
- `app/src/test/java/com/intutec/viveroapp/feature/auth/backend/`: 7 pruebas remotas y 8 del ciclo de sesión con adaptadores sintéticos.

Las rutas Kotlin anteriores son relativas a `app/src/main/java/com/intutec/viveroapp/`.
La configuración pública BACKEND_API_URL y restricciones HTTPS/debug están en
[backend-android-sales.md](backend-android-sales.md). No se agregaron dependencias,
tablas, secretos, persistencia de contraseñas/tokens ni conversiones de UUID.

## Ciclo y límites para activar consumidores

El login publica sesión y contexto juntos, únicamente después de verificar ambos.
Cuenta sin rol o sucursal inactiva no puede operar. Al refrescar se retiran permisos;
un error de red conserva el token sin contexto autorizado, y HTTP 401 limpia la
sesión. No hay reintentos automáticos. Las operaciones duplicadas se rechazan.
El vencimiento local se calcula desde el comienzo del login de forma conservadora.

El cierre local invalida inmediatamente respuestas pendientes. `signOut()` intenta
revocar en servidor e informa si no se confirmó. `SessionStore.clear()` también
invalida la sesión API local, pero no hace llamadas de red: ese cierre global por
sí solo no confirma revocación remota. Un token no revocado sigue sujeto al TTL y
la autorización del servidor. Un login parcialmente completado intenta revocar
su token con un límite de ocho segundos, también ante cancelación.

Al activar la UI, su propietario debe lanzar `monitorExpiry()` en un scope del
ciclo de vida, refrescar contexto al retomar y permitir operaciones solo con
accessStatus READY, sesión vigente y capacidad/sucursal válidas. Hoy no existe ese
consumidor activo. El backend sigue siendo autoridad de autorización. No se
implementó cambio de contraseña o recuperación autónoma en Android en este bloque.

Antes de activar ventas hace falta catálogo API y migración explícita del outbox
Room preservando intentos e identidades. Android y pedidos Web deben emitir al
mismo destino que Caja; no se debe cortar Caja dejando esos emisores en Supabase.

## Validación ejecutada en este bloque

Desde la raíz, usando el JDK 21 ya instalado únicamente para el proceso:

```powershell
$env:JAVA_HOME='C:/Users/GAMER/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat testDebugUnitTest --tests 'com.intutec.viveroapp.feature.auth.backend.*'
.\gradlew.bat assembleDebug testDebugUnitTest
git diff --check
```

Resultado: compilación y APK debug correctos; 248 pruebas totales, cero fallos,
errores u omitidas, incluidas las 15 nuevas. Se comprobaron login/contexto atómico,
contratos inválidos, cancelación, duplicación, vencimiento, refresco fallido y
respuestas tardías tras cierre. Las pruebas usan transporte/reloj inyectados;
no constituyen prueba HTTP real, de pantalla o dispositivo. No se inició emulador
ni Docker en este bloque. No aplica validación SQL porque no cambió SQL.
Gradle requirió ejecución autorizada fuera del sandbox por su caché; no se instaló
Java ni se cambió configuración global.

Se conservaron los cambios anteriores, incluidos build.gradle.kts, manifiesto
debug, consumidor de ventas y documentos previos. Sin commit, push o despliegue.
