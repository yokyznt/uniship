import { cuerpo } from '../../lib/comun.js';

export default async function handler(req, res) {
  if (req.method !== 'POST') return res.status(405).end();
  let datos;
  try { datos = JSON.parse(await cuerpo(req)); } catch { return res.status(400).json({ error: 'json invalido' }); }
  const base = {
    client_id: process.env.ML_CLIENT_ID,
    client_secret: process.env.ML_CLIENT_SECRET
  };
  let campos;
  if (datos.refresh_token) {
    campos = { ...base, grant_type: 'refresh_token', refresh_token: datos.refresh_token };
  } else if (datos.code) {
    campos = { ...base, grant_type: 'authorization_code', code: datos.code, redirect_uri: process.env.ML_REDIRECT_URI };
    if (datos.code_verifier) campos.code_verifier = datos.code_verifier;
  } else {
    return res.status(400).json({ error: 'falta code o refresh_token' });
  }
  const r = await fetch('https://api.mercadolibre.com/oauth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
    body: new URLSearchParams(campos)
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) return res.status(r.status).json({ error: j.message || j.error || 'error de mercado libre' });
  return res.status(200).json({
    access_token: j.access_token,
    refresh_token: j.refresh_token,
    expires_in: j.expires_in,
    user_id: j.user_id
  });
}
