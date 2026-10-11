# Especificacion Tecnica: Sistema de Respaldo e Importacion/Exportacion de Paquetes de Stickers

## 1. Introduccion y Objetivos

Este documento describe la arquitectura, organizacion interna y esquema de datos del sistema de respaldo (.zip) implementado en **Zinko Stickers**.

A diferencia de la exportacion hacia WhatsApp (la cual transfiere los paquetes hacia la aplicacion externa mediante un ContentProvider dedicado con restricciones estrictas de WhatsApp), este sistema de respaldo tiene como proposito:

- **Portabilidad total**: Permitir a los usuarios generar un archivo comprimido unico con todos sus paquetes o una seleccion personalizada de los mismos.
- **Respaldo e integridad**: Conservar tanto las imagenes procesadas (WebP) y los iconos de bandeja (PNG), como la totalidad de sus metadatos (nombres, autores, emojis asociados, configuraciones de recorte y estados de animacion).
- **Intercambio sencillo**: Facilitar compartir colecciones de stickers entre usuarios o migrarlos a nuevos dispositivos sin degradacion de calidad ni perdida de configuracion.

---

## 2. Estructura del Archivo Comprimido (.zip)

El archivo generado es un contenedor ZIP estandar (MIME `application/zip`). Su nomenclatura por defecto sigue el patron:

```text
zinko_stickers_backup_<timestamp>.zip
```

### Arbol de Directorios Interno

```text
zinko_stickers_backup_<timestamp>.zip
|-- manifest.json
+-- packs/
    |-- <identifier_pack_1>/
    |   |-- tray_<identifier_pack_1>.png
    |   +-- stickers/
    |       |-- sticker_<hash_o_id_1>.webp
    |       |-- sticker_<hash_o_id_2>.webp
    |       +-- ...
    |-- <identifier_pack_2>/
    |   |-- tray_<identifier_pack_2>.png
    |   +-- stickers/
    |       |-- sticker_<hash_o_id_3>.webp
    |       +-- ...
    +-- ...
```

### Reglas de Ubicacion de Archivos

1. **`manifest.json`**: Debe residir obligatoriamente en la raiz del archivo comprimido. Contiene el indice maestro y la metadata completa de todos los paquetes.
2. **`packs/<identifier>/`**: Cada paquete cuenta con su propio subdirectorio aislado, nombrado segun el `identifier` alfanumerico del paquete.
3. **`packs/<identifier>/tray_<identifier>.png`**: Archivo grafico correspondiente al icono de bandeja del paquete (96x96 pixeles).
4. **`packs/<identifier>/stickers/`**: Carpeta contenedora de los stickers procesados en formato WebP (512x512 pixeles), tanto estaticos como animados.

---

## 3. Especificacion del Archivo `manifest.json`

El archivo `manifest.json` utiliza codificacion UTF-8 y define la relacion jerarquica entre los paquetes y sus stickers componentes.

### 3.1 Estructura del Nivel Raiz (Root)

| Campo | Tipo | Requerido | Descripcion |
| :--- | :--- | :--- | :--- |
| `format_version` | Integer | Si | Version del esquema del formato de respaldo. Valor actual: `1`. |
| `app_name` | String | Si | Nombre de la aplicacion que origino el respaldo. Valor: `"Zinko Stickers"`. |
| `export_timestamp` | Long | Si | Marca de tiempo Unix en milisegundos en el momento de la creacion. |
| `export_date` | String | Si | Fecha y hora en formato estandar ISO 8601 UTC (ejemplo: `"2026-10-11T14:30:00Z"`). |
| `packs_count` | Integer | Si | Cantidad total de paquetes incluidos en el respaldo. |
| `packs` | Array | Si | Lista de objetos JSON, donde cada elemento representa un paquete de stickers. |

---

### 3.2 Objeto `StickerPack` (Elemento del arreglo `packs`)

Cada paquete contiene la informacion administrativa y la lista de sus stickers:

| Campo | Tipo | Requerido | Descripcion |
| :--- | :--- | :--- | :--- |
| `id` | String | Si | Identificador unico universal (UUID v4) interno del paquete. |
| `identifier` | String | Si | Identificador alfanumerico unico utilizado en el sistema de archivos y el ContentProvider. |
| `name` | String | Si | Nombre visible del paquete (maximo 128 caracteres). |
| `publisher` | String | Si | Nombre del creador, autor o publicador del paquete (maximo 128 caracteres). |
| `publisher_email` | String | Opcional | Correo electronico de contacto del publicador (puede ser cadena vacia). |
| `publisher_website` | String | Opcional | URL del sitio web del publicador (puede ser cadena vacia). |
| `privacy_policy_website` | String | Opcional | URL con las politicas de privacidad del paquete (puede ser cadena vacia). |
| `license_agreement_website` | String | Opcional | URL con los terminos o contrato de licencia (puede ser cadena vacia). |
| `is_animated` | Boolean | Si | `true` si es un paquete de stickers animados; `false` si es un paquete de stickers estaticos. |
| `tray_image_file` | String | Si | Ruta relativa dentro del archivo ZIP hacia el icono de bandeja (ejemplo: `"packs/pack_1/tray_pack_1.png"`). |
| `stickers` | Array | Si | Lista de objetos JSON que definen los stickers individuales incluidos en este paquete. |

---

### 3.3 Objeto `PackStickerItem` (Elemento del arreglo `stickers`)

