// app/src/main/java/com/svensson/titan/presentation/active_workout/ActiveWorkoutViewModel.kt
package com.svensson.titan.presentation.active_workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svensson.titan.domain.model.BleConnectionState
import com.svensson.titan.domain.model.LiveWorkoutData
import com.svensson.titan.domain.model.ProgramSegment
import com.svensson.titan.domain.model.ResistanceLevelRange
import com.svensson.titan.domain.model.ResistanceOffset
import com.svensson.titan.domain.model.WorkoutProgram
import com.svensson.titan.domain.model.shiftResistance
import com.svensson.titan.domain.repository.BleRepository
import com.svensson.titan.domain.repository.UserSettingsRepository
import com.svensson.titan.domain.repository.WorkoutProgramRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import com.svensson.titan.domain.usecase.CalculatePointsUseCase
import com.svensson.titan.domain.usecase.RequestBleControlUseCase
import com.svensson.titan.domain.usecase.SaveWorkoutUseCase
import com.svensson.titan.domain.usecase.SetResistanceLevelUseCase
import com.svensson.titan.presentation.navigation.Screen
import com.svensson.titan.service.WorkoutForegroundService
import com.svensson.titan.util.LogBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import android.os.SystemClock
import com.svensson.titan.domain.model.WorkoutRating
import com.svensson.titan.domain.usecase.AnalyzeProgramTrendUseCase
import com.svensson.titan.domain.model.ProgramTrend
import android.media.MediaPlayer
import com.svensson.titan.R
import com.svensson.titan.data.backup.WorkoutBackupWriter

private const val METRIC_HISTORY_SIZE = 180
private const val TAG_WORKOUT = "TitanWorkout"
private const val MANUAL_RESISTANCE_DEBOUNCE_MS = 250L
private const val CONTROL_POLL_MS = 1000L
/** Заезды с меньшим числом очков не пишем в историю — почти наверняка случайный/тестовый старт. */
private const val MIN_POINTS_TO_SAVE = 3
// FTMS отзывает control permission после 30с без единого Op Code от клиента —
// в свободном заезде без смены нагрузки управление молча терялось через полминуты.
private const val CONTROL_KEEPALIVE_MS = 15_000L
private const val RESISTANCE_TOLERANCE = 1f

// Работающую консоль перезахватываем быстро, дежурную дёргаем реже — но дёргаем.
private const val DESYNC_TICKS_AWAKE = 4
private const val DESYNC_TICKS_IDLE = 10
private const val IDLE_WATCHDOG_MS = 8_000L
private const val RECONNECT_GAP_MS = 1_500L
private const val MAX_IDLE_RECONNECTS = 2
private const val LOOP_LOG_INTERVAL_MS = 30_000L


	
/** Метров на один оборот педалей: два хода × 500 мм по паспорту TITAN. */
private const val METERS_PER_REV = 1.0f

/** Сколько "метров" консоль накручивает за один оборот. Замерено: 30 оборотов → 171 м. */
private const val CONSOLE_METERS_PER_REV = 5.7f

// Уравнение ACSM для велоэргометрии: VO2 мл/кг/мин = 10.8 × Вт / масса + 7.
// Множитель 10.8 = 1.8 мл на кг·м/мин × 6.12 кг·м/мин в одном ватте.
// Слагаемое 7 = 3.5 покоя + 3.5 холостого хода, поэтому результат ВАЛОВЫЙ.
// Уравнение валидировано примерно на 50–200 Вт; ниже — экстраполяция.
private const val ACSM_VO2_PER_WATT = 10.8f
private const val ACSM_VO2_BASE = 7.0f

/** Килокалорий на литр потреблённого кислорода. */
private const val KCAL_PER_LITER_O2 = 5.0f

/**
 * Поправка на тип тренажёра. Уравнение выше — велосипедное; эллипс несёт вес тела
 * и подключает руки, поэтому при тех же ваттах кислорода уходит больше, и наша оценка
 * заведомо нижняя. Валидированного уравнения для эллипса не существует, поэтому
 * множитель честно оставлен единицей — уточняется замером с нагрудным ремнём.
 */
private const val ELLIPTICAL_CORRECTION = 1.0f

/** Валовая стоимость ходьбы по ровному ~5 км/ч, выведена из уравнения ходьбы ACSM. */
private const val WALK_KCAL_PER_KG_PER_KM = 0.70f

/** Разовый сигнал при выходе за порог, затем не чаще этого интервала, пока пульс всё ещё вне зоны. */
private const val HR_ALERT_COOLDOWN_MS = 30_000L
/** Пульс разгоняется первые минуты, пока не "вошёл в режим" — алерты до этого момента не дёргаем. */
private const val HR_ALERT_GRACE_SECONDS = 180
private const val HR_ALERT_TONE_DURATION_MS = 200
/** Сколько баннер-предупреждение висит на экране, прежде чем сам скрыться. */
private const val HR_ALERT_BANNER_VISIBLE_MS = 4_000L
/** Ниже этой доли времени с валидным пульсом от времени движения — считаем, что
 *  пульсометра по факту не было (разовые касания рукояток консоли не в счёт). */
