package com.stickerforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stickerforge.app.ui.screens.CropScreen
import com.stickerforge.app.ui.screens.PackDetailScreen
import com.stickerforge.app.ui.screens.PackListScreen
import com.stickerforge.app.ui.screens.PackMetadataReviewScreen
import com.stickerforge.app.ui.theme.StickerForgeTheme
import com.stickerforge.app.ui.viewmodel.CurrentScreen
import com.stickerforge.app.ui.viewmodel.StickerViewModel

class MainActivity : ComponentActivity() {

    private val stickerViewModel: StickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StickerForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by stickerViewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    BackHandler(enabled = uiState.currentScreen != CurrentScreen.PACKS_LIST) {
                        when (uiState.currentScreen) {
                            CurrentScreen.CROP -> stickerViewModel.cancelEditingSticker()
                            CurrentScreen.METADATA_REVIEW -> stickerViewModel.navigateTo(CurrentScreen.PACK_DETAIL)
                            CurrentScreen.PACK_DETAIL -> stickerViewModel.navigateBackToPacksList()
                            CurrentScreen.PREVIEW -> stickerViewModel.navigateTo(CurrentScreen.PACK_DETAIL)
                            CurrentScreen.PACKS_LIST -> { }
                        }
                    }

                    Crossfade(
                        targetState = uiState.currentScreen,
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            CurrentScreen.PACKS_LIST -> {
                                PackListScreen(viewModel = stickerViewModel)
                            }
                            CurrentScreen.PACK_DETAIL -> {
                                PackDetailScreen(
                                    viewModel = stickerViewModel,
                                    onProceedToPhase3 = {
                                        stickerViewModel.navigateTo(CurrentScreen.METADATA_REVIEW)
                                    }
                                )
                            }
                            CurrentScreen.METADATA_REVIEW -> {
                                PackMetadataReviewScreen(viewModel = stickerViewModel)
                            }
                            CurrentScreen.CROP -> {
                                val stickerToEdit = uiState.editingSticker
                                if (stickerToEdit != null) {
                                    CropScreen(
                                        stickerItem = stickerToEdit,
                                        isProcessing = uiState.isProcessing,
                                        onBack = {
                                            stickerViewModel.cancelEditingSticker()
                                        },
                                        onSaveCrop = { newCrop ->
                                            stickerViewModel.saveEditedCrop(context, newCrop)
                                        }
                                    )
                                } else {
                                    stickerViewModel.navigateTo(CurrentScreen.PACK_DETAIL)
                                }
                            }
                            CurrentScreen.PREVIEW -> {
                                PackDetailScreen(
                                    viewModel = stickerViewModel,
                                    onProceedToPhase3 = {
                                        stickerViewModel.navigateTo(CurrentScreen.METADATA_REVIEW)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
