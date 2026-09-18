package com.svensson.titan.domain.model

/** Три готовые программы из ТЗ. Конкретные цифры (длительность/нагрузка) — условные,
 *  подбери под реальные диапазоны сопротивления TITAN-650. */
object PredefinedPrograms {

    val PYRAMID = WorkoutProgram(
        id = "predefined_pyramid",
        name = "Пирамида",
        segments = listOf(
            ProgramSegment(60, 3), ProgramSegment(60, 5), ProgramSegment(60, 7),
            ProgramSegment(60, 9), ProgramSegment(60, 11),
            ProgramSegment(60, 9), ProgramSegment(60, 7), ProgramSegment(60, 5), ProgramSegment(60, 3),
        ),
    )

    val INTERVALS_1_2_1_2 = WorkoutProgram(
        id = "predefined_intervals_1212",
        name = "Интервалы 1-2-1-2",
        segments = buildList {
            repeat(6) {
                add(ProgramSegment(60, 4))
                add(ProgramSegment(120, 10))
            }
        },
    )

    val HILLS = WorkoutProgram(
        id = "predefined_hills",
        name = "Холмы",
        segments = listOf(
            ProgramSegment(120, 3), ProgramSegment(90, 8), ProgramSegment(60, 12), ProgramSegment(90, 8),
            ProgramSegment(120, 3), ProgramSegment(90, 9), ProgramSegment(60, 13), ProgramSegment(90, 9),
            ProgramSegment(120, 3),
        ),
    )

    val all: List<WorkoutProgram> = listOf(PYRAMID, INTERVALS_1_2_1_2, HILLS)
}