Cada sticker individual contiene la metadata tecnica, asociacion de emojis y parametros de edicion:

| Campo | Tipo | Requerido | Descripcion |
| :--- | :--- | :--- | :--- |
| `id` | String | Si | Identificador unico universal (UUID v4) del sticker. |
| `file_name` | String | Si | Ruta relativa dentro del archivo ZIP hacia el archivo WebP (ejemplo: `"packs/pack_1/stickers/sticker_1.webp"`). |
| `width` | Integer | Si | Ancho de la imagen en pixeles (512). |
| `height` | Integer | Si | Alto de la imagen en pixeles (512). |
| `size_bytes` | Long | Si | Tamano exacto en bytes del archivo WebP. |
| `is_animated` | Boolean | Si | `true` si el sticker es animado; `false` si es una imagen estatica. |
| `name` | String | Si | Nombre o etiqueta descriptiva asignada al sticker. |
| `accessibility_text` | String | Opcional | Descripcion textual del sticker para lectores de pantalla y accesibilidad. |
| `emojis` | Array[String] | Si | Lista de emojis asociados al sticker (entre 1 y 3 caracteres de emojis compatibles). |
| `crop_params` | Object | Si | Objeto que almacena los parametros de recorte y transformacion con los que se genero el sticker. |

---

### 3.4 Objeto `crop_params`

Permite preservar los ajustes aplicados durante la fase de recorte en caso de futuras re-ediciones:

| Campo | Tipo | Requerido | Descripcion |
| :--- | :--- | :--- | :--- |
| `scale` | Double | Si | Factor de zoom o escala aplicado a la imagen original (por defecto `1.0`). |
| `offset_x` | Double | Si | Desplazamiento horizontal en el lienzo de recorte (en pixeles o coordenadas relativas). |
| `offset_y` | Double | Si | Desplazamiento vertical en el lienzo de recorte (en pixeles o coordenadas relativas). |
| `rotation` | Double | Si | Angulo de rotacion en grados sexagesimales (`0.0`, `90.0`, `180.0`, `270.0`). |
| `viewport_size` | Double | Si | Dimension de referencia del viewport en el momento de la edicion. |

---

## 4. Ejemplo Completo de `manifest.json`

```json
{
  "format_version": 1,
  "app_name": "Zinko Stickers",
  "export_timestamp": 1791738000000,
  "export_date": "2026-10-11T14:20:00Z",
  "packs_count": 1,
  "packs": [
    {
      "id": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
      "identifier": "pepe_pack_2026",
      "name": "Pepe Classic",
      "publisher": "Zinko Stickers",
      "publisher_email": "soporte@zinko.com",
      "publisher_website": "https://zinko.com",
      "privacy_policy_website": "https://zinko.com/privacy",
      "license_agreement_website": "",
      "is_animated": false,
      "tray_image_file": "packs/pepe_pack_2026/tray_pepe_pack_2026.png",
      "stickers": [
        {
          "id": "4a7d2391-7643-4e89-a5c2-f9d782163102",
          "file_name": "packs/pepe_pack_2026/stickers/pepe_smolder.webp",
          "width": 512,
          "height": 512,
          "size_bytes": 48192,
          "is_animated": false,
          "name": "Pepe Smolder",
          "accessibility_text": "Pepe mirando fijamente con expresion confiada",
          "emojis": [
            "smirk",
            "sunglasses"
          ],
          "crop_params": {
            "scale": 1.25,
            "offset_x": 0.0,
            "offset_y": -15.5,
            "rotation": 0.0,
            "viewport_size": 512.0
          }
        }
      ]
    }
  ]
}
```

---

## 5. Medidas de Seguridad y Resiliencia en la Importacion

El componente de deserializacion e importacion (`StickerPackBackupManager`) aplica las siguientes garantias de seguridad y consistencia:

### 5.1 Proteccion contra Desbordamiento de Rutas (Zip Slip)
Los archivos comprimidos provienen de fuentes potencialmente no confiables (otros dispositivos, descargas web). Durante el proceso de descompresion:
- Se rechazan de forma estricta todas las entradas del archivo ZIP que contengan secuencias de escape de directorio (`..`) o rutas absolutas que inicien con `/` o `\\`.
- Se valida en tiempo de ejecucion que la ruta canonica de cada archivo destino se encuentre rigurosamente confinada dentro del directorio temporal de extraccion (`tempImportDir.canonicalPath`).

### 5.2 Manejo de Colisiones de Identificadores
Si un usuario importa un paquete cuyo campo `identifier` ya existe en la base de datos local de la aplicacion:
- El sistema detecta la colision automaticamente.
- Se genera un identificador alternativo unico agregando un sufijo aleatorio seguro (por ejemplo: `mi_pack_a1b2`), evitando sobrescribir o corromper el paquete existente.

### 5.3 Persistencia y Aislamiento en Almacenamiento Interno
- Tras descomprimir y validar las imagenes y tray icons, estos se trasladan al almacenamiento interno privado de la aplicacion (`context.filesDir/stickers/` y `context.filesDir/tray_icons/`).
- Las referencias se construyen como URIs seguras provistas por el `FileProvider` de la aplicacion (`content://com.stickerforge.app.fileprovider/...`), garantizando compatibilidad con Android 10+ (Scoped Storage) y aislamiento de procesos.
- Al finalizar la importacion, el directorio temporal de extraccion en memoria cache se elimina por completo.
