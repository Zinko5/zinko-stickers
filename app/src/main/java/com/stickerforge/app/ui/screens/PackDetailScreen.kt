package com.stickerforge.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import com.stickerforge.app.ui.components.StickerMetadataDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stickerforge.app.R
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.model.StickerPack
import com.stickerforge.app.ui.components.TransparencyGrid
import com.stickerforge.app.ui.theme.AccentTeal
import com.stickerforge.app.ui.theme.AnimatedPurple
import com.stickerforge.app.ui.theme.CardDark
import com.stickerforge.app.ui.theme.EmeraldLight
import com.stickerforge.app.ui.theme.EmeraldPrimary
import com.stickerforge.app.ui.theme.ErrorRed
import com.stickerforge.app.ui.theme.SurfaceDark
import com.stickerforge.app.ui.theme.TextPrimary
import com.stickerforge.app.ui.theme.TextSecondary
import com.stickerforge.app.ui.theme.WarningAmber
import com.stickerforge.app.ui.viewmodel.StickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackDetailScreen(
    viewModel: StickerViewModel,
    onProceedToPhase3: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val activePack = uiState.activePack

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Multi-image photo picker (permite seleccionar hasta 30 de una sola vez)
    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImagesToActivePack(context.contentResolver, context, uris)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.warningMessage) {
        uiState.warningMessage?.let { warning ->
            snackbarHostState.showSnackbar(warning)
            viewModel.dismissWarning()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBackToPacksList() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (activePack.isAnimated) AnimatedPurple else AccentTeal,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (activePack.isAnimated) Icons.Default.Animation else Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = activePack.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (activePack.isAnimated) "Pack Animado" else "Pack Estatico",
                                    fontSize = 11.sp,
                                    color = if (activePack.isAnimated) AnimatedPurple else AccentTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = " • por ${activePack.publisher}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = " • ${activePack.count}/30",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onProceedToPhase3) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Metadatos y Tray Icon (Fase 3)",
                            tint = EmeraldLight
                        )
                    }
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Renombrar pack",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar pack",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        floatingActionButton = {
            if (!activePack.isFull) {
                FloatingActionButton(
                    onClick = {
                        multiImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = EmeraldPrimary,
                    contentColor = SurfaceDark
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.add_more_images)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = EmeraldPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Procesando stickers a 512x512 WebP...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Grid de stickers o vista vacia
            if (activePack.stickers.isEmpty()) {
                EmptyPackView(
                    isAnimated = activePack.isAnimated,
                    onSelectImages = {
                        multiImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onProceedToPhase3 = onProceedToPhase3
                )
            } else {
                PackStickersGrid(
                    pack = activePack,
                    onEditSticker = { sticker ->
                        viewModel.startEditingSticker(sticker)
                    },
                    onEditMetadata = { sticker ->
                        viewModel.startEditingMetadata(sticker)
                    },
                    onDeleteSticker = { stickerId ->
                        viewModel.removeStickerFromActivePack(stickerId)
                    },
                    onAddMore = {
                        multiImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onProceedToPhase3 = onProceedToPhase3
                )
            }
        }
    }

    // Dialogo de edicion de metadatos y emojis del sticker (Fase 3)
    uiState.editingMetadataSticker?.let { sticker ->
        StickerMetadataDialog(
            stickerItem = sticker,
            onDismiss = { viewModel.dismissEditingMetadata() },
            onSave = { name, emojis, accessibilityText ->
                viewModel.updateStickerMetadata(sticker.id, name, emojis, accessibilityText)
            }
        )
    }

    if (showRenameDialog) {
        EditPackDetailsDialog(
            currentName = activePack.name,
            currentPublisher = activePack.publisher,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName, newPublisher ->
                viewModel.updatePackDetails(activePack.id, newName, newPublisher)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        DeletePackConfirmDialog(
            packName = activePack.name,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                viewModel.deletePack(activePack.id)
                showDeleteConfirm = false
            }
        )
    }
}

@Composable
fun PackStickersGrid(
    pack: StickerPack,
    onEditSticker: (PackStickerItem) -> Unit,
    onEditMetadata: (PackStickerItem) -> Unit,
    onDeleteSticker: (String) -> Unit,
    onAddMore: () -> Unit,
    onProceedToPhase3: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Encabezado del paquete
        item(span = { GridItemSpan(3) }) {
            PackHeaderStatus(pack = pack, onProceedToPhase3 = onProceedToPhase3)
        }

        // Elementos de la cuadricula
        items(pack.stickers, key = { it.id }) { stickerItem ->
            StickerGridCard(
                stickerItem = stickerItem,
                onEditCrop = { onEditSticker(stickerItem) },
                onEditMetadata = { onEditMetadata(stickerItem) },
                onDelete = { onDeleteSticker(stickerItem.id) }
            )
        }

        // Boton agregar dentro de la cuadricula si aun no esta lleno
        if (!pack.isFull) {
            item {
                AddStickerSlotCard(onClick = onAddMore)
            }
        }
    }
}

@Composable
fun PackHeaderStatus(
    pack: StickerPack,
    onProceedToPhase3: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Autor: ${pack.publisher} • " + if (pack.isAnimated) "Animado (WebP)" else "Estatico (WebP 512x512)",
                        fontSize = 11.sp,
                        color = if (pack.isAnimated) AnimatedPurple else AccentTeal
                    )
                }
                Text(
                    text = stringResource(R.string.pack_count_format, pack.count),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (pack.canExport) EmeraldLight else WarningAmber
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!pack.canExport) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pack_min_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onProceedToPhase3,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ver Metadatos y Tray Icon (Fase 3)",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pack_valid_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldLight
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onProceedToPhase3,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = SurfaceDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_next_phase),
                        fontWeight = FontWeight.Bold,
                        color = SurfaceDark
                    )
                }
            }
        }
    }
}

