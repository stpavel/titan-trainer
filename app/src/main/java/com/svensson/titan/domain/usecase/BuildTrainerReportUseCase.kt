package com.svensson.titan.domain.usecase

import com.svensson.titan.domain.model.TrainerReport
import com.svensson.titan.domain.model.Workout
import com.svensson.titan.domain.repository.TrainerApiRepository
import com.svensson.titan.domain.repository.TrainerApiResult
import com.svensson.titan.domain.repository.TrainerReportRepository
import com.svensson.titan.domain.repository.TrainerSettingsRepository
import com.svensson.titan.domain.repository.UserSettingsRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class BuildReportResult {
    data class Success(val report: TrainerReport, val fromCache: Boolean = false) : BuildReportResult()
    data class Error(val message: String) : BuildReportResult()
    data object NoWorkouts : BuildReportResult()
}

private const val PERIOD_DAYS = 30L

private const val SYSTEM_PROMPT = "Ты — персональный тренер по кардио-тренировкам. " +
    "Пользователь тренируется дома на эллиптическом тренажёре, подключённом по Bluetooth, " +
    "приложение фиксирует телеметрию каждой тренировки."

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/**
 * Собирает тренировки за последний месяц + профиль пользователя в промпт для LLM
 * (см. документ ai-trainer-feature-spec в проекте) и кэширует результат как последний отчёт.
 * Период на MVP фиксирован — 30 дней, без выбора в UI.
 *
 * Если промпт (данные + модель) не изменился с прошлого раза — к LLM не идём вообще,
 * отдаём отчёт из кэша (см. promptHash в TrainerReport).
 */
class BuildTrainerReportUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val trainerSettingsRepository: TrainerSettingsRepository,
    private val trainerApiRepository: TrainerApiRepository,
    private val trainerReportRepository: TrainerReportRepository,
) {
    private val zone = ZoneId.systemDefault()

	suspend operator fun invoke(): BuildReportResult {
		val zone = ZoneId.systemDefault()
		val allWorkouts = workoutRepository.observeAllWorkouts().first()
		
		// Берем тренировки за 30 дней от ПОСЛЕДНЕЙ тренировки (или текущего дня, если база пуста)
		val latestWorkoutDate = allWorkouts.maxOfOrNull { it.startedAt }?.atZone(zone)?.toLocalDate() 
			?: LocalDate.now(zone)
		val periodEnd = latestWorkoutDate
		val periodStart = periodEnd.minusDays(PERIOD_DAYS)
		val periodStartInstant = periodStart.atStartOfDay(zone).toInstant()

		val workouts = allWorkouts
			.filter { !it.startedAt.isBefore(periodStartInstant) }
			.sortedBy { it.startedAt }

		if (workouts.isEmpty()) return BuildReportResult.NoWorkouts

		val ageYears = userSettingsRepository.ageYears.first()
		val weightKg = userSettingsRepository.weightKg.first()
		val lowerOverride = userSettingsRepository.heartRateLowerOverride.first()
		val upperOverride = userSettingsRepository.heartRateUpperOverride.first()
		val model = trainerSettingsRepository.model.first()

		// Хэш зависит ТОЛЬКО от реальных данных, а не от даты вызова
		val workoutsSignature = workouts.joinToString(";") { 
			"${it.id}_${it.startedAt.toEpochMilli()}_${it.pointsEarned}" 
		}
		val dataSignature = "$model|$ageYears|$weightKg|$lowerOverride|$upperOverride|$workoutsSignature"
		val promptHash = sha256(dataSignature)

		// Проверяем кэш напрямую
		val cached = trainerReportRepository.getLatestReport()
		if (cached != null && cached.promptHash == promptHash) {
			return BuildReportResult.Success(cached, fromCache = true)
		}

		val userPrompt = buildUserPrompt(
			ageYears = ageYears,
			weightKg = weightKg,
			lowerOverride = lowerOverride,
			upperOverride = upperOverride,
			periodStart = periodStart,
			periodEnd = periodEnd,
			workouts = workouts,
		)

		val result = trainerApiRepository.chatCompletion(
			baseUrl = trainerSettingsRepository.baseUrl.first(),
			apiKey = trainerSettingsRepository.apiKey.first(),
			model = model,
			systemPrompt = SYSTEM_PROMPT,
			userPrompt = userPrompt,
		)

		return when (result) {
			is TrainerApiResult.Error -> BuildReportResult.Error(result.message)
			is TrainerApiResult.Success -> {
				val report = TrainerReport(
					generatedAt = Instant.now(),
					periodStart = periodStart,
					periodEnd = periodEnd,
					reportText = result.text,
					promptHash = promptHash,
				)
				trainerReportRepository.saveReport(report)
				BuildReportResult.Success(report, fromCache = false)
			}
		}
	}

    private fun buildUserPrompt(
        ageYears: Int?,
        weightKg: Int,
        lowerOverride: Int?,
        upperOverride: Int?,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        workouts: List<Workout>,
    ): String = buildString {
        appendLine("Данные о пользователе:")
        appendLine("- Возраст: ${ageYears?.let { "$it лет" } ?: "не указан"}")
        appendLine("- Вес: $weightKg кг")
        val effectiveLower = UserSettingsRepository.effectiveLowerThreshold(ageYears, lowerOverride)
        val effectiveUpper = UserSettingsRepository.effectiveUpperThreshold(ageYears, upperOverride)
        val autoLower = ageYears?.let { UserSettingsRepository.moderateLowerBound(it) }
        val autoUpper = ageYears?.let { UserSettingsRepository.vigorousUpperBound(it) }
        val isManualZone = lowerOverride != null || upperOverride != null
        if (effectiveLower != null && effectiveUpper != null) {
            appendLine("- Рабочая пульсовая зона: $effectiveLower–$effectiveUpper уд/мин")
            if (isManualZone) {
                if (autoLower != null && autoUpper != null) {
                    appendLine("- Общий ориентир по возрасту (AHA, 50–85% от максимума): $autoLower–$autoUpper уд/мин")
                }
                appendLine(
                    "  Рабочую зону пользователь сузил сам, сознательно, и менять её пока не намерен. " +
                        "Держись как уверенный тренер, который знает нормативы, а не как соглашатель. " +
                        "Что это значит: если по общему ориентиру интенсивность выглядит невысокой — скажи это " +
                        "прямо и один раз, спокойно, без давления и без повторов в каждом разделе. " +
                        "Дальше работай внутри выбранных границ: как выжать максимум пользы именно в этом диапазоне. " +
                        "Не превращай сужение зоны в повод объявить, что всё идеально и корректировать нечего — " +
                        "оценивай реальную динамику (регулярность, длительность, темп, восстановление) честно, " +
                        "она от границ зоны не зависит. Конкретные рекомендации давай только в пределах рабочей зоны.",
                )
            } else {
                appendLine("  Рассчитано автоматически по возрасту (формула AHA: 50–85% от максимума).")
            }
        } else {
            appendLine("- Целевая пульсовая зона: не задана — нет ни ручных порогов, ни возраста для расчёта")
        }
        appendLine()
        appendLine("Формат данных по каждой тренировке:")
        appendLine("- startedAt — дата и время начала")
        appendLine("- durationSeconds — длительность в секундах")
        appendLine("- distanceMeters — пройденная дистанция, метры")
        appendLine("- caloriesKcal — сожжено калорий")
        appendLine("- avgHeartRateBpm — средний пульс за тренировку (нет данных — датчик пульса не был подключён)")
        appendLine("- avgPowerWatts — средняя мощность нагрузки, ватты (нет данных — не фиксировалось)")
        appendLine("- belowZoneSharePercent — доля времени тренировки с пульсом НИЖЕ целевой зоны, в процентах")
        appendLine("- pointsEarned — очки, начисленные приложением (время в целевой зоне + бонус за дистанцию)")
        appendLine("- isPenalty — true, если это штрафная запись за пропущенный день (без реальной тренировки)")
        appendLine()
        appendLine("Данные за период ${periodStart.format(DATE_FORMAT)} – ${periodEnd.format(DATE_FORMAT)}:")
        appendLine()
        workouts.forEach { w ->
            appendLine(
                "id=${w.id}, ${DATE_TIME_FORMAT.format(w.startedAt.atZone(zone))}, " +
                    "duration=${w.durationSeconds}с, distance=${w.distanceMeters}м, calories=${w.caloriesKcal}, " +
                    "avgHR=${w.avgHeartRateBpm ?: "нет данных"}, avgPower=${w.avgPowerWatts ?: "нет данных"}, " +
                    "belowZone=${w.belowZoneSharePercent?.let { "$it%" } ?: "нет данных"}, " +
                    "points=${w.pointsEarned}, penalty=${w.isPenalty}",
            )
        }
        appendLine()
        appendLine("Задача:")
        appendLine("1. С учётом возраста и веса оцени, адекватна ли интенсивность тренировок для кардио-эффекта.")
        appendLine("2. Дай краткую сводку прогресса за период — по делу, без общих фраз.")
        appendLine("3. Отметь, что идёт хорошо и что стоит скорректировать.")
        appendLine(
            "4. Если в данных есть записи, которые выглядят нестыковкой (нереалистичное соотношение " +
                "дистанции/калорий/пульса, отсутствующий пульс и т.п.) — укажи это отдельно и не делай на их основе выводов о прогрессе.",
        )
        appendLine("5. Дай 2-3 конкретные рекомендации на следующую неделю, привязанные к этим цифрам.")
        appendLine("6. Не давай медицинских советов и диагнозов, ты не врач.")
        appendLine()
        append(
            "Ответ дай на русском, структурированно, без воды. Пиши ЧИСТЫМ текстом, без markdown-разметки: " +
                "не используй **, ##, `, таблицы с | — экран приложения не умеет их рисовать, только обычный текст. " +
                "Заголовки разделов — отдельной строкой с двоеточием, списки — через дефис, таблицы замени обычными строками " +
                "вида «12.09: 1338 м, 180 ккал, пульс 121, 43 очка».",
        )
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}