package com.svensson.titan.domain.model

import kotlin.math.roundToInt

/**
 * Сегмент шаблона: доля от общей длительности (0..1) вместо абсолютных секунд —
 * именно это делает шаблон масштабируемым на любую длительность тренировки,
 * сохраняя форму профиля нагрузки неизменной.
 */
data class TemplateSegment(val durationFraction: Float, val targetResistance: Int)

/**
 * Заготовка программы: один и тот же профиль нагрузки растягивается на любую
 * суммарную длительность — instantiate() пропорционально пересчитывает доли в секунды.
 *
 * Множителя сложности здесь больше нет: на шкале из 16 дискретных уровней процентное
 * масштабирование не двигало нижние уровни вообще (2 × 1.1 = 2.2 → те же 2). Нужен
 * другой профиль — копируй шаблон в свою программу и правь сегменты руками.
 */
data class WorkoutProgramTemplate(
    val id: String,
    val name: String,
    val segments: List<TemplateSegment>,
) {
    fun instantiate(totalDurationSeconds: Int): WorkoutProgram {
        require(segments.isNotEmpty()) { "Шаблон должен содержать хотя бы один сегмент" }
        require(totalDurationSeconds > 0) { "Длительность должна быть положительной" }

        val raw = segments.map { (it.durationFraction * totalDurationSeconds).roundToInt().coerceAtLeast(10) }
        val diff = totalDurationSeconds - raw.sum()
        val adjusted = raw.toMutableList()
        adjusted[adjusted.lastIndex] = (adjusted.last() + diff).coerceAtLeast(10)

        val range = ResistanceLevelRange.FALLBACK

        return WorkoutProgram(
            id = "$id-${totalDurationSeconds}s",
            name = "$name (${totalDurationSeconds / 60} мин)",
            segments = segments.mapIndexed { index, seg ->
                ProgramSegment(
                    durationSeconds = adjusted[index],
                    targetResistance = seg.targetResistance.coerceIn(range.min, range.max),
                )
            },
        )
    }
}