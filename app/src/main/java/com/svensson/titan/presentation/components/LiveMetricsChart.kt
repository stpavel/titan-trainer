package com.svensson.titan.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Простой линейный график последних значений одной метрики (0..maxValue) без внешних
 * чарт-библиотек — используется для живых показателей (пульс, скорость, мощность)
 * на экране активной тренировки.
 */
@Composable
fun LiveMetricsChart(
    values: List<Float>,
    maxValue: Float,
    lineColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        if (values.size < 2 || maxValue <= 0f) return@Canvas

        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value.coerceIn(0f, maxValue) / maxValue) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
    }
}