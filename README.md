# Zinko Stickers

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9+-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue.svg)](https://developer.android.com/about/versions/14)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Aplicacion nativa para Android disenada para crear, personalizar, respaldar y exportar paquetes de stickers tanto estaticos como animados a WhatsApp, cumpliendo con los estandares y especificaciones tecnicas oficiales de la plataforma.

---

## Descripcion

Zinko Stickers permite transformar fotografias, videos y GIFs de tu galeria en stickers optimizados para WhatsApp. La aplicacion procesa los archivos multimedia de forma eficiente garantizando compatibilidad total, fondos transparentes, resolucion estricta de 512x512 pixeles y control riguroso de peso, evitando errores de exportacion o rechazos por parte de WhatsApp.

Ademas, incluye un sistema completo de respaldo e importacion/exportacion en archivos comprimidos `.zip`, lo que permite conservar o compartir colecciones enteras entre dispositivos sin depender de servidores externos ni perder configuraciones.

---

## Descarga e Instalacion

Para instalar y probar la aplicacion directamente en tu dispositivo Android:

1. Ve a la seccion **[Releases](../../releases)** del repositorio.
2. Descarga el archivo `app-release.apk` adjunto en la ultima version disponible.
3. Abre el archivo descargado en tu dispositivo Android.
   - Si el sistema lo solicita, habilita el permiso de *Instalar aplicaciones de fuentes desconocidas* para tu navegador o explorador de archivos.
   - Si Google Play Protect muestra un aviso preventivo (*"App desconocida"*), selecciona **Mas detalles** y luego **Instalar de todas formas**.

---

## Caracteristicas Principales

- **Stickers Estaticos y Animados**:
  - Creacion de stickers estaticos a partir de JPG, PNG y WebP (<100 KB).
  - Creacion de stickers animados a partir de videos (MP4, WebM) y GIFs animados, codificados en formato Animated WebP puro (<500 KB, loop infinito, duracion <= 6s).
  - Reproduccion y visualizacion fluida de stickers animados en tiempo real en la interfaz de usuario.
- **Procesamiento Preciso y Recorte**:
  - Lienzo estricto de 512x512 pixeles.
  - Modo de escalado proporcional (FIT) que preserva bordes transparentes.
  - Herramienta de recorte con zoom, desplazamiento y rotacion.
- **Gestion de Paquetes**:
  - Organizacion de paquetes con nombres y autores personalizados.
  - Control de limites de WhatsApp (3 a 30 stickers por paquete).
  - Distincion clara entre paquetes estaticos y animados.
  - Generacion automatica o personalizada del icono de bandeja (*tray icon*) de 96x96 pixeles (<50 KB).
  - Indicador visual sutil de paquetes ya exportados a WhatsApp ("En WhatsApp" / "Instalado") en la pantalla principal.
- **Exportacion Directa a WhatsApp**:
  - Exportacion oficial mediante `StickerContentProvider` y los Intents de WhatsApp Stickers API.
  - Deteccion automatica y selector para WhatsApp y WhatsApp Business.
  - Boton de exportacion rapida en cada tarjeta de la pantalla principal sin necesidad de abrir el editor paquete por paquete.
  - Simulador de chat interactivo con fondos claros, oscuros y verde de WhatsApp para previsualizar stickers antes de exportar.
- **Sistema de Respaldo (.zip) Portatil**:
  - Exportacion de todos o de paquetes seleccionados a un archivo `.zip` que contiene imagenes WebP, iconos de bandeja y un `manifest.json` detallado.
  - Restauracion completa e importacion segura con proteccion activa contra ataques de *Zip Slip* y resolucion automatica de conflictos de identificadores.
  - Especificacion tecnica documentada en [Sticker_Pack_Backup_Specification.md](Sticker_Pack_Backup_Specification.md).

---

## Especificaciones Tecnicas de WhatsApp

| Parametro | Requerimiento Oficial | Manejo en Zinko Stickers |
|---|---|---|
| Resolucion del sticker | 512 x 512 px | Redimensionado exacto con aspect ratio preservado y fondo transparente |
| Peso maximo (Estatico) | Menor a 100 KB | Compresion adaptativa WebP |
| Peso maximo (Animado) | Menor a 500 KB | Codificador WebP animado con compresion de fotogramas adaptativa |
| Icono de bandeja (Tray icon) | 96 x 96 px (<50 KB) | Generacion PNG de 96x96 px con canal alfa |
| Tamano del paquete | 3 a 30 stickers | Validacion estricta en interfaz y exportacion |
| Homogeneidad | Solo estaticos o solo animados | Separacion forzada por tipo de paquete |
| Formato de manifiesto | contents.json estricto | Generador de manifiesto validado para WhatsApp |

---

## Arquitectura del Proyecto

El proyecto sigue una arquitectura limpia basada en **MVVM (Model-View-ViewModel)** y flujo unidireccional de datos (UDF).

```text
app/src/main/java/com/stickerforge/app/
├── data/               # Repositorios y persistencia local JSON
│   └── StickerPackRepository.kt
├── domain/             # Casos de uso, procesamiento multimedia y respaldos
│   ├── AnimatedWebpEncoder.kt
│   ├── ContentsJsonGenerator.kt
│   ├── StickerPackBackupManager.kt
│   ├── StickerProcessor.kt
│   └── WhatsAppExporter.kt
├── model/              # Modelos de datos del dominio
│   ├── CropParameters.kt
│   ├── PackStickerItem.kt
│   ├── ProcessedSticker.kt
│   └── StickerPack.kt
├── provider/           # ContentProvider oficial de WhatsApp Stickers
│   ├── StickerContentProvider.kt
│   ├── StickerContentProviderConstants.kt
│   └── StickerPackStorage.kt
├── ui/                 # Capa de presentacion en Jetpack Compose
│   ├── components/     # Componentes reutilizables (dialogos, rejillas, crop)
│   ├── screens/        # Pantallas (PackListScreen, PackDetailScreen, CropScreen, PackPreviewScreen, etc.)
│   ├── theme/          # Sistema de diseno y colores Material 3
│   └── viewmodel/      # Gestion de estado reactivo y acciones de UI
└── util/               # Utilidades de bajo nivel y helpers de bitmap
    └── ImageUtils.kt
```

### Tecnologias Utilizadas

- **Lenguaje**: Kotlin 1.9+
- **UI Toolkit**: Jetpack Compose con Material 3
- **Concurrencia**: Coroutines y StateFlow (`collectAsStateWithLifecycle`)
- **Carga y Decodificacion Multimedia**: Coil Compose con soporte para Animated WebP y GIF
- **Testing**: JUnit 4, Mockito (32 pruebas unitarias automatizadas)

---

## Compilacion Local

### Requisitos Previos

- **JDK**: Version 17
- **Android SDK**: Minimo API 26 (Android 8.0 Oreo), Target API 34 (Android 14)
- **Gradle**: Wrapper incluido en el proyecto (Gradle 8.2+)

### Instrucciones

1. Clonar el repositorio:
   ```bash
   git clone git@github.com:Zinko5/zinko-stickers.git
   cd zinko-stickers
   ```

2. Ejecutar pruebas unitarias:
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. Compilar APK Release:
   ```bash
   ./gradlew assembleRelease
   ```
   El APK generado se ubicara en: `app/build/outputs/apk/release/app-release.apk`.

---

## Descargo de Responsabilidad (Disclaimer)

Este proyecto es un software independiente de codigo abierto y no esta afiliado, asociado, autorizado, respaldado ni conectado oficialmente de ninguna manera con WhatsApp LLC, Meta Platforms, Inc., ni con ninguna de sus subsidiarias o afiliados. El nombre "WhatsApp", asi como sus marcas, insignias e imagenes relacionadas, son marcas comerciales registradas de sus respectivos propietarios.

---

## Licencia

Este proyecto se distribuye bajo la licencia **MIT**. Para mas detalles, consulta el archivo [LICENSE](LICENSE).
