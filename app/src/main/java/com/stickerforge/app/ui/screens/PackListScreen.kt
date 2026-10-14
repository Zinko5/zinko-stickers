package com.stickerforge.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import com.stickerforge.app.BuildConfig
import com.stickerforge.app.domain.WhatsAppExporter
import com.stickerforge.app.ui.components.AppUpdateDialog
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchActive by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var packToDelete by remember { mutableStateOf<StickerPack?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var selectedPackIdsForExport by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Launcher para exportar respaldo a archivo comprimido .zip
    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destinationUri ->
        if (destinationUri != null) {
            viewModel.exportBackup(context, selectedPackIdsForExport, destinationUri)
        }
    }

    // Launcher para importar respaldo desde archivo comprimido .zip
    val openZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            viewModel.importBackup(context, sourceUri)
        }
    }

    var packForExportSelection by remember { mutableStateOf<StickerPack?>(null) }
    var exportingPackId by remember { mutableStateOf<String?>(null) }

    // Launcher para exportar paquete directamente a WhatsApp
    val exportWhatsAppLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val exportResult = WhatsAppExporter.parseExportResult(result.resultCode, result.data)
        viewModel.handleExportResult(exportResult, exportingPackId)
        exportingPackId = null
    }

    LaunchedEffect(uiState.exportSuccessMessage) {
        uiState.exportSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissExportMessages()
        }
    }

    LaunchedEffect(uiState.exportErrorMessage) {
        uiState.exportErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissExportMessages()
        }
    }

    LaunchedEffect(uiState.backupSuccessMessage) {
        uiState.backupSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBackupMessages()
        }
    }

    LaunchedEffect(uiState.backupErrorMessage) {
        uiState.backupErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBackupMessages()
        }
    }

    LaunchedEffect(uiState.updateStatusMessage) {
        uiState.updateStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUpdateStatusMessage()
        }
    }

    LaunchedEffect(uiState.updateErrorMessage) {
        uiState.updateErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUpdateStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            Image(
                                painter = painterResource(R.drawable.app_logo),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
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

                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opciones y ajustes",
                                    tint = TextPrimary
                                )
                            }

                            if (uiState.availableUpdate != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 2.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldLight)
                                )
                            }

                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                                modifier = Modifier.background(CardDark)
                            ) {
                                if (uiState.availableUpdate != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Actualizar app (${uiState.availableUpdate?.versionTag})",
                                                    color = EmeraldLight,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "Nueva version disponible",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.SystemUpdate,
                                                contentDescription = null,
                                                tint = EmeraldLight
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.showUpdateDialog()
                                        }
                                    )

                                    HorizontalDivider(
                                        color = Color(0x22FFFFFF),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Exportar Respaldo (.zip)", color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = EmeraldLight
                                        )
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        selectedPackIdsForExport = uiState.packs.map { it.id }.toSet()
                                        showExportBackupDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Importar Respaldo (.zip)", color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FileUpload,
                                            contentDescription = null,
                                            tint = AccentTeal
                                        )
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        openZipLauncher.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    }
                                )

                                HorizontalDivider(
                                    color = Color(0x22FFFFFF),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                DropdownMenuItem(
                                    text = { Text("Buscar actualizaciones", color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Update,
                                            contentDescription = null,
                                            tint = AccentTeal
                                        )
                                    },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.checkForAppUpdate(context, isManualCheck = true)
                                    }
                                )
                            }
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
            if (uiState.isProcessing) {
                Surface(
                    color = CardDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = EmeraldPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Procesando paquetes...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

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
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayPacks, key = { it.id }) { pack ->
                        PackCardItem(
                            pack = pack,
                            onClick = { viewModel.openPack(pack.id) },
                            onDelete = { packToDelete = pack },
                            onExportToWhatsApp = {
                                val installed = WhatsAppExporter.getInstalledWhatsAppPackages(context)
                                if (installed.isEmpty()) {
                                    val (intent, issues) = viewModel.prepareAndGetExportIntent(context, targetPackage = null, packId = pack.id)
                                    if (intent != null) {
                                        exportingPackId = pack.id
                                        try {
                                            exportWhatsAppLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No se encontro aplicacion compatible de WhatsApp.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, issues.firstOrNull() ?: "El paquete no cumple con los requisitos para WhatsApp.", Toast.LENGTH_LONG).show()
                                    }
                                } else if (installed.size == 1) {
                                    val targetPkg = installed.first().packageName
                                    val (intent, issues) = viewModel.prepareAndGetExportIntent(context, targetPackage = targetPkg, packId = pack.id)
                                    if (intent != null) {
                                        exportingPackId = pack.id
                                        try {
                                            exportWhatsAppLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al lanzar WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, issues.firstOrNull() ?: "El paquete no cumple con los requisitos para WhatsApp.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    packForExportSelection = pack
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    packForExportSelection?.let { pack ->
        val installed = remember(context) { WhatsAppExporter.getInstalledWhatsAppPackages(context) }
        AlertDialog(
            onDismissRequest = { packForExportSelection = null },
            title = {
                Text(
                    text = if (pack.isExportedToWhatsApp) "Actualizar en WhatsApp" else "Exportar a WhatsApp",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = if (pack.isExportedToWhatsApp)
                            "Selecciona en cual aplicacion de WhatsApp deseas actualizar \"${pack.name}\":"
                        else
                            "Selecciona en cual aplicacion de WhatsApp deseas anadir \"${pack.name}\":",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    installed.forEach { pkg ->
                        Button(
                            onClick = {
                                val selectedPack = pack
                                packForExportSelection = null
                                val (intent, issues) = viewModel.prepareAndGetExportIntent(context, targetPackage = pkg.packageName, packId = selectedPack.id)
                                if (intent != null) {
                                    exportingPackId = selectedPack.id
                                    try {
                                        exportWhatsAppLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error al lanzar ${pkg.title}: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, issues.firstOrNull() ?: "Error de validacion", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = SurfaceDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Anadir a ${pkg.title}", color = SurfaceDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { packForExportSelection = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
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

    if (showExportBackupDialog) {
        ExportBackupDialog(
            packs = uiState.packs,
            selectedPackIds = selectedPackIdsForExport,
            onSelectionChange = { selectedPackIdsForExport = it },
            onDismiss = { showExportBackupDialog = false },
            onConfirmExport = {
                showExportBackupDialog = false
                createZipLauncher.launch("zinko_stickers_backup_${System.currentTimeMillis()}.zip")
            }
        )
    }

    if (uiState.isUpdateDialogVisible && uiState.availableUpdate != null) {
        AppUpdateDialog(
            updateInfo = uiState.availableUpdate!!,
            currentVersionName = BuildConfig.VERSION_NAME,
            isDownloading = uiState.isDownloadingUpdate,
            downloadProgress = uiState.updateDownloadProgress,
            readyToInstall = uiState.readyToInstallApk != null,
            errorMessage = uiState.updateErrorMessage,
            onUpdate = { viewModel.startUpdateDownload(context) },
            onInstall = { viewModel.installApk(context) },
            onDismiss = { viewModel.dismissUpdateDialog() },
            onIgnoreThisVersion = { viewModel.ignoreCurrentUpdate(context) }
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
    val notExportedCount = packs.count { !it.isExportedToWhatsApp }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .horizontalScroll(rememberScrollState())
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

        FilterChip(
            selected = selectedFilter == PackFilter.NOT_EXPORTED,
            onClick = { onSelectFilter(PackFilter.NOT_EXPORTED) },
            label = { Text("Sin exportar ($notExportedCount)", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = WarningAmber.copy(alpha = 0.2f),
                selectedLabelColor = WarningAmber,
                labelColor = TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = Color(0x33FFFFFF),
                selectedBorderColor = WarningAmber,
                enabled = true,
                selected = selectedFilter == PackFilter.NOT_EXPORTED
            )
        )
    }
}

@Composable
fun PackCardItem(
    pack: StickerPack,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExportToWhatsApp: () -> Unit
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

                // Badge sutil: "En WhatsApp"
                if (pack.isExportedToWhatsApp) {
                    Surface(
                        color = Color(0xDD075E54), // Verde sutil WhatsApp
                        shape = RoundedCornerShape(topEnd = 0.dp, bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "En WhatsApp",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Boton de exportar directo a WhatsApp
                        IconButton(
                            onClick = onExportToWhatsApp,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (pack.isExportedToWhatsApp) "Actualizar en WhatsApp" else "Exportar a WhatsApp",
                                tint = if (pack.canExport) EmeraldLight else TextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

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
                                text = if (pack.isExportedToWhatsApp) "Instalado" else "Listo",
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

@Composable
fun ExportBackupDialog(
    packs: List<StickerPack>,
    selectedPackIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit
) {
    val allSelected = packs.isNotEmpty() && selectedPackIds.size == packs.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = EmeraldLight,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Exportar Respaldo (.zip)",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selecciona los paquetes que deseas incluir en el archivo comprimido para respaldar o compartir:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Fila Seleccionar Todos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (allSelected) {
                                onSelectionChange(emptySet())
                            } else {
                                onSelectionChange(packs.map { it.id }.toSet())
                            }
                        }
                        .background(CardDark)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                onSelectionChange(packs.map { it.id }.toSet())
                            } else {
                                onSelectionChange(emptySet())
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = EmeraldPrimary,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = SurfaceDark
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Seleccionar todos (${packs.size})",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${selectedPackIds.size} elegidos",
                        color = AccentTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (packs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay paquetes creados para exportar.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(packs, key = { it.id }) { pack ->
                            val isChecked = pack.id in selectedPackIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val newSet = if (isChecked) {
                                            selectedPackIds - pack.id
                                        } else {
                                            selectedPackIds + pack.id
                                        }
                                        onSelectionChange(newSet)
                                    }
                                    .background(if (isChecked) EmeraldPrimary.copy(alpha = 0.08f) else CardDark)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val newSet = if (checked) {
                                            selectedPackIds + pack.id
                                        } else {
                                            selectedPackIds - pack.id
                                        }
                                        onSelectionChange(newSet)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EmeraldPrimary,
                                        uncheckedColor = TextSecondary,
                                        checkmarkColor = SurfaceDark
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                // Tray icon / Preview
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val previewFile = pack.trayImageFile ?: pack.stickers.firstOrNull()?.processedSticker?.file
                                    if (previewFile != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(previewFile)
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pack.name,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (pack.isAnimated) "Animado" else "Estatico",
                                            color = if (pack.isAnimated) AnimatedPurple else AccentTeal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = " - ${pack.stickers.size} stickers",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExport,
                enabled = selectedPackIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    disabledContainerColor = EmeraldPrimary.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "Exportar (${selectedPackIds.size})",
                    color = SurfaceDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancelar",
                    color = TextSecondary
                )
            }
        },
        containerColor = SurfaceDark
    )
}

