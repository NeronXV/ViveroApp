import { createClient } from 'npm:@supabase/supabase-js@2.112.3'

const origin = Deno.env.get('APP_ORIGIN') ?? ''
const headers = { 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info', 'Access-Control-Allow-Methods': 'POST, OPTIONS', 'Vary': 'Origin' }
const response = (body: unknown, status = 200) => Response.json(body, { status, headers })
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

async function send(to: string, subject: string, text: string, key: string) {
  const result = await fetch('https://api.resend.com/emails', {
    method: 'POST', signal: AbortSignal.timeout(10000),
    headers: { Authorization: 'Bearer ' + Deno.env.get('RESEND_API_KEY'), 'Content-Type': 'application/json', 'Idempotency-Key': key },
    body: JSON.stringify({ from: Deno.env.get('NEWSLETTER_FROM'), to: [to], subject, text }),
  })
  if (!result.ok) throw new Error('MAIL_UNAVAILABLE')
}

Deno.serve(async (request) => {
  if (!origin || request.headers.get('Origin') !== origin) return response({ error: 'Origen no permitido.' }, 403)
  if (request.method === 'OPTIONS') return new Response(null, { headers })
  if (request.method !== 'POST') return response({ error: 'Método no permitido.' }, 405)
  if (!Deno.env.get('RESEND_API_KEY') || !Deno.env.get('NEWSLETTER_FROM')) return response({ error: 'El envío de correo aún no está configurado.' }, 503)
  try {
    const raw = await request.text()
    if (raw.length > 2000) return response({ error: 'Solicitud demasiado grande.' }, 413)
    const body = JSON.parse(raw)
    const url = Deno.env.get('SUPABASE_URL')!
    const admin = createClient(url, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false, autoRefreshToken: false } })
    if (body.action === 'subscribe') {
      if (body.consent !== true || typeof body.email !== 'string' || body.email.length > 254) return response({ error: 'Revisa el correo y acepta la suscripción.' }, 400)
      const { data, error } = await admin.rpc('prepare_newsletter_subscription', { p_email: body.email })
      if (error) return response({ error: 'No se pudo registrar la solicitud. Revisa el correo o espera unos minutos.' }, 400)
      if (data) await send(data.email, 'Confirma tu suscripción a Boletín Verde',
        'Confirma que deseas recibir novedades de Vivero Dulcinea abriendo este enlace y pulsando Confirmar:\n' + origin + '/boletin?confirm=' + data.token + '\nEl enlace caduca en 24 horas. Si no lo solicitaste, ignora este mensaje.',
        'newsletter-confirm-' + data.token)
      return response({ accepted: true })
    }
    if (body.action !== 'send' || typeof body.campaignId !== 'string' || !uuid.test(body.campaignId)) return response({ error: 'Solicitud inválida.' }, 400)
    const auth = request.headers.get('Authorization') ?? ''
    const client = createClient(url, Deno.env.get('SUPABASE_ANON_KEY')!, { global: { headers: { Authorization: auth } }, auth: { persistSession: false } })
    const { data: user, error: authError } = await client.auth.getUser(auth.replace(/^Bearer /, ''))
    if (authError || !user.user) return response({ error: 'Inicia sesión.' }, 401)
    const { data: allowed, error: permissionError } = await client.rpc('has_permission', { required_permission: 'MANAGE_SETTINGS' })
    if (permissionError || allowed !== true) return response({ error: 'Sin permiso para enviar boletines.' }, 403)
    const { data: campaign, error: campaignError } = await admin.from('newsletter_campaigns').select('id,subject,body').eq('id', body.campaignId).single()
    if (campaignError || !campaign) return response({ error: 'Campaña no disponible.' }, 404)
    const { data: deliveries, error } = await admin.from('newsletter_deliveries').select('subscriber_id,first_attempt_at').eq('campaign_id', campaign.id).is('sent_at', null).is('skipped_at', null).order('subscriber_id').limit(20)
    if (error) throw error
    let sent = 0
    for (const delivery of deliveries ?? []) {
      const { data: subscriber, error: subscriberError } = await admin.from('newsletter_subscribers').select('email,unsubscribe_token,confirmed_at,unsubscribed_at').eq('id', delivery.subscriber_id).single()
      if (subscriberError) throw subscriberError
      if (!subscriber.confirmed_at || subscriber.unsubscribed_at) {
        const { error: skipError } = await admin.from('newsletter_deliveries').update({ skipped_at: new Date().toISOString() }).eq('campaign_id', campaign.id).eq('subscriber_id', delivery.subscriber_id)
        if (skipError) throw skipError
        continue
      }
      // Resend guarantees idempotency for 24h; uncertain older sends require operator review.
      if (delivery.first_attempt_at && Date.now() - Date.parse(delivery.first_attempt_at) > 23 * 3600000) return response({ error: 'Hay un envío antiguo sin confirmar. Revisa su estado en Resend antes de continuar.' }, 409)
      const { error: attemptError } = await admin.from('newsletter_deliveries').update({ first_attempt_at: new Date().toISOString() }).eq('campaign_id', campaign.id).eq('subscriber_id', delivery.subscriber_id).is('first_attempt_at', null)
      if (attemptError) throw attemptError
      await send(subscriber.email, campaign.subject, campaign.body + '\n\nCancelar suscripción: ' + origin + '/boletin?unsubscribe=' + subscriber.unsubscribe_token,
        'newsletter-' + campaign.id + '-' + delivery.subscriber_id)
      const { error: savedError } = await admin.from('newsletter_deliveries').update({ sent_at: new Date().toISOString() }).eq('campaign_id', campaign.id).eq('subscriber_id', delivery.subscriber_id)
      if (savedError) throw savedError
      sent++
    }
    return response({ sent, batchSize: deliveries?.length ?? 0 })
  } catch {
    return response({ error: 'No se pudo confirmar el envío. Consulta el estado antes de reintentar.' }, 502)
  }
})
