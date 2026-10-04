import { ApiError } from './catalog.js';

export function browserOrigin(value, development = false) {
  if (!value) return null;
  let url;
  try { url = new URL(value); } catch { throw new Error('Invalid WEB_ORIGIN'); }
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.origin !== value || url.username || url.password ||
      !(url.protocol === 'https:' || (development && loopback && url.protocol === 'http:'))) throw new Error('Invalid WEB_ORIGIN');
  return value;
}

// Same-origin reverse proxy only. No CORS, cookies or trusted forwarded headers.
export function requireBrowserOrigin(request, allowed, aliases = []) {
  if (request.headers['sec-fetch-site'] === 'cross-site' ||
      (request.headers.origin && request.headers.origin !== allowed && !aliases.includes(request.headers.origin))) {
    throw new ApiError(403, 'BROWSER_ORIGIN_FORBIDDEN');
  }
}
