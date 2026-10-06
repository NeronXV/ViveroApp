# Configuración de correo — Vivero Dulcinea

**Fecha:** 5 de octubre de 2026  
**Estado:** IMPLEMENTADO (proveedor Resend) — CONFIGURACIÓN PENDIENTE (credenciales)

---

## IMPLEMENTADO

### Componente que envía correo
El backend Node (`backend/src/server.js`) inicializa dos remitentes al arrancar:

1. **`accountMailer`** — para invitaciones de personal y recuperación de contraseña
2. **`newsletterConfig.send`** — para envío de boletín (newsletter)

Ambos usan la misma librería: **Resend** (API HTTPS, no SMTP).

### Librería utilizada
- **Resend** — API REST sobre HTTPS (`https://api.resend.com/emails`)
- Implementación propia en `backend/src/auth/mail.js` (sin dependencias npm adicionales)
- Usa `fetch` nativo (Node 18+) con `AbortSignal.timeout(8000)`
- Idempotencia via header `Idempotency-Key` (hash del token de sesión)

### Variables de entorno requeridas

| Variable | Descripción | Ejemplo | Secreto |
|---|---|---|---|
| `RESEND_API_KEY` | API key de Resend (formato `re_...`) | `re_abcdef123456...` | **SÍ** |
| `ACCOUNT_MAIL_FROM` | Remitente para invitaciones/recuperación | `Vivero Dulcinea <acceso@tudominio.com>` | **SÍ** |
| `NEWSLETTER_FROM` | Remitente para boletín | `Vivero Dulcinea <boletin@tudominio.com>` | **SÍ** |
| `NEWSLETTER_LINK_KEY` | Clave HMAC (64 hex chars) para enlaces de suscripción | `a1b2c3d4...` (64 chars) | **SÍ** |

### Cómo configurar proveedor (Resend)

1. Crear cuenta en https://resend.com
2. Verificar dominio de envío (DNS: SPF, DKIM, DMARC)
3. Crear API key en Resend Dashboard → API Keys
4. Configurar las 4 variables arriba en `.env.vps` (VPS) o `.env` (local)
5. **Nunca** usar `replace-with-` en valores reales

### Cómo verificar conexión
```bash
# En el contenedor API o local con Node
node -e "
const fetch = require('node:fetch');
const key = process.env.RESEND_API_KEY;
if (!key) { console.log('RESEND_API_KEY no configurada'); process.exit(1); }
fetch('https://api.resend.com/emails', {
  method: 'POST',
  headers: { Authorization: 'Bearer ' + key, 'Content-Type': 'application/json' },
  body: JSON.stringify({ from: process.env.ACCOUNT_MAIL_FROM, to: ['test@example.invalid'], subject: 'Test', text: 'Test' })
}).then(r => console.log(r.ok ? 'OK' : 'FAIL ' + r.status)).catch(e => console.log('ERROR', e.message));
"
```

### Comportamiento si NO está configurado
- `createResendSender` retorna `null` si `RESEND_API_KEY` no está seteada o empieza con `replace-with-`
- `createAccountMailer` retorna `null` si falta `RESEND_API_KEY`, `ACCOUNT_MAIL_FROM` u `origin`
- Los endpoints que requieren correo (`/auth/recovery`, `/auth/password`, `/admin/staff/invitations`, `/admin/newsletter/campaigns/:id/send`) responden **503 `MAIL_UNAVAILABLE`**
- No hay fallback a SMTP ni a otro proveedor
- No se envía correo silenciosamente; el error es explícito

### Logs y errores comunes
| Error | Causa | Acción |
|---|---|---|
| `MAIL_UNAVAILABLE` | Variables no configuradas o Resend rechaza | Verificar `.env.vps` y dominio verificado en Resend |
| `Invalid mail configuration` | API key con prefijo `replace-with-` | Reemplazar con key real |
| `Invalid account mail configuration` | `from` contiene salto de línea | Sanear `ACCOUNT_MAIL_FROM` |
| Timeout 8s | Resend no responde | Reintentar; revisar conectividad saliente HTTPS |

### Valores que DEBEN ser secretos
- `RESEND_API_KEY` — acceso completo a envío en tu cuenta Resend
- `ACCOUNT_MAIL_FROM` / `NEWSLETTER_FROM` — exponen dominio verificado
- `NEWSLETTER_LINK_KEY` — permite falsificar enlaces de suscripción/desuscripción
- **Nunca** commitear al Git, ni loguear, ni pasar por chat

---

## CONFIGURACIÓN PENDIENTE

- [ ] Cuenta Resend creada y dominio verificado
- [ ] `RESEND_API_KEY` en `/opt/vivero/shared/.env.vps` (modo 600)
- [ ] `ACCOUNT_MAIL_FROM` con dominio verificado
- [ ] `NEWSLETTER_FROM` con dominio verificado
- [ ] `NEWSLETTER_LINK_KEY` generado: `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`
- [ ] Envío de prueba: invitación de personal + recuperación + newsletter
- [ ] Confirmar recepción real en buzón destino

---

## RECOMENDACIÓN

- Usar **subdominios separados** para transaccional (`acceso@tudominio.com`) y marketing (`boletin@tudominio.com`) para reputación independiente
- Configurar **webhooks de Resend** (bounces, complaints) para mantener lista limpia
- Para desarrollo local: usar cuenta Resend de prueba con dominio `example.invalid` o similar; no usar credenciales de producción
- Monitorear cuotas de Resend (gratis: 3,000 emails/mes, 100/día)

---

## NO IMPLEMENTADO (por ahora)

- SMTP tradicional (Postfix, Sendmail, etc.)
- Cola local de emails con reintentos
- Templates HTML complejos (solo texto plano actual)
- Adjuntos
- Programación de envíos masivos (newsletter usa loop simple con rate limit 32 concurrentes)