# Boletín Verde en Backend API y MariaDB

El módulo Web usa exclusivamente la API oficial para suscripción, confirmación,
baja, campañas y lotes. La migración **025** añade tres tablas con IDs enteros y
FK reales: `newsletter_subscribers`, `newsletter_campaigns`,
`newsletter_deliveries`. No modifica Supabase ni importa sus suscriptores.

## Contratos y consentimiento

- `POST /api/v1/newsletter/subscribe`, `{email,consent:true}`: 202
  `{accepted:true}`. Normaliza el correo; sin consentimiento responde 400.
  Solo envía confirmación si hace falta y han pasado diez minutos desde la
  solicitud anterior. El límite persistente es 10 solicitudes por correo y 60
  por dirección de conexión cada 15 minutos, además de 100 solicitudes globales
  por hora. No se confía en cabeceras de IP; detrás de Caddy el límite IP es
  compartido. La respuesta pública no espera al proveedor ni revela la cuenta
  o el resultado de entrega. Hay como máximo 32 correos pendientes por proceso.
- `POST /api/v1/newsletter/confirm`, `{token}`: 200 `{confirmed:true}`.
  El token aleatorio de 32 bytes se almacena como SHA-256, vence a las 24 horas
  y solo se consume con POST. Confirmar registra fecha y versión del consentimiento.
- `POST /api/v1/newsletter/unsubscribe`, `{token}`: 200 `{unsubscribed:true}`,
  incluso para un token desconocido con formato válido. La baja es idempotente;
  invalida una confirmación pendiente. No necesita credenciales de correo ni la
  clave de cifrado. Reconfirmar genera un enlace de baja nuevo; uno anterior no
  cancela el consentimiento nuevo.

Los enlaces usan `/boletin#confirm=...` y `/boletin#unsubscribe=...`; Web los
quita del historial y los conserva solo en memoria. Abrir GET no confirma ni
cancela: la persona debe pulsar Confirmar. Recargar exige volver a abrir el enlace.

## Campañas y recuperación

Las rutas administrativas exigen sesión y `MANAGE_SETTINGS` en el servidor:

- `GET /api/v1/admin/newsletter/campaigns`: `{items:[...]}`, hasta 50 campañas
  recientes; ID entero, asunto, cuerpo y conteos `recipients`, `sent`, `skipped`,
  `review`. No expone correos, tokens ni mensajes del proveedor.
- `POST` en la misma ruta: `{subject,body}` y `Idempotency-Key` de 64 caracteres
  hexadecimales. Responde 201 o 200 en repetición, con `{id,recipients,idempotent_replay}`.
  Asunto de 3–150 caracteres, cuerpo de 10–10000; JSON limitado a 64 KiB en
  este endpoint para admitir Unicode. La clave pertenece al actor; otro texto con la misma clave responde 409.
  La transacción conserva los destinatarios confirmados en ese momento, incluso
  si eran cero. No agrega nuevos destinatarios al recuperar una campaña.
- `POST /api/v1/admin/newsletter/campaigns/:id/send`, `{}`: procesa hasta 20
  destinatarios y devuelve `{sent,batch_size}`. Se limita el inicio de trabajo a
  cuatro segundos por solicitud; un proveedor lento puede producir un lote
  menor o una respuesta incierta. No hay envío automático ni cron.

Crear una campaña guarda asunto, mensaje, remitente, destinatario y enlace de
baja de cada envío como un snapshot cifrado con AES-256-GCM. Cada entrega tiene
una clave aleatoria estable para Resend. Cambiar remitente/dominio no cambia el
mensaje ya preparado. Los privilegios runtime no permiten modificar campañas ni
payloads de entregas; solo sus marcas de intento, aceptación y omisión.

Antes de cada envío se vuelve a comprobar permiso y consentimiento/versiones.
Una baja o una versión anterior se omite. Un correo ya aceptado por el proveedor
no puede retirarse si la baja llega después de iniciar esa solicitud.
Un lock MariaDB por campaña impide dos lotes simultáneos, también entre procesos.
No se mantiene una transacción ni se bloquea la sesión durante la llamada de red.
La fecha del primer intento se confirma en MariaDB **antes** de llamar al proveedor.
Si se pierde la respuesta o falla guardar su aceptación, el intento permanece
incierto; reintentar conserva exactamente mensaje y clave. `sent` significa
aceptación por el proveedor, no recepción en la bandeja.

