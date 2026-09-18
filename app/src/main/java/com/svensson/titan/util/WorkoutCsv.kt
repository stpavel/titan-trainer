// app/src/main/java/com/svensson/titan/util/WorkoutCsv.kt
package com.svensson.titan.util

import com.svensson.titan.domain.model.Workout
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экспорт/импорт истории тренировок в CSV. Формат простой, без экранирования кавычками —
 * ни одно поле Workout сейчас не содержит запятых или переносов строк. Если появится
 * текстовое поле (например, заметка) — формат нужно будет пересмотреть.
 */
object WorkoutCsv {
	private val FIELD_COMMENTS = listOf(
		"id - внутренний идентификатор тренировки; ",
		"startedAt - дата и время начала тренировки ",
		"programId - идентификатор программы; пусто для свободной тренировки",
		"durationSeconds - длительность тренировки в секундах",
		"distanceMeters - пройденная дистанция в метрах",
		"caloriesKcal - сожжено калорий (ккал)",
		"avgHeartRateBpm - средний пульс за тренировку (уд/мин); пусто если пульс не измерялся",
		"avgPowerWatts - средняя мощность (Вт)",
		"belowZoneSharePercent - доля времени тренировки ниже целевой зоны нагрузки (%); пусто если нет данных",
		"pointsEarned - начислено очков за тренировку",
		"isPenalty - штрафная запись без очков (true/false) ",
	)
    private const val HEADER = "id,startedAt,programId,durationSeconds,distanceMeters,caloriesKcal," +
        "avgHeartRateBpm,avgPowerWatts,belowZoneSharePercent,pointsEarned,isPenalty"
	// Excel понимает "yyyy-MM-dd HH:mm:ss" как дату/время сразу, без буквы T, зоны и миллисекунд.
    // Локальное время, а не UTC — иначе часы разъедутся с тем, что реально было на часах.
    private val CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
	


	fun toCsv(workouts: List<Workout>): String = buildString {
		appendLine("sep=,")
		for (comment in FIELD_COMMENTS) {
			appendLine("# $comment")
		}
		appendLine(HEADER)
		for (w in workouts) {
			appendLine(
				listOf(
					w.id,
					w.startedAt.atZone(ZoneId.systemDefault()).format(CSV_DATE_FORMAT),
					w.programId.orEmpty(),
					w.durationSeconds,
					w.distanceMeters,
					w.caloriesKcal,
					w.avgHeartRateBpm?.toString().orEmpty(),
					w.avgPowerWatts?.toString().orEmpty(),
					w.belowZoneSharePercent?.toString().orEmpty(),
					w.pointsEarned,
					w.isPenalty,
				).joinToString(","),
			)
		}
	}

    data class ParseResult(val workouts: List<Workout>, val skippedLines: Int)

    /** Битые строки пропускаются (их число — в [ParseResult.skippedLines]), а не рушат весь импорт. */
    fun fromCsv(text: String): ParseResult {
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return ParseResult(emptyList(), 0)

        // "sep=," — директива для Excel, добавленная в toCsv(). При импорте не заголовок,
        // просто пропускаем, если она есть (её может не быть в файле старого экспорта).
		
		val withoutSepDirective = if (lines.first().startsWith("sep=")) lines.drop(1) else lines
		if (withoutSepDirective.isEmpty()) return ParseResult(emptyList(), 0)

		val withoutComments = withoutSepDirective.dropWhile { it.startsWith("#") }
		if (withoutComments.isEmpty()) return ParseResult(emptyList(), 0)

		val header = withoutComments.first()
		val delimiter = if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
		val columns = header.split(delimiter).map { it.trim() }

		val workouts = mutableListOf<Workout>()
		var skipped = 0
		for (line in withoutComments.drop(1)) {
				

            val cells = line.split(delimiter).map { it.trim() }
            if (cells.size != columns.size) {
                skipped++
                continue
            }
            val row = columns.zip(cells).toMap()
            val workout = runCatching {
                Workout(
                    // id = 0: импортированные записи всегда добавляются как новые — иначе
                    // повторный импорт того же файла переписал бы существующие по id.
                    id = 0,
                    programId = row["programId"]?.takeIf { it.isNotEmpty() },
                    startedAt = LocalDateTime.parse(row.getValue("startedAt"), CSV_DATE_FORMAT)
                        .atZone(ZoneId.systemDefault())
                        .toInstant(),
                    durationSeconds = row.getValue("durationSeconds").toInt(),
                    distanceMeters = row.getValue("distanceMeters").toInt(),
                    caloriesKcal = row.getValue("caloriesKcal").toInt(),
                    avgHeartRateBpm = row["avgHeartRateBpm"]?.takeIf { it.isNotEmpty() }?.toInt(),
                    avgPowerWatts = row["avgPowerWatts"]?.takeIf { it.isNotEmpty() }?.toInt(),
                    belowZoneSharePercent = row["belowZoneSharePercent"]?.takeIf { it.isNotEmpty() }?.toInt(),
                    pointsEarned = row.getValue("pointsEarned").toInt(),
                    isPenalty = row.getValue("isPenalty").toBooleanStrictOrNull() ?: false,
                )
            }.getOrNull()
            if (workout != null) workouts.add(workout) else skipped++
        }
        return ParseResult(workouts, skipped)
    }
}