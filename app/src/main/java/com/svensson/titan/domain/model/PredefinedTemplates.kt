package com.svensson.titan.domain.model

/**
 * Библиотека шаблонов программ. Формы профилей — по стандартным встроенным
 * программам коммерческих кардиотренажёров (Sole E25/E35 — Hill, Fat Burn,
 * Cardio, Strength, Interval) плюс два общепринятых формата (HIIT, фартлек).
 * Три программы из MVP (Пирамида/Интервалы/Холмы) переведены в шаблонную
 * форму без изменения формы графика — теперь их можно масштабировать.
 * Резистанс — условные цифры, подбери под реальный диапазон TITAN-650.
 */
object PredefinedTemplates {

    val PYRAMID = WorkoutProgramTemplate(
        id = "tmpl_pyramid",
        name = "Пирамида",
        segments = listOf(3, 5, 7, 9, 11, 9, 7, 5, 3).map { r -> TemplateSegment(1f / 9f, r) },
    )

    val INTERVALS_1_2_1_2 = WorkoutProgramTemplate(
        id = "tmpl_intervals_1212",
        name = "Интервалы 1-2-1-2",
        segments = buildList {
            repeat(6) {
                add(TemplateSegment(1f / 18f, 4))
                add(TemplateSegment(1f / 9f, 10))
            }
        },
    )

    val HILLS = WorkoutProgramTemplate(
        id = "tmpl_hills",
        name = "Холмы",
        segments = listOf(
            TemplateSegment(1f / 7f, 3), TemplateSegment(0.107143f, 8), TemplateSegment(0.071429f, 12),
            TemplateSegment(0.107143f, 8), TemplateSegment(1f / 7f, 3), TemplateSegment(0.107143f, 9),
            TemplateSegment(0.071429f, 13), TemplateSegment(0.107143f, 9), TemplateSegment(1f / 7f, 3),
        ),
    )

    /** Ровный, низкий-умеренный — классический "жиросжигающий" профиль. */
    val FAT_BURN = WorkoutProgramTemplate(
        id = "tmpl_fat_burn",
        name = "Жиросжигание",
        segments = listOf(
            TemplateSegment(0.10f, 4), TemplateSegment(0.20f, 6), TemplateSegment(0.40f, 7),
            TemplateSegment(0.20f, 6), TemplateSegment(0.10f, 4),
        ),
    )

    /** Умеренная устойчивая нагрузка выше, чем Fat Burn — на выносливость. */
    val CARDIO = WorkoutProgramTemplate(
        id = "tmpl_cardio",
        name = "Кардио",
        segments = listOf(TemplateSegment(0.08f, 5), TemplateSegment(0.84f, 8), TemplateSegment(0.08f, 5)),
    )

    /** Высокая нагрузка блоками с короткими паузами — на силу. */
    val STRENGTH = WorkoutProgramTemplate(
        id = "tmpl_strength",
        name = "Сила",
        segments = buildList {
            repeat(5) {
                add(TemplateSegment(0.14f, 11))
                add(TemplateSegment(0.06f, 4))
            }
        },
    )

    /** Классический HIIT: короткий пик, длинное восстановление (~1:3). */
    val HIIT = WorkoutProgramTemplate(
        id = "tmpl_hiit",
        name = "HIIT",
        segments = buildList {
            repeat(5) {
                add(TemplateSegment(0.05f, 14))
                add(TemplateSegment(0.15f, 3))
            }
        },
    )

    /** Фартлек — "свободная игра скоростей": неструктурированные всплески. */
    val FARTLEK = WorkoutProgramTemplate(
        id = "tmpl_fartlek",
        name = "Фартлек",
        segments = listOf(
            TemplateSegment(0.10f, 5), TemplateSegment(0.05f, 10), TemplateSegment(0.15f, 4),
            TemplateSegment(0.05f, 12), TemplateSegment(0.20f, 6), TemplateSegment(0.10f, 9),
            TemplateSegment(0.15f, 5), TemplateSegment(0.20f, 8),
        ),
    )

    /** Один длинный подъём и спуск — в отличие от "Холмов" с повторяющимися волнами. */
    val LONG_CLIMB = WorkoutProgramTemplate(
        id = "tmpl_long_climb",
        name = "Затяжной подъём",
        segments = listOf(4, 6, 8, 10, 10, 8, 6, 4).map { r -> TemplateSegment(0.125f, r) },
    )

    val all: List<WorkoutProgramTemplate> = listOf(
        PYRAMID, INTERVALS_1_2_1_2, HILLS, FAT_BURN, CARDIO, STRENGTH, HIIT, FARTLEK, LONG_CLIMB,
    )
}