import { createRemoteJWKSet, jwtVerify } from 'jose';
import { Redis } from '@upstash/redis';

const jwks = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));

export const redis = new Redis({
  url: process.env.UPSTASH_REDIS_REST_URL || process.env.KV_REST_API_URL,
  token: process.env.UPSTASH_REDIS_REST_TOKEN || process.env.KV_REST_API_TOKEN
});

export async function usuario(req) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return null;
  const proyecto = process.env.FIREBASE_PROJECT_ID;
  try {
    const { payload } = await jwtVerify(token, jwks, {
      issuer: `https://securetoken.google.com/${proyecto}`,
      audience: proyecto
    });
    return payload.sub || null;
  } catch {
    return null;
  }
}

export async function cuerpo(req) {
  if (req.body !== undefined && req.body !== null) {
    if (typeof req.body === 'string') return req.body;
    if (Buffer.isBuffer(req.body)) return req.body.toString('utf8');
    return JSON.stringify(req.body);
  }
  const partes = [];
  for await (const p of req) partes.push(p);
  return Buffer.concat(partes).toString('utf8');
}
