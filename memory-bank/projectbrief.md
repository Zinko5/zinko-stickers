# Project Brief: StickerForge

## Descripcion General
StickerForge es una aplicacion nativa para Android disenada para permitir a los usuarios crear paquetes de stickers personalizados a partir de imagenes de su galeria y exportarlos directamente a WhatsApp cumpliendo estrictamente con las especificaciones tecnicas oficiales.

## Objetivos Principales
- Facilitar la creacion rapida (<30 seg por sticker) y sin friccion de stickers para WhatsApp.
- Cumplir estrictamente con los estandares de WhatsApp (>95% de tasa de aceptacion en exportaciones):
  - Formato WebP transparente.
  - Dimensiones exactas de 512x512 pixeles.
  - Peso maximo de 100 KB por sticker.
  - Paquetes de 3 a 30 stickers.
  - Generacion de metadatos validos (`contents.json`, emojis asociados, accesibilidad).
- Exportacion directa mediante la API oficial de stickers de WhatsApp (`WhatsApp Sticker API`).

## Alcance del Proyecto (Scope)
- **Incluido en v1**:
  - Seleccion de imagenes desde la galeria (JPG, PNG, WebP).
  - Recorte cuadrado (1:1) y ajuste a lienzo 512x512 con soporte de transparencia.
  - Asignacion de metadatos (nombre, hasta 3 emojis, texto de accesibilidad de hasta 125 caracteres).
  - Empaquetado y validacion de tamano/formato.
  - Vista previa interactiva previa a la exportacion.
  - Exportacion oficial a WhatsApp.
- **Fuera de alcance (v1)**:
  - Edicion avanzada (capas, filtros complejos, dibujo manual).
  - Stickers animados (solo estaticos en v1).
  - Tienda comunitaria o marketplace de stickers.

## Criterios de Exito
- Exportaciones aceptadas por WhatsApp en >95% de los intentos.
- Tiempo medio de creacion por sticker inferior a 30 segundos.
- >60% de retencion de usuarios creando mas de 3 stickers en su primera sesion.
