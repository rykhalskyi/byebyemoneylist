package com.otakeeesen.byebyemoneylist.ui.components.shoppinglist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShoppingListStatusIndicator(
    isFinished: Boolean,
    isArchived: Boolean,
    isInStore: Boolean,
    modifier: Modifier = Modifier,
    dotRadius: Dp = 5.dp,
    lineLength: Dp = 12.dp,
    lineThickness: Dp = 2.dp,
) {
    val currentStep = when {
        isArchived -> 3
        isFinished -> 2
        isInStore -> 1
        else -> 0
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline
    val completedGreen = Color(0xFF4CAF50)
    val futureOutline = outlineColor.copy(alpha = 0.5f)
    val futureLineColor = outlineColor.copy(alpha = 0.3f)

    val currentColor by animateColorAsState(
        targetValue = when (currentStep) {
            0, 1 -> primaryColor
            2 -> errorColor
            3 -> outlineColor
            else -> primaryColor
        },
        label = "currentDotColor"
    )

    val dotDiameter = dotRadius * 2
    val totalWidth = dotDiameter * 4 + lineLength * 3

    val stepLabels = listOf("Active", "In Store", "Finished", "Archived")
    val contentDesc = stepLabels[currentStep.coerceIn(0, 3)]

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(dotDiameter)
            .semantics { contentDescription = contentDesc }
    ) {
        val dotDiaPx = dotDiameter.toPx()
        val lineLenPx = lineLength.toPx()
        val radiusPx = dotRadius.toPx()
        val centerY = size.height / 2

        for (i in 0..2) {
            val startX = i * (dotDiaPx + lineLenPx) + dotDiaPx
            val endX = startX + lineLenPx
            val isCompleted = i < currentStep

            drawLine(
                color = if (isCompleted) completedGreen else futureLineColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = lineThickness.toPx(),
                pathEffect = if (isCompleted) null else dashEffect,
            )
        }

        for (i in 0..3) {
            val centerX = i * (dotDiaPx + lineLenPx) + radiusPx

            when {
                i < currentStep -> {
                    drawCircle(
                        color = completedGreen,
                        radius = radiusPx,
                        center = Offset(centerX, centerY)
                    )
                }
                i == currentStep -> {
                    drawCircle(
                        color = currentColor,
                        radius = radiusPx,
                        center = Offset(centerX, centerY)
                    )
                }
                else -> {
                    drawCircle(
                        color = futureOutline,
                        radius = radiusPx,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}
