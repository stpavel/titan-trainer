package com.svensson.titan.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Моноширинный HUD-шрифт (встроенный FontFamily.Monospace, без доп. файлов шрифтов)
// для заголовков/цифр/лейблов — "приборный" вид. Обычный текст остаётся читаемым
// на стандартном шрифте.
private val HudFont = FontFamily.Monospace

val TitanTypography = Typography(
    headlineSmall = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.4.sp),
    titleSmall = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)