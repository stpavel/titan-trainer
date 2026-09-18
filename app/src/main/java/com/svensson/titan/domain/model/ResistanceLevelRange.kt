package com.svensson.titan.domain.model

/**
 * Границы регулировки сопротивления, прочитанные с тренажёра (FTMS 0x2AD6).
 * FALLBACK используется, пока характеристика не прочитана или не поддерживается
 * тренажёром — конкретные цифры (1/16/1) заданы по ТЗ, поправь если знаешь реальные.
 */
data class ResistanceLevelRange(
    val min: Int = DEFAULT_MIN,
    val max: Int = DEFAULT_MAX,
    val step: Int = DEFAULT_STEP,
) {
    companion object {
        const val DEFAULT_MIN = 1
        const val DEFAULT_MAX = 16
        const val DEFAULT_STEP = 1

        val FALLBACK = ResistanceLevelRange()
    }
}