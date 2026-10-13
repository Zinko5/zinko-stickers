# Especificacion del Pipeline de Stickers Animados: Zinko Stickers

## 1. Proposito y Alcance
Este documento define la arquitectura estandar, algoritmos, umbrales y tecnicas de procesamiento para la transformacion de cualquier fuente de video o animacion (archivos locales, TikTok, Twitter/X, o futuras fuentes) en stickers animados WebP compatibles con WhatsApp.

El objetivo central es preservar un equilibrio optimo entre **fluidez visual constante (sin efecto de diapositivas ni congelamientos)** y el **cumplimiento estricto de las restricciones de WhatsApp (<485 KB de margen de seguridad sobre el limite de 500 KB, 512x512 px, duracion <= 9.6s, loop infinito)**.

Toda nueva funcionalidad que procese video o animaciones dentro de la aplicacion debe adherirse estrictamente a este pipeline unificado implementado en [AnimatedWebpEncoder.kt](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/AnimatedWebpEncoder.kt).

---

## 2. Restricciones y Especificaciones Tecnicas de WhatsApp
Para que un sticker animado sea aceptado sin rechazos por el validador oficial de WhatsApp, debe cumplir los siguientes parametros exactos:

| Parametro | Especificacion Oficial WhatsApp | Margen Implementado en Zinko Stickers |
| :--- | :--- | :--- |
| **Formato de Archivo** | Animated WebP (Extended VP8X con chunks ANIM y ANMF) | Generado a nivel de bytes en contenedor RIFF WebP |
| **Dimensiones** | 512 x 512 pixeles exactos | 512 x 512 pixeles exactos con lienzo transparente |
| **Peso Maximo** | < 500 KB (512,000 bytes) | < 485 KB (496,640 bytes) como margen de seguridad estricto |
| **Duracion Maxima** | < 10.0 segundos | Maximo 9.6 segundos (`MAX_WHATSAPP_DURATION_MS = 9600L`) |
| **Loop** | Reproduccion en bucle infinito | Campo `loop_count = 0` en chunk `ANIM` |
| **Color de Fondo** | Transparente | RGBA `0x00000000` en chunk `ANIM` y alfa en cada fotograma |
| **Cadencia Minima / Maxima**| Minimo 8 ms por fotograma | Forzado en rango ergonomico de 60 ms a 110 ms |

---

## 3. Arquitectura del Pipeline

El pipeline consta de 5 fases secuenciales:

```
[ Fuente de Video / GIF ]
          │
          ▼
1. Ingesta y Normalizacion MP4 (ftyp / ctts)
          │
          ▼
2. Decodificacion Secuencial por Hardware (MediaExtractor + MediaCodec)
          │
          ▼
3. Muestreo Temporal Uniforme y Curva Adaptativa de FPS (1.0x sin cortes)
          │
          ▼
4. Pipeline de Compresion WebP Adaptativa en 4 Etapas (< 485 KB)
          │
          ▼
5. Ensamblado del Contenedor RIFF WebP (VP8X + ANIM + ANMF)
          │
          ▼
[ Archivo .webp animado listo para WhatsApp ]
```

---

## 4. Detalle de Fases de Implementacion

### Fase 1: Ingesta y Normalizacion de Metadatos MP4
Los contenedores MP4 descargados de plataformas sociales (Twitter/X, TikTok, Reddit, etc.) frecuentemente presentan encabezados no estandarizados que rompen el parser nativo de Android Stagefright (`SampleTable.cpp`):
- **Marcas de compatibilidad (`ftyp`)**: Marcas como `iso5` o `iso6` son reemplazadas por marcas estandar compatibles (`isom` / `mp42`).
- **Tablas de composicion temporal (`ctts`)**: Modificacion del byte de flags de 1 a 0 para prevenir errores de asignacion de memoria y rechazo del archivo en Android.

Metodo de referencia: `normalizeMp4Brands(file: File)` en [TikTokMediaResolver.kt](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/TikTokMediaResolver.kt) y [TwitterMediaResolver.kt](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/TwitterMediaResolver.kt).

---

### Fase 2: Decodificacion Secuencial por Hardware (`MediaCodec`)
Los videos procedentes de plataformas sociales suelen tener GOPs (*Group of Pictures*) extremadamente amplios (por ejemplo, 2 keyframes en 9 segundos).
- **Problema de `MediaMetadataRetriever`**: Si se busca por tiempo en GOPs amplios, el decodificador retorna repetidamente el keyframe mas cercano, provocando congelamientos de varios segundos en el sticker.
- **Solucion estandar**: Se utiliza `MediaCodec` junto con `MediaExtractor` decodificando secuencialmente fotogramas intermedios B y P.
- **Calculo de PTS relativo**: Se mide el tiempo de presentacion relativo (`relativePtsUs = currentPtsUs - firstPtsUs`) para asegurar sincronizacion uniforme a lo largo de toda la duracion del clip sin desfases.

