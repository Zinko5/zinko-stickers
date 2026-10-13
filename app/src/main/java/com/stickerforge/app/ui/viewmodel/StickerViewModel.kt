package com.stickerforge.app.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerforge.app.data.StickerPackRepository
import com.stickerforge.app.domain.ContentsJsonGenerator
import com.stickerforge.app.domain.DiagnosticLogger
import com.stickerforge.app.domain.ExportResult
import com.stickerforge.app.domain.BackupExportResult
import com.stickerforge.app.domain.BackupImportResult
import com.stickerforge.app.domain.InstagramMediaResolver
import com.stickerforge.app.domain.SocialPlatform
import com.stickerforge.app.domain.StickerPackBackupManager
import com.stickerforge.app.domain.StickerProcessor
import com.stickerforge.app.domain.TikTokMediaResolver
import com.stickerforge.app.domain.TwitterMediaResolver
import com.stickerforge.app.domain.UniversalMediaResolver
import com.stickerforge.app.domain.UniversalMediaResult
import com.stickerforge.app.domain.WhatsAppExporter
import com.stickerforge.app.domain.YouTubeMediaResolver
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
    ANIMATED,
    NOT_EXPORTED
}

data class StickerUiState(
    val currentScreen: CurrentScreen = CurrentScreen.PACKS_LIST,
    val packs: List<StickerPack> = listOf(StickerPack(name = "Pack 1", isAnimated = false)),
    val activePackId: String = "",
    val searchQuery: String = "",
    val selectedFilter: PackFilter = PackFilter.ALL,
    val editingSticker: PackStickerItem? = null,
    val editingMetadataSticker: PackStickerItem? = null,
    val previewingSticker: PackStickerItem? = null,
    val generatedContentsJson: String? = null,
    val savedContentsJsonFile: java.io.File? = null,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingProgress: String = "",
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val exportSuccessMessage: String? = null,
    val exportErrorMessage: String? = null,
    val backupSuccessMessage: String? = null,
    val backupErrorMessage: String? = null,
    val lastDiagnosticLog: String? = null
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
                PackFilter.NOT_EXPORTED -> list.filter { !it.isExportedToWhatsApp }
            }
            return list
        }
}

