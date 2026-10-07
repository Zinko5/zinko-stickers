package com.stickerforge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TransparencyGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val squareSize = 24f
        val numCols = (size.width / squareSize).toInt() + 1
        val numRows = (size.height / squareSize).toInt() + 1

        val color1 = Color(0xFF1E2830)
        val color2 = Color(0xFF151D24)

        for (row in 0 until numRows) {
            for (col in 0 until numCols) {
                val color = if ((row + col) % 2 == 0) color1 else color2
                drawRect(
                    color = color,
                    topLeft = Offset(col * squareSize, row * squareSize),
                    size = Size(squareSize, squareSize)
                )
            }
        }
    }
}

@Composable
fun CropGuideGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = Color(0x40FFFFFF)
        val strokeW = 1.dp.toPx()

        val oneThirdW = size.width / 3f
        val twoThirdsW = size.width * 2f / 3f
        val oneThirdH = size.height / 3f
        val twoThirdsH = size.height * 2f / 3f

        // Lineas verticales
        drawLine(lineColor, Offset(oneThirdW, 0f), Offset(oneThirdW, size.height), strokeW)
        drawLine(lineColor, Offset(twoThirdsW, 0f), Offset(twoThirdsW, size.height), strokeW)

        // Lineas horizontales
        drawLine(lineColor, Offset(0f, oneThirdH), Offset(size.width, oneThirdH), strokeW)
        drawLine(lineColor, Offset(0f, twoThirdsH), Offset(size.width, twoThirdsH), strokeW)
    }
}