private const val MIN_HR_COVERAGE_PERCENT = 20

data class ActiveWorkoutUiState(
    /** Сумма очков по всей истории — курсов больше нет, копим общий баланс. */
    val totalPoints: Int = 0,
    val connectionState: BleConnectionState = BleConnectionState.Disconnected,
    val liveData: LiveWorkoutData? = null,
    val isTracking: Boolean = false,
    val elapsedSeconds: Int = 0,
    val currentPoints: Int = 0,
    // Программа и авто-режим
    val program: WorkoutProgram? = null,
    val currentSegmentIndex: Int = 0,
    val segmentRemainingSeconds: Int = 0,
    val resistanceRange: ResistanceLevelRange = ResistanceLevelRange.FALLBACK,
    val programElapsedSeconds: Int = 0,
    val isAutoModeActive: Boolean = false,
    /** Ручка "вся тренировка легче/тяжелее", в уровнях сопротивления. */
    val resistanceOffset: Int = ResistanceOffset.DEFAULT,
    val targetResistance: Int = ResistanceLevelRange.DEFAULT_MIN,
    val distanceOffsetMeters: Int = 0,
    val weightKg: Int = UserSettingsRepository.DEFAULT_WEIGHT_KG,
    /** Накопленная механическая работа за тренировку, кДж. */
    val energyKj: Float = 0f,
    /** Потраченные калории (валовые), накоплены по уравнению ACSM. */
    val kcalBurned: Int = 0,
    val controlAcquired: Boolean = false,
    // Буферы для живых графиков
    val heartRateHistory: List<Float> = emptyList(),
    val speedHistory: List<Float> = emptyList(),
    val powerHistory: List<Float> = emptyList(),
    // Зоны пульса (пульс берём из FTMS-телеметрии консоли — liveData.heartRateBpm)
    val hrAlertsEnabled: Boolean = false,
    val hrLowerThreshold: Int? = null,
    val hrUpperThreshold: Int? = null,
	val hrAlertMessage: String? = null,
    /** Разовое уведомление "тренировка не сохранена" — короткая/слабая сессия не прошла порог очков. */
    val discardNoticeMessage: String? = null,

    /** Оценка только что завершённой тренировки — не null, пока не закрыта карточка итогов. */
    val lastSessionRating: WorkoutRating? = null,
    val lastSessionSummary: WorkoutSummary? = null,
    val trendMessage: String? = null,	

) {
    /** Программа с учётом сдвига нагрузки — её и рисуем, и исполняем. */
    val effectiveProgram: WorkoutProgram? get() = program?.shiftedBy(resistanceOffset, resistanceRange)

    /**
     * Обороты педалей с начала тренировки. Источник — счётчик дистанции консоли:
     * он растёт от реальных оборотов сразу, тогда как Step Per Minute у этой консоли
     * оживает только через 5–8 секунд после начала движения.
     */
    val revolutionsSinceStart: Float
        get() = ((liveData?.totalDistanceMeters ?: 0) - distanceOffsetMeters)
            .coerceAtLeast(0) / CONSOLE_METERS_PER_REV

    /** Шаги: один оборот педалей = два движения ногами. */
    val stepsSinceStart: Int
        get() = (revolutionsSinceStart * 2f).roundToInt()

    /** Честная дистанция: ход педали 500 мм, два хода за оборот. */
    val realDistanceMeters: Int
        get() = (revolutionsSinceStart * METERS_PER_REV).roundToInt()

    /** Сколько бы прошёл пешком, потратив столько же энергии. */
    val walkEquivalentMeters: Int
        get() = if (weightKg <= 0) 0
        else (kcalBurned / (weightKg * WALK_KCAL_PER_KG_PER_KM) * 1000f).roundToInt()

    /** Средняя механическая мощность за тренировку, Вт. */
    val averagePowerWatts: Int
        get() = if (elapsedSeconds <= 0) 0 else (energyKj * 1000f / elapsedSeconds).roundToInt()

    /**
     * Консоль в дежурке: сопротивление ниже объявленного минимума — своей сессии нет,
     * счётчики стоят, любые команды подтверждаются вхолостую.
     */
    val consoleIdle: Boolean
        get() = (liveData?.resistanceLevel ?: 0f) < resistanceRange.min

    val currentSegment: ProgramSegment? get() = effectiveProgram?.segments?.getOrNull(currentSegmentIndex)

    val programProgressFraction: Float
        get() {
            val total = program?.totalDurationSeconds ?: 0
            return if (total <= 0) 0f else (programElapsedSeconds.toFloat() / total).coerceIn(0f, 1f)
        }
}

