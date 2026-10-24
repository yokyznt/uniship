# UniShip

Rastreo unificado de paquetes para México (Android, Kotlin + Jetpack Compose).

- `app/`: app Android. `npm run android:apk` genera la APK firmada en `~/Trabajos/APKs/UniShip-APK`; `npm run android:aab` genera el bundle para Play Store.
- `server/`: funciones de Vercel (respaldo en Upstash, OAuth de Mercado Libre, aviso de privacidad).

## Fuentes de rastreo

| Paquetería | Método |
|---|---|
| J&T Express | API pública de jtexpress.mx (pide últimos 4 dígitos del celular) |
| iMile | API pública de imile.com (firma RSA de la web) |
| Estafeta | HTML de cs.estafeta.com |
| Paquetexpress | API pública de cc.paquetexpress.com.mx |
| Correos de México | Formulario ASP.NET |
| 99minutos | API pública de tracking.99minutos.com |
| Redpack | admin-ajax de redpack.com.mx |
| DHL | WebView oculto que captura `/utapi` |
| FedEx | WebView oculto que captura `/track/v2/shipments` |

## Configuración

`local.properties` o variables de entorno:

```
UNISHIP_SERVER=https://uniship-xi.vercel.app
ML_CLIENT_ID=<app id de Mercado Libre>
```

Firebase: copiar `google-services.json` a `app/`. Sin ese archivo la app compila y el respaldo queda desactivado.

Vercel (`server/`): `FIREBASE_PROJECT_ID`, `ML_CLIENT_ID`, `ML_CLIENT_SECRET`, `ML_REDIRECT_URI=https://<dominio>/api/ml/callback`, y las variables de Upstash (`KV_REST_API_URL`/`KV_REST_API_TOKEN` o `UPSTASH_REDIS_REST_URL`/`UPSTASH_REDIS_REST_TOKEN`).

Pruebas: `./gradlew testDebugUnitTest`. Contra los servidores reales: `UNISHIP_EN_VIVO=1 ./gradlew testDebugUnitTest --tests '*EnVivoTest*'`.
