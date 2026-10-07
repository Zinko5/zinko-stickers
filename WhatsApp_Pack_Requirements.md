# Requerimientos Técnicos: Paquetes de Stickers para WhatsApp

> **Fuente de verdad:** Este documento se basa en la documentación oficial de WhatsApp y en las librerías de código abierto mantenidas por la comunidad. Cualquier discrepancia con el PRD original debe resolverse a favor de este archivo.

---

## 1. Estructura del Archivo `contents.json`

El archivo `contents.json` es el manifiesto que describe el paquete. Debe residir en `app/src/main/assets/` y seguir la estructura que se detalla a continuación.

### 1.1 Campos a nivel de Paquete

| Campo | Obligatorio | Tipo | Descripción |
|-------|-------------|------|-------------|
| `identifier` | **Sí** | String | Identificador único del pack. **No puede contener puntos (`.`).** Ejemplo válido: `zinko_pack_01`. |
| `name` | **Sí** | String | Nombre visible del paquete en WhatsApp. |
| `publisher` | **Sí** | String | Nombre del autor o entidad que publica el pack. |
| `tray_image_file` | **Sí** | String | Nombre del archivo de imagen del icono del paquete (ver sección 3). |
| `stickers` | **Sí** | Array | Lista de stickers. Mínimo 3, máximo 30. |
| `publisher_email` | No | String | Email de contacto del publicador. |
| `publisher_website` | No | String | URL del sitio web del publicador. |
| `privacy_policy_website` | No | String | URL de la política de privacidad. |
| `license_agreement_website` | No | String | URL del acuerdo de licencia. |
| `ios_app_store_link` | No | String | Enlace a la App Store de iOS. |
| `android_play_store_link` | No | String | Enlace a Google Play. |
| `animated_sticker_pack` | No | Boolean | `true` si el pack es animado. Por defecto `false`. |

> **Importante:** Los campos `publisher_email`, `publisher_website`, `privacy_policy_website`, `license_agreement_website`, `ios_app_store_link` y `android_play_store_link` son **opcionales**. Puedes omitirlos o dejarlos vacíos (`""`) sin que WhatsApp rechace el paquete. La documentación de las librerías oficiales los marca explícitamente como `String?` (nullable)[reference:0].

### 1.2 Campos a nivel de Sticker (cada elemento del array `stickers`)

| Campo | Obligatorio | Tipo | Descripción |
|-------|-------------|------|-------------|
| `image_file` | **Sí** | String | Nombre exacto del archivo `.webp` del sticker. |
| `emojis` | **Recomendado** | Array de String | Hasta 3 emojis que describen el sticker. |
| `accessibility_text` | **Recomendado** | String | **Campo de búsqueda por texto.** Máximo 125 caracteres. Aquí el usuario debe escribir las palabras clave que describen el sticker. |

---

## 2. Requisitos de las Imágenes (Stickers)

### 2.1 Stickers Estáticos

| Requisito | Valor |
|-----------|-------|
| Formato | WebP |
| Dimensiones | Exactamente **512 x 512 píxeles** |
| Tamaño máximo | **100 KB** por archivo |
| Fondo | **Transparente** |
| Cantidad por paquete | Entre **3 y 30** stickers[reference:2] |

> **Consejo de diseño:** WhatsApp recomienda añadir un contorno blanco de 8 píxeles y un margen interno de 16 píxeles, aunque no es estrictamente obligatorio.

### 2.2 Stickers Animados (fuera del alcance de la v1, pero relevante para la arquitectura)

| Requisito | Valor |
|-----------|-------|
| Formato | WebP animado |
| Dimensiones | Exactamente **512 x 512 píxeles** |
| Tamaño máximo | **500 KB** por archivo |
| Duración total | Menos de **10 segundos** |
| Cantidad por paquete | Entre **3 y 30** stickers[reference:3] |

---

## 3. Requisitos del Icono del Paquete (`tray_image_file`)

