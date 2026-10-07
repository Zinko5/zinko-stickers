# Stickers Zinko

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9+-blue.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue.svg)](https://developer.android.com/about/versions/14)

Aplicacion nativa para Android disenada para crear, personalizar y exportar paquetes de stickers a WhatsApp cumpliendo rigurosamente con los estandares y especificaciones tecnicas oficiales de la plataforma.

---

## Descripcion

Stickers Zinko permite a los usuarios transformar facilmente fotografias e ilustraciones de su galeria en stickers optimizados para WhatsApp. La aplicacion procesa las imagenes de manera automatica y eficiente para garantizar compatibilidad total, transparencia y alta calidad, evitando fallos de exportacion o rechazos debidos a limitaciones tecnicas de formato o tamano.

---

## Caracteristicas Principales

- **Seleccion Flexible**: Soporte para formatos JPG, PNG y WebP con seleccion individual o multiple de hasta 30 imagenes a traves del Photo Picker moderno de Android.
- **Procesamiento Preciso**: 
  - Ajuste a lienzo estricto de 512x512 pixeles.
  - Modo de escalado proporcional (FIT) que preserva bordes transparentes sin recortar partes esenciales de la imagen.
  - Conversion a WebP con compresion adaptativa para mantener el peso estrictamente por debajo de los 100 KB por sticker.
- **Gestion de Paquetes**:
  - Organizacion en colecciones de 3 a 30 stickers (limites requeridos por WhatsApp).
  - Validacion estricta para evitar la mezcla de elementos estaticos y animados en un mismo paquete.
  - Generacion automatica de icono de bandeja (tray icon) de 96x96 pixeles (<50 KB).
- **Asignacion de Metadatos**:
  - Asociacion de hasta 3 emojis representativos por sticker para facilitar su busqueda en WhatsApp.
  - Descripciones de texto para accesibilidad (hasta 125 caracteres).
  - Estructuracion y serializacion automatica del archivo de manifiesto `contents.json`.
- **Integracion Oficial con WhatsApp**:
  - Exportacion directa mediante `StickerContentProvider` y los Intents oficiales de WhatsApp Stickers API.

---

## Especificaciones Tecnicas de WhatsApp

| Parametro | Requerimiento Oficial | Manejo en Stickers Zinko |
|---|---|---|
| Resolucion del sticker | 512 x 512 px | Redimensionado exacto con preservacion de aspect ratio y relleno transparente |
| Tamano maximo por sticker | Menos de 100 KB | Compresion WebP optimizada |
| Icono de bandeja (Tray icon) | 96 x 96 px (<50 KB) | Generacion automatica a partir del pack |
| Formato de archivo | WebP con canal alfa | Codificacion directa WebP |
| Tamano del paquete | 3 a 30 stickers | Validacion previa a exportacion |
| Homogeneidad | Solo estaticos o solo animados | Validacion de tipo en tiempo de adicion |

---

## Arquitectura y Tecnologias

El proyecto sigue una arquitectura limpia basada en **MVVM (Model-View-ViewModel)** y flujo unidireccional de datos (UDF).

```
app/src/main/java/com/stickerforge/app/
├── domain/             # Casos de uso y motor de procesamiento de imagenes
│   └── StickerProcessor.kt
├── model/              # Modelos de dominio y estado
│   ├── CropParameters.kt
│   ├── ImageMetadata.kt
│   ├── PackStickerItem.kt
│   ├── ProcessedSticker.kt
│   └── StickerPack.kt
├── ui/                 # Capa de presentacion (Jetpack Compose)
│   ├── components/     # Componentes visuales reutilizables
│   ├── screens/        # Pantallas (PackDetail, CropScreen, StickerPreview)
│   ├── theme/          # Sistema de diseno (Material Design 3)
│   └── viewmodel/      # Gestion de estado y logica reactiva
└── util/               # Utilidades de bajo nivel y helpers de bitmap
    └── ImageUtils.kt
```

### Stack Tecnico

- **Lenguaje**: Kotlin 1.9+
- **UI Toolkit**: Jetpack Compose con Material 3
- **Concurrencia**: Kotlin Coroutines y StateFlow (`collectAsStateWithLifecycle`)
- **Carga de Imagenes**: Coil Compose
- **Testing**: JUnit 4, Mockito, Espresso, Compose UI Testing

---

## Requisitos Previos

- **JDK**: Version 17
- **Android SDK**: Minimo API 26 (Android 8.0 Oreo), Target API 34 (Android 14)
- **Gradle**: Wrapper incluido en el repositorio (Gradle 8.2+)

---

## Compilacion y Ejecucion

### Clonar el repositorio
```bash
git clone git@github.com:Zinko5/zinko-stickers.git
cd zinko-stickers
```

### Ejecutar pruebas unitarias
```bash
./gradlew testDebugUnitTest
```

### Compilar APK en modo depuracion
```bash
./gradlew assembleDebug
```
El archivo APK generado se encontrara en: `app/build/outputs/apk/debug/app-debug.apk`.

---

## Licencia

Este proyecto esta bajo los terminos de la licencia definida por el autor.
