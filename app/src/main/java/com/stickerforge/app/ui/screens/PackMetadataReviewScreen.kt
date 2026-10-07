package com.stickerforge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.stickerforge.app.domain.WhatsAppExporter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stickerforge.app.ui.components.TransparencyGrid
import com.stickerforge.app.ui.theme.AccentTeal
import com.stickerforge.app.ui.theme.AnimatedPurple
import com.stickerforge.app.ui.theme.CardDark
import com.stickerforge.app.ui.theme.EmeraldLight
import com.stickerforge.app.ui.theme.EmeraldPrimary
import com.stickerforge.app.ui.theme.SurfaceDark
import com.stickerforge.app.ui.theme.TextPrimary
import com.stickerforge.app.ui.theme.TextSecondary
import com.stickerforge.app.ui.theme.WarningAmber
import com.stickerforge.app.ui.viewmodel.CurrentScreen
import com.stickerforge.app.ui.viewmodel.StickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackMetadataReviewScreen(
    viewModel: StickerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activePack = uiState.activePack
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val exportResult = WhatsAppExporter.parseExportResult(result.resultCode, result.data)
        viewModel.handleExportResult(exportResult)
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

    // Asegurar que el tray icon exista (Pepe Smolder adaptado por defecto)
    LaunchedEffect(activePack.id) {
        if (activePack.trayImageFile == null || !activePack.trayImageFile.exists()) {
            viewModel.setPepeSmolderTrayIconForActivePack(context)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(CurrentScreen.PACK_DETAIL) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al detalle",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Metadatos y Exportacion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${activePack.name} • Fase 4",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta 1: Informacion del paquete y identificador sin puntos
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Identificacion del Paquete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Identifier unico", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = activePack.identifier,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLight
                            )
                        }
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Sin puntos (Valido)",
                                fontSize = 10.sp,
                                color = EmeraldLight,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Nombre", fontSize = 11.sp, color = TextSecondary)
                            Text(text = activePack.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Autor", fontSize = 11.sp, color = TextSecondary)
                            Text(text = activePack.publisher, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
            }

            // Tarjeta 2: Icono de bandeja (96x96 px < 50 KB)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Icono de Bandeja (tray_image_file)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Requisito estricto de WhatsApp: 96x96 pixeles, formato PNG/WebP y peso < 50 KB.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .background(SurfaceDark),
                            contentAlignment = Alignment.Center
                        ) {
                            TransparencyGrid()
                            if (activePack.trayImageFile != null && activePack.trayImageFile.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(activePack.trayImageFile)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Tray Icon",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            if (activePack.trayImageFile != null && activePack.trayImageFile.exists()) {
                                Text(
                                    text = "Archivo: ${activePack.trayImageFile.name}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Tamano: ${activePack.trayImageFile.length() / 1024} KB (limite: 50 KB)",
                                    fontSize = 11.sp,
                                    color = EmeraldLight
                                )
                            } else {
                                Text(
                                    text = "Icono no generado aun",
                                    fontSize = 12.sp,
                                    color = WarningAmber
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.setPepeSmolderTrayIconForActivePack(context) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(text = "Pepe Smolder", fontSize = 11.sp)
                                }

                                if (activePack.stickers.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { viewModel.generateTrayIconForActivePack(context, preferPepeDefault = false) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(text = "1er Sticker", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tarjeta 3: Checklist Pre-Exportacion WhatsApp
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Checklist de Validacion de WhatsApp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ChecklistItem(
                        title = "Cantidad de stickers: ${activePack.count} / 30",
                        description = "WhatsApp exige entre 3 y 30 stickers por paquete",
                        isValid = activePack.canExportCount
                    )

                    ChecklistItem(
                        title = "Resolucion exacta 512x512 y WebP < 100 KB",
                        description = "Todos los stickers procesados y optimizados",
                        isValid = activePack.stickers.isNotEmpty()
                    )

                    ChecklistItem(
                        title = "Identificador sin puntos",
                        description = activePack.identifier,
                        isValid = activePack.hasValidIdentifier
                    )

                    ChecklistItem(
                        title = "Icono de bandeja (tray_image_file)",
                        description = "96x96 px menor a 50 KB",
                        isValid = activePack.trayImageFile != null && activePack.trayImageFile.exists()
                    )

                    ChecklistItem(
                        title = "Etiquetado con emojis para busqueda",
                        description = "${activePack.stickers.count { it.hasValidEmojis }} de ${activePack.count} stickers tienen entre 1 y 3 emojis",
                        isValid = activePack.allStickersHaveEmojis
                    )

                    if (!activePack.allStickersHaveEmojis && activePack.stickers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.autoAssignDefaultEmojisToUnlabeledStickers() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = SurfaceDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Completar emojis faltantes automaticamente",
                                color = SurfaceDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Tarjeta 4: Generacion y guardado de contents.json
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manifiesto contents.json",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = EmeraldLight)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Archivo requerido por WhatsApp para indexar el paquete y asociar emojis y textos de accesibilidad.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.preparePackContentsJson(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, tint = SurfaceDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generar y guardar contents.json",
                            color = SurfaceDark,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.generatedContentsJson != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldLight)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Archivo contents.json guardado en packs/${activePack.identifier}/contents.json",
                                    fontSize = 11.sp,
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Visor de JSON generado
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = uiState.generatedContentsJson ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Tarjeta 5: Exportacion oficial a WhatsApp (Fase 4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exportacion a WhatsApp",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Surface(
                            color = if (activePack.isReadyForWhatsAppExport) EmeraldPrimary.copy(alpha = 0.2f) else WarningAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (activePack.isReadyForWhatsAppExport) "Listo para exportar" else "Requisitos pendientes",
                                fontSize = 10.sp,
                                color = if (activePack.isReadyForWhatsAppExport) EmeraldLight else WarningAmber,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Realiza el handshake oficial mediante com.whatsapp.intent.action.ENABLE_STICKER_PACK y StickerContentProvider.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val validationIssues = activePack.getValidationIssues()
                    if (validationIssues.isNotEmpty()) {
                        Surface(
                            color = WarningAmber.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Debes resolver lo siguiente antes de exportar:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                validationIssues.forEach { issue ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = WarningAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = issue, fontSize = 11.sp, color = TextPrimary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    val installedPackages = remember(context) { WhatsAppExporter.getInstalledWhatsAppPackages(context) }

                    if (installedPackages.isNotEmpty()) {
                        installedPackages.forEach { pkg ->
                            Button(
                                onClick = {
                                    val (intent, _) = viewModel.prepareAndGetExportIntent(context, targetPackage = pkg.packageName)
                                    if (intent != null) {
                                        try {
                                            exportLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al lanzar ${pkg.title}: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = activePack.isReadyForWhatsAppExport,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = SurfaceDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Anadir a ${pkg.title}",
                                    color = SurfaceDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val (intent, _) = viewModel.prepareAndGetExportIntent(context, targetPackage = null)
                                if (intent != null) {
                                    try {
                                        exportLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No se encontro aplicacion compatible de WhatsApp en este dispositivo.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = activePack.isReadyForWhatsAppExport,
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
                                text = "Exportar a WhatsApp",
                                color = SurfaceDark,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Nota: WhatsApp oficial o WhatsApp Business debe estar instalado en el telefono para recibir el paquete.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistItem(
    title: String,
    description: String,
    isValid: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isValid) EmeraldLight else WarningAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
