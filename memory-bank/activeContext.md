# Active Context: StickerForge

## Foco Actual
- Incorporacion de los requerimientos de Stickers Zinko completada:
  - Nombre de la app: "Stickers Zinko".
  - Seleccion multiple de imagenes desde la galeria (`PickMultipleVisualMedia(maxItems = 30)`).
  - Preservacion de bordes transparentes (escalado FIT para fotos no cuadradas, sin cortes no deseados).
  - Prevencion estricta de mezclar stickers animados y estaticos en el mismo paquete.
  - Flujo directo a cuadricula del paquete con boton de editar por cada sticker.
- Listo para avanzar con la Fase 3: Asignacion de metadatos (nombre, selector de emojis, texto de accesibilidad) y generacion formal de `contents.json`.

## Cambios Recientes
- Modificacion de `StickerProcessor` para usar `minOf` (FIT completo con bordes transparentes) y mapeo proporcional de encuadre.
- Creacion de `PackDetailScreen` con cuadricula responsiva de stickers, badges de estado y acciones (editar/eliminar).
- Creacion de `CanvasGrids` reutilizable con patron de cuadros de transparencia.
- Actualizacion de `StickerViewModel` con control de paquetes, seleccion masiva y validacion de tipos (animado vs estatico).
- Creacion de pruebas unitarias para `StickerPack` y validacion de reglas de negocio.
- Configuracion del repositorio de GitHub: definicion de `.gitignore` especifico para Android/Gradle y creacion de `README.md` tecnico y documentado.

## Proximos Pasos Inmediatos
1. Implementar Fase 3: Formulario de metadatos del sticker (nombre, selector de hasta 3 emojis, texto de accesibilidad de hasta 125 caracteres).
2. Estructuracion del paquete de stickers (coleccion de 3 a 30 stickers).
3. Generador del archivo `contents.json` con el schema requerido por WhatsApp.
5. Implementar Fase 4: ContentProvider e integracion con el Intent oficial de WhatsApp.
6. Implementar Fase 5: Vista previa, gestion de errores, recompresion automatica y validaciones.

## Decisiones y Consideraciones Activas
- Definir la estrategia para el manejo de fondos transparentes cuando la imagen de entrada contiene fondo solido.
- Asegurar compatibilidad con Android 13+ respecto a la seleccion de medios utilizando el nuevo Photo Picker del sistema (`PickVisualMedia`).