/** Сводка для карточки итогов — то, что реально хочется увидеть после тренировки. */
data class WorkoutSummary(
    val durationSeconds: Int,
    val distanceMeters: Int,
    val caloriesKcal: Int,
    val avgHeartRateBpm: Int?,
    val pointsEarned: Int,
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutProgramRepository: WorkoutProgramRepository,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val calculatePoints: CalculatePointsUseCase,
    private val requestBleControl: RequestBleControlUseCase,
    private val setResistanceLevel: SetResistanceLevelUseCase,
    private val userSettingsRepository: UserSettingsRepository,
    private val analyzeProgramTrend: AnalyzeProgramTrendUseCase,
    private val workoutBackupWriter: WorkoutBackupWriter,	
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private var workoutStartedAt: Instant? = null
    private var freeRideTimerJob: Job? = null
    private var manualResistanceJob: Job? = null
    private var lastSentResistance: Int? = null
    private var controlLoopJob: Job? = null
    private var idleWatchdogJob: Job? = null
    private var idleReconnects = 0
    private var desyncTicks = 0
    private var lastLoopLogMs = 0L
    private var lastLoopSignature = ""
    private var lastCommandAtMs = 0L

    private var energyKjAccum = 0.0
    private var kcalAccum = 0.0
    private var zoneScoreAccum = 0.0
    private var lastEnergySampleMs = 0L	
	
    // Для оценки тренировки и тренда: честное среднее по пульсу и разбивка
    // времени на "в зоне"/"ниже зоны", а не только смешанная ставка очков.
    private var hrSumAccum = 0.0
    private var hrValidSecondsAccum = 0.0
    private var belowZoneSecondsAccum = 0.0
    private var inZoneSecondsAccum = 0.0
    private var movingSecondsAccum = 0.0

    private var toneGenerator: ToneGenerator? = null
    private var lastHighHrAlertAtMs = 0L
    private var lastLowHrAlertAtMs = 0L
    private var hrAlertClearJob: Job? = null
    private var discardNoticeClearJob: Job? = null
	
    private val programRunner = WorkoutProgramRunner(
        scope = viewModelScope,
        onSegmentStart = { segment, index -> onProgramSegmentStart(segment, index) },
        onTick = { segmentElapsed, totalElapsed -> onProgramTick(segmentElapsed, totalElapsed) },
        onFinished = { finishWorkout() },
    )

    init {
        toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME) }.getOrNull()

        viewModelScope.launch {
            workoutRepository.observeTotalPoints().collect { total -> _uiState.update { it.copy(totalPoints = total) } }
        }
        viewModelScope.launch {
            bleRepository.liveData.collect { data -> onLiveData(data) }
        }
        viewModelScope.launch {
            bleRepository.resistanceLevelRange.collect { range ->
                // Реальный минимум читаем только после подключения — если он окажется
                // выше дефолтного (например min=2), подтягиваем цель вверх сразу же.
                _uiState.update {
                    it.copy(
                        resistanceRange = range,
                        targetResistance = it.targetResistance.coerceAtLeast(range.min),
                    )
                }
            }
        }
        viewModelScope.launch {
            bleRepository.machineStatus.collect { status ->
                if (status.isControlLost || status.isStoppedByUser) {
                    LogBus.w(TAG_WORKOUT, "Тренажёр забрал управление: ${status.describe()}")
                    _uiState.update { it.copy(controlAcquired = false) }
                }
            }
        }
        viewModelScope.launch {
            bleRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is BleConnectionState.Ready) startIdleWatchdog() else idleWatchdogJob?.cancel()
            }
        }
        viewModelScope.launch {
            userSettingsRepository.weightKg.collect { kg -> _uiState.update { it.copy(weightKg = kg) } }
        }
        viewModelScope.launch {
            userSettingsRepository.heartRateAlertsEnabled.collect { enabled ->
                _uiState.update { it.copy(hrAlertsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            combine(
                userSettingsRepository.ageYears,
                userSettingsRepository.heartRateLowerOverride,
                userSettingsRepository.heartRateUpperOverride,
            ) { age, lowerOverride, upperOverride ->
                UserSettingsRepository.effectiveLowerThreshold(age, lowerOverride) to
                    UserSettingsRepository.effectiveUpperThreshold(age, upperOverride)
            }.collect { (lower, upper) ->
                _uiState.update { it.copy(hrLowerThreshold = lower, hrUpperThreshold = upper) }
            }
        }

        val programId = savedStateHandle.get<String>(Screen.ActiveWorkout.ARG_PROGRAM_ID)
        if (programId != null) {
            viewModelScope.launch {
                val program = workoutProgramRepository.getProgram(programId)
                _uiState.update {
                    it.copy(
                        program = program,
                        segmentRemainingSeconds = program?.segments?.firstOrNull()?.durationSeconds ?: 0,
                        targetResistance = program?.segments?.firstOrNull()?.targetResistance ?: 0,
                    )
                }
            }
        }
    }

    private fun onLiveData(data: LiveWorkoutData) {
        if ((data.resistanceLevel ?: 0f) >= _uiState.value.resistanceRange.min) idleReconnects = 0

        if (_uiState.value.isTracking) {
            val watts = data.instantaneousPowerWatts ?: 0
            val cadence = data.stepsPerMinute?.takeIf { it > 0f }
                ?: data.instantaneousCadenceRpm?.takeIf { it > 0f }
            val isMoving = watts > 0 || cadence != null
            val weight = _uiState.value.weightKg
            if (lastEnergySampleMs > 0L && isMoving) {
                val dtS = (data.receivedAtMs - lastEnergySampleMs).coerceIn(0L, 5_000L) / 1000.0
                energyKjAccum += watts * dtS / 1000.0
                kcalAccum += (ACSM_VO2_PER_WATT * watts + ACSM_VO2_BASE * weight) *
                    KCAL_PER_LITER_O2 * ELLIPTICAL_CORRECTION / 60_000.0 * dtS
                zoneScoreAccum += calculatePoints.zoneRate(data.heartRateBpm, _uiState.value.hrLowerThreshold) * (dtS / 60.0)

                movingSecondsAccum += dtS
                val bpm = data.heartRateBpm
                val lowerThreshold = _uiState.value.hrLowerThreshold
                // 0 — тоже "нет данных" (см. checkHeartRateAlert), в расчёт не идёт.
                if (bpm != null && bpm > 0) {
                    hrSumAccum += bpm * dtS
                    hrValidSecondsAccum += dtS
                    if (lowerThreshold != null) {
                        if (bpm >= lowerThreshold) inZoneSecondsAccum += dtS else belowZoneSecondsAccum += dtS
                    }
                }
            }
            lastEnergySampleMs = data.receivedAtMs
        } else {
            energyKjAccum = 0.0
            kcalAccum = 0.0
            zoneScoreAccum = 0.0
            lastEnergySampleMs = 0L
            hrSumAccum = 0.0
            hrValidSecondsAccum = 0.0
            belowZoneSecondsAccum = 0.0
            inZoneSecondsAccum = 0.0
            movingSecondsAccum = 0.0
        }

        val prevResistance = _uiState.value.liveData?.resistanceLevel
        if (data.resistanceLevel != null && data.resistanceLevel != prevResistance) {
            LogBus.d(TAG_WORKOUT, "Телеметрия: факт.нагрузка=${data.resistanceLevel}, цель=${_uiState.value.targetResistance}")
        }

        _uiState.update { state ->
            state.copy(
                liveData = data,
                energyKj = energyKjAccum.toFloat(),
                kcalBurned = kcalAccum.roundToInt(),
                heartRateHistory = (state.heartRateHistory + (data.heartRateBpm?.toFloat() ?: 0f)).takeLast(METRIC_HISTORY_SIZE),
                speedHistory = (state.speedHistory + (data.instantaneousSpeedKmh ?: 0f)).takeLast(METRIC_HISTORY_SIZE),
                powerHistory = (state.powerHistory + (data.instantaneousPowerWatts?.toFloat() ?: 0f)).takeLast(METRIC_HISTORY_SIZE),
            )
        }
        checkHeartRateAlert(data.heartRateBpm)
    }

    /**
     * Пересечение порога зоны пульса во время тренировки: разовый сигнал при выходе,
     * затем не чаще HR_ALERT_COOLDOWN_MS, пока пульс всё ещё вне диапазона. Возврат
     * в норму сбрасывает таймеры — следующий выход снова сигналит сразу.
     */
    private fun checkHeartRateAlert(bpm: Int?) {
        val state = _uiState.value
        // bpm == 0 — тоже "нет данных": именно так консоль сообщает отсутствие
        // пульсометра, а не null. Без этой проверки 0 всегда ниже нижнего порога
        // зоны и алерт "добавьте интенсивность" сыпется постоянно без пульсометра.
        if (!state.isTracking || !state.hrAlertsEnabled || bpm == null || bpm <= 0) return
        if (state.elapsedSeconds < HR_ALERT_GRACE_SECONDS) return		
        val now = System.currentTimeMillis()
        val upper = state.hrUpperThreshold
        val lower = state.hrLowerThreshold
        when {
            upper != null && bpm > upper -> {
                if (now - lastHighHrAlertAtMs >= HR_ALERT_COOLDOWN_MS) {
                    lastHighHrAlertAtMs = now
                    LogBus.d(TAG_WORKOUT, "Пульс $bpm выше порога $upper — «снизьте интенсивность»")
                    triggerHeartRateAlert("СБАВЬТЕ ИНТЕНСИВНОСТЬ!", exceeded = true)
                }
            }
            lower != null && bpm < lower -> {
                if (now - lastLowHrAlertAtMs >= HR_ALERT_COOLDOWN_MS) {
                    lastLowHrAlertAtMs = now
                    LogBus.d(TAG_WORKOUT, "Пульс $bpm ниже порога $lower — «добавьте интенсивность»")
                    triggerHeartRateAlert("ДОБАВЬТЕ ИНТЕНСИВНОСТЬ!", exceeded = false)
                }
            }
            else -> {
                lastHighHrAlertAtMs = 0L
                lastLowHrAlertAtMs = 0L
            }
        }
    }

    /** Звук + баннер на экране. Баннер сам гаснет через HR_ALERT_BANNER_VISIBLE_MS,
     *  повторный вызов (следующее превышение) просто перезапускает таймер скрытия. */
    private fun triggerHeartRateAlert(message: String, exceeded: Boolean) {
        playHeartRateAlertTone(exceeded)
        _uiState.update { it.copy(hrAlertMessage = message) }
        hrAlertClearJob?.cancel()
        hrAlertClearJob = viewModelScope.launch {
            delay(HR_ALERT_BANNER_VISIBLE_MS)
            _uiState.update { it.copy(hrAlertMessage = null) }
        }
    }
    /** Тренировка не прошла порог очков и не сохранена — коротким баннером сообщаем об этом,
     *  иначе выглядит так, будто кнопка "Завершить" просто ничего не сделала. */
    private fun notifyWorkoutDiscarded(points: Int) {
        _uiState.update { it.copy(discardNoticeMessage = "Тренировка слишком короткая — не сохранена ($points очк.)") }
        discardNoticeClearJob?.cancel()
        discardNoticeClearJob = viewModelScope.launch {
            delay(HR_ALERT_BANNER_VISIBLE_MS)
            _uiState.update { it.copy(discardNoticeMessage = null) }
        }
    }
    private fun playHeartRateAlertTone(exceeded: Boolean) {
        val tone = if (exceeded) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
        runCatching { toneGenerator?.startTone(tone, HR_ALERT_TONE_DURATION_MS) }
    }

    /** Короткая фанфара при завершении тренировки — чисто разовый эффект, плеер сам себя освобождает. */
    private fun playFinishFanfare() {
        runCatching {
            MediaPlayer.create(appContext, R.raw.workout_complete)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }


    fun connect() {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = BleConnectionState.Connecting) }
            bleRepository.connect()
        }
    }



    fun startWorkout() {
        val startState = _uiState.value
        if (startState.isTracking) {
            LogBus.w(TAG_WORKOUT, "startWorkout() проигнорирован: тренировка уже идёт")
            return
        }
        LogBus.reset()
        LogBus.d(
            TAG_WORKOUT,
            "startWorkout(): программа=${startState.program?.name ?: "свободный заезд"}, " +
                "соединение=${startState.connectionState}, цель=${startState.targetResistance}",
        )
        workoutStartedAt = Instant.now()
        val distanceBaseline = startState.liveData?.totalDistanceMeters ?: 0
        energyKjAccum = 0.0
        kcalAccum = 0.0
		zoneScoreAccum = 0.0
        lastEnergySampleMs = 0L
        hrSumAccum = 0.0
        hrValidSecondsAccum = 0.0
        belowZoneSecondsAccum = 0.0
        inZoneSecondsAccum = 0.0
        movingSecondsAccum = 0.0
		
        _uiState.update {
            it.copy(
                isTracking = true,
                elapsedSeconds = 0,
                currentPoints = 0,
                currentSegmentIndex = 0,
                programElapsedSeconds = 0,
                distanceOffsetMeters = distanceBaseline,
                energyKj = 0f,
                kcalBurned = 0,
                lastSessionRating = null,
                lastSessionSummary = null,				
                trendMessage = null,				
            )
        }
        WorkoutForegroundService.start(appContext, SystemClock.elapsedRealtime())

        // Программу/таймер запускаем сразу — они не должны ждать тренажёр.
        // Захватом управления занимается фоновый контур: он захватывает, проверяет
        // по телеметрии и перезахватывает при потере.
        val program = startState.program
        if (program != null) {
            _uiState.update { it.copy(isAutoModeActive = true) }
            programRunner.start(program)
        } else {
            startFreeRideTimer()
        }
        startControlLoop()
    }

    /**
     * Первое подключение сразу после включения тренажёра застаёт консоль в дежурке
     * (сопр=0, счётчики стоят), и сама она оттуда не выходит: команды подтверждает,
     * экран не блокирует. Лечится переподключением по BLE — то же самое, что перезапуск
     * приложения руками, только автоматически.
     */
    private fun startIdleWatchdog() {
        idleWatchdogJob?.cancel()
        idleWatchdogJob = viewModelScope.launch {
            delay(IDLE_WATCHDOG_MS)
            val state = _uiState.value
            if (state.connectionState !is BleConnectionState.Ready) return@launch
            if (state.isTracking) return@launch // тренировку рвать не будем
            if (!state.consoleIdle) return@launch
            if (idleReconnects >= MAX_IDLE_RECONNECTS) {
                LogBus.w(TAG_WORKOUT, "Консоль в дежурке, лимит переподключений исчерпан")
                return@launch
            }
            idleReconnects++
            LogBus.w(TAG_WORKOUT, "Консоль ${IDLE_WATCHDOG_MS / 1000}с в дежурке — переподключаемся (попытка $idleReconnects)")
            bleRepository.disconnect()
            delay(RECONNECT_GAP_MS)
            bleRepository.connect()
        }
    }

    /** Полная последовательность захвата: 0x00 → 0x07 → 0x04(цель) + проверка по телеметрии. */
    private suspend fun acquireControl(reason: String) {
        val state = _uiState.value
        val target = if (state.targetResistance > 0) state.targetResistance else state.resistanceRange.min
        lastSentResistance = null
        desyncTicks = 0
        val ok = requestBleControl(target)
        lastCommandAtMs = System.currentTimeMillis()
        _uiState.update { it.copy(controlAcquired = ok) }
        LogBus.d(TAG_WORKOUT, "Захват ($reason): acquired=$ok, цель=$target")
    }

    private fun startControlLoop() {
        controlLoopJob?.cancel()
        desyncTicks = 0
        lastCommandAtMs = 0L
        lastLoopLogMs = 0L
        lastLoopSignature = ""
        controlLoopJob = viewModelScope.launch {
            LogBus.d(TAG_WORKOUT, "Контроль-контур запущен")
            // Захват идём делать СРАЗУ, не дожидаясь ног. RequestControl + StartOrResume —
            // единственное, чем можно попробовать вытащить консоль из дежурки (сопр=0).
            acquireControl("старт тренировки")

            while (true) {
                delay(CONTROL_POLL_MS)
                val state = _uiState.value
                if (!state.isTracking) continue
                if (state.connectionState !is BleConnectionState.Ready) {
                    _uiState.update { it.copy(controlAcquired = false) }
                    continue
                }

                val range = state.resistanceRange
                val target = if (state.targetResistance > 0) state.targetResistance else range.min
                val actual = state.liveData?.resistanceLevel
                // 0x2AD6 объявляет min=1. Значение 0 — вне диапазона: своей сессии у консоли
                // нет, счётчики стоят, любые команды подтверждаются вхолостую.
                val awake = actual != null && actual >= range.min
                val inSync = awake && kotlin.math.abs(actual!! - target) <= RESISTANCE_TOLERANCE

                // Пишем только смену состояния либо раз в полминуты: в норме контур
                // повторяет одну и ту же строку и топит остальной лог.
                val signature = "$awake|$inSync|${state.controlAcquired}|$target"
                val now = System.currentTimeMillis()
                if (signature != lastLoopSignature || now - lastLoopLogMs >= LOOP_LOG_INTERVAL_MS) {
                    lastLoopSignature = signature
                    lastLoopLogMs = now
                    LogBus.d(
                        TAG_WORKOUT,
                        "Контур: консоль=${if (awake) "в сеансе" else "ДЕЖУРКА"}, факт=$actual, цель=$target, " +
                            "захвачено=${state.controlAcquired}, синхрон=$inSync",
                    )
                }

                if (inSync) {
                    desyncTicks = 0
                    if (!state.controlAcquired) _uiState.update { it.copy(controlAcquired = true) }
                    if (now - lastCommandAtMs > CONTROL_KEEPALIVE_MS) {
                        sendTarget(target, "keepalive", quiet = true)
                    }
                    continue
                }

                desyncTicks++
                val threshold = if (awake) DESYNC_TICKS_AWAKE else DESYNC_TICKS_IDLE
                if (desyncTicks >= threshold) {
                    LogBus.w(TAG_WORKOUT, "Рассинхрон: факт=$actual, цель=$target, консоль в сеансе=$awake")
                    acquireControl(if (awake) "рассинхрон" else "консоль в дежурке")
                }
            }
        }
    }

    private suspend fun sendTarget(level: Int, reason: String, quiet: Boolean = false): Boolean {
        lastCommandAtMs = System.currentTimeMillis()
        lastSentResistance = level
        val accepted = setResistanceLevel(level, quiet)
        // Keepalive уходит раз в 15 секунд и в норме всегда успешен — в логе он только шумит.
        if (!quiet || !accepted) {
            LogBus.d(TAG_WORKOUT, "$reason: level=$level, принято тренажёром=$accepted")
        }
        return accepted
    }

    /** Ручной перезахват по кнопке. */
    fun retryControl() {
        viewModelScope.launch { acquireControl("кнопка «повторить»") }
    }
	
    /** Закрывает карточку итогов тренировки. */
    fun dismissSessionSummary() {
        _uiState.update { it.copy(lastSessionRating = null, lastSessionSummary = null, trendMessage = null) }
    }

    private fun startFreeRideTimer() {
        freeRideTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { state ->
                    val newElapsed = state.elapsedSeconds + 1
                    val points = calculatePoints(
                        zoneScore = zoneScoreAccum.toFloat(),
                        distanceMeters = state.realDistanceMeters,
                    )
                    state.copy(elapsedSeconds = newElapsed, currentPoints = points)
                }
            }
        }
    }

    private suspend fun onProgramSegmentStart(segment: ProgramSegment, index: Int) {
        // segment приходит из ИСХОДНОЙ программы — применяем текущий сдвиг нагрузки.
        val state = _uiState.value
        val target = shiftResistance(segment.targetResistance, state.resistanceOffset, state.resistanceRange)
        _uiState.update {
            it.copy(
                currentSegmentIndex = index,
                segmentRemainingSeconds = segment.durationSeconds,
                targetResistance = target,
            )
        }
        sendTarget(target, "Сегмент #$index (профиль=${segment.targetResistance}, сдвиг=${state.resistanceOffset})")
        desyncTicks = 0
    }

    private fun onProgramTick(segmentElapsed: Int, totalElapsed: Int) {
        _uiState.update { state ->
            val segmentDuration = state.currentSegment?.durationSeconds ?: 0
            val points = calculatePoints(
                zoneScore = zoneScoreAccum.toFloat(),
                distanceMeters = state.realDistanceMeters,
            )
            state.copy(
                elapsedSeconds = totalElapsed,
                programElapsedSeconds = totalElapsed,
                segmentRemainingSeconds = (segmentDuration - segmentElapsed).coerceAtLeast(0),
                currentPoints = points,
            )
        }
    }

    /**
     * Сдвиг нагрузки в режиме программы: двигает ВСЮ тренировку разом (и текущий сегмент,
     * и все следующие), программу при этом не останавливает — крутить отдельный сегмент
     * смысла нет, его всё равно перезапишет следующий шаг.
     */
    fun setResistanceOffset(offset: Int) {
        val clamped = offset.coerceIn(ResistanceOffset.MIN, ResistanceOffset.MAX)
        _uiState.update { it.copy(resistanceOffset = clamped) }

        val target = _uiState.value.currentSegment?.targetResistance ?: return
        _uiState.update { it.copy(targetResistance = target) }
        sendResistanceDebounced(target, "сдвиг=$clamped")
    }

    fun adjustResistanceOffset(delta: Int) {
        setResistanceOffset(_uiState.value.resistanceOffset + delta)
    }

    /** Свободный заезд (без программы): абсолютный уровень нагрузки. */
    fun setResistanceManually(level: Int) {
        val range = _uiState.value.resistanceRange
        val newLevel = level.coerceIn(range.min, range.max)
        _uiState.update { it.copy(targetResistance = newLevel) }
        sendResistanceDebounced(newLevel, "ручная установка")
    }

    fun adjustResistanceManually(delta: Int) {
        setResistanceManually(_uiState.value.targetResistance + delta)
    }

    /**
     * Слайдер сыплет десятком значений за полсекунды, тренажёр переваривает каждую команду
     * ~60мс и не успевает за пальцем. Шлём только то, на чём палец встал, и не дублируем
     * уже отправленный уровень. Отправку делает sendTarget — он же двигает метку keepalive,
     * чтобы контроль-контур не слал лишнюю команду сразу следом.
     */
    private fun sendResistanceDebounced(level: Int, reason: String) {
        manualResistanceJob?.cancel()
        manualResistanceJob = viewModelScope.launch {
            delay(MANUAL_RESISTANCE_DEBOUNCE_MS)
            if (level == lastSentResistance) return@launch
            // Цель только что сменилась — тренажёру нужно время доехать до неё,
            // счётчик рассинхрона обнуляем, чтобы контур не полез в перезахват.
            desyncTicks = 0
            sendTarget(level, reason)
        }
    }

      fun finishWorkout() {
        val startedAt = workoutStartedAt
        val state = _uiState.value
        val duration = state.elapsedSeconds
        val realDistance = state.realDistanceMeters
        val burned = state.kcalBurned
        val finalPoints = state.currentPoints
        val programId = state.program?.id
        val avgPower = state.averagePowerWatts.takeIf { duration > 0 }
        val avgHeartRate = if (hrValidSecondsAccum > 0) (hrSumAccum / hrValidSecondsAccum).roundToInt() else null
        val hrCoveragePercent = if (movingSecondsAccum > 0) {
            ((hrValidSecondsAccum / movingSecondsAccum) * 100).roundToInt()
        } else {
            0
        }
        // Разовые касания рукояток консоли не в счёт — если валидный пульс покрывает
        // меньше MIN_HR_COVERAGE_PERCENT времени движения, пульсометра по факту не было.
        val belowZoneSharePercent = if (
            hrCoveragePercent >= MIN_HR_COVERAGE_PERCENT && (belowZoneSecondsAccum + inZoneSecondsAccum) > 0
        ) {
            ((belowZoneSecondsAccum / (belowZoneSecondsAccum + inZoneSecondsAccum)) * 100).roundToInt()
        } else {
            null
        }

        // Останавливаем всё безусловно — раньше метод выходил по `?: return` при отсутствии
        // курса, из-за чего кнопка "Завершить" не реагировала.
        freeRideTimerJob?.cancel()
        manualResistanceJob?.cancel()
        programRunner.stop()
        controlLoopJob?.cancel()
        WorkoutForegroundService.stop(appContext)

        viewModelScope.launch {
            bleRepository.stopOrPause()
            var rating: WorkoutRating? = null
            var summary: WorkoutSummary? = null			
            var trendMessage: String? = null

            if (startedAt != null) {
                if (finalPoints < MIN_POINTS_TO_SAVE) {
                    LogBus.d(
                        TAG_WORKOUT,
                        "Тренировка НЕ сохранена: $finalPoints очков (< $MIN_POINTS_TO_SAVE) — похоже на случайный старт",
                    )
                    notifyWorkoutDiscarded(finalPoints)
                } else {
                    val points = saveWorkoutUseCase(
                        startedAt = startedAt,
                        durationSeconds = duration,
                        distanceMeters = realDistance,
                        caloriesKcal = burned,
                        avgHeartRateBpm = avgHeartRate,
                        pointsEarned = finalPoints,
                        programId = programId,
                        avgPowerWatts = avgPower,
                        belowZoneSharePercent = belowZoneSharePercent,
                    )
                    LogBus.d(TAG_WORKOUT, "Тренировка сохранена: ${duration}с, ${realDistance}м, ${burned}ккал, +$points очков")
                    workoutBackupWriter.backup()					

                    rating = WorkoutRating.forSession(belowZoneSharePercent)
                    summary = WorkoutSummary(
                        durationSeconds = duration,
                        distanceMeters = realDistance,
                        caloriesKcal = burned,
                        avgHeartRateBpm = avgHeartRate,
                        pointsEarned = points,
                    )
                    playFinishFanfare()
                    if (programId != null) {
                        val trend = analyzeProgramTrend(programId)
                        if (trend is ProgramTrend.EasyTrend) {
                            trendMessage = "«${state.program?.name}»: ${trend.sessionsConsidered} заезда подряд " +
                                "в среднем ${trend.avgBelowZonePercent}% времени ниже целевой зоны. " +
                                "Похоже, полегчало — в следующий раз попробуй сдвинуть нагрузку повыше."
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isTracking = false,
                    elapsedSeconds = 0,
                    currentPoints = 0,
                    isAutoModeActive = false,
                    programElapsedSeconds = 0,
                    currentSegmentIndex = 0,
                    distanceOffsetMeters = 0,
                    resistanceOffset = ResistanceOffset.DEFAULT,
                    energyKj = 0f,
                    kcalBurned = 0,
 			        hrAlertMessage = null,
                    lastSessionRating = rating,
                    lastSessionSummary = summary,					
                    trendMessage = trendMessage,
                )
            }
            workoutStartedAt = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator?.release()
        toneGenerator = null
        hrAlertClearJob?.cancel()
        discardNoticeClearJob?.cancel()
        // Заезд идёт, а экран уничтожают (система выбросила Activity ради памяти —
        // например, под входящий звонок). Рвать BLE и гасить сервис здесь НЕЛЬЗЯ:
        // раньше это молча убивало тренировку, не записав её в историю.
        if (_uiState.value.isTracking) {
            LogBus.w(TAG_WORKOUT, "Экран уничтожен во время заезда — сессию не трогаем")
            return
        }

        bleRepository.stopAndRelease()
        controlLoopJob?.cancel()
        idleWatchdogJob?.cancel()
        freeRideTimerJob?.cancel()
        manualResistanceJob?.cancel()
        programRunner.stop()
        WorkoutForegroundService.stop(appContext)
    }
}