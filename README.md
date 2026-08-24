# Lyra V5 — Asistente personal Android

Lyra es una mini app Android nativa y local orientada a organización personal por voz.

## V5: consolidación

- Pantalla principal **Hoy con Lyra**.
- Citas, recordatorios, tareas y notas en una sola bandeja.
- Tareas y notas sin fecha obligatoria.
- Conversación por voz de varios turnos.
- Memoria local, visible, editable y eliminable.
- Comandos de voz para crear, completar, cancelar, reprogramar y posponer.
- Google Calendar para citas y recordatorios programados.
- Notificaciones con acciones: Hecho, 10 min, 1 hora y Reprogramar.
- Perfil local, tema claro/oscuro, colores y orden de Inicio.
- Activación por voz “Lyra”: prioriza reconocimiento local cuando Android lo ofrece y usa el servicio configurado en el sistema como respaldo.
- Icono propio de Lyra incluido.

## Ejemplos de voz

- `Lyra, agenda una reunión con Yorch mañana a las 3 de la tarde.`
- `Lyra, mañana tengo que pagar la luz.`
- `Lyra, anota que el código de pintura es A527.`
- `Lyra, recuerda que Yorch es mi proveedor.`
- `Lyra, marca pagar la luz como hecho.`
- `Lyra, mueve la cita con Yorch para el viernes a las 10 de la mañana.`
- `Lyra, pospone pagar la luz 10 minutos.`

## Privacidad

El perfil, memoria, tareas, notas y agenda local se guardan en el teléfono. Lyra no necesita un servidor propio para estas funciones. La sincronización de calendario utiliza los calendarios ya configurados en Android.

## Compilar sin Android Studio

El repositorio incluye `.github/workflows/build-apk.yml`. Al subir esta versión a GitHub, el workflow `Build APK` crea un APK de depuración y publica el artefacto `Lyra-V5-APK`.

## Tecnología

- Android nativo en Java.
- minSdk 26.
- target/compileSdk 36.
- Android Gradle Plugin 9.3.0.
- Java 17.
- Sin librerías externas dentro de la app.

## Nota sobre “Di Lyra”

Esta versión corrige varios problemas de compatibilidad, recuperación del reconocimiento y conflicto de micrófono. Aun así, la palabra de activación se apoya en `SpeechRecognizer` de Android. Para una versión comercial con escucha permanente de máxima fiabilidad conviene sustituir esa pieza por un motor dedicado de wake word.