class StickerViewModel(
    private var repository: StickerPackRepository? = null
) : ViewModel() {

    private val defaultPack = StickerPack(name = "Pack 1", isAnimated = false)
    private val _uiState = MutableStateFlow(
        StickerUiState(
            packs = listOf(defaultPack),
            activePackId = defaultPack.id
        )
    )
    val uiState: StateFlow<StickerUiState> = _uiState.asStateFlow()

    private var lastPackCreationTime = 0L

    init {
        repository?.let { loadSavedData(it) }
    }

    fun initRepository(context: Context) {
        if (this.repository != null) return
        val repo = StickerPackRepository(context.applicationContext)
        this.repository = repo
        loadSavedData(repo)
    }

    fun setRepository(repo: StickerPackRepository) {
        this.repository = repo
        loadSavedData(repo)
    }

    private fun loadSavedData(repo: StickerPackRepository) {
        val state = repo.loadPacks()
        if (state != null && state.packs.isNotEmpty()) {
            val activeId = if (!state.activePackId.isNullOrBlank() && state.packs.any { it.id == state.activePackId }) {
                state.activePackId
            } else {
                state.packs.first().id
            }
            _uiState.update { current ->
                current.copy(
                    packs = state.packs,
                    activePackId = activeId
                )
            }
        }
    }

    private fun persistCurrentState() {
        val current = _uiState.value
        repository?.savePacks(current.packs, current.activePackId)
    }

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
        persistCurrentState()
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
        persistCurrentState()
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
        persistCurrentState()
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
        persistCurrentState()
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
                    val processed = StickerProcessor.processImage(
                        context = context,
                        sourceUri = uri,
                        cropParameters = defaultCrop,
                        isAnimated = isAnim
                    )
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
            persistCurrentState()
        }
    }

    /**
     * Descarga y anade un medio desde cualquier enlace admitido (YouTube, Instagram, TikTok, Twitter/X o enlace directo)
     * al paquete activo, asociando automaticamente el emoji correspondiente de la plataforma (▶️, 📸, 🎵, ✖️, ✨)
     * y las palabras clave de busqueda correspondientes.
     */
    fun addUrlMediaToActivePack(
        context: Context,
        url: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentPack = _uiState.value.activePack
        val availableSlots = (30 - currentPack.count).coerceAtLeast(0)
        if (availableSlots <= 0) {
            val msg = "El paquete ya alcanzo el maximo de 30 stickers permitido por WhatsApp."
            _uiState.update { it.copy(errorMessage = msg) }
            onError(msg)
            return
        }

        DiagnosticLogger.clear()
        val detectedPlatform = UniversalMediaResolver.detectPlatform(url)
        _uiState.update {
            it.copy(
                isProcessing = true,
                processingProgress = "Descargando medio de ${detectedPlatform.displayName}...",
                lastDiagnosticLog = null
            )
        }

        viewModelScope.launch {
            try {
                val mediaResult = withContext(Dispatchers.IO) {
                    UniversalMediaResolver.resolveAndDownload(context, url)
                }

                val willBeAnimated = mediaResult.isVideo && currentPack.isAnimated
                _uiState.update {
                    it.copy(
                        processingProgress = if (willBeAnimated)
                            "Convirtiendo a sticker animado WebP..."
                        else
                            "Convirtiendo a sticker estatico WebP..."
                    )
                }

                val processed = withContext(Dispatchers.IO) {
                    val p = StickerProcessor.processImage(
                        context = context,
                        sourceUri = Uri.fromFile(mediaResult.file),
                        cropParameters = CropParameters(),
                        isAnimated = willBeAnimated
                    )
                    try { mediaResult.file.delete() } catch (ignored: Exception) {}
                    p
                }

                if (processed != null) {
                    val defaultName = when {
                        !mediaResult.username.isNullOrBlank() -> "${mediaResult.platform.displayName} @${mediaResult.username}"
                        !mediaResult.title.isNullOrBlank() -> "${mediaResult.platform.displayName} ${mediaResult.title.take(24)}".trim()
                        else -> "${mediaResult.platform.displayName} Sticker ${currentPack.count + 1}"
                    }

                    val defaultAccessibility = buildString {
                        append(mediaResult.platform.displayName.lowercase())
                        append(" ")
                        append(mediaResult.defaultEmoji)
                        if (!mediaResult.username.isNullOrBlank()) {
                            append(" @${mediaResult.username}")
                        }
                        if (!mediaResult.title.isNullOrBlank()) {
                            append(" ${mediaResult.title.take(60)}")
                        }
                    }.trim().take(125)

                    val newItem = PackStickerItem(
                        sourceUri = processed.uri,
                        processedSticker = processed,
                        isAnimated = willBeAnimated,
                        name = defaultName,
                        emojis = listOf(mediaResult.defaultEmoji),
                        accessibilityText = defaultAccessibility
                    )

                    _uiState.update { state ->
                        val currentActive = state.activePack
                        val updatedPack = currentActive.copy(
                            stickers = currentActive.stickers + newItem
                        )
                        val updatedPacks = state.packs.map { p ->
                            if (p.id == updatedPack.id) updatedPack else p
                        }
                        state.copy(
                            isProcessing = false,
                            processingProgress = "",
                            packs = updatedPacks,
                            lastDiagnosticLog = null
                        )
                    }
                    persistCurrentState()
                    onSuccess()
                } else {
                    val diagLog = DiagnosticLogger.getLog()
                    android.util.Log.e("StickerViewModel", "Fallo al procesar medio de ${mediaResult.platform.displayName}: archivo=${mediaResult.file.name}, tamano=${mediaResult.file.length()}")
                    val msg = "No se pudo procesar el medio de ${mediaResult.platform.displayName} a formato compatible de WhatsApp."
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            processingProgress = "",
                            errorMessage = msg,
                            lastDiagnosticLog = diagLog
                        )
                    }
                    onError(msg)
                }
            } catch (e: Exception) {
                val diagLog = DiagnosticLogger.getLog()
                android.util.Log.e("StickerViewModel", "Error al descargar o procesar medio desde enlace: ${e.message}", e)
                val msg = e.message ?: "Error al descargar o procesar el contenido desde el enlace."
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = "",
                        errorMessage = msg,
                        lastDiagnosticLog = diagLog
                    )
                }
                onError(msg)
            }
        }
    }

    /**
     * Mantiene retrocompatibilidad delegando en addUrlMediaToActivePack.
     */
    fun addTwitterMediaToActivePack(
        context: Context,
        url: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        addUrlMediaToActivePack(context, url, onSuccess, onError)
    }

    /**
     * Mantiene retrocompatibilidad delegando en addUrlMediaToActivePack.
     */
    fun addTikTokMediaToActivePack(
        context: Context,
        url: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        addUrlMediaToActivePack(context, url, onSuccess, onError)
    }

    fun clearDiagnosticLog() {
        _uiState.update { it.copy(lastDiagnosticLog = null) }
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
                StickerProcessor.processImage(
                    context = context,
                    sourceUri = currentSticker.sourceUri,
                    cropParameters = newCropParameters,
                    isAnimated = currentSticker.isAnimated
                )
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
                persistCurrentState()
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
        persistCurrentState()
    }

    fun selectStickerForPreview(sticker: PackStickerItem?) {
        _uiState.update { it.copy(previewingSticker = sticker) }
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
        persistCurrentState()
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
            persistCurrentState()
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
            persistCurrentState()
            true
        } else {
            false
        }
    }

    fun autoAssignDefaultEmojisToUnlabeledStickers() {
        _uiState.update { state ->
            val activePack = state.activePack
            val defaultEmoji = "\u2B1C" // ⬜ (cuadrado blanco)
            val updatedStickers = activePack.stickers.mapIndexed { index, item ->
                if (item.emojis.isEmpty()) {
                    item.copy(
                        name = item.name.ifBlank { "Sticker ${index + 1}" },
                        emojis = listOf(defaultEmoji),
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
        persistCurrentState()
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
     * Valida y prepara un paquete para exportacion formal a WhatsApp.
     * Si no se especifica packId, utiliza el activePack.
     * Si le falta icono de bandeja o emojis, los genera/asigna automaticamente para permitir exportacion directa desde el menu.
     */
    fun prepareAndGetExportIntent(
        context: Context,
        targetPackage: String? = null,
        packId: String? = null
    ): Pair<Intent?, List<String>> {
        val targetPackId = packId ?: _uiState.value.activePackId
        var pack = _uiState.value.packs.firstOrNull { it.id == targetPackId } ?: _uiState.value.activePack

        // Si le faltan emojis, asignamos el cuadrado blanco automaticamente para no bloquear exportacion
        if (!pack.allStickersHaveEmojis && pack.stickers.isNotEmpty()) {
            val defaultEmoji = "\u2B1C"
            val filledStickers = pack.stickers.mapIndexed { idx, item ->
                if (item.emojis.isEmpty()) {
                    item.copy(
                        name = item.name.ifBlank { "Sticker ${idx + 1}" },
                        emojis = listOf(defaultEmoji),
                        accessibilityText = item.accessibilityText.ifBlank { item.name.ifBlank { "Sticker ${idx + 1}" } }
                    )
                } else item
            }
            pack = pack.copy(stickers = filledStickers)
        }

        // Si no tiene tray icon y tiene stickers, generar Pepe Smolder adaptado
        val currentTray = pack.trayImageFile
        if (currentTray == null || !currentTray.exists()) {
            val defaultTray = StickerProcessor.createDefaultPepeTrayIcon(context, pack.identifier)
            if (defaultTray != null) {
                pack = pack.copy(trayImageFile = defaultTray)
            }
        }

        // Actualizar el estado con el paquete listo
        val finalPack = pack
        _uiState.update { state ->
            val updatedPacks = state.packs.map { p ->
                if (p.id == finalPack.id) finalPack else p
            }
            state.copy(packs = updatedPacks)
        }
        persistCurrentState()

        val issues = finalPack.getValidationIssues()
        if (issues.isNotEmpty()) {
            return Pair(null, issues)
        }

        // Sincronizar paquete y archivos con almacenamiento interno de la app
        StickerPackStorage.savePackForExport(context, finalPack)

        val intent = WhatsAppExporter.createExportIntent(
            context = context,
            pack = finalPack,
            targetPackage = targetPackage
        )
        return Pair(intent, emptyList())
    }

    fun handleExportResult(result: ExportResult, exportedPackId: String? = null) {
        when (result) {
            is ExportResult.Success -> {
                val packIdToMark = exportedPackId ?: _uiState.value.activePackId
                _uiState.update { state ->
                    val updatedPacks = state.packs.map { p ->
                        if (p.id == packIdToMark) p.copy(isExportedToWhatsApp = true) else p
                    }
                    state.copy(
                        packs = updatedPacks,
                        exportSuccessMessage = "Paquete anadido a WhatsApp con exito.",
                        exportErrorMessage = null
                    )
                }
                persistCurrentState()
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

    fun exportBackup(
        context: Context,
        selectedPackIds: Set<String>,
        outputUri: Uri
    ) {
        val currentPacks = _uiState.value.packs
        val packsToExport = if (selectedPackIds.isEmpty()) {
            currentPacks
        } else {
            currentPacks.filter { selectedPackIds.contains(it.id) }
        }

        if (packsToExport.isEmpty()) {
            _uiState.update { it.copy(backupErrorMessage = "No hay paquetes seleccionados para exportar.") }
            return
        }

        _uiState.update { it.copy(isProcessing = true, backupErrorMessage = null, backupSuccessMessage = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(outputUri)?.use { out ->
                        StickerPackBackupManager.exportPacksToZip(packsToExport, out)
                    } ?: BackupExportResult.Error("No se pudo abrir el archivo de destino para escritura.")
                } catch (e: Exception) {
                    BackupExportResult.Error("Error al escribir el archivo de respaldo: ${e.message}")
                }
            }

            _uiState.update { state ->
                when (result) {
                    is BackupExportResult.Success -> {
                        state.copy(
                            isProcessing = false,
                            backupSuccessMessage = "Respaldo exportado exitosamente: ${result.packsCount} paquete(s) y ${result.stickersCount} sticker(s).",
                            backupErrorMessage = null
                        )
                    }
                    is BackupExportResult.Error -> {
                        state.copy(
                            isProcessing = false,
                            backupErrorMessage = result.message,
                            backupSuccessMessage = null
                        )
                    }
                }
            }
        }
    }

    fun importBackup(
        context: Context,
        inputUri: Uri
    ) {
        _uiState.update { it.copy(isProcessing = true, backupErrorMessage = null, backupSuccessMessage = null) }

        viewModelScope.launch {
            val existingIds = _uiState.value.packs.map { it.identifier }.toSet()
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(inputUri)?.use { inStream ->
                        StickerPackBackupManager.importPacksFromZip(context, inStream, existingIds)
                    } ?: BackupImportResult.Error("No se pudo abrir el archivo seleccionado.")
                } catch (e: Exception) {
                    BackupImportResult.Error("Error al leer el archivo de respaldo: ${e.message}")
                }
            }

            _uiState.update { state ->
                when (result) {
                    is BackupImportResult.Success -> {
                        val updatedPacks = state.packs + result.importedPacks
                        val newActiveId = result.importedPacks.firstOrNull()?.id ?: state.activePackId
                        state.copy(
                            isProcessing = false,
                            packs = updatedPacks,
                            activePackId = newActiveId,
                            backupSuccessMessage = "Respaldo importado exitosamente: ${result.packsCount} paquete(s) y ${result.stickersCount} sticker(s).",
                            backupErrorMessage = null
                        )
                    }
                    is BackupImportResult.Error -> {
                        state.copy(
                            isProcessing = false,
                            backupErrorMessage = result.message,
                            backupSuccessMessage = null
                        )
                    }
                }
            }
            persistCurrentState()
        }
    }

    fun dismissBackupMessages() {
        _uiState.update { it.copy(backupSuccessMessage = null, backupErrorMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissWarning() {
        _uiState.update { it.copy(warningMessage = null) }
    }
}
