// Offline boundary checks. Run with: node this-file <path-to-typescript-module>
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const ts = require(process.argv[2])
function handler(name, settings = {}, client = {}) {
  let serve
  const code = readFileSync(join(__dirname, '../functions', name, 'index.ts'), 'utf8')
    .replace(/^import .*from 'npm:.*'$/m, '')
  const js = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }, reportDiagnostics: true })
  assert.equal(js.diagnostics.length, 0, 'TypeScript syntax')
  vm.runInNewContext(js.outputText, { Response, AbortSignal, Date, JSON,
    createClient: () => client,
    fetch: () => { throw new Error('Live network forbidden in test') },
    Deno: { env: { get: key => settings[key] }, serve: fn => { serve = fn } },
  })
  return serve
}
const origin = 'https://synthetic.example.test'
function req(body, headers = {}, method = 'POST') {
  return new Request(origin, { method, headers: { Origin: origin, ...headers }, ...(method === 'POST' ? { body: JSON.stringify(body) } : {}) })
}
async function main() {
  const mail = handler('newsletter', { APP_ORIGIN: origin })
  assert.equal((await mail(req({}, { Origin: 'https://other.example.test' }))).status, 403)
  assert.equal((await mail(req({}, {}, 'OPTIONS'))).status, 200)
  assert.equal((await mail(req({}, {}, 'GET'))).status, 405)
  assert.equal((await mail(req({ action: 'subscribe' }))).status, 503)
  const configured = handler('newsletter', { APP_ORIGIN: origin, RESEND_API_KEY: 'synthetic', NEWSLETTER_FROM: 'test@example.test' })
  assert.equal((await configured(req({ action: 'subscribe', email: 'test@example.test', consent: false }))).status, 400)
  const anonymous = { auth: { getUser: async () => ({ data: { user: null }, error: new Error('unauthorized') }) } }
  const invite = handler('invite-staff', { APP_ORIGIN: origin }, anonymous)
  assert.equal((await invite(req({ email: 'test@example.test', name: 'Synthetic' }))).status, 401)
  const denied = { auth: { getUser: async () => ({ data: { user: { id: 'synthetic' } }, error: null }) }, rpc: async () => ({ data: false, error: null }) }
  assert.equal((await handler('invite-staff', { APP_ORIGIN: origin }, denied)(req({}))).status, 403)
  console.log('7 offline Edge Function boundary checks passed; no network used.')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
