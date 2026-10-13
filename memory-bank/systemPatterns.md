# System Patterns: Zinko Stickers

## Arquitectura General
- **Patron arquitectonico**: MVVM (Model-View-ViewModel) con arquitectura limpia por capas.
- **UI Toolkit**: Android Jetpack Compose (100% declarativo, sin vistas XML).
- **Manejo de Estado**: StateFlow / SharedFlow en ViewModel consumidos reactivamente por Compose (`collectAsStateWithLifecycle`).

## Capas del Sistema
1. **Presentacion (UI & ViewModels)**:
   - Pantallas Compose organizadas por caracteristicas (`PackListScreen`, `PackDetailScreen`, `CropScreen`).
   - ViewModels dedicados para manejar estado de UI, validaciones de negocio y llamadas a casos de uso/repositorios.
2. **Dominio (Modelos y Logica de Negocio)**:
   - Modelos de datos: `StickerPack`, `Sticker`, `StickerMetadata`.
   - Validadores: Verificacion de limites de peso (<100 KB), resolucion (512x512), limites de coleccion (3-30 items).
3. **Procesamiento de Imagenes (Media Processing Engine)**:
   - Pipeline de recorte 1:1 y redimensionamiento exacto a 512x512 px.
   - Modulo de conversion/compresion a WebP con optimizacion de calidad dinamica para mantener el archivo bajo 100 KB (estaticos) y bajo 485 KB (animados).
   - **Pipeline de Stickers Animados (`AnimatedWebpEncoder`)**:
     - Decodificacion secuencial por hardware con `MediaCodec` + `MediaExtractor` resolviendo GOPs extremos de plataformas sociales (TikTok, Twitter) sin congelamientos de fotogramas.
     - Curva adaptativa de FPS (hasta 15 FPS para <=2.5s; ~8.5 FPS para hasta 9.6s, maximo 70 fotogramas reales a velocidad 1.0x sin recortes arbitrarios).
     - Compresion WebP adaptativa en 4 etapas: ajuste fino de calidad, reescalado adaptativo 0.85x en lienzo transparente (ahorro del 35% reteniendo 100% de cuadros), diezmado con cadencia protegida (<=95 ms / >=10.5 FPS) y guarda final estricta a 90 ms (<485 KB garantizado).
     - Ensamblado directo del contenedor binario RIFF WebP (chunks VP8X, ANIM loop=0 y ANMF con sub-chunks ALPH/VP8).
     - Ver especificacion completa en `Animated_Sticker_Pipeline_Specification.md`.
4. **Integracion con WhatsApp (ContentProvider & IPC)**:
   - Implementacion de `StickerContentProvider` compatible con la API de WhatsApp Stickers.
   - Generacion y serializacion de `contents.json`.
   - Lanzador de Intent para comunicar con la aplicacion de WhatsApp instalada.

## Decisiones Tecnicas Clave
- **Sin dependencias pesadas no auditadas**: Procesamiento de imagen con APIs nativas de Android (`Bitmap`, `Canvas`, `ImageDecoder`, `MediaCodec`, `MediaExtractor`).
- **Manejo asincrono**: Kotlin Coroutines y Dispatchers dedicados (`Dispatchers.IO` / `Dispatchers.Default`) para procesamiento de bitmaps y lectura/escritura de archivos sin congelar la UI.
- **Normalizacion de medios**: Normalizacion binaria previa de marcas de contenedor MP4 (`normalizeMp4Brands`) para compatibilidad con Stagefright.
- **Inyeccion de Dependencias**: Hilt / Dagger para modularidad y testabilidad.
