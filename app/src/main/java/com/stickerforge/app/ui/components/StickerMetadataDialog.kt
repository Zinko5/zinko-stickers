package com.stickerforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stickerforge.app.model.PackStickerItem
import com.stickerforge.app.ui.theme.AccentTeal
import com.stickerforge.app.ui.theme.CardDark
import com.stickerforge.app.ui.theme.EmeraldLight
import com.stickerforge.app.ui.theme.EmeraldPrimary
import com.stickerforge.app.ui.theme.SurfaceDark
import com.stickerforge.app.ui.theme.TextPrimary
import com.stickerforge.app.ui.theme.TextSecondary
import com.stickerforge.app.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerMetadataDialog(
    stickerItem: PackStickerItem,
    onDismiss: () -> Unit,
    onSave: (name: String, emojis: List<String>, accessibilityText: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(stickerItem.name) }
    var accessibilityText by remember { mutableStateOf(stickerItem.accessibilityText) }
    var selectedEmojis by remember { mutableStateOf(stickerItem.emojis.take(3)) }
    var hasUserEditedKeywordsManually by remember { mutableStateOf(stickerItem.accessibilityText.isNotBlank()) }

    val emojiCategories = listOf(
        "Emociones" to listOf("😀", "😂", "🤣", "😍", "😎", "🥳", "🤔", "😱", "😭", "😡", "😴", "🥺", "👍", "👎", "👏", "💪"),
        "Animales" to listOf("🐶", "🐱", "🐭", "🦊", "🐻", "🐼", "🦁", "🐯", "🐵", "🐸", "🦄", "🐔", "🐧", "🐺", "🐷", "🐝"),
        "Objetos" to listOf("❤️", "🔥", "✨", "🎉", "💯", "⭐", "🚀", "🎁", "💡", "💥", "💣", "☕", "🍕", "🍺", "⚽", "🏆"),
        "Expresiones" to listOf("👀", "🤷", "🤦", "🤐", "🤫", "🤪", "🤠", "👻", "💀", "👽", "🤖", "💩", "🎯", "⚡", "🌈", "☀️")
    )
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Metadatos y Busqueda del Sticker",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Previsualizacion miniatura y estado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        TransparencyGrid()
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(stickerItem.processedSticker.file)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (stickerItem.isAnimated) "Sticker animado" else "Sticker estatico (512x512)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentTeal
                        )
                        Text(
                            text = "Peso: ${stickerItem.processedSticker.formattedSize}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campo 1: Nombre del sticker
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        name = newName
                        // Recomendacion UX: precargar palabras clave con el nombre si no ha sido editado manualmente
                        if (!hasUserEditedKeywordsManually) {
                            accessibilityText = newName.take(125)
                        }
                    },
                    label = { Text("Nombre del sticker") },
                    placeholder = { Text("Ej: Gato pensativo") },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Campo 2: Palabras clave de busqueda (accessibility_text max 125 chars)
                OutlinedTextField(
                    value = accessibilityText,
                    onValueChange = {
                        hasUserEditedKeywordsManually = true
                        accessibilityText = it.take(125)
                    },
                    label = { Text("Palabras clave de busqueda") },
                    placeholder = { Text("Ej: gato pensando risa humor") },
                    supportingText = {
                        Text(
                            text = "${accessibilityText.length} / 125 caracteres (accessibility_text)",
                            fontSize = 10.sp,
                            color = if (accessibilityText.length >= 120) WarningAmber else TextSecondary
                        )
                    },
                    singleLine = false,
                    maxLines = 2,
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

                // Campo 3: Selector de hasta 3 emojis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Emojis asociados para WhatsApp",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${selectedEmojis.size} / 3",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedEmojis.isNotEmpty()) EmeraldLight else WarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Emojis actualmente seleccionados
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardDark, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedEmojis.isEmpty()) {
                        Text(
                            text = "Toca abajo para asociar entre 1 y 3 emojis",
                            fontSize = 12.sp,
                            color = WarningAmber
                        )
                    } else {
                        selectedEmojis.forEach { emoji ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceDark,
                                modifier = Modifier.clickable {
                                    selectedEmojis = selectedEmojis.filterNot { it == emoji }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = emoji, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Quitar emoji",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selector rapido por categorias
                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex,
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    edgePadding = 0.dp
                ) {
                    emojiCategories.forEachIndexed { idx, pair ->
                        Tab(
                            selected = selectedCategoryIndex == idx,
                            onClick = { selectedCategoryIndex = idx },
                            text = {
                                Text(
                                    text = pair.first,
                                    fontSize = 11.sp,
                                    color = if (selectedCategoryIndex == idx) EmeraldLight else TextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Grilla de emojis de la categoria activa
                val activeEmojiList = emojiCategories[selectedCategoryIndex].second
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeEmojiList.forEach { emoji ->
                        val isSelected = selectedEmojis.contains(emoji)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) EmeraldPrimary.copy(alpha = 0.3f) else CardDark)
                                .clickable {
                                    if (isSelected) {
                                        selectedEmojis = selectedEmojis.filterNot { it == emoji }
                                    } else if (selectedEmojis.size < 3) {
                                        selectedEmojis = selectedEmojis + emoji
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        selectedEmojis,
                        accessibilityText.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(
                    text = "Guardar",
                    color = SurfaceDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}
