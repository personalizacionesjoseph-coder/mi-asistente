# Lyra — V4 Android

Lyra es un asistente Android local para citas, recordatorios y agenda por voz, con Google Calendar, perfil personal y una interfaz personalizable.

## V4 incluye

- Crear y editar citas y recordatorios.
- Avisos locales aunque cierres la app.
- Google Calendar mediante el proveedor de calendario de Android.
- Tema Sistema / Claro / Oscuro, cinco colores y secciones reordenables.
- Perfil local: nombre, nombre preferido, horario habitual, aviso predeterminado, respuestas de voz y contexto libre.
- Conversación por voz de varios pasos.
- Lyra pregunta los datos que faltan en vez de inventar fecha/hora.
- Confirmación por voz: guardar, editar o cancelar.
- Limpieza de títulos dictados: “Quisiera agendar una cita para con el nombre de Yorsh” se convierte en “Cita con Yorsh”.
- Notificaciones simplificadas sin fecha/hora duplicada.
- Consultas por voz: hoy, mañana y próxima cita.
- Interpretación básica del perfil para expresiones como “después del trabajo”.
- Opción de solicitar el rol de asistente del sistema en Android 10+.

## Activación al decir “Lyra”

V4 incluye un modo **experimental** de palabra de activación:

- Android 12 o superior.
- Solo se habilita si el dispositivo ofrece `SpeechRecognizer` en el dispositivo.
- El usuario debe activarlo manualmente desde Configuración.
- Android muestra una notificación persistente mientras el micrófono está en uso.
- Al detectar “Lyra”, el servicio responde “Te escucho” y entra en conversación por voz.
- No se inicia automáticamente después de reiniciar el teléfono.

Este modo usa las APIs nativas disponibles sin claves externas. Android documenta que `SpeechRecognizer` no está pensado para reconocimiento continuo, por lo que puede gastar más batería y no debe considerarse todavía un motor de hotword de producción. Para una versión comercial conviene sustituirlo por un motor de wake word dedicado o una integración más profunda con el asistente del sistema.

## Privacidad

- El perfil se guarda en `SharedPreferences` local del teléfono.
- Las citas se guardan en SQLite local.
- Lyra no incluye servidor propio.
- No guarda contraseñas de Google.
- Google Calendar se integra mediante `CalendarContract` con las cuentas ya configuradas en Android.
- El modo “Di Lyra” exige reconocimiento local para evitar usar un reconocedor remoto como escucha permanente.

## Tecnología

- Android nativo en Java.
- minSdk 26.
- target/compileSdk 36.
- Android Gradle Plugin 9.3.0.
- Gradle 9.5.0.
- SQLite local.
- Sin librerías externas en la app.

## Generar APK sin Android Studio

El repositorio incluye `.github/workflows/build-apk.yml`. Al subir los archivos a GitHub, el workflow **Build APK** compila automáticamente y publica el artefacto:

`Lyra-V4-APK`

Dentro del ZIP del artefacto está `app-debug.apk`.

## Actualizar tu repositorio existente

Descomprime el ZIP de Lyra V4 y sube **todo el contenido de `lyra_android`** encima de tu repositorio actual. Confirma el commit y abre **Actions → Build APK**.

No crees un repositorio nuevo: se conserva el mismo `applicationId` (`com.joseph.miasistente`) para que el APK pueda actualizar la instalación anterior mientras mantenga la misma firma debug de GitHub Actions.
