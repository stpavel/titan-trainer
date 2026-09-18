package com.svensson.titan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Плоская панель счётчика: закруглённый прямоугольник на surfaceVariant с
 * тонкой акцентной полосой сверху — вместо прежних неоновых уголков-скобок.
 * Та же цветовая маркировка по смыслу (accentColor), но без перегруза.
 * Используется вместо обычных Card на ключевых экранах.
 */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: Dp = 16.dp,
    cornerLength: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerLength)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), shape),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(accentColor),
        )
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}