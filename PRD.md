# PRD: StickerForge - App de Creación de Stickers para WhatsApp

## 1. Header
- **Owner (product)**: [Tu nombre]
- **Engineering lead**: Antigravity IDE (agente)
- **Status**: Draft
- **Last updated**: 2026-10-06
- **Change history**: 2026-10-06 / Initial draft

## 2. Problem statement
Los usuarios de WhatsApp quieren crear stickers personalizados a partir de sus propias fotos, pero las herramientas actuales son complejas, llenas de anuncios o no respetan las especificaciones técnicas de WhatsApp, lo que provoca fallos en la exportación.

## 3. Goals & success metrics
| Goal | Metric | Baseline | Target | Measured by |
|------|--------|----------|--------|-------------|
| Creación de stickers exitosa | % de exportaciones que WhatsApp acepta | 0% | >95% | Logs de la app |
| Facilidad de uso | Tiempo medio para crear 1 sticker | N/A | <30 seg | Analytics |
| Retención | Usuarios que crean >3 stickers en primera sesión | 0% | >60% | Analytics |

## 4. Non-goals (out of scope)
- No se implementará edición avanzada de imagen (capas, filtros artísticos).
- No se soportarán stickers animados en la v1 (solo estáticos).
- No se creará una tienda de packs; el usuario solo exporta a WhatsApp.

## 5. User stories
- **US-01**: Como usuario, quiero seleccionar una imagen de mi galería para convertirla en sticker.
- **US-02**: Como usuario, quiero recortar la imagen en formato cuadrado (1:1) antes de exportar.
- **US-03**: Como usuario, quiero asignar un nombre y emojis a mi sticker para poder encontrarlo después en WhatsApp.
- **US-04**: Como usuario, quiero que la app empaquete mis stickers y los exporte directamente a WhatsApp.
- **US-05**: Como usuario, quiero ver una vista previa de cómo se verá el sticker antes de exportarlo.

## 6. Functional requirements
### FR-01: Selección de imagen
- La app debe abrir la galería del dispositivo.
- Debe permitir seleccionar imágenes en formatos JPG, PNG y WebP.

### FR-02: Recorte y edición básica
- El usuario debe poder recortar la imagen a un cuadrado perfecto (1:1).
- El lienzo de salida debe ser **exactamente de 512x512 píxeles**.
- El fondo debe ser **transparente** (si la imagen original no lo es, la app debe permitir eliminar el fondo o crear uno).

### FR-03: Metadatos del sticker
- El usuario debe poder asignar un **nombre** al sticker.
- El usuario debe poder seleccionar hasta **3 emojis** representativos.
- La app debe generar una **descripción de accesibilidad** (`accessibilityText`) de hasta 125 caracteres.

### FR-04: Empaquetado y exportación
- La app debe agrupar los stickers en un paquete válido para WhatsApp.
- Un paquete debe contener **entre 3 y 30 stickers** para ser válido[reference:2].
- Cada sticker debe ser un archivo **WebP** de **menos de 100 KB**[reference:3].
- La exportación debe usar la **API oficial de WhatsApp para stickers** (no un simple share de imagen).

### FR-05: Vista previa
- Antes de exportar, el usuario debe ver una vista previa del sticker con fondo transparente y el tamaño final.

## 7. Technical requirements
- **Plataforma**: Android nativo (Kotlin + Jetpack Compose).
- **Min SDK**: 26 (Android 8.0).
- **Target SDK**: Última versión estable.
- **Librerías clave**:
  - `androidx.compose` para UI.
  - `androidx.activity` para `ActivityResultContracts` (galería).
  - `androidx.core:core-ktx` para utilidades.
  - **WhatsApp Sticker API** (dependencia oficial del repositorio `WhatsApp/stickers`)[reference:4].
- **Permisos**: `READ_MEDIA_IMAGES` (Android 13+) y `READ_EXTERNAL_STORAGE` (versiones anteriores).
- **Formato de salida**: Archivos `.webp` empaquetados en un `contents.json` con la estructura requerida por WhatsApp[reference:5].
- **Manejo de errores**: Si el sticker supera 100 KB, la app debe recomprimir automáticamente sin pérdida visible de calidad.

## 8. Acceptance criteria (machine-checkable)
- [ ] Al exportar, WhatsApp muestra el paquete de stickers correctamente.
- [ ] Cada sticker generado tiene dimensiones exactas de 512x512 píxeles.
- [ ] Cada sticker pesa menos de 100 KB.
- [ ] El paquete contiene entre 3 y 30 stickers.
- [ ] Los stickers tienen fondo transparente.
- [ ] Los stickers son buscables en WhatsApp por su nombre y emojis asociados.
- [ ] La app no crashea al seleccionar imágenes de más de 10 MB.

## 9. Stack constraints
- **Lenguaje**: Kotlin.
- **UI**: Jetpack Compose (no XML layouts).
- **Arquitectura**: MVVM con ViewModel + StateFlow.
- **Inyección de dependencias**: Hilt (opcional, pero recomendado).
- **No usar**: Java, XML layouts, AsyncTask, librerías de terceros para edición de imagen no auditadas.

## 10. Do-not-touch list
- No modificar el `AndroidManifest.xml` sin consultar.
- No cambiar la versión de Gradle ni de AGP sin autorización.
- No añadir dependencias nuevas sin justificación técnica.

## 11. Open questions
- ¿Se debe permitir al usuario elegir el color de fondo si la imagen original no tiene transparencia?
- ¿La app debe funcionar offline o requiere conexión para la exportación?
- ¿Se necesita soporte para tablets?

## 12. Milestones
| Fase | Entregable | Criterio de aceptación |
|------|------------|------------------------|
| Fase 1 | Proyecto base + galería | App abre, selecciona imagen, muestra en pantalla |
| Fase 2 | Recorte y procesamiento | Imagen se recorta a 512x512 y se guarda como WebP <100KB |
| Fase 3 | Metadatos + empaquetado | Se genera `contents.json` válido |
| Fase 4 | Exportación a WhatsApp | WhatsApp instala el pack y los stickers son usables |
| Fase 5 | Pulido | Vista previa, manejo de errores, tests |