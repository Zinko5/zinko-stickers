# Product Context: Zinko Stickers

## Por que existe este proyecto
Actualmente, los usuarios de WhatsApp que desean crear stickers a partir de fotos personales se enfrentan a aplicaciones llenas de publicidad invasiva, interfaces confusas o herramientas que generan stickers con parametros incorrectos (tamano indebido, exceso de peso en KB, falta de transparencia o ausencia de metadatos), lo que causa fallos silenciosos o rechazos durante la exportacion a WhatsApp.

## Problemas que resuelve
- **Fallos de exportacion**: WhatsApp requiere especificaciones muy estrictas (WebP 512x512 px, <100 KB, transparencia, paquete de 3 a 30 elementos, metadatos y emojis). StickerForge automatiza y garantiza el cumplimiento tecnico.
- **Complejidad innecesaria**: Proporciona un flujo intuitivo y guiado en pocos pasos: Seleccionar -> Recortar -> Configurar metadatos -> Previsualizar -> Exportar.
- **Buscabilidad**: Permite asociar hasta 3 emojis y etiquetas para que los stickers sean facilmente localizables dentro del selector de WhatsApp.

## Como debe funcionar el flujo de usuario
1. **Seleccion**: El usuario abre la app y selecciona una o varias imagenes de su galeria (JPG, PNG, WebP).
2. **Edicion y recorte**: Ajusta la imagen en un encuadre 1:1, asegurando un escalado a 512x512 px y gestion de transparencia.
3. **Metadatos**: Define nombre del paquete/sticker, asocia emojis descriptivos (1 a 3) y genera texto de accesibilidad.
4. **Validacion y previsualizacion**: Revisa el paquete y la apariencia visual de los stickers antes de enviar.
5. **Exportacion**: La app envia el paquete directamente a WhatsApp mediante el ContentProvider / Intent oficial.

## Experiencia de Usuario (UX)
- Interfaz moderna, reactiva y limpia basada en Jetpack Compose.
- Feedback visual inmediato durante el procesamiento y compresion de imagenes.
- Prevencion de errores proactiva (por ejemplo, advertir o recomprimir automaticamente si un sticker supera 100 KB o si el paquete tiene menos de 3 elementos).