Resend conserva sus claves durante 24 horas; por eso se bloquean intentos sin
confirmar mayores de 23 horas con `NEWSLETTER_REVIEW_REQUIRED`, sin reenviarlos.
Fuente: [idempotencia de Resend](https://resend.com/docs/dashboard/emails/idempotency-keys).
`review` permite identificar esas campañas en Web. La conciliación de esos envíos
requiere revisar el proveedor; todavía no hay un endpoint para resolverlos
administrativamente. No borrar marcas ni crear otra campaña para eludir el bloqueo.

Web guarda el texto/clave del intento de creación en localStorage **antes** del
POST, comprueba su escritura, lo separa por actor y usa Web Locks entre pestañas.
Después de una recarga recupera el texto original. Si no puede guardar, no envía.
Una respuesta incierta conserva el intento hasta recuperar su resultado; no genera
automáticamente otra clave ni cambia a Supabase. Se requiere un navegador con
Web Locks y contexto seguro (HTTPS o localhost).

## Correo y respaldo

Configurar en el archivo privado del mismo Compose:

```dotenv
RESEND_API_KEY=replace-with-private-resend-key
NEWSLETTER_FROM=Vivero Dulcinea <boletin@example.invalid>
NEWSLETTER_LINK_KEY=replace-with-64-lowercase-hex-characters
WEB_ORIGIN=https://vivero.example.invalid
```

En VPS, el overlay obtiene WEB_ORIGIN de VIVERO_DOMAIN. Usar remitente verificado.
Generar NEWSLETTER_LINK_KEY con 32 bytes aleatorios independientes y conservarla
fuera de Git, junto al plan de copias cifradas. No usar valores VITE, contraseñas
de base de datos ni la clave de otro entorno. **No rotarla en el sitio**: se necesita
para descifrar enlaces de baja y snapshots pendientes. Perderla o cambiarla impide
preparar/reanudar campañas anteriores; no se debe reenviar con otra clave de entrega.
El dump SQL no contiene esta clave: la recuperación exige restaurar también la
configuración privada. No se repitió el ensayo de respaldo completo en esta entrega.

Sin correo/origen/clave, suscripción y creación/envío de campañas responden 503;
la consulta administrativa sigue disponible. Confirmación requiere la clave de
cifrado; la baja funciona sin ella. El transporte Resend se comparte con el correo
de acceso existente, con timeout de ocho segundos y sin registrar secretos.
Health valida esquema/almacenamiento, no la entrega de correo.

La confirmación pública no tiene una cola durable: un reinicio puede perder ese
correo; solicitar otro después del plazo de diez minutos. Las campañas y sus
intentos sí permanecen en MariaDB y se reanudan manualmente desde Web.

## Validación local y pendientes

Verificación del 2 de octubre de 2026: 468 pruebas Web, build y lint correctos;
68 pruebas backend y check correctos; **25 pruebas HTTP/SQL Docker** correctas.
La prueba newsletter usa el servidor real y el usuario runtime de privilegios
mínimos contra MariaDB, con transporte simulado. Cubre consentimiento, expiración,
uso único, baja, reconfirmación/versiones, snapshots vacíos y carrera de creación,
autorización, respuesta incierta de correo, mismo payload/clave con configuración
distinta, bloqueo de lotes concurrentes, revisión después de 23 horas, límites y
ausencia de configuración. Las filas sintéticas se limpian. Esquema local actual:
44 tablas y 24 marcadores. Build Web ya no emite un chunk Supabase; permanecen
helpers/dependencia históricos sin uso en este recorrido. No se cambió el lockfile.

Para repetir, desde ViveroApp en un entorno **local de pruebas** con `.env` privado:

```powershell
docker compose --env-file .env -f infra/docker/compose.yaml run --rm --build migrate
docker compose --env-file .env -f infra/docker/compose.yaml up -d --build api
docker compose --env-file .env -f infra/docker/compose.yaml run --rm --build tests
```

En `backend/`: `npm test`, `npm run check`. En ViveroWeb: `npm test`,
`npm run build`, `npm run lint`. No ejecutar fixtures contra datos operativos.
Esta sesión utilizó el proyecto local `vivero-cutover-20261001` y sus archivos
privados ignorados de configuración/red. No hubo correos reales, importación ni despliegue.
No se iniciaron dispositivos Android. No hubo commit
ni push. La validación SQL fue MariaDB; no cambió SQL Supabase ni se ejecutó pgTAP.

**Falta la exportación/importación real**, incluidos suscriptores, consentimientos,
campañas y envíos inciertos. Los enlaces UUID antiguos de Supabase todavía no se
resuelven en el contrato nuevo: durante el corte debe prepararse su transición,
preservar las bajas y conciliar los envíos previos con el proveedor. No asumir
que un envío anterior quedó pendiente para reenviarlo con una clave nueva.
También falta validar UI en navegador y entrega con dominio/remitente propios.
Android operativo, AppCliente y el cierre de datos siguen pendientes; este bloque
no permite apagar Supabase para todo el proyecto.

## Archivos de esta entrega

ViveroApp: `backend/src/newsletter.js`, `auth/mail.js`, `app.js`, `server.js`,
`backend/package.json`, `backend/scripts/verify-local-install.js`,
`backend/test/newsletter.test.js`, `newsletter-integration.test.js`,
`integration.test.js`, `backup-fixture.js`, `database/mysql/migrations/025_newsletter.sql`,
`infra/docker/compose.yaml`, `vps.env.example`, `.env.example`, README y documentos
`backend-newsletter.md`, `backend-complete-cutover.md`, `backend-vps-preparation.md`,
`supabase-migration-map.md`.

ViveroWeb: `NewsletterSignup.tsx`, `NewsletterConfirmation.tsx`, `AdminNewsletter.tsx`,
`newsletter-service.ts`, `newsletter-service.test.ts`, `campaign-attempt.ts` en
`src/features/newsletter/`; se retiró `newsletter-legacy-service.ts`, ya sustituido
por la frontera API oficial. README y `docs/PROJECT_STATUS.md` actualizados.
Los cambios previos de Android, IDE y otros módulos se conservaron.
