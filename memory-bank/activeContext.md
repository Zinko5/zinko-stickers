# Active Context: Zinko Stickers

## Foco Actual
- Soporte completo para stickers y paquetes animados de WhatsApp: generacion de archivos Animated WebP (512x512 px, <500 KB, loop=0, duracion <=6s), seleccion y extraccion de videos (MP4/WebM) y GIFs animados, visualizacion en tiempo real con `coil-gif` y exportacion validada a WhatsApp.
- Proyecto completamente operativo y verificado con 27 pruebas unitarias.

## Cambios Recientes
- Visualizacion en tiempo real de Stickers Animados en la UI:
  - Incorporacion de `io.coil-kt:coil-gif:2.6.0` en `build.gradle.kts`.
  - Creacion de `StickerForgeApp` (`Application`, `ImageLoaderFactory`) con `ImageDecoderDecoder.Factory` (API 28+) y `GifDecoder.Factory` (API <28).
  - Configuracion explicita de `decoderFactory` en `PackDetailScreen`, `PackPreviewScreen`, `CropScreen` y `StickerMetadataDialog` para que los stickers animados se reproduzcan fluidamente en la cuadricula y vistas previas en vez de renderizarse vacios por limitacion de `BitmapFactory`.
- Soporte y Generador de Stickers Animados (`AnimatedWebpEncoder`):
  - Extraccion real de fotogramas de GIFs animados mediante `android.graphics.Movie`.
  - Extraccion fluida de fotogramas de video con `MediaMetadataRetriever` usando `OPTION_CLOSEST`.
  - Muxer puro en Kotlin para formato WebP Extendido (`RIFF...WEBP`) con chunks `VP8X`, `ANIM` y `ANMF` (sub-chunks `ALPH` y `VP8`/`VP8L`).
  - Compresion adaptativa WebP para asegurar que cada sticker animado cumpla estrictamente con el limite maximo de 500 KB de WhatsApp.
- Selector Multimedia Inteligente (`PackDetailScreen`):
  - Actualizado para solicitar `PickVisualMedia.ImageAndVideo` cuando el paquete activo es animado (`isAnimated == true`).
- Integracion en ViewModel y Flujo de Exportacion:
  - Enrutamiento transparente en `StickerViewModel` pasando el flag `isAnim` durante la adicion y edicion de stickers.
  - Exportacion coherente con `animated_sticker_pack: true` en `contents.json` y `1` en `StickerContentProvider`.
- Identidad Visual y Logo Oficial:
  - Generacion de conjunto completo de mipmaps (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) para `ic_launcher.png`, `ic_launcher_round.png` y `ic_launcher_foreground.png` a partir de `logo-zinko-stickers-cuadrado-512x512.png`.
  - Configuracion de adaptive icon oficial con fondo `#0373B5` y safe zone de 72dp centrada.
  - Integracion del logo oficial en la barra superior de `PackListScreen`.
- Pantalla de Vista Previa de WhatsApp (`PackPreviewScreen`):
  - Simulacion interactiva de conversacion de WhatsApp con burbujas de chat, hora y doble check azul.
  - Alternador de fondos de WhatsApp en vivo: WhatsApp Claro (`#EFEAE2`), WhatsApp Oscuro (`#0B141A`), Verde WhatsApp (`#075E54`) y Rejilla de Transparencia checkerboard.
  - Modo alternativo de bandeja/teclado de stickers en cuadrícula 4x4.
  - Inspector tecnico interactivo al tocar cualquier sticker: resolucion 512x512, tamano exacto en KB (<100 KB), emojis asociados, accesibilidad y accesos rapidos a recorte o metadatos.
  - Enlaces de acceso directo a la vista previa desde `PackDetailScreen`, `PackMetadataReviewScreen` y navegacion de la app.
- Optimizacion de Memoria y Manejo de Errores:
  - `loadSampledBitmap`: submuestreo eficiente con `inSampleSize` y rescate activo ante `OutOfMemoryError` con reintento a doble submuestreo para fotos de camara de alta resolucion (>10-50 MP).
  - `compressToWebpUnderLimit`: algoritmo adaptativo de calidad con garantia estricta de salida por debajo de 98 KB (<100 KB).
- Pruebas Unitarias:
  - 26 pruebas unitarias ejecutadas y superadas al 100%, incluyendo pruebas completas de persistencia en `StickerPackRepositoryTest`.

## Decisiones y Consideraciones Activas
- Separacion de persistencia de metadatos y almacenamiento fisico de WebP: Los archivos de imagen se conservan en el directorio interno seguro `files/stickers` mientras que `packs_metadata.json` orquesta la relacion relacional y metadatos de los paquetes.
- Compatibilidad de testing en JVM: Se desacoplo la resolucion de URIs en el repositorio mediante inyeccion de funciones puras, garantizando pruebas reproducibles tanto en entornos locales como de CI/CD.
