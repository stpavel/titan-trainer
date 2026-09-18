// app/src/main/java/com/svensson/titan/domain/model/WorkoutRating.kt
package com.svensson.titan.domain.model

/**
 * Оценка одной тренировки по доле времени ниже целевой пульсовой зоны.
 * Без надёжных данных о пульсе (belowZoneSharePercent == null) — нейтральная
 * оценка GOOD: не хвалим и не ругаем за то, чего не можем измерить, и не
 * дёргаем пользователя вопросом, если пульсометра просто нет.
 */
enum class WorkoutRating(val label: String) {
    EXCELLENT("Отлично"),
    GOOD("Хорошо"),
    FAIR("Удовлетворительно"),
    ;

    companion object {
        private const val EXCELLENT_MAX_BELOW_ZONE = 25
        private const val GOOD_MAX_BELOW_ZONE = 55

        fun forSession(belowZoneSharePercent: Int?): WorkoutRating = when {
            belowZoneSharePercent == null -> GOOD
            belowZoneSharePercent < EXCELLENT_MAX_BELOW_ZONE -> EXCELLENT
            belowZoneSharePercent < GOOD_MAX_BELOW_ZONE -> GOOD
            else -> FAIR
        }
    }
}