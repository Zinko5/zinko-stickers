package com.stickerforge.app.provider

/**
 * Constantes oficiales requeridas por la API de WhatsApp Stickers para el ContentProvider.
 * No modificar los nombres de las cadenas para preservar compatibilidad con WhatsApp.
 */
object StickerContentProviderConstants {
    const val DEFAULT_AUTHORITY = "com.stickerforge.app.stickercontentprovider"

    // Parametros de consulta para metadatos del paquete
    const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
    const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
    const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
    const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
    const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
    const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
    const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
    const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
    const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
    const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
    const val IMAGE_DATA_VERSION = "image_data_version"
    const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
    const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

    // Parametros de consulta para stickers individuales
    const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
    const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
    const val STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text"

    // Rutas de URI
    const val METADATA = "metadata"
    const val STICKERS = "stickers"
    const val STICKERS_ASSET = "stickers_asset"

    // Columnas del Cursor de metadatos
    val METADATA_COLUMNS = arrayOf(
        STICKER_PACK_IDENTIFIER_IN_QUERY,
        STICKER_PACK_NAME_IN_QUERY,
        STICKER_PACK_PUBLISHER_IN_QUERY,
        STICKER_PACK_ICON_IN_QUERY,
        ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
        IOS_APP_DOWNLOAD_LINK_IN_QUERY,
        PUBLISHER_EMAIL,
        PUBLISHER_WEBSITE,
        PRIVACY_POLICY_WEBSITE,
        LICENSE_AGREEMENT_WEBSITE,
        IMAGE_DATA_VERSION,
        AVOID_CACHE,
        ANIMATED_STICKER_PACK
    )

    // Columnas del Cursor de stickers
    val STICKERS_COLUMNS = arrayOf(
        STICKER_FILE_NAME_IN_QUERY,
        STICKER_FILE_EMOJI_IN_QUERY,
        STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY
    )
}
