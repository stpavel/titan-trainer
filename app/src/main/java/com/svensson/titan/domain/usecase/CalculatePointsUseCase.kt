// app/src/main/java/com/svensson/titan/domain/usecase/CalculatePointsUseCase.kt
package com.svensson.titan.domain.usecase

import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Начисление очков за тренировку.
 *
 * Раньше очки считались от мгновенного сопротивления/каденса — это легко
 * накрутить рывком в последний момент, и это дублирует калории, которые уже
 * честно накапливаются по времени (см. ActiveWorkoutViewModel.onLiveData).
 *
 * Новый принцип — как Apple Exercise Minutes / Garmin Intensity Minutes:
 * очки идут за ВРЕМЯ, проведённое с пульсом в целевой зоне (или выше неё),
 * плюс небольшой бонус за пройденную дистанцию.
 */
class CalculatePointsUseCase @Inject constructor() {

    /**
     * Скорость начисления очков (очков в минуту) для текущего пульса.
     * Вызывается на каждом тике телеметрии и умножается на dt в минутах —
     * так очки честно накапливаются по факту времени в зоне.
     *
     * @param bpm текущий пульс (консоль передаёт его через FTMS)
     * @param lowerThreshold нижняя граница целевой зоны (из возраста/настроек)
     */
    fun zoneRate(bpm: Int?, lowerThreshold: Int?): Float = when {
        bpm == null || lowerThreshold == null -> NO_DATA_RATE
        bpm >= lowerThreshold -> IN_ZONE_RATE
        else -> BELOW_ZONE_RATE
    }

    /**
     * Итоговые очки.
     * @param zoneScore сумма zoneRate * dt(мин), накопленная за тренировку
     * @param distanceMeters пройденная дистанция
     */
    operator fun invoke(zoneScore: Float, distanceMeters: Int): Int {
        val distanceBonus = distanceMeters / METERS_PER_DISTANCE_POINT
        return (zoneScore + distanceBonus).roundToInt()
    }

    companion object {
        /** Очков/мин, если пульс в целевой зоне и выше — полноценная нагрузка. */
        const val IN_ZONE_RATE = 1.0f
        /** Очков/мин ниже зоны — разминочный темп, тоже засчитывается, но вполовину. */
        const val BELOW_ZONE_RATE = 0.5f
        /** Нет данных о возрасте/пульсе — нейтральная ставка, не наказываем и не поощряем. */
        const val NO_DATA_RATE = 0.7f
        /** Бонус: 1 очко за каждые 100 метров. */
        const val METERS_PER_DISTANCE_POINT = 100
    }
}