| Requisito | Valor |
|-----------|-------|
| Formato | PNG o WebP |
| Dimensiones | **96 x 96 píxeles** |
| Tamaño máximo | **50 KB** |
| Función | Imagen que se muestra en la bandeja de stickers de WhatsApp |

> **Nota:** El icono de 96x96 es la única etiqueta visual que el usuario ve al deslizar por la bandeja. Elige una forma simple y de alto contraste que sea legible a ese tamaño[reference:4].

---

## 4. Mecánica de Búsqueda por Texto (Feature Solicitada)

### 4.1 ¿Cómo funciona?

WhatsApp **no analiza el contenido de la imagen**. La búsqueda se basa exclusivamente en los **metadatos de etiquetado** que se asocian al sticker al momento de la creación[reference:5].

El campo `emojis` del sticker es el que impulsa la búsqueda: WhatsApp coincide con los emojis que el publicador asocia al sticker, hasta un máximo de 3 por sticker[reference:6].

### 4.2 Requisito de Implementación en la App

La pantalla de edición del sticker (Fase 3) debe incluir:

1.  Un campo de texto para el **nombre del sticker** (ej: "Trump pensativo").
2.  Un selector de **hasta 3 emojis** (ej: 🤔, 🇺🇸, 😂).
3.  Un campo de texto para **accessibility_text** (ej: "Donald Trump con expresión pensativa").

Estos datos se escribirán en el array `emojis` y en `accessibility_text` dentro del `contents.json`.

### 4.3 Implementación de Búsqueda por Texto (Nombre Descriptivo)

Para que tus stickers sean buscables por texto, la pantalla de edición de la app (Fase 3) debe incluir:

1.  Un campo de texto principal etiquetado como **"Nombre del sticker"** (ej: "Trump pensativo").
2.  Un selector de **hasta 3 emojis** (ej: 🤔, 🇺🇸).
3.  Un campo de texto secundario etiquetado como **"Palabras clave de búsqueda"**. Este campo se mapeará directamente al `accessibility_text` del `contents.json`.

**Recomendación UX:** Puedes precargar el campo de "Palabras clave" con el valor del "Nombre del sticker" para facilitar la tarea al usuario.

---

## 5. Flujo de Exportación a WhatsApp

La exportación se realiza mediante un **handshake** entre tu app y WhatsApp:

1.  **ContentProvider:** Tu app debe implementar un `StickerContentProvider` que exponga los metadatos y archivos del pack.
2.  **Intent:** Se debe lanzar un `Intent` con la acción `com.whatsapp.intent.action.ENABLE_STICKER_PACK`, incluyendo la autoridad del `ContentProvider` y los datos del pack[reference:8].
3.  **Validación:** WhatsApp ejecutará sus propias validaciones. Si falla, mostrará un error genérico. Por ello, es crucial que tu app valide **antes** de exportar.

> **Constantes críticas:** Los nombres de las constantes del `ContentProvider` (como `sticker_pack_identifier`, `sticker_pack_name`, etc.) **no deben modificarse**, ya que son utilizadas por WhatsApp para la comunicación[reference:9].

---

## 6. Checklist de Validación Pre-Exportación

El agente debe implementar validaciones que verifiquen **estos criterios** antes de permitir al usuario exportar:

- [ ] El paquete tiene **entre 3 y 30 stickers**.
- [ ] Cada sticker es **exactamente 512x512 píxeles**.
- [ ] Cada sticker estático pesa **menos de 100 KB**.
- [ ] Cada sticker animado pesa **menos de 500 KB**.
- [ ] Cada sticker tiene fondo **transparente**.
- [ ] El `identifier` del paquete **no contiene puntos**.
- [ ] El `contents.json` tiene todos los campos **OBLIGATORIOS** completos.
- [ ] El icono del paquete (`tray_image_file`) es **96x96 px** y pesa **menos de 50 KB**.
- [ ] Cada sticker tiene **entre 1 y 3 emojis** asociados en los metadatos.
- [ ] La duración total de los stickers animados no supera los **10 segundos**.