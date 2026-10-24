export default function handler(req, res) {
  const { code, state, error } = req.query;
  const q = new URLSearchParams();
  if (code) q.set('code', String(code));
  if (state) q.set('state', String(state));
  if (error) q.set('error', String(error));
  const destino = `uniship://ml?${q.toString()}`;
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.status(200).send(`<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>UniShip</title><meta http-equiv="refresh" content="0;url=${destino}"><body style="font-family:sans-serif;background:#FFFBF5;color:#6B4226;padding:32px"><p>Regresando a UniShip…</p><p><a style="color:#E07A2F" href="${destino}">Abrir UniShip</a></p></body>`);
}
