// app/src/main/java/com/svensson/titan/domain/usecase/RequestBleControlUseCase.kt
package com.svensson.titan.domain.usecase

import com.svensson.titan.domain.repository.BleRepository
import com.svensson.titan.util.LogBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.abs

/**
 * Захват управления с ПРОВЕРКОЙ ПО ТЕЛЕМЕТРИИ.
 *
 * Консоль TITAN подтверждает Control Point командам SUCCESS даже когда физически их не
 * исполняет (свежепроснувшийся тренажёр, маховик стоит). Поэтому единственный надёжный
 * критерий захвата — фактическая нагрузка в телеметрии сошлась с целевой.
 * Reset (0x01) здесь сознательно НЕ шлём: тренажёр его не поддерживает, а по спеке FTMS
 * он возвращает машину в Idle и снимает только что выданный control permission.
 */
class RequestBleControlUseCase @Inject constructor(
    private val bleRepository: BleRepository,
) {
    /** @param targetLevel уровень, которым проверяем реальность захвата (обычно цель сегмента). */
    suspend operator fun invoke(targetLevel: Int): Boolean {
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS)

            val granted = bleRepository.requestControl()
            delay(COMMAND_GAP_MS)
            val started = bleRepository.startOrResume()
            delay(COMMAND_GAP_MS)
            val accepted = bleRepository.setTargetResistanceLevel(targetLevel)

            if (granted && started && accepted && confirmedByTelemetry(targetLevel)) {
                LogBus.d(TAG, "Захват подтверждён телеметрией с попытки ${attempt + 1}")
                return true
            }
            LogBus.w(
                TAG,
                "Попытка ${attempt + 1}: control=$granted start=$started set=$accepted — " +
                    "тренажёр не вывел нагрузку на $targetLevel",
            )
        }
        return false
    }

    /** replay=1 у liveData отдаёт закешированный кадр из прошлого — его пропускаем через drop(1). */
    private suspend fun confirmedByTelemetry(target: Int): Boolean =
        withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
            bleRepository.liveData
                .drop(1)
                .first { data ->
                    val actual = data.resistanceLevel
                    actual != null && abs(actual - target) <= TOLERANCE
                }
            true
        } ?: false

    companion object {
        private const val TAG = "TitanControl"
        private const val ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 800L
        private const val COMMAND_GAP_MS = 120L
        private const val CONFIRM_TIMEOUT_MS = 6_000L
        private const val TOLERANCE = 1f
    }
}