Metodo de referencia: [AnimatedWebpEncoder.extractFramesViaMediaCodec](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/AnimatedWebpEncoder.kt#L774-L975).

---

### Fase 3: Curva Adaptativa de FPS y Duracion Completa (1.0x)
El sistema **no recorta arbitrariamente el video** a los primeros 2 o 3 segundos. Cubre el 100% de la duracion del clip (hasta un maximo de 9.6 segundos) a velocidad real (1.0x) aplicando la siguiente curva adaptativa:

| Rango de Duracion | Tasa de FPS Objetivo | Estimacion de Cuadros | Duracion por Cuadro |
| :--- | :--- | :--- | :--- |
| **0.5 s a 2.5 s** | 15.0 FPS | 16 a 37 cuadros | ~66 ms |
| **2.5 s a 4.5 s** | 13.5 FPS | 34 a 60 cuadros | ~74 ms |
| **4.5 s a 6.5 s** | 11.5 FPS | 52 a 70 cuadros | ~87 ms |
| **6.5 s a 9.6 s** | ~8.5 FPS | Maximo 70 cuadros (`MAX_STICKER_FRAMES`) | ~105 a 110 ms |

Constantes criticas:
- `MAX_WHATSAPP_DURATION_MS = 9600L`
- `MAX_STICKER_FRAMES = 70`
- Limite de duracion por cuadro ergonomica: `frameDurationMs.coerceIn(60, 110)`

---

### Fase 4: Compresion WebP Adaptativa en 4 Etapas
La regla de oro de este pipeline es **priorizar la conservacion de fotogramas antes de degradar la tasa de FPS**:

#### Etapa 1: Reduccion Progresiva de Calidad Lossy
- Se inicia con calidad 70.
- Si el tamano supera 485 KB, se decrementa la calidad en pasos de 6 hasta un piso de 20.
- Se preserva el **100% de los fotogramas y el 100% de la cadencia de FPS**.

#### Etapa 2: Reescalado Adaptativo de Lienzo (0.85x en 512x512 transparente)
- Si con calidad 20 aun supera 485 KB, se reescala el contenido visual a un factor de `0.85x` (435x435 px) y se centra sobre el lienzo transparente de 512x512 px.
- **Mecanismo de ahorro**: El fondo transparente uniforme comprime de forma extremadamente eficiente en WebP (lossless alpha), reduciendo el tamano total del archivo entre un **30% y un 40%** de inmediato.
- **Resultado**: Permite mantener el **100% de los fotogramas y la tasa de FPS completa** en videos con alto nivel de ruido o textura en bordes.

#### Etapa 3: Diezmado Gradual con Cadencia Fluida Protegida
- Si aun supera 485 KB, se reduce el conteo de fotogramas de forma controlada mediante factores de `[0.78, 0.62, 0.48, 0.38]`.
- **Regla critica anti-diapositivas**: La duracion por cuadro se fija con `minOf(currentDurationMs, 95)` (>= 10.5 FPS reales). Nunca se incrementa la duracion del cuadro a 200–300 ms, manteniendo un movimiento natural.

#### Etapa 4: Guarda Estricta Final
- Para videos con ruido extremo persistente, se fija un intervalo estricto de 90 ms (11.1 FPS) con calidad 12, garantizando matematicamente que ningun sticker supere 485 KB.

---

### Fase 5: Estructura del Contenedor Binario RIFF WebP
Implementado en [AnimatedWebpEncoder.encodeToAnimatedWebp](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/AnimatedWebpEncoder.kt#L497-L580):

1. **Header RIFF**: 12 bytes (`RIFF` + tamano total - 8 + `WEBP`).
2. **Chunk `VP8X`**: 18 bytes.
   - Flags: `0x12` (Bit 1 = Animacion `0x02`, Bit 4 = Transparencia/Alfa `0x10`).
   - Dimensiones de canvas: Ancho 511, Alto 511 (representando 512x512 px en codificacion 24 bits LE).
3. **Chunk `ANIM`**: 14 bytes.
   - Color de fondo: `0x00000000` (transparente).
   - Loop count: `0x0000` (infinito).
4. **Chunks `ANMF` (uno por fotograma)**:
   - Posicion X=0, Y=0.
   - Dimensiones: 511x511.
   - Duracion: calculada en milisegundos (`little-endian` 24 bits).
   - Flags: `0x02` (Alpha blending habilitado, sin dispose).
   - Payload: sub-chunks limpios `ALPH` + `VP8` / `VP8L` extraidos de la compresion de cada bitmap individual.
   - Byte de padding `0x00` en caso de longitud impar.

---

## 5. Directrices para Nuevas Funcionalidades
Cuando se anadan nuevas fuentes de importacion, herramientas de edicion o convertidores de animacion (por ejemplo, camara directa, GIFs locales, otras redes sociales o generadores interactivos):

1. **Canalizacion obligatoria**: Utilizar exclusivamente [AnimatedWebpEncoder.processAnimatedMedia](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/AnimatedWebpEncoder.kt#L44-L204) o [AnimatedWebpEncoder.encodeToAnimatedWebp](file:///home/zinko/publico/zinko-stickers/app/src/main/java/com/stickerforge/app/domain/AnimatedWebpEncoder.kt#L497-L580).
2. **No alterar los techos de seguridad**:
   - `SAFETY_MARGIN_BYTES = 485 * 1024` (485 KB).
   - `MAX_WHATSAPP_DURATION_MS = 9600L` (9.6 segundos).
   - `MAX_STICKER_FRAMES = 70`.
3. **Respeto a la cadencia**: Jamas permitir que un cuadro supere 110 ms de exposicion en stickers animados, ya que rompe la percepcion de fluidez del usuario.
4. **Pruebas de aceptacion**: Cada nueva fuente debe ser validada con videos de prueba verificando que el archivo generado sea menor a 485 KB y que la reproduccion en la vista previa de chat sea completamente dinamica y continua.
