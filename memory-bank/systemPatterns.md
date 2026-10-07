# System Patterns: StickerForge

## Arquitectura General
- **Patron arquitectonico**: MVVM (Model-View-ViewModel) con arquitectura limpia por capas.
- **UI Toolkit**: Android Jetpack Compose (100% declarativo, sin vistas XML).
- **Manejo de Estado**: StateFlow / SharedFlow en ViewModel consumidos reactivamente por Compose (`collectAsStateWithLifecycle`).

## Capas del Sistema
1. **Presentacion (UI & ViewModels)**:
   - Pantallas Compose organizadas por caracteristicas (Home/Pack List, Editor/Crop, Pack Detail/Preview).
   - ViewModels dedicados para manejar estado de UI, validaciones de negocio y llamadas a casos de uso/repositorios.
2. **Dominio (Modelos y Logica de Negocio)**:
   - Modelos de datos: `StickerPack`, `Sticker`, `StickerMetadata`.
   - Validadores: Verificacion de limites de peso (<100 KB), resolucion (512x512), limites de coleccion (3-30 items).
3. **Procesamiento de Imagenes (Media Processing Engine)**:
   - Pipeline de recorte 1:1 y redimensionamiento exacto a 512x512 px.
   - Modulo de conversion/compresion a WebP con optimizacion de calidad dinamica para mantener el archivo bajo 100 KB.
4. **Integracion con WhatsApp (ContentProvider & IPC)**:
   - Implementacion de `StickerContentProvider` compatible con la API de WhatsApp Stickers.
   - Generacion y serializacion de `contents.json`.
   - Lanzador de Intent para comunicar con la aplicacion de WhatsApp instalada.

## Decisiones Tecnicas Clave
- **Sin dependencias pesadas no auditadas**: Procesamiento de imagen con APIs nativas de Android (`Bitmap`, `Canvas`, `ImageDecoder`) o librerias estables oficiales.
- **Manejo asincrono**: Kotlin Coroutines y Dispatchers dedicados (`Dispatchers.IO` / `Dispatchers.Default`) para procesamiento de bitmaps y lectura/escritura de archivos sin congelar la UI.
- **Inyeccion de Dependencias**: Hilt / Dagger para modularidad y testabilidad.
