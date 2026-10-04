# Conexión de navegador: bloque 023 (2026-10-01)

Canal Web → API habilitado y comprobado mediante el proxy de desarrollo Vite.
Es infraestructura del backend oficial, sin otro backend ni selector de motor.
**No es todavía un cambio de consumidores:** AuthProvider, catálogo, carrito,
pedidos, administración y caja Web conservan servicios Supabase. Android también.

## Decisión de alcance

Cambiar solo login retiraría el JWT necesario para RPC operativos actuales.
Cambiar solo catálogo introduciría IDs enteros en carrito/pedidos que esperan
UUID. Se habilita primero el canal común para que el siguiente cambio pueda
abarcar un recorrido completo. No se convierten UUID en enteros en el navegador,
no se canjea JWT Supabase por sesión API ni se añade fallback de motor.

## Configuración local

En `.env` ignorado del backend, además de passwords y API_PORT:

```dotenv
WEB_ORIGIN=http://localhost:5173
```

En `.env.local` ignorado de ViveroWeb, manteniendo la configuración Supabase:

```dotenv
BACKEND_PROXY_TARGET=http://127.0.0.1:3001
```

Si API_PORT cambia, cambiar el puerto de BACKEND_PROXY_TARGET. Este último es
solo configuración del servidor Vite, sin prefijo VITE_ ni inclusión en bundle.
Admite únicamente HTTP loopback (localhost, 127.0.0.1, [::1]), puerto válido,
sin ruta, credenciales, query ni fragmento. Valor predeterminado 127.0.0.1:3001.
Vite usa localhost:5173, strictPort: true para que un puerto ocupado no cambie
silenciosamente el origen autorizado. Acceder por localhost, no por 127.0.0.1.

Vite redirige exclusivamente `/api/v1` y sus subrutas, preservando Origin y
Authorization; no modifica cabeceras para aparentar confianza. No proxy de health,
Supabase ni archivos externos. La configuración no afecta los RPC existentes.

WEB_ORIGIN es un origen exacto sin slash final ni credenciales. HTTPS admitido;
HTTP solo loopback con NODE_ENV=development. Valor inválido impide arrancar API.
Sin configurar, solicitudes con Origin permanecen denegadas. Clientes nativos
sin Origin continúan disponibles. Origin desconocido/null y Sec-Fetch-Site
cross-site se rechazan con 403 antes de SQL. Sin wildcard CORS, cookies,
trust de X-Forwarded-* ni relajación de sesiones/capacidades. API no responde
preflights CORS: el navegador usa rutas relativas bajo su propio origen.

En VPS habrá que configurar el origen HTTPS exacto y reverse proxy de `/api/v1`
bajo el dominio Web. Vite dev proxy no está incluido en el bundle ni sirve como
reverse proxy de producción. No se desplegó ni configuró un VPS en este bloque.

## Prueba comprobada

Proyecto Docker sintético vivero-fresh-20261001c, API 33002, origen localhost:5173.
Servidor Vite real temporal. Solicitudes HTTP con cabeceras de navegador pasan
por Vite, API y MariaDB: dos productos demo, rechazo de origen ajeno, escritura
sin sesión 401, login del OWNER sintético, contexto con rol, reporte y logout;
token revocado produce 401. No se imprimieron credenciales y Vite se cerró.
No se probó una pantalla de login migrada: todavía usa Supabase.

- Backend: 57/57 unidades y 21/21 integraciones completas aprobadas en Docker.
- Web: lint, TypeScript/bundle y 361/361 pruebas en 26 archivos aprobados.
- Build inicial detectó falta de URL global en el tsconfig de Node; se resolvió
  con validación estricta de la gramática loopback sin añadir dependencias.
  esbuild requería procesos bloqueados por sandbox; validación ejecutada fuera
  del sandbox, sin cambios de versiones/lockfile.
- Sintaxis y git diff --check aprobados. Sin cambios SQL, Android ni PostgreSQL;
  Gradle/pgTAP no aplican. Sin datos reales, importación, commit, push o despliegue.

## Comandos

En ViveroApp, usando `.env` local configurado:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build --wait api
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --build --rm tests npm test
docker compose --env-file .env -f infra/docker/compose.yaml --profile test run --rm tests
```

En ViveroWeb:

```powershell
npm run lint
npm run build
npm test
npm run dev
```

Para comprobar la lectura pública desde localhost:5173, solicitar
`/api/v1/products` en ese mismo origen. La UI seguirá mostrando catálogo Supabase
hasta el corte de sus servicios. No usar cuentas Supabase para login API.

Archivos ViveroApp: backend/src/browser-origin.js, src/app.js, src/server.js,
backend/test/browser-origin.test.js, backend/package.json, infra/docker/compose.yaml,
.env.example, esta guía, backend-api-mariadb.md, supabase-migration-map.md.
ViveroWeb: backend-proxy.ts, backend-proxy.test.ts, vite.config.ts, .env.example,
docs/backend-browser-connection.md y apéndice backend-catalog-cutover.md.
Trabajo previo preservado en ambos repositorios, ambas ramas main.

Siguiente paso: corte coordinado de catálogo, carrito, pedidos y su administración
con identidad API. Incluye parsers/IDs enteros, carrito persistido y recepción en
caja; no activar un catálogo comprable mientras administración/cobro use otro motor.
