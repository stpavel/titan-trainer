package com.svensson.titan.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Семисегментный индикатор для панели счётчиков — как на приборке тренажёра.
 * Рисуется сам, а не шрифтом: у погашенного разряда виден слабый контур
 * ("призрак" полной восьмёрки) — это и продаёт эффект LED-табло.
 *
 * Понимает цифры, ':' и ',' (десятичный разделитель), остальные символы
 * пропускает. lit=false гасит все разряды независимо от текста — состояние
 * "нет данных" (виден только контур, ничего не горит).
 *
 * Размер разряда держим одним и тем же на всех карточках экрана — этим
 * достигается визуальный баланс панели, а не подгонкой под ширину карточки.
 */
@Composable
fun SevenSegmentDisplay(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    lit: Boolean = true,
    digitWidth: Dp = 13.dp,
    digitHeight: Dp = 22.dp,
    gap: Dp = 3.dp,
    segmentThickness: Float = 0.18f,
    ghostAlpha: Float = 0.16f,
) {
    val colonWidth = digitWidth * 0.4f
    val widthDp = remember(text, digitWidth, colonWidth, gap) {
        var total = 0f
        text.forEachIndexed { i, ch ->
            total += (if (ch == ':' || ch == ',') colonWidth else digitWidth).value
            if (i != text.lastIndex) total += gap.value
        }
        total.dp
    }

    Canvas(modifier.size(width = widthDp, height = digitHeight)) {
        var x = 0f
        val digitW = digitWidth.toPx()
        val colonW = colonWidth.toPx()
        val h = digitHeight.toPx()
        val g = gap.toPx()
        for (ch in text) {
            when (ch) {
                ':' -> { drawColon(x, x + colonW, h, color); x += colonW + g }
                ',' -> { drawComma(x, x + colonW, h, color); x += colonW + g }
                else -> {
                    val digit = ch - '0'
                    if (digit in 0..9) {
                        drawSevenSegmentDigit(x, 0f, digitW, h, digit, color, segmentThickness, ghostAlpha, lit)
                    }
                    x += digitW + g
                }
            }
        }
    }
}

/** a..g — стандартные обозначения сегментов: a-верх, g-середина, d-низ, b/c справа, f/e слева. */
private val DIGIT_SEGMENTS: Map<Int, Set<Char>> = mapOf(
    0 to setOf('a', 'b', 'c', 'd', 'e', 'f'),
    1 to setOf('b', 'c'),
    2 to setOf('a', 'b', 'g', 'e', 'd'),
    3 to setOf('a', 'b', 'g', 'c', 'd'),
    4 to setOf('f', 'g', 'b', 'c'),
    5 to setOf('a', 'f', 'g', 'c', 'd'),
    6 to setOf('a', 'f', 'g', 'e', 'c', 'd'),
    7 to setOf('a', 'b', 'c'),
    8 to setOf('a', 'b', 'c', 'd', 'e', 'f', 'g'),
    9 to setOf('a', 'b', 'c', 'd', 'f', 'g'),
)

private fun DrawScope.drawSevenSegmentDigit(
    x: Float, y: Float, w: Float, h: Float, digit: Int, color: Color,
    thicknessRatio: Float, ghostAlpha: Float, lit: Boolean,
) {
    val on = if (lit) DIGIT_SEGMENTS[digit].orEmpty() else emptySet()
    val t = w * thicknessRatio
    val midX = x + w / 2f
    val leftX = x + t * 0.55f
    val rightX = x + w - t * 0.55f
    val topY = y + t * 0.55f
    val midY = y + h / 2f
    val botY = y + h - t * 0.55f
    val upperY = y + h * 0.27f
    val lowerY = y + h * 0.73f
    val horizLen = w * 0.62f
    val vertLen = h * 0.42f

    fun seg(letter: Char, path: Path) {
        val isLit = letter in on
        if (isLit) drawPath(path, color = color.copy(alpha = 0.20f))
        drawPath(path, color = if (isLit) color else color.copy(alpha = ghostAlpha))
    }

    seg('a', hSegment(midX, topY, horizLen, t))
    seg('g', hSegment(midX, midY, horizLen, t))
    seg('d', hSegment(midX, botY, horizLen, t))
    seg('f', vSegment(leftX, upperY, vertLen, t))
    seg('b', vSegment(rightX, upperY, vertLen, t))
    seg('e', vSegment(leftX, lowerY, vertLen, t))
    seg('c', vSegment(rightX, lowerY, vertLen, t))
}

/** Вытянутый шестиугольник — классическая форма сегмента LED-индикатора. */
private fun hSegment(cx: Float, cy: Float, length: Float, thickness: Float): Path = Path().apply {
    val half = length / 2f
    val ht = thickness / 2f
    moveTo(cx - half, cy)
    lineTo(cx - half + ht, cy - ht)
    lineTo(cx + half - ht, cy - ht)
    lineTo(cx + half, cy)
    lineTo(cx + half - ht, cy + ht)
    lineTo(cx - half + ht, cy + ht)
    close()
}

private fun vSegment(cx: Float, cy: Float, length: Float, thickness: Float): Path = Path().apply {
    val half = length / 2f
    val ht = thickness / 2f
    moveTo(cx, cy - half)
    lineTo(cx + ht, cy - half + ht)
    lineTo(cx + ht, cy + half - ht)
    lineTo(cx, cy + half)
    lineTo(cx - ht, cy + half - ht)
    lineTo(cx - ht, cy - half + ht)
    close()
}

private fun DrawScope.drawColon(xStart: Float, xEnd: Float, h: Float, color: Color) {
    val cx = (xStart + xEnd) / 2f
    val r = (xEnd - xStart) * 0.28f
    listOf(h * 0.34f, h * 0.66f).forEach { cy ->
        drawCircle(color.copy(alpha = 0.25f), radius = r * 1.8f, center = Offset(cx, cy))
        drawCircle(color, radius = r, center = Offset(cx, cy))
    }
}

/** Десятичный разделитель — одна точка у нижней линии разрядов (как запятая на LED-табло). */
private fun DrawScope.drawComma(xStart: Float, xEnd: Float, h: Float, color: Color) {
    val cx = (xStart + xEnd) / 2f
    val r = (xEnd - xStart) * 0.26f
    val cy = h * 0.86f
    drawCircle(color.copy(alpha = 0.25f), radius = r * 1.8f, center = Offset(cx, cy))
    drawCircle(color, radius = r, center = Offset(cx, cy))
}