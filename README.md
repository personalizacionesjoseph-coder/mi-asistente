# Mi Asistente — MVP Android

Mini app Android offline para guardar citas y recordatorios y recibir avisos locales.

## Incluye

- Citas y recordatorios.
- Título y notas.
- Fecha y hora.
- Aviso a la hora, 10 min, 30 min, 1 h o 1 día antes.
- Opción sin aviso.
- Editar tocando un elemento.
- Eliminar manteniendo pulsado.
- Base SQLite local.
- Reprogramación de avisos después de reiniciar el teléfono.
- Notificaciones locales; no necesita cuenta ni internet.

## Tecnología

- Android nativo en Java.
- minSdk 26.
- target/compileSdk 36.
- Android Gradle Plugin 9.3.0.
- Gradle 9.5.0.
- Sin librerías externas en la app.

## Generar APK sin Android Studio

El repositorio incluye `.github/workflows/build-apk.yml`.
Al subir el proyecto a GitHub, la acción `Build APK` compila automáticamente el APK de depuración y lo publica como artefacto `MiAsistente-APK`.

## Nota de producción

El APK `debug` sirve para instalar y probar. Para distribuir públicamente conviene generar una firma privada de producción y construir una variante `release` firmada.
