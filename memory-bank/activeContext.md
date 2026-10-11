# Active Context: Zinko Stickers

## Foco Actual
- Sistema integral de importacion y exportacion de respaldos (.zip) para paquetes de stickers.
- Los usuarios pueden exportar todos sus paquetes o una seleccion personalizada a un archivo ZIP con manifest.json y assets intactos (WebP, tray PNG, emojis, nombres, accesibilidad, parametros de recorte y estado de animacion).
- Restauracion e importacion completa desde archivos ZIP con proteccion contra Zip Slip, resolucion de colisiones de identificadores y persistencia en almacenamiento seguro.
- Especificacion tecnica detallada documentada en `Sticker_Pack_Backup_Specification.md`.
- Proyecto completamente operativo y verificado con 32 pruebas unitarias (100% exitosas).

## Cambios Recientes
- Administrador de Respaldos (`StickerPackBackupManager`):
  - Creacion de `exportPacksToZip`: comprime los paquetes seleccionados junto a `manifest.json`, iconos de bandeja y WebPs correspondientes.
  - Creacion de `importPacksFromZip`: descomprime y parsea `manifest.json`, valida rutas para prevenir vulnerabilidades de Path Traversal (Zip Slip), resuelve duplicados de identificadores y traslada assets al almacenamiento seguro de la aplicacion.
  - Desacoplamiento de `fileUriProvider` para permitir pruebas unitarias limpias y reproducibles en entornos JVM y CI/CD.
- Interfaz de Usuario y ViewModel (`PackListScreen` y `StickerViewModel`):
  - Botones de "Exportar Respaldo (.zip)" e "Importar Respaldo (.zip)" en el menu contextual superior de la pantalla principal.
  - Dialogo interactivo de exportacion (`ExportBackupDialog`) que permite alternar la seleccion individual de paquetes o seleccionar todos, mostrando previsualizaciones, conteo y tipos (estatico / animado).
  - Integracion de `ActivityResultContracts.CreateDocument("application/zip")` y `ActivityResultContracts.OpenDocument()`.
  - Notificaciones en `Snackbar` informando el exito o detalle de errores en importacion/exportacion.
- Documentacion Tecnica:
  - Creacion de `Sticker_Pack_Backup_Specification.md` con la especificacion completa del formato de empaquetado, estructura del archivo ZIP, esquema campo por campo de `manifest.json` y consideraciones de seguridad.
- Selector y Relleno de Emojis:
  - Modificado el autorrelleno en `StickerViewModel.autoAssignDefaultEmojisToUnlabeledStickers`: ahora asigna de manera uniforme el emoji de cuadrado blanco (⬜) en lugar de una seleccion aleatoria cuando los stickers no poseen emojis asignados.
  - Actualizado `StickerMetadataDialog` anadiendo una categoria destacada "Populares" con todos los emojis solicitados por el usuario (😂, 😊, 😮, 😭, 😡, 🐸, 💃, 👌, 🗿, 😨, ❤️, 🔥, 😴, 🤔, 🤡, 😎, 🙏, 🤦, 💀, ⬜) para seleccion rapida y manual.
- Registro y Exportacion Directa a WhatsApp desde la Pantalla Principal:
  - Anadido campo persistente `isExportedToWhatsApp` a `StickerPack` guardado y restaurado en `StickerPackRepository` y respaldos ZIP.
  - Indicador visual sutil "En WhatsApp" con icono de verificacion en la esquina superior derecha de las tarjetas en `PackListScreen`, y cambio de estado a "Instalado" en el pie de la tarjeta.
  - Boton de accion rapida (icono de envio) en cada tarjeta para exportar directamente a WhatsApp sin necesidad de abrir la pantalla de edicion de cada paquete.
  - Auto-preparacion transparente: si el paquete no tenia tray icon o emojis asignados, se generan/asignan automaticamente antes de enviar el Intent oficial.
- Pruebas Unitarias y Build:
  - 32 pruebas unitarias ejecutadas y superadas al 100%.
  - Generacion exitosa del APK de depuracion en `app/build/outputs/apk/debug/app-debug.apk`.

## Decisiones y Consideraciones Activas
- Aislamiento de respaldo vs exportacion a WhatsApp: La exportacion a WhatsApp sigue orientada a su ContentProvider e Intent oficial; el respaldo ZIP es un sistema de respaldo completo, portable y compatible entre instalaciones o usuarios.
- Seguridad en descompresion: Rechazo estricto de entradas maliciosas con secuencias `..` o rutas absolutas durante la extraccion de archivos comprimidos.
- Resolucion de conflictos: Si un identificador ya existe localmente, se genera un sufijo aleatorio unico para evitar sobrescribir colecciones existentes del usuario.
