# Lyra V6 Live · 0.6.0

Lyra pasa de un parser rígido a conversación de voz en tiempo real con OpenAI Realtime API.

## Qué incluye
- Conversación continua por WebRTC.
- GPT decide cuándo crear citas, recordatorios, tareas o notas mediante herramientas controladas.
- Consultar hoy/mañana/todo.
- Completar, borrar, reprogramar y posponer.
- Memoria local.
- Recordatorios Android y reprogramación tras reinicio.
- Sincronización opcional con el primer calendario Google editable del dispositivo.
- UI responsive dentro de Android.
- La API key NO se guarda en el APK.

## Antes de usar Lyra Live
Necesitas desplegar `server/cloudflare-worker.js` en Cloudflare Workers (o adaptar el mismo endpoint a otro servidor) y configurar el secreto `OPENAI_API_KEY`.

La app pide a ese servidor `/token` una credencial temporal. El teléfono usa esa credencial para abrir WebRTC directamente con OpenAI.

## GitHub Actions
Sube el contenido de esta carpeta al repositorio. `Actions > Build APK` genera el artefacto `Lyra-V6-Live-APK`.

## Primera configuración en el teléfono
1. Instala el APK.
2. Abre Ajustes.
3. Escribe tu nombre.
4. Pega la URL HTTPS del Worker, sin `/token` al final.
5. Guarda.
6. Toca `Hablar con Lyra` y concede micrófono.

## Importante
El acceso a la API de OpenAI se factura aparte de ChatGPT. No subas tu API key a GitHub ni la escribas dentro del APK.
