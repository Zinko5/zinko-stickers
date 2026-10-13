# Active Context: Zinko Stickers

## Foco Actual
- Soporte robusto y exhaustivo para GIFs y videos de Twitter / X en `TwitterMediaResolver` y `AnimatedWebpEncoder`.
- Resolucion definitiva del fallo en GIFs de Twitter:
  - Hallazgo y causa raiz comprobada: Twitter muxea los GIFs (MP4s silenciosos de bucle corto) utilizando su muxer interno "Twitter-vork muxer". Dicho muxer escribe la cabecera de la caja `ctts` (Composition Time to Sample) con `version=0, flags=1` (0x00000001). En Android AOSP `libstagefright/SampleTable.cpp`, la funcion `setCompositionTimeToSampleParams` comprueba estrictamente:
    `if (U32_AT(header) != 0) return ERROR_MALFORMED;`
    Dado que `U32_AT(header)` es 0x00000001, Stagefright aborta la lectura de metadatos (`readMetaData() != OK`), causando que `countTracks()` devuelva 0 pistas y que `MediaMetadataRetriever` y `MediaExtractor` fallen por completo en el dispositivo real.
  - Solucion aplicada en `TwitterMediaResolver.normalizeMp4Brands`:
    1) Escanea el archivo MP4 y localiza la caja `ctts`.
    2) Si los 4 bytes de version y flags (`verFlags`) son distintos de 0, los sobreescribe a `0x00000000`, eliminando el rechazo de Stagefright.
    3) Mantiene la normalizacion de `ftyp` (`iso5`/`iso6` a `isom`/`mp42`).
  - Robustez adicional en `AnimatedWebpEncoder`:
    - Incorporada inicializacion multi-estrategia con re-instanciacion limpia para `MediaExtractor` (intento 1: FileDescriptor simple, intento 2: FileDescriptor con offset/longitud, intento 3: absolutePath, intento 4: context+uri).
    - Incorporada estrategia con FileDescriptor simple en `MediaMetadataRetriever`.
  - 43 pruebas unitarias superadas al 100%. Debug APK generado con exito.

## Cambios Recientes
- Filtro de Paquetes No Exportados ("Sin exportar"):
  - Incorporado el valor `NOT_EXPORTED` en `PackFilter` y en `StickerUiState.filteredPacks` (`!it.isExportedToWhatsApp`).
  - Anadido chip "Sin exportar (N)" en `PackFilterChipsRow` con color de aviso ámbar (`WarningAmber`).
  - Habilitado scroll horizontal ergonómico (`horizontalScroll(rememberScrollState())`) en la fila de filtros para acomodar cómodamente todas las opciones en pantallas de cualquier tamaño.
- Importación Directa de Video/GIF desde Twitter / X (`TwitterMediaResolver`):
  - Creación de `TwitterMediaResolver` en `com.stickerforge.app.domain`:
    - Detección de enlaces de tweets (`x.com`, `twitter.com`, `fxtwitter.com`, etc.) y enlaces directos de video.
    - Resolución escalonada: consulta JSON a FxTwitter, respaldo mediante API de sindicación pública de Twitter con cálculo algorítmico de token, y resolución de redirección por subdominio directo.
    - Descarga segura mediante streaming con verificación estricta de tamaño máximo (50 MB), timeouts y limpieza de archivos temporales.
  - Método `addTwitterMediaToActivePack` en `StickerViewModel`: descarga y convierte en segundo plano a WebP animado de WhatsApp.
  - Asignación automática del emoji `✖️` como emoji principal y texto de accesibilidad ("twitter x @usuario").
- Descubrimiento de Emojis y Buscador en `StickerMetadataDialog`:
  - Incorporación del emoji `✖️` en las categorías "Populares" y "Objetos y Símbolos".
  - Nuevo campo de búsqueda de emojis en tiempo real con catálogo de etiquetas ("x", "twitter", "cruz", "fuego", "gato", etc.).
- Permisos:
  - Añadido permiso `android.permission.INTERNET` en `AndroidManifest.xml` para permitir descargas de medios.
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
