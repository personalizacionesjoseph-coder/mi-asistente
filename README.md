# Lyra — V3 Android

Mini asistente Android para citas, recordatorios y agenda por voz, con sincronización opcional con Google Calendar y una interfaz personalizable.

## V3 incluye

- Crear y editar citas y recordatorios.
- Avisos locales aunque cierres la app.
- Asistente por voz en español.
- Consultas por voz: qué tengo hoy, mañana y cuál es mi próxima cita.
- Google Calendar mediante el proveedor de calendario de Android.
- Selección del calendario Google que se usará.
- Los eventos creados por Lyra se vinculan con su evento de Google Calendar.
- Cambios realizados en un evento vinculado desde Google Calendar se reflejan al volver a Lyra.
- Si un evento vinculado se elimina desde Google Calendar, se elimina también de la agenda local al sincronizar.
- Al activar la sincronización, los eventos futuros locales que aún no estén vinculados se envían al calendario elegido.
- Tema Sistema / Claro / Oscuro.
- Cinco colores principales.
- Mostrar u ocultar Asistente por voz, Acciones rápidas y Agenda.
- Reordenar esas secciones desde Configuración.
- Interfaz rediseñada con tarjeta de voz, chips de estado y tarjetas de agenda modernas.

## Privacidad y arquitectura

No usa servidor propio ni guarda contraseñas de Google. La integración usa `CalendarContract`, es decir, el calendario que ya está sincronizado en Android. La app pide permisos de lectura y escritura de calendario solo cuando el usuario decide conectarlo.

## Tecnología

- Android nativo en Java.
- minSdk 26.
- target/compileSdk 36.
- Android Gradle Plugin 9.3.0.
- Gradle 9.5.0.
- SQLite local.
- Sin librerías externas de la app.

## Generar APK sin Android Studio

El proyecto incluye `.github/workflows/build-apk.yml`. Al subir estos archivos al repositorio, GitHub Actions ejecuta `Build APK` y publica el artefacto `Lyra-APK`.

El APK de prueba queda como `app-debug.apk` dentro del ZIP del artefacto.

## Actualizar un repositorio existente

Sube el contenido de esta carpeta sobre el repositorio de Lyra y confirma el commit. GitHub reemplazará los archivos con el mismo nombre y agregará los nuevos, entre ellos `CalendarBridge.java`, `AppPrefs.java` y `SettingsActivity.java`.

Después abre **Actions → Build APK** y espera el resultado.

## Nota

La sincronización V3 está enfocada en eventos que Lyra crea o vincula. No importa automáticamente todos los eventos históricos de Google Calendar a la base local.
