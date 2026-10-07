package com.stickerforge.app.ui.screens

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.stickerforge.app.ui.viewmodel.PackFilter
import com.stickerforge.app.ui.viewmodel.StickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackListScreen(
    viewModel: StickerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var packToDelete by remember { mutableStateOf<StickerPack?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_packs_hint),
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = EmeraldPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar busqueda",
                                tint = TextSecondary
                            )
                        }
                    },
                    actions = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpiar busqueda",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldPrimary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = null,
                                        tint = SurfaceDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${uiState.packs.size} paquetes en total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar paquetes",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = SurfaceDark,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_new_pack)
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.create_new_pack),
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filtros de paquetes
            PackFilterChipsRow(
                selectedFilter = uiState.selectedFilter,
                onSelectFilter = { viewModel.setFilter(it) },
                packs = uiState.packs
            )

            val displayPacks = uiState.filteredPacks

            if (displayPacks.isEmpty()) {
                EmptyPacksResultView(
                    hasQuery = uiState.searchQuery.isNotBlank(),
                    onClearSearch = { viewModel.setSearchQuery("") },
                    onCreatePack = { showCreateDialog = true }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayPacks, key = { it.id }) { pack ->
                        PackCardItem(
                            pack = pack,
                            onClick = { viewModel.openPack(pack.id) },
                            onDelete = { packToDelete = pack }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePackDialog(
            defaultName = "Pack ${uiState.packs.size + 1}",
            defaultPublisher = "Zinko Stickers",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, publisher, isAnimated ->
                showCreateDialog = false
                viewModel.createNewPack(name = name, publisher = publisher, isAnimated = isAnimated)
            }
        )
    }

    packToDelete?.let { pack ->
        DeletePackConfirmDialog(
            packName = pack.name,
            onDismiss = { packToDelete = null },
            onConfirm = {
                viewModel.deletePack(pack.id)
                packToDelete = null
            }
        )
    }
}

@Composable
fun PackFilterChipsRow(
    selectedFilter: PackFilter,
    onSelectFilter: (PackFilter) -> Unit,
    packs: List<StickerPack>
) {
    val staticCount = packs.count { !it.isAnimated }
    val animatedCount = packs.count { it.isAnimated }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedFilter == PackFilter.ALL,
            onClick = { onSelectFilter(PackFilter.ALL) },
            label = { Text("Todos (${packs.size})", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                selectedLabelColor = EmeraldLight,
                labelColor = TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = Color(0x33FFFFFF),
                selectedBorderColor = EmeraldPrimary,
                enabled = true,
                selected = selectedFilter == PackFilter.ALL
            )
        )

        FilterChip(
            selected = selectedFilter == PackFilter.STATIC,
            onClick = { onSelectFilter(PackFilter.STATIC) },
            label = { Text("Estaticos ($staticCount)", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentTeal.copy(alpha = 0.2f),
                selectedLabelColor = AccentTeal,
                labelColor = TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = Color(0x33FFFFFF),
                selectedBorderColor = AccentTeal,
                enabled = true,
                selected = selectedFilter == PackFilter.STATIC
            )
        )

        FilterChip(
            selected = selectedFilter == PackFilter.ANIMATED,
            onClick = { onSelectFilter(PackFilter.ANIMATED) },
            label = { Text("Animados ($animatedCount)", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AnimatedPurple.copy(alpha = 0.2f),
                selectedLabelColor = AnimatedPurple,
                labelColor = TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = Color(0x33FFFFFF),
                selectedBorderColor = AnimatedPurple,
                enabled = true,
                selected = selectedFilter == PackFilter.ANIMATED
            )
        )
    }
}

@Composable
fun PackCardItem(
    pack: StickerPack,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column {
            // Zona visual de previsualizacion
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                TransparencyGrid()

                if (pack.stickers.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (pack.isAnimated) Icons.Default.Animation else Icons.Default.Image,
                            contentDescription = null,
                            tint = if (pack.isAnimated) AnimatedPurple.copy(alpha = 0.7f) else AccentTeal.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sin stickers",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                } else {
                    // Muestra hasta 4 stickers en una mini-cuadricula 2x2
                    val previewItems = pack.stickers.take(4)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(previewItems.first().processedSticker.file)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (previewItems.size > 1) {
                                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(previewItems[1].processedSticker.file)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.weight(1f).fillMaxSize())
                            }
                        }

                        if (previewItems.size > 2) {
                            Row(
                                modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(previewItems[2].processedSticker.file)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (previewItems.size > 3) {
                                    Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(previewItems[3].processedSticker.file)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                }

                // Badge de tipo: Estatico o Animado
                Surface(
                    color = if (pack.isAnimated) AnimatedPurple else AccentTeal,
                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (pack.isAnimated) "Animado" else "Estatico",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Informacion del paquete
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pack.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "por ${pack.publisher}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar paquete",
                            tint = TextSecondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pack.count}/30",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    if (pack.canExport) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldLight,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Listo",
                                fontSize = 11.sp,
                                color = EmeraldLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Min 3",
                                fontSize = 11.sp,
                                color = WarningAmber
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPacksResultView(
    hasQuery: Boolean,
    onClearSearch: () -> Unit,
    onCreatePack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = SurfaceDark
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (hasQuery) Icons.Default.Search else Icons.Default.Collections,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasQuery) stringResource(R.string.no_packs_found) else stringResource(R.string.no_packs_created),
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (hasQuery) {
            Button(
                onClick = onClearSearch,
                colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Limpiar busqueda", color = TextPrimary)
            }
        } else {
            Button(
                onClick = onCreatePack,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = stringResource(R.string.create_new_pack), color = SurfaceDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CreatePackDialog(
    defaultName: String,
    defaultPublisher: String = "Zinko Stickers",
    onDismiss: () -> Unit,
    onConfirm: (name: String, publisher: String, isAnimated: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var publisher by remember { mutableStateOf(defaultPublisher) }
    var isAnimated by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_create_pack_title),
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.pack_type_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Opcion Estatico
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = !isAnimated,
                            onClick = { isAnimated = false }
                        )
                        .background(if (!isAnimated) AccentTeal.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !isAnimated,
                        onClick = { isAnimated = false },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AccentTeal,
                            unselectedColor = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.pack_type_static),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.pack_type_static_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Opcion Animado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = isAnimated,
                            onClick = { isAnimated = true }
                        )
                        .background(if (isAnimated) AnimatedPurple.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isAnimated,
                        onClick = { isAnimated = true },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AnimatedPurple,
                            unselectedColor = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.pack_type_animated),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.pack_type_animated_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, publisher, isAnimated) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(
                    text = stringResource(R.string.btn_create),
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

@Composable
fun DeletePackConfirmDialog(
    packName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_pack_title),
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = "${stringResource(R.string.delete_pack_confirm)}\n\nPaquete: $packName",
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text(
                    text = stringResource(R.string.btn_delete),
                    color = Color.White,
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
