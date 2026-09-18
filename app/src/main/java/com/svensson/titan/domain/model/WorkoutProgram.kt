// app/src/main/java/com/svensson/titan/domain/model/WorkoutProgram.kt
package com.svensson.titan.domain.model

data class ProgramSegment(
    val durationSeconds: Int,
    val targetResistance: Int,
)

data class WorkoutProgram(
    val id: String,
    val name: String,
    val segments: List<ProgramSegment>,
    val isCustom: Boolean = false,
) {
    val totalDurationSeconds: Int get() = segments.sumOf { it.durationSeconds }

    /**
     * Копия программы со сдвигом всей нагрузки на offsetLevels уровней. Форма профиля
     * сохраняется, двигается общий уровень. В базу не пишется — это вид на исходную
     * программу, им же рисуем график и шлём команды.
     *
     * Раньше здесь был процентный множитель, но на шкале из 16 дискретных уровней он
     * не работал внизу диапазона: 2 × 1.1 = 2.2 → те же 2. Сдвиг в уровнях двигает
     * нагрузку одинаково предсказуемо на всей шкале.
     */
    fun shiftedBy(offsetLevels: Int, range: ResistanceLevelRange): WorkoutProgram =
        if (offsetLevels == 0) {
            this
        } else {
            copy(
                segments = segments.map {
                    it.copy(targetResistance = shiftResistance(it.targetResistance, offsetLevels, range))
                },
            )
        }
}

/** Границы ручки "вся тренировка легче/тяжелее", в уровнях сопротивления. */
object ResistanceOffset {
    const val MIN = -5
    const val MAX = 5
    const val STEP = 1
    const val DEFAULT = 0
}

fun shiftResistance(baseResistance: Int, offsetLevels: Int, range: ResistanceLevelRange): Int =
    (baseResistance + offsetLevels).coerceIn(range.min, range.max)