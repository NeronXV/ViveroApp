import { createClient } from 'npm:@supabase/supabase-js@2.112.3'

const origin = Deno.env.get('APP_ORIGIN') ?? ''
const headers = { 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info', 'Access-Control-Allow-Methods': 'POST, OPTIONS', 'Vary': 'Origin' }
const response = (body: unknown, status = 200) => Response.json(body, { status, headers })
Deno.serve(async (request) => {
  if (!origin || request.headers.get('Origin') !== origin) return response({ error: 'Origen no permitido.' }, 403)
  if (request.method === 'OPTIONS') return new Response(null, { headers })
  if (request.method !== 'POST') return response({ error: 'Método no permitido.' }, 405)
  try {
    const auth = request.headers.get('Authorization') ?? ''
    const url = Deno.env.get('SUPABASE_URL')!
    const userClient = createClient(url, Deno.env.get('SUPABASE_ANON_KEY')!, { global: { headers: { Authorization: auth } }, auth: { persistSession: false } })
    const { data, error } = await userClient.auth.getUser(auth.replace(/^Bearer /, ''))
    if (error || !data.user) return response({ error: 'Inicia sesión.' }, 401)
    const { data: allowed, error: permissionError } = await userClient.rpc('has_permission', { required_permission: 'MANAGE_USERS' })
    if (permissionError || allowed !== true) return response({ error: 'No tienes permiso para invitar personal.' }, 403)
    const raw = await request.text()
    if (raw.length > 1000) return response({ error: 'Solicitud inválida.' }, 400)
    const input = JSON.parse(raw)
    if (typeof input.email !== 'string' || input.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email)
      || typeof input.name !== 'string' || input.name.trim().length < 2 || input.name.length > 160) return response({ error: 'Revisa el nombre y el correo.' }, 400)
    const admin = createClient(url, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false, autoRefreshToken: false } })
    const invited = await admin.auth.admin.inviteUserByEmail(input.email.trim().toLowerCase(), {
      data: { full_name: input.name.trim() }, redirectTo: origin + '/recuperar',
    })
    if (invited.error) return response({ error: 'No se pudo enviar la invitación. Verifica si la cuenta ya existe o si el correo está configurado.' }, 400)
    // No role or branch is granted here. Existing authorized RPCs handle assignment.
    return response({ invited: true })
  } catch { return response({ error: 'No se confirmó la invitación. Revisa el directorio antes de reintentar.' }, 502) }
})
