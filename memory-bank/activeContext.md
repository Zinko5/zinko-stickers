# Active Context: Zinko Stickers

## Foco Actual
- Optimizacion ergonomica de la pantalla de Vista Previa de WhatsApp (`PackPreviewScreen`) y eliminacion del desperdicio de espacio vertical en la cabecera.
- Motor de muestreo adaptativo de video a WebP animado (`AnimatedWebpEncoder`): preservacion de duracion completa (hasta 9.6s conforme al limite estricto <10s de WhatsApp), distribucion temporal continua y compresion adaptativa bajo 500 KB.
- Pruebas unitarias incrementadas a 33 (100% exitosas).

## Cambios Recientes
- Rediseño Compacto de Vista Previa (`PackPreviewScreen`):
  - Reemplazo de la cabecera sobredimensionada con chips de texto de varias lineas por `CompactPreviewControlsBar`: conmutador [Chat | Teclado] a la izquierda y swatches circulares de color de temas (Claro, Oscuro, Verde, Malla/Transparente) a la derecha, en una sola fila de ~42dp.
  - Correccion de espaciado y ventana de insets en `Scaffold` (`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) y `TopAppBar` para erradicar el vacio superior.
  - La ficha tecnica del sticker (`StickerTechnicalInspector`) ahora es desplegable y opcional (con boton de informacion en la barra superior o al pulsar un sticker), dejando el lienzo de conversacion despejado.
  - Correccion del limite de peso en la ficha tecnica: ahora distingue dinamicamente entre stickers animados (<500 KB) y estaticos (<100 KB), mostrando el peso en verde sin falsas advertencias de limite.
  - Burbuja informativa en `ChatSimulationView` redisenada en formato sutil tipo chip.
- Muestreo Continuo y Fluidez Incrementada en Stickers Animados (`AnimatedWebpEncoder`):
  - Erradicado el limite fijo que truncaba videos a los 2 primeros segundos (24 cuadros a 12 fps).
  - Elevada la tasa de fotogramas objetivo: hasta 10-14 FPS (videos de 7s se procesan a 10 FPS con ~70 fotogramas en vez de los 5 FPS anteriores).
  - Limite maximo de fotogramas aumentado de 36 a 70 cuadros, aprovechando el margen de peso disponible (<500 KB) para eliminar el efecto trabado/entrecortado.
  - Ajuste de compresion WebP con pasos mas finos (de 8 puntos) para retener la maxima fidelidad visual posible manteniendo el archivo estrictamente por debajo de 485 KB.
  - Muestreo temporal uniforme a lo largo del 100% de la duracion del video (hasta 9.6s maximo oficial de WhatsApp).
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
- Preparacion de Release Publico:
  - Creacion del archivo `LICENSE` bajo los terminos oficiales de la licencia MIT.
  - Correccion de regla de respaldo en `data_extraction_rules.xml` (atributo `domain="file"`) para resolver validaciones estrictas de Android Lint en ensamblados de produccion.
  - Configuracion de `signingConfig` en `release` para generar binarios APK instalables de produccion.
  - Perfeccionamiento y actualizacion completa de `README.md` con guias de instalacion para usuarios, arquitectura, caracteristicas, disclaimer de WhatsApp y badge de licencia.
  - Compilacion exitosa del binario APK de produccion en `app/build/outputs/apk/release/app-release.apk` (12 MB).
- Pruebas Unitarias y Build:
  - 32 pruebas unitarias ejecutadas y superadas al 100%.

## Decisiones y Consideraciones Activas
- Aislamiento de respaldo vs exportacion a WhatsApp: La exportacion a WhatsApp sigue orientada a su ContentProvider e Intent oficial; el respaldo ZIP es un sistema de respaldo completo, portable y compatible entre instalaciones o usuarios.
- Seguridad en descompresion: Rechazo estricto de entradas maliciosas con secuencias `..` o rutas absolutas durante la extraccion de archivos comprimidos.
- Resolucion de conflictos: Si un identificador ya existe localmente, se genera un sufijo aleatorio unico para evitar sobrescribir colecciones existentes del usuario.
