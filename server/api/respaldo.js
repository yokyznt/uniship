import { redis, usuario, cuerpo } from '../lib/comun.js';

const MAX = 3 * 1024 * 1024;

export default async function handler(req, res) {
  const uid = await usuario(req);
  if (!uid) return res.status(401).json({ error: 'sin sesion' });
  const llave = `respaldo:${uid}`;
  if (req.method === 'GET') {
    const datos = await redis.get(llave);
    if (!datos) return res.status(404).json({ error: 'sin respaldo' });
    res.setHeader('Content-Type', 'application/json');
    return res.status(200).send(typeof datos === 'string' ? datos : JSON.stringify(datos));
  }
  if (req.method === 'PUT') {
    const texto = await cuerpo(req);
    if (texto.length > MAX) return res.status(413).json({ error: 'muy grande' });
    try { JSON.parse(texto); } catch { return res.status(400).json({ error: 'json invalido' }); }
    await redis.set(llave, texto);
    return res.status(204).end();
  }
  if (req.method === 'DELETE') {
    await redis.del(llave);
    return res.status(204).end();
  }
  res.setHeader('Allow', 'GET, PUT, DELETE');
  return res.status(405).end();
}
