package com.stickerforge.app.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerforge.app.domain.ContentsJsonGenerator
import com.stickerforge.app.domain.ExportResult
import com.stickerforge.app.domain.StickerProcessor
import com.stickerforge.app.domain.WhatsAppExporter
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.StickerPack
import com.stickerforge.app.provider.StickerPackStorage
import com.stickerforge.app.util.ImageUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class CurrentScreen {
    PACKS_LIST,
    PACK_DETAIL,
    CROP,
    METADATA_REVIEW,
    PREVIEW
}

enum class PackFilter {
    ALL,
    STATIC,
    ANIMATED
}

data class StickerUiState(
    val currentScreen: CurrentScreen = CurrentScreen.PACKS_LIST,
    val packs: List<StickerPack> = listOf(StickerPack(name = "Pack 1", isAnimated = false)),
    val activePackId: String = "",
    val searchQuery: String = "",
    val selectedFilter: PackFilter = PackFilter.ALL,
    val editingSticker: PackStickerItem? = null,
    val editingMetadataSticker: PackStickerItem? = null,
    val generatedContentsJson: String? = null,
    val savedContentsJsonFile: java.io.File? = null,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingProgress: String = "",
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val exportSuccessMessage: String? = null,
    val exportErrorMessage: String? = null
) {
    val activePack: StickerPack
        get() = packs.firstOrNull { it.id == activePackId } ?: packs.firstOrNull() ?: StickerPack()

    val filteredPacks: List<StickerPack>
        get() {
            var list = packs
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim().lowercase()
                list = list.filter { it.name.lowercase().contains(query) }
            }
            list = when (selectedFilter) {
                PackFilter.ALL -> list
                PackFilter.STATIC -> list.filter { !it.isAnimated }
                PackFilter.ANIMATED -> list.filter { it.isAnimated }
            }
            return list
        }
}

class StickerViewModel : ViewModel() {

    private val defaultPack = StickerPack(name = "Pack 1", isAnimated = false)
    private val _uiState = MutableStateFlow(
        StickerUiState(
            packs = listOf(defaultPack),
            activePackId = defaultPack.id
        )
    )
    val uiState: StateFlow<StickerUiState> = _uiState.asStateFlow()

    private var lastPackCreationTime = 0L

