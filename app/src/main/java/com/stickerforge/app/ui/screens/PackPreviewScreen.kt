package com.stickerforge.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.ui.components.TransparencyGrid
import com.stickerforge.app.ui.theme.AccentTeal
import com.stickerforge.app.ui.theme.BackgroundDark
import com.stickerforge.app.ui.theme.CardDark
import com.stickerforge.app.ui.theme.EmeraldLight
import com.stickerforge.app.ui.theme.EmeraldPrimary
import com.stickerforge.app.ui.theme.SurfaceDark
import com.stickerforge.app.ui.theme.TextPrimary
import com.stickerforge.app.ui.theme.TextSecondary
import com.stickerforge.app.ui.viewmodel.CurrentScreen
import com.stickerforge.app.ui.viewmodel.StickerViewModel

enum class PreviewBackground(val label: String, val color: Color) {
    WHATSAPP_LIGHT("WhatsApp Claro", Color(0xFFEFEAE2)),
    WHATSAPP_DARK("WhatsApp Oscuro", Color(0xFF0B141A)),
    WHATSAPP_GREEN("Verde WhatsApp", Color(0xFF075E54)),
    TRANSPARENCY("Transparencia", Color.Transparent)
}

enum class PreviewDisplayMode(val label: String) {
    CHAT_SIMULATION("Vista Chat"),
    KEYBOARD_GRID("Selector / Teclado")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackPreviewScreen(
    viewModel: StickerViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { viewModel.navigateTo(CurrentScreen.PACK_DETAIL) },
    onProceedToExport: () -> Unit = { viewModel.navigateTo(CurrentScreen.METADATA_REVIEW) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activePack = uiState.activePack

    var selectedBg by remember { mutableStateOf(PreviewBackground.WHATSAPP_DARK) }
    var displayMode by remember { mutableStateOf(PreviewDisplayMode.CHAT_SIMULATION) }
    var inspectedSticker by remember { mutableStateOf<PackStickerItem?>(activePack.stickers.firstOrNull()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Vista Previa de WhatsApp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${activePack.name} (${activePack.count} stickers)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onProceedToExport) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Metadatos y Exportacion",
                            tint = EmeraldLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceDark,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(text = "Volver al Pack", color = TextPrimary)
                    }

                    Button(
                        onClick = onProceedToExport,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = SurfaceDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Exportar a WhatsApp",
                            fontWeight = FontWeight.Bold,
                            color = SurfaceDark
                        )
                    }
                }
            }
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Controles de fondo y modo de visualizacion
            Surface(
                color = SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Selector de fondo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewBackground.entries.forEach { bgOption ->
                            FilterChip(
                                selected = selectedBg == bgOption,
                                onClick = { selectedBg = bgOption },
                                label = {
                                    Text(
                                        text = bgOption.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedBg == bgOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary,
                                    selectedLabelColor = SurfaceDark,
                                    containerColor = CardDark,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Selector de modo de vista (Chat vs Cuadricula de teclado)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreviewDisplayMode.entries.forEach { mode ->
                            FilterChip(
                                selected = displayMode == mode,
                                onClick = { displayMode = mode },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (mode == PreviewDisplayMode.CHAT_SIMULATION) Icons.AutoMirrored.Filled.Send else Icons.Default.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (displayMode == mode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentTeal,
                                    selectedLabelColor = SurfaceDark,
                                    containerColor = CardDark,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Lienzo de previsualizacion interactivo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (selectedBg == PreviewBackground.TRANSPARENCY) SurfaceDark else selectedBg.color)
            ) {
                if (selectedBg == PreviewBackground.TRANSPARENCY) {
                    TransparencyGrid(modifier = Modifier.fillMaxSize())
                }

                if (activePack.stickers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay stickers en este paquete para previsualizar.",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (displayMode == PreviewDisplayMode.CHAT_SIMULATION) {
                    ChatSimulationView(
                        stickers = activePack.stickers,
                        isDarkBg = selectedBg != PreviewBackground.WHATSAPP_LIGHT,
                        onSelectSticker = { inspectedSticker = it }
                    )
                } else {
                    KeyboardTraySimulationView(
                        stickers = activePack.stickers,
                        onSelectSticker = { inspectedSticker = it }
                    )
                }
            }

            // Inspector del sticker seleccionado
            inspectedSticker?.let { sticker ->
                StickerTechnicalInspector(
                    sticker = sticker,
                    onClose = { inspectedSticker = null },
                    onEditCrop = {
                        viewModel.startEditingSticker(sticker)
                    },
                    onEditMetadata = {
                        viewModel.startEditingMetadata(sticker)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatSimulationView(
    stickers: List<PackStickerItem>,
    isDarkBg: Boolean,
    onSelectSticker: (PackStickerItem) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Burbuja informativa de cabecera simulando chat
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = if (isDarkBg) Color(0xFF182229) else Color(0xFFFFFFFF).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = "Simulacion de conversacion de WhatsApp - Toca cualquier sticker para inspeccionar",
                        fontSize = 11.sp,
                        color = if (isDarkBg) TextSecondary else Color(0xFF54656F),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        items(stickers) { item ->
            // Stickers en WhatsApp se muestran flotantes sobre el fondo, con hora sutil a la derecha
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectSticker(item) }
                        .padding(4.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.processedSticker.file)
                            .decoderFactory(
                                if (Build.VERSION.SDK_INT >= 28) {
                                    ImageDecoderDecoder.Factory()
                                } else {
                                    GifDecoder.Factory()
                                }
                            )
                            .crossfade(true)
                            .build(),
                        contentDescription = item.effectiveSearchKeywords,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Hora y check sutil de WhatsApp en la esquina inferior derecha
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "12:00",
                                fontSize = 9.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardTraySimulationView(
    stickers: List<PackStickerItem>,
    onSelectSticker: (PackStickerItem) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Bandeja de Stickers (Como se vera en el teclado de WhatsApp):",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stickers) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDark.copy(alpha = 0.6f))
                        .clickable { onSelectSticker(item) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.processedSticker.file)
                            .decoderFactory(
                                if (Build.VERSION.SDK_INT >= 28) {
                                    ImageDecoderDecoder.Factory()
                                } else {
                                    GifDecoder.Factory()
                                }
                            )
                            .crossfade(true)
                            .build(),
                        contentDescription = item.effectiveSearchKeywords,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerTechnicalInspector(
    sticker: PackStickerItem,
    onClose: () -> Unit,
    onEditCrop: () -> Unit,
    onEditMetadata: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ficha Tecnica del Sticker",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dimensiones: ${sticker.processedSticker.width}x${sticker.processedSticker.height} px",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Peso: ${sticker.processedSticker.formattedSize} (<100 KB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sticker.processedSticker.isUnder100KB) EmeraldLight else Color(0xFFEA4335)
                    )
                    Text(
                        text = "Nombre: ${sticker.name.ifBlank { "Sin nombre" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Emojis: ${if (sticker.emojis.isNotEmpty()) sticker.emojis.joinToString(" ") else "Sin emojis"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tipo: ${if (sticker.isAnimated) "Animado" else "Estatico WebP"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditCrop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Ajustar Recorte", fontSize = 11.sp, color = TextPrimary)
                }

                OutlinedButton(
                    onClick = onEditMetadata,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Editar Metadatos", fontSize = 11.sp, color = TextPrimary)
                }
            }
        }
    }
}
