package com.stickerforge.app.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stickerforge.app.domain.StickerProcessor
import com.stickerforge.app.model.CropParameters
import com.stickerforge.app.model.ImageMetadata
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.ProcessedSticker
import com.stickerforge.app.model.StickerPack
import com.stickerforge.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class CurrentScreen {
    PACK_DETAIL,
    CROP,
    PREVIEW
}

data class StickerUiState(
    val currentScreen: CurrentScreen = CurrentScreen.PACK_DETAIL,
    val packs: List<StickerPack> = listOf(StickerPack(name = "Pack 1")),
    val activePackId: String = "",
    val editingSticker: PackStickerItem? = null,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingProgress: String = "",
    val errorMessage: String? = null,
    val warningMessage: String? = null
) {
    val activePack: StickerPack
        get() = packs.firstOrNull { it.id == activePackId } ?: packs.firstOrNull() ?: StickerPack()
}

class StickerViewModel : ViewModel() {

    private val defaultPack = StickerPack(name = "Pack 1")
    private val _uiState = MutableStateFlow(
        StickerUiState(
            packs = listOf(defaultPack),
            activePackId = defaultPack.id
        )
    )
    val uiState: StateFlow<StickerUiState> = _uiState.asStateFlow()

    fun navigateTo(screen: CurrentScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun selectPack(packId: String) {
        _uiState.update { it.copy(activePackId = packId) }
    }

    fun createNewPack(name: String = "Nuevo Pack") {
        val newPack = StickerPack(
            id = UUID.randomUUID().toString(),
            name = name
        )
        _uiState.update {
            it.copy(
                packs = it.packs + newPack,
                activePackId = newPack.id
            )
        }
    }

    fun updatePackName(packId: String, newName: String) {
        _uiState.update { state ->
            val updated = state.packs.map { pack ->
                if (pack.id == packId) pack.copy(name = newName) else pack
            }
            state.copy(packs = updated)
        }
    }

    /**
     * Anade multiples imagenes de una vez al paquete activo.
     * Valida limites (max 30) y asegura que no se mezclen stickers animados y estaticos.
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

            // Validacion de mezcla de animados y estaticos
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

            val packAlreadyHasStickers = activePack.stickers.isNotEmpty()
            val selectionIsAnimated = hasAnimatedInSelection

            if (packAlreadyHasStickers && activePack.isAnimated != selectionIsAnimated) {
                val packType = if (activePack.isAnimated) "animados" else "estaticos"
                val selectionType = if (selectionIsAnimated) "animados" else "estaticos"
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = "Este paquete es de stickers $packType. No puedes anadir stickers $selectionType."
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
                    isAnimated = if (currentActive.stickers.isEmpty()) selectionIsAnimated else currentActive.isAnimated,
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

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissWarning() {
        _uiState.update { it.copy(warningMessage = null) }
    }
}