    fun navigateTo(screen: CurrentScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun openPack(packId: String) {
        _uiState.update {
            it.copy(
                activePackId = packId,
                currentScreen = CurrentScreen.PACK_DETAIL
            )
        }
    }

    fun navigateBackToPacksList() {
        _uiState.update { it.copy(currentScreen = CurrentScreen.PACKS_LIST) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: PackFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    /**
     * Crea un nuevo paquete con proteccion de rebote (debounce) para evitar bloqueos por clics continuos.
     */
    fun createNewPack(
        name: String = "Nuevo Pack",
        publisher: String = "Zinko Stickers",
        isAnimated: Boolean = false
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPackCreationTime < 400L) {
            return false
        }
        lastPackCreationTime = now

        val trimmedName = name.trim().ifEmpty {
            val count = _uiState.value.packs.size + 1
            "Pack $count"
        }
        val trimmedPublisher = publisher.trim().ifEmpty { "Zinko Stickers" }

        val newPack = StickerPack(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            publisher = trimmedPublisher,
            isAnimated = isAnimated
        )
        _uiState.update { state ->
            state.copy(
                packs = state.packs + newPack,
                activePackId = newPack.id,
                currentScreen = CurrentScreen.PACK_DETAIL
            )
        }
        return true
    }

    fun deletePack(packId: String) {
        _uiState.update { state ->
            val updatedPacks = state.packs.filterNot { it.id == packId }
            val nextActiveId = if (state.activePackId == packId) {
                updatedPacks.firstOrNull()?.id ?: ""
            } else {
                state.activePackId
            }
            state.copy(
                packs = updatedPacks,
                activePackId = nextActiveId,
                currentScreen = if (updatedPacks.isEmpty() || state.activePackId == packId) CurrentScreen.PACKS_LIST else state.currentScreen
            )
        }
    }

    fun updatePackDetails(packId: String, newName: String, newPublisher: String) {
        val trimmedName = newName.trim()
        val trimmedPublisher = newPublisher.trim().ifEmpty { "Zinko Stickers" }
        _uiState.update { state ->
            val updated = state.packs.map { pack ->
                if (pack.id == packId) {
                    pack.copy(
                        name = trimmedName.ifEmpty { pack.name },
                        publisher = trimmedPublisher
                    )
                } else pack
            }
            state.copy(packs = updated)
        }
    }

    fun updatePackName(packId: String, newName: String) {
        val currentPublisher = _uiState.value.packs.firstOrNull { it.id == packId }?.publisher ?: "Zinko Stickers"
        updatePackDetails(packId, newName, currentPublisher)
    }

    /**
     * Anade multiples imagenes al paquete activo.
     * Valida limites (max 30) y verifica compatibilidad estricta con el tipo de paquete (animado vs estatico).
     */
    fun addImagesToActivePack(
        contentResolver: ContentResolver,
        context: Context,
        uris: List<Uri>
    ) {
        if (uris.isEmpty()) return

        val activePack = _uiState.value.activePack
        val currentCount = activePack.count
        val availableSlots = (30 - currentCount).coerceAtLeast(0)

        if (availableSlots <= 0) {
            _uiState.update {
                it.copy(errorMessage = "El paquete ya alcanzo el maximo de 30 stickers permitido por WhatsApp.")
            }
            return
        }

        val toProcessUris = uris.take(availableSlots)
        val omittedCount = uris.size - toProcessUris.size

        _uiState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                warningMessage = if (omittedCount > 0) "Solo se agregaron los primeros $availableSlots stickers para respetar el limite maximo de 30." else null
            )
        }

        viewModelScope.launch {
            val inspectedItems = withContext(Dispatchers.IO) {
                toProcessUris.mapNotNull { uri ->
                    val metadata = ImageUtils.extractMetadata(contentResolver, uri)
                    if (metadata != null) Pair(uri, metadata.isAnimated) else null
                }
            }

            if (inspectedItems.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "No se pudieron leer las imagenes seleccionadas."
                    )
                }
                return@launch
            }

            // Validacion de no mezclar dentro de la misma seleccion
            val hasAnimatedInSelection = inspectedItems.any { it.second }
            val hasStaticInSelection = inspectedItems.any { !it.second }

