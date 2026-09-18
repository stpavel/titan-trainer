package com.svensson.titan.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.svensson.titan.domain.model.WorkoutProgram
import androidx.compose.ui.unit.Dp
/**
 * Гистограмма плановой нагрузки программы: ширина столбца ∝ длительности сегмента,
 * высота ∝ целевому сопротивлению. progressFraction (0..1) — указатель прогресса тренировки.
 */
@Composable
fun ProgramProfileChart(
    program: WorkoutProgram,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 80.dp,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val pastBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val markerColor = MaterialTheme.colorScheme.error

    val totalDuration = program.totalDurationSeconds.coerceAtLeast(1)
    val maxResistance = (program.segments.maxOfOrNull { it.targetResistance } ?: 1).coerceAtLeast(1)


    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        var elapsedSoFar = 0f
        val progressX = size.width * progressFraction.coerceIn(0f, 1f)
        val gapPx = 1.dp.toPx()

        program.segments.forEach { segment ->
            val startX = size.width * (elapsedSoFar / totalDuration)
            val segmentWidth = (size.width * (segment.durationSeconds.toFloat() / totalDuration) - gapPx)
                .coerceAtLeast(0f)
            val barHeight = size.height * (segment.targetResistance.toFloat() / maxResistance)

            drawRect(
                color = if (startX < progressX) pastBarColor else barColor,
                topLeft = Offset(startX, size.height - barHeight),
                size = Size(segmentWidth, barHeight),
            )
            elapsedSoFar += segment.durationSeconds
        }

        drawLine(
            color = markerColor,
            start = Offset(progressX, 0f),
            end = Offset(progressX, size.height),
            strokeWidth = 2.dp.toPx(),
        )
    }
}