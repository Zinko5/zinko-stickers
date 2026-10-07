# Tech Context: StickerForge

## Stack Tecnologico
- **Plataforma**: Android Nativo
- **Lenguaje**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: Ultima version estable de Android
- **Arquitectura**: MVVM con StateFlow y Coroutines

## Librerias y Dependencias Clave
- `androidx.compose.ui`, `androidx.compose.material3`, `androidx.compose.foundation`
- `androidx.activity:activity-compose` (manejo de ActivityResultContracts para seleccion de imagenes)
- `androidx.lifecycle:lifecycle-viewmodel-compose`, `androidx.lifecycle:lifecycle-runtime-compose`
- `androidx.core:core-ktx`
- `WhatsApp Sticker API` (esquema oficial de ContentProvider e Intents para exportacion)
- Inyeccion de dependencias: Hilt / Dagger (recomendado)

## Restricciones Tecnicas y de Entorno
- **Formato de Salida**: Archivos `.webp` empaquetados bajo la estructura de `contents.json` exigida por WhatsApp.
- **Limites de Tamano**:
  - Resolucion fija: 512x512 pixeles.
  - Peso por archivo: Menos de 100 KB por sticker.
  - Tamano del paquete: Minimo 3 y maximo 30 stickers por paquete.
  - Icono de bandeja del paquete (Tray icon): 96x96 pixeles, <50 KB.
- **Permisos requeridos**:
  - `READ_MEDIA_IMAGES` para Android 13+ (API 33+).
  - `READ_EXTERNAL_STORAGE` para versiones anteriores a Android 13.
  - No requiere permisos de almacenamiento para archivos internos de la app.

## Restricciones de Implementacion (Do-Not-Touch)
- No utilizar Java.
- No utilizar layouts XML tradicionales para vistas principales.
- No utilizar librerias de terceros de edicion de imagen no auditadas.
- Respetar las reglas de no ejecutar comandos de Git directamente.
