# Active Context: Zinko Stickers

## Foco Actual
- Finalizada con exito la Fase 4: Implementacion de `StickerContentProvider`, almacenamiento sincronizado de paquetes (`StickerPackStorage`), gestor de exportacion WhatsApp (`WhatsAppExporter`), interfaz de exportacion con resultado en `PackMetadataReviewScreen` y pruebas unitarias.
- Preparacion para la Fase 5: Pulido y validaciones finales (manejo de imagenes pesadas >10 MB, previsualizacion en chat simulado y optimizacion adaptativa).

## Cambios Recientes
- Configuracion del sistema y AndroidManifest:
  - Definicion de `buildConfig = true`, authority `com.stickerforge.app.stickercontentprovider` inyectada como constante y placeholder de manifiesto.
  - Bloque `<queries>` para visibilidad de paquetes en Android 11+ (`com.whatsapp`, `com.whatsapp.w4b`, y accion `ENABLE_STICKER_PACK`).
  - Registro de `<provider>` para `StickerContentProvider` con permiso de lectura `com.whatsapp.sticker.READ` y `android:exported="true"`.
- Contrato y ContentProvider de WhatsApp (`StickerContentProviderConstants` y `StickerContentProvider`):
  - Definicion de todas las columnas y constantes requeridas por WhatsApp (`metadata`, `metadata/*`, `stickers/*`, `stickers_asset/*/*`).
  - Implementacion de `openAssetFile` con resolucion segura a archivos internos mediante `ParcelFileDescriptor` y proteccion contra Directory Traversal.
- Sincronizacion de archivos para WhatsApp (`StickerPackStorage`):
  - Copia y sincronizacion fisica de stickers `.webp`, `tray_image_file` `.png` y generacion de `contents.json` en el directorio interno `files/packs/<identifier>`.
- Orquestador de exportacion (`WhatsAppExporter`):
  - Deteccion de instalaciones activas de WhatsApp estandar y WhatsApp Business.
  - Creacion de `Intent` oficial con accion `com.whatsapp.intent.action.ENABLE_STICKER_PACK` y extras requeridos (`sticker_pack_id`, `sticker_pack_authority`, `sticker_pack_name`).
  - Procesamiento seguro de resultados de actividad con extraccion de codigo de error (`validation_error`).
- UI y ViewModel (`PackMetadataReviewScreen` y `StickerViewModel`):
  - Integracion de tarjeta de exportacion a WhatsApp con deteccion en vivo de clientes instalados.
  - Lanzador de actividad `rememberLauncherForActivityResult` para delegar el paquete a WhatsApp y reportar confirmacion o error.
  - Notificaciones de exito y aviso de advertencia si el paquete no cumple los requisitos antes de exportar.
- Pruebas unitarias:
  - Verificacion de contratos de ContentProvider y URIs de consulta.
  - Verificacion de parseo de resultados y validacion de errores en exportacion.

## Proximos Pasos Inmediatos
1. Iniciar Fase 5: Manejo robusto de imagenes de gran tamano (>10 MB) con submuestreo eficiente para evitar `OutOfMemoryError`.
2. Proporcionar vista previa del paquete simulando la interfaz de stickers de WhatsApp.
3. Asegurar validaciones finales de integridad antes de la distribucion.

## Decisiones y Consideraciones Activas
- Directorio Traversal Prevention: `StickerContentProvider` valida canonicamente que cualquier ruta solicitada por `openAssetFile` no contenga secuencias relativas peligrosas y resida estrictamente dentro de `filesDir`.
- Compatibilidad Android 11+ (API 30+): Se definio explicitamente el bloque `<queries>` en `AndroidManifest.xml` para garantizar la resolucion de paquetes sin requerir permisos invasivos de visibilidad total.
- Robustez en WhatsApp Business: La UI permite elegir entre WhatsApp convencional y WhatsApp Business segun la disponibilidad detectada en el dispositivo.
