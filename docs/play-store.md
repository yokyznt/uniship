# UniShip en Google Play

## Ficha

- **Nombre (máx. 30):** UniShip: rastreo de paquetes
- **Descripción corta (máx. 80):** Todos tus paquetes de Amazon, Mercado Libre, Shein y Temu en un solo lugar.
- **Categoría:** Compras
- **Correo de contacto:** pendiente (lo pide Play Console y se muestra en la ficha)
- **Política de privacidad:** https://uniship-xi.vercel.app/privacidad

### Descripción larga

UniShip junta en una sola lista los envíos de tus compras y te avisa cada vez que se mueven, aunque la app de la tienda no mande notificación.

Rastrea directo con la paquetería, cada 2 horas:
J&T Express, iMile, Estafeta, DHL, FedEx, 99minutos, Paquetexpress, Correos de México y Redpack.

Detecta tus guías solo (opcional): si le das acceso a notificaciones, UniShip toma los números de guía de los avisos de Mercado Libre, Amazon, Temu, Shein, AliExpress, TikTok y Gmail, y crea el paquete por ti. Las demás notificaciones se ignoran.

También puedes pegar la guía o escanear el código de barras de la etiqueta.

- Notificación por cada movimiento: estado y ciudad.
- Línea de tiempo de cada paquete.
- Conexión opcional con tu cuenta de Mercado Libre.
- Respaldo opcional con tu cuenta de Google.
- Gratis, sin anuncios y sin rastreadores de publicidad.

## Seguridad de los datos (Data safety)

| Pregunta | Respuesta |
|---|---|
| ¿Recopila o comparte datos? | Sí, recopila |
| ¿Se cifran en tránsito? | Sí (HTTPS) |
| ¿El usuario puede pedir que se borren? | Sí: Ajustes > Privacidad > Borrar todos mis datos |
| Información personal > Dirección de correo | Recopilada, opcional, solo para la cuenta de respaldo (Firebase Auth). No compartida |
| Información personal > Número de teléfono | Recopilado, opcional (4 dígitos), funcionalidad de la app. Se manda a J&T para el rastreo |
| Mensajes > Otros mensajes en la app | Recopilados, opcional: el texto de los avisos de envío del lector de notificaciones. Funcionalidad de la app |
| Actividad en apps > Otro contenido generado por el usuario | Recopilado, opcional: paquetes respaldados en la nube. Funcionalidad de la app |
| Compartido con terceros | Número de guía con la paquetería correspondiente, para dar el servicio que pide el usuario |
| Publicidad o marketing | No |

## Declaraciones

- **Acceso a notificaciones (NotificationListenerService):** se usa solo para detectar números de guía y avisos de envío de las apps listadas en el aviso de privacidad. Se pide con una explicación visible en el onboarding (paso «Detectar guías solas») antes de mandar a los ajustes de Android, y se puede rechazar.
- **Cámara:** solo para escanear códigos de barras de etiquetas, procesados en el dispositivo.
- **Optimización de batería:** la app no usa `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; solo abre la pantalla de ajustes del sistema.
- **Clasificación de contenido:** Utilidades, sin contenido sensible.
- **Público objetivo:** 18+ (compras en línea).
- **Anuncios:** No contiene anuncios.

## Pasos

1. Crear cuenta de desarrollador personal ($25 USD) y verificar identidad.
2. `npm run android:aab` genera `~/Trabajos/APKs/UniShip-APK/UniShip-<versión>.aab`.
3. Crear la app, llenar la ficha y Data safety con lo de arriba, subir el AAB a **Prueba cerrada**.
4. Invitar a 12 testers (lista de correos o Grupo de Google) y mantenerlos activos 14 días.
5. Solicitar acceso a producción.
6. Antes de cada subida, subir `versionCode` en `app/build.gradle.kts`.

Capturas de pantalla: pendientes (sacarlas del teléfono con paquetes reales).