@Composable
fun StickerGridCard(
    stickerItem: PackStickerItem,
    onEditCrop: () -> Unit,
    onEditMetadata: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Lienzo con patron de transparencia (al pulsar abre la edicion de metadatos)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(SurfaceDark)
                    .clickable(onClick = onEditMetadata),
                contentAlignment = Alignment.Center
            ) {
                TransparencyGrid()

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(stickerItem.processedSticker.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Sticker",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Etiqueta de peso en KB
                Surface(
                    color = SurfaceDark.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = stickerItem.processedSticker.formattedSize,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Insignia de emojis asignados o aviso en esquina superior
                if (stickerItem.emojis.isNotEmpty()) {
                    Surface(
                        color = SurfaceDark.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = stickerItem.emojis.joinToString(" "),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = WarningAmber.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = "Sin emojis",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Barra de acciones: Recortar, Metadatos/Emojis y Eliminar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardDark)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEditCrop,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = "Editar recorte",
                        tint = AccentTeal,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onEditMetadata,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = "Editar emojis y palabras clave",
                        tint = EmeraldLight,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFEA4335),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddStickerSlotCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Anadir",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun EmptyPackView(
    isAnimated: Boolean,
    onSelectImages: () -> Unit,
    onProceedToPhase3: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(90.dp),
            shape = CircleShape,
            color = SurfaceDark
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isAnimated) Icons.Default.Animation else Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = if (isAnimated) AnimatedPurple else EmeraldPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isAnimated) "Paquete Animado Vacio" else "Paquete Estatico Vacio",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isAnimated) {
                "Selecciona entre 3 y 30 archivos animados (GIF o WebP animado) para este paquete."
            } else {
                stringResource(R.string.empty_pack_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSelectImages,
            colors = ButtonDefaults.buttonColors(containerColor = if (isAnimated) AnimatedPurple else EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.select_image_button),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onProceedToPhase3,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ver Metadatos y Tray Icon (Fase 3)",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EditPackDetailsDialog(
    currentName: String,
    currentPublisher: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newPublisher: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var publisher by remember { mutableStateOf(currentPublisher) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.edit_pack_details_title),
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pack_name_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = EmeraldLight,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text(stringResource(R.string.pack_publisher_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = EmeraldLight,
                        unfocusedLabelColor = TextSecondary,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.ifBlank { currentName },
                        publisher.ifBlank { currentPublisher }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(
                    text = stringResource(R.string.btn_save),
                    color = SurfaceDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = TextSecondary
                )
            }
        },
        containerColor = SurfaceDark
    )
}