            if (hasAnimatedInSelection && hasStaticInSelection) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "No se pueden mezclar stickers animados y estaticos en el mismo paquete de WhatsApp."
                    )
                }
                return@launch
            }

            val selectionIsAnimated = hasAnimatedInSelection

            // Verificacion estricta de coherencia con el tipo de paquete definido
            if (activePack.isAnimated != selectionIsAnimated) {
                val errorMsg = if (activePack.isAnimated) {
                    "Este paquete es de tipo animado. Solo se permiten stickers animados (WebP animado / GIF)."
                } else {
                    "Este paquete es de tipo estatico. No se permiten stickers animados."
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = errorMsg
                    )
                }
                return@launch
            }

            // Procesar cada imagen con escala FIT (bordes transparentes preservados)
            val processedItems = mutableListOf<PackStickerItem>()
            withContext(Dispatchers.IO) {
                for (item in inspectedItems) {
                    val uri = item.first
                    val isAnim = item.second
                    val defaultCrop = CropParameters()
                    val processed = StickerProcessor.processImage(context, uri, defaultCrop)
                    if (processed != null) {
                        processedItems.add(
                            PackStickerItem(
                                sourceUri = uri,
                                processedSticker = processed,
                                cropParams = defaultCrop,
                                isAnimated = isAnim
                            )
                        )
                    }
                }
            }

            _uiState.update { state ->
                val currentActive = state.activePack
                val updatedPack = currentActive.copy(
                    stickers = currentActive.stickers + processedItems
                )
                val updatedPacks = state.packs.map { p ->
                    if (p.id == updatedPack.id) updatedPack else p
                }
                state.copy(
                    isProcessing = false,
                    packs = updatedPacks
                )
            }
        }
    }

    fun startEditingSticker(sticker: PackStickerItem) {
        _uiState.update {
            it.copy(
                editingSticker = sticker,
                currentScreen = CurrentScreen.CROP
            )
        }
    }

    fun saveEditedCrop(context: Context, newCropParameters: CropParameters) {
        val currentSticker = _uiState.value.editingSticker ?: return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val reprocessed = withContext(Dispatchers.IO) {
                StickerProcessor.processImage(context, currentSticker.sourceUri, newCropParameters)
            }

            if (reprocessed != null) {
                _uiState.update { state ->
                    val activePack = state.activePack
                    val updatedStickers = activePack.stickers.map { item ->
                        if (item.id == currentSticker.id) {
                            item.copy(
                                processedSticker = reprocessed,
                                cropParams = newCropParameters
                            )
                        } else {
                            item
                        }
                    }
                    val updatedPack = activePack.copy(stickers = updatedStickers)
                    val updatedPacks = state.packs.map { p ->
                        if (p.id == updatedPack.id) updatedPack else p
                    }
                    state.copy(
                        isProcessing = false,
                        editingSticker = null,
                        packs = updatedPacks,
                        currentScreen = CurrentScreen.PACK_DETAIL
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Error al actualizar el recorte del sticker."
                    )
                }
            }
        }
    }

    fun cancelEditingSticker() {
        _uiState.update {
            it.copy(
                editingSticker = null,
                currentScreen = CurrentScreen.PACK_DETAIL
            )
        }
    }

    fun removeStickerFromActivePack(stickerId: String) {
        _uiState.update { state ->
            val activePack = state.activePack
            val updatedPack = activePack.copy(
                stickers = activePack.stickers.filterNot { it.id == stickerId }
            )
            val updatedPacks = state.packs.map { p ->
                if (p.id == updatedPack.id) updatedPack else p
            }
            state.copy(packs = updatedPacks)
        }
    }

    fun startEditingMetadata(sticker: PackStickerItem) {
        _uiState.update { it.copy(editingMetadataSticker = sticker) }
    }

    fun dismissEditingMetadata() {
        _uiState.update { it.copy(editingMetadataSticker = null) }
    }

    fun updateStickerMetadata(
        stickerId: String,
        name: String,
        emojis: List<String>,
        accessibilityText: String
    ) {
        _uiState.update { state ->
            val activePack = state.activePack
            val updatedStickers = activePack.stickers.map { item ->
                if (item.id == stickerId) {
                    item.copy(
                        name = name.trim(),
                        emojis = emojis.take(3),
                        accessibilityText = accessibilityText.trim().take(125)
                    )
                } else item
            }
            val updatedPack = activePack.copy(stickers = updatedStickers)
            val updatedPacks = state.packs.map { p ->
                if (p.id == updatedPack.id) updatedPack else p
            }
            state.copy(
                packs = updatedPacks,
                editingMetadataSticker = null
            )
        }
    }

    /**
     * Establece el icono de bandeja Pepe Smolder adaptado (96x96 px PNG <50 KB con canal alfa) para el paquete activo.
     */
    fun setPepeSmolderTrayIconForActivePack(context: Context): Boolean {
        val activePack = _uiState.value.activePack
        val trayFile = StickerProcessor.createDefaultPepeTrayIcon(
            context = context,
            packIdentifier = activePack.identifier
        )
        return if (trayFile != null) {
            _uiState.update { state ->
                val updatedPack = state.activePack.copy(trayImageFile = trayFile)
                val updatedPacks = state.packs.map { p ->
                    if (p.id == updatedPack.id) updatedPack else p
                }
                state.copy(packs = updatedPacks)
            }
            true
        } else {
            false
        }
    }

    /**
     * Genera el icono de bandeja para el paquete activo.
     * Si preferPepeDefault es verdadero y no se indico un sticker especifico, utiliza Pepe Smolder adaptado.
     */
    fun generateTrayIconForActivePack(
        context: Context,
        sourceSticker: PackStickerItem? = null,
        preferPepeDefault: Boolean = true
    ): Boolean {
        val activePack = _uiState.value.activePack
        if (preferPepeDefault && sourceSticker == null) {
            val pepeSuccess = setPepeSmolderTrayIconForActivePack(context)
            if (pepeSuccess) return true
        }

        val chosenSticker = sourceSticker ?: activePack.stickers.firstOrNull() ?: return false
        val trayFile = StickerProcessor.generateTrayIcon(
            context = context,
            sourceFile = chosenSticker.processedSticker.file,
            packIdentifier = activePack.identifier
        )
        return if (trayFile != null) {
            _uiState.update { state ->
                val updatedPack = state.activePack.copy(trayImageFile = trayFile)
                val updatedPacks = state.packs.map { p ->
                    if (p.id == updatedPack.id) updatedPack else p
                }
                state.copy(packs = updatedPacks)
            }
            true
        } else {
            false
        }
    }

    fun autoAssignDefaultEmojisToUnlabeledStickers() {
        _uiState.update { state ->
            val activePack = state.activePack
            val defaultEmojiPool = listOf("😀", "🔥", "✨", "🎉", "❤️", "👍", "🥳", "😎")
            val updatedStickers = activePack.stickers.mapIndexed { index, item ->
                if (item.emojis.isEmpty()) {
                    val assignedEmoji = defaultEmojiPool[index % defaultEmojiPool.size]
                    item.copy(
                        name = item.name.ifBlank { "Sticker ${index + 1}" },
                        emojis = listOf(assignedEmoji),
                        accessibilityText = item.accessibilityText.ifBlank { item.name.ifBlank { "Sticker ${index + 1}" } }
                    )
                } else item
            }
            val updatedPack = activePack.copy(stickers = updatedStickers)
            val updatedPacks = state.packs.map { p ->
                if (p.id == updatedPack.id) updatedPack else p
            }
            state.copy(packs = updatedPacks)
        }
    }

    fun preparePackContentsJson(context: Context): Boolean {
        val activePack = _uiState.value.activePack
        if (activePack.trayImageFile == null && activePack.stickers.isNotEmpty()) {
            generateTrayIconForActivePack(context)
        }

        val packWithTray = _uiState.value.activePack
        val jsonString = ContentsJsonGenerator.generateContentsJson(packWithTray)

        val packDirectory = StickerPackStorage.savePackForExport(context, packWithTray)
        val savedFile = File(packDirectory, "contents.json")

        _uiState.update { state ->
            state.copy(
                generatedContentsJson = jsonString,
                savedContentsJsonFile = savedFile
            )
        }
        return true
    }

    /**
     * Valida y prepara el paquete para exportacion formal a WhatsApp.
     * Si no cumple los requisitos, retorna null y la lista de problemas.
     */
    fun prepareAndGetExportIntent(
        context: Context,
        targetPackage: String? = null
    ): Pair<Intent?, List<String>> {
        val activePack = _uiState.value.activePack
        val issues = activePack.getValidationIssues()
        if (issues.isNotEmpty()) {
            return Pair(null, issues)
        }

        // Sincronizar paquete y archivos con almacenamiento interno de la app
        StickerPackStorage.savePackForExport(context, activePack)

        val intent = WhatsAppExporter.createExportIntent(
            context = context,
            pack = activePack,
            targetPackage = targetPackage
        )
        return Pair(intent, emptyList())
    }

    fun handleExportResult(result: ExportResult) {
        when (result) {
            is ExportResult.Success -> {
                _uiState.update {
                    it.copy(
                        exportSuccessMessage = "Paquete anadido a WhatsApp con exito.",
                        exportErrorMessage = null
                    )
                }
            }
            is ExportResult.Cancelled -> {
                if (!result.validationError.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            exportErrorMessage = "Error de validacion de WhatsApp: ${result.validationError}",
                            exportSuccessMessage = null
                        )
                    }
                }
            }
            is ExportResult.Error -> {
                _uiState.update {
                    it.copy(
                        exportErrorMessage = result.message,
                        exportSuccessMessage = null
                    )
                }
            }
        }
    }

    fun dismissExportMessages() {
        _uiState.update { it.copy(exportSuccessMessage = null, exportErrorMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissWarning() {
        _uiState.update { it.copy(warningMessage = null) }
    }
}
