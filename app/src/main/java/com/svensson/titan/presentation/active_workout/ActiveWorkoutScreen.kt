// app/src/main/java/com/svensson/titan/presentation/active_workout/ActiveWorkoutScreen.kt
package com.svensson.titan.presentation.active_workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svensson.titan.domain.model.BleConnectionState
import com.svensson.titan.domain.model.ResistanceLevelRange
import com.svensson.titan.domain.model.ResistanceOffset
import com.svensson.titan.domain.model.WorkoutProgram
import com.svensson.titan.presentation.components.HudPanel
import com.svensson.titan.presentation.components.LiveMetricsChart
import com.svensson.titan.presentation.components.ProgramProfileChart
import com.svensson.titan.presentation.components.SevenSegmentDisplay
import com.svensson.titan.util.PermissionUtils
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.svensson.titan.domain.model.WorkoutRating

private const val MAX_REASONABLE_SPEED_KMH = 45f

/**
 * Экран активной тренировки в стиле приборной панели/пульта управления.
 * Один источник телеметрии на устройство (см. TitanBleManager).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onOpenHistory: () -> Unit,
    onOpenLog: () -> Unit,
    onBack: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitConfirm by remember { mutableStateOf(false) }
    

    val view = LocalView.current
    DisposableEffect(uiState.isTracking) {
        view.keepScreenOn = uiState.isTracking
        onDispose { view.keepScreenOn = false }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) viewModel.connect()
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Завершить тренировку?") },
            text = { Text("Тренировка ещё идёт. Если выйти сейчас, заезд будет завершён и сохранён.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    viewModel.finishWorkout()
                    onBack()
                }) { Text("Выйти и завершить") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Остаться") }
            },
        )
    }

    uiState.lastSessionRating?.let { rating ->
            WorkoutSummaryDialog(
                rating = rating,
                summary = uiState.lastSessionSummary,
                trendMessage = uiState.trendMessage,
                onDismiss = viewModel::dismissSessionSummary,
            )
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((uiState.program?.name ?: "Тренировка").uppercase()) },
                navigationIcon = {
                    IconButton(onClick = { if (uiState.isTracking) showExitConfirm = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenLog) { Text("ЛОГ") }
                    TextButton(onClick = onOpenHistory) { Text("ИСТОРИЯ") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 12.dp) {
                Column(Modifier.padding(16.dp)) {
                    when {
                        uiState.connectionState !is BleConnectionState.Ready -> {
                            NeonButton(
                                text = "ПОДКЛЮЧИТЬСЯ К ТРЕНАЖЁРУ",
                                onClick = { permissionLauncher.launch(PermissionUtils.bluetoothPermissions()) },
                            )
                        }
                        !uiState.isTracking -> {
                            if (uiState.consoleIdle) {
                                Text(
                                    "ТРЕНАЖЁР В ДЕЖУРНОМ РЕЖИМЕ — СДЕЛАЙТЕ НЕСКОЛЬКО ДВИЖЕНИЙ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                )
                            }
                            NeonButton(
                                text = if (uiState.program != null) "НАЧАТЬ ПРОГРАММУ" else "НАЧАТЬ ТРЕНИРОВКУ",
                                onClick = viewModel::startWorkout,
                            )
                        }
                        else -> {
                            NeonButton(
                                text = "ЗАВЕРШИТЬ ТРЕНИРОВКУ",
                                onClick = viewModel::finishWorkout,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            StatusRow(uiState)

            if (uiState.connectionState !is BleConnectionState.Ready) {
                EmptyStateHint()
            } else {
                uiState.effectiveProgram?.let { program -> ProgramHeroCard(uiState = uiState, program = program) }

                MetricsGrid(uiState)

                LiveChartsRow(uiState)

                // В программе кнопки двигают нагрузку всей тренировки на уровень вверх/вниз,
                // в свободном заезде — задают абсолютный уровень.
                if (uiState.program != null) {
                    OffsetControlsRow(
                        offset = uiState.resistanceOffset,
                        currentTarget = uiState.targetResistance,
                        actualResistance = uiState.liveData?.resistanceLevel,
                        onAdjust = viewModel::adjustResistanceOffset,
                    )
                } else {
                    ManualControlsRow(
                        targetResistance = uiState.targetResistance,
                        actualResistance = uiState.liveData?.resistanceLevel,
                        range = uiState.resistanceRange,
                        onAdjust = viewModel::adjustResistanceManually,
                        onSetAbsolute = viewModel::setResistanceManually,
                    )
                }

                if (uiState.isTracking && !uiState.controlAcquired) {
                    if (uiState.consoleIdle) {
                        Text(
                            "⏳ ЖДЁМ ПРОБУЖДЕНИЯ КОНСОЛИ — КРУТИТЕ ПЕДАЛИ, УПРАВЛЕНИЕ ПЕРЕХВАТИТСЯ АВТОМАТИЧЕСКИ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        FilledTonalButton(onClick = viewModel::retryControl, modifier = Modifier.fillMaxWidth()) {
                            Text("ТРЕНАЖЁР НЕ ОТДАЛ УПРАВЛЕНИЕ — ПОВТОРИТЬ")
                        }
                    }
                }
            }
        }
        AlertBanner(
            message = uiState.hrAlertMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        )
        AlertBanner(
            message = uiState.discardNoticeMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        )
	  }	
    }
}

@Composable
private fun NeonButton(text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.primary) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, color.copy(alpha = 0.7f), MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = MaterialTheme.colorScheme.background),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusRow(uiState: ActiveWorkoutUiState) {
    val ready = uiState.connectionState is BleConnectionState.Ready
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ВСЕГО ОЧКОВ: ${uiState.totalPoints}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                    ),
            )
            Text(
                connectionStateLabel(uiState.connectionState).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyStateHint() {
    HudPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ТРЕНАЖЁР НЕ ПОДКЛЮЧЁН", style = MaterialTheme.typography.titleMedium)
            Text(
                "Нажми «Подключиться к тренажёру» внизу экрана, чтобы увидеть показатели и запустить тренировку.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgramHeroCard(uiState: ActiveWorkoutUiState, program: WorkoutProgram) {
    HudPanel(modifier = Modifier.fillMaxWidth(), accentColor = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    program.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.isTracking) {
                    Text(
                        if (uiState.controlAcquired) "● АВТО" else "○ РУЧНОЙ",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.controlAcquired) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            ProgramProfileChart(
                program = program,
                progressFraction = uiState.programProgressFraction,
                barHeight = 52.dp,
            )

            // ОСТАЛОСЬ убрано — дублировало плитку ОСТАЛОСЬ/ВРЕМЯ в MetricsGrid.
            Text(
                "СЕГМЕНТ ${uiState.currentSegmentIndex + 1}/${program.segments.size} · ЦЕЛЬ ${uiState.targetResistance} · " +
                    "${formatDuration(uiState.programElapsedSeconds)} / ${formatDuration(program.totalDurationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Вторую строку (elapsed/total) тоже убрали, но её высоту оставили
            // пустым Spacer — чтобы карточка не "сжималась" визуально.
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Сетка счётчиков в 3 ряда по 3. Единицы измерения — в названии карточки, не в
 * значении: на LED идёт только число. Разряд фиксирован по типу метрики, чтобы
 * дисплейчики были одного размера на всех карточках (без подгонки под контент).
 */
@Composable
private fun MetricsGrid(uiState: ActiveWorkoutUiState) {
    val data = uiState.liveData
    val distanceSinceStart = (data?.totalDistanceMeters
        ?.let { (it - uiState.distanceOffsetMeters).coerceAtLeast(0) }) ?: 0
    val cadence = (data?.stepsPerMinute?.takeIf { it > 0f }?.roundToInt()
        ?: data?.instantaneousCadenceRpm?.takeIf { it > 0f }?.let { (it * 2).roundToInt() }) ?: 0

    val timeMetric = uiState.program?.let { program ->
        val remaining = (program.totalDurationSeconds - uiState.programElapsedSeconds).coerceAtLeast(0)
        LedMetric("ОСТАЛОСЬ", formatDuration(remaining), lit = true)
    } ?: LedMetric("ВРЕМЯ", formatDuration(uiState.elapsedSeconds), lit = true)

    val metrics = listOf(
        timeMetric,
        LedMetric("ШАГИ", ledInt(uiState.stepsSinceStart, 5), lit = uiState.stepsSinceStart > 0),
        LedMetric("ДИСТ, М", ledInt(uiState.realDistanceMeters, 5), lit = uiState.realDistanceMeters > 0),
        LedMetric("ТЕМП, Ш/М", ledInt(cadence, 3), lit = cadence > 0),
        LedMetric("ККАЛ", ledInt(uiState.kcalBurned, 4), lit = uiState.kcalBurned > 0),
        LedMetric("ПЕШКОМ, М", ledInt(uiState.walkEquivalentMeters, 5), lit = uiState.walkEquivalentMeters > 0),
        LedMetric("СР.ВТ", ledInt(uiState.averagePowerWatts, 3), lit = uiState.averagePowerWatts > 0),
        LedMetric("ОЧКИ", ledInt(uiState.currentPoints, 4), lit = true),
        LedMetric("КОНСОЛЬ, М", ledInt(distanceSinceStart, 5), lit = distanceSinceStart > 0),
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        metrics.chunked(3).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rowMetrics.forEach { metric ->
                    MetricCard(metric = metric, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class LedMetric(val label: String, val text: String, val lit: Boolean)

@Composable
private fun MetricCard(metric: LedMetric, modifier: Modifier = Modifier) {
    HudPanel(
        modifier = modifier,
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 9.dp,
        cornerLength = 11.dp,
    ) {
        Column {
            Text(
                metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                letterSpacing = 0.4.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            SevenSegmentDisplay(
                text = metric.text,
                color = MaterialTheme.colorScheme.primary,
                lit = metric.lit,
            )
        }
    }
}

@Composable
private fun LiveChartsRow(uiState: ActiveWorkoutUiState) {
    val heartRate = uiState.liveData?.heartRateBpm ?: 0
    val speed = uiState.liveData?.instantaneousSpeedKmh?.takeIf { it <= MAX_REASONABLE_SPEED_KMH }
    val power = uiState.liveData?.instantaneousPowerWatts ?: 0

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricSparkCard(
            label = "ПУЛЬС, УД/МИН",
            text = ledInt(heartRate, 3),
            lit = heartRate > 0,
            values = uiState.heartRateHistory,
            maxValue = 200f,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        MetricSparkCard(
            label = "СКОР. КМ/Ч",
            text = ledSpeed(speed),
            lit = speed != null,
            values = uiState.speedHistory,
            maxValue = 50f,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        MetricSparkCard(
            label = "МОЩНОСТЬ, ВТ",
            text = ledInt(power, 3),
            lit = power > 0,
            values = uiState.powerHistory,
            maxValue = 500f,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricSparkCard(
    label: String,
    text: String,
    lit: Boolean,
    values: List<Float>,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    HudPanel(modifier = modifier, accentColor = color, contentPadding = 9.dp, cornerLength = 11.dp) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                letterSpacing = 0.4.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            SevenSegmentDisplay(text = text, color = color, lit = lit)
            Spacer(Modifier.height(5.dp))
            LiveMetricsChart(values = values, maxValue = maxValue, lineColor = color, modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ManualControlsRow(
    targetResistance: Int,
    actualResistance: Float?,
    range: ResistanceLevelRange,
    onAdjust: (Int) -> Unit,
    onSetAbsolute: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("НАГРУЗКА: $targetResistance (${range.min}–${range.max})", style = MaterialTheme.typography.titleSmall)
                AppliedLabel(actualResistance, targetResistance)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAdjust(-range.step) }, enabled = targetResistance > range.min) { Text("–") }
                OutlinedButton(onClick = { onAdjust(range.step) }, enabled = targetResistance < range.max) { Text("+") }
            }
        }
        Slider(
            value = targetResistance.toFloat(),
            onValueChange = { onSetAbsolute(it.roundToInt()) },
            valueRange = range.min.toFloat()..range.max.toFloat(),
            steps = (((range.max - range.min) / range.step.coerceAtLeast(1)) - 1).coerceAtLeast(0),
        )
    }
}

/**
 * Сдвиг нагрузки всей программы в уровнях. Ползунка нет специально: шкала
 * из 11 позиций (−5…+5) на слайдере попадается пальцем хуже, чем две кнопки.
 */
@Composable
private fun OffsetControlsRow(
    offset: Int,
    currentTarget: Int,
    actualResistance: Float?,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ЦЕЛЬ $currentTarget" + if (offset != 0) "  ·  СДВИГ ${if (offset > 0) "+" else ""}$offset" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            AppliedLabel(actualResistance, currentTarget)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onAdjust(-ResistanceOffset.STEP) },
                enabled = offset > ResistanceOffset.MIN,
            ) { Text("ЛЕГЧЕ") }
            OutlinedButton(
                onClick = { onAdjust(ResistanceOffset.STEP) },
                enabled = offset < ResistanceOffset.MAX,
            ) { Text("ТЯЖЕЛЕЕ") }
        }
    }
}

/**
 * Тренажёр часто встаёт на соседний уровень (цель 13 → факт 14) и дальше не едет —
 * это его округление, а не "ещё применяется". Отсюда допуск ±1.
 */
@Composable
private fun AppliedLabel(actualResistance: Float?, target: Int) {
    val applied = actualResistance?.let { kotlin.math.abs(it - target) <= 1f }
    Text(
        when (applied) {
            true -> "✓ ПРИМЕНЕНО"
            false -> "⏳ ПРИМЕНЯЕТСЯ…"
            null -> "—"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (applied == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun connectionStateLabel(state: BleConnectionState): String = when (state) {
    is BleConnectionState.Disconnected -> "не подключено"
    is BleConnectionState.Connecting -> "подключение..."
    is BleConnectionState.Connected -> "подключено, инициализация..."
    is BleConnectionState.Ready -> "готово"
    is BleConnectionState.Failed -> "ошибка: ${state.reason}"
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** Целое число на LED: обрезаем по числу разрядов и дополняем нулями слева. */
private fun ledInt(value: Int, digits: Int): String {
    val max = "9".repeat(digits).toInt()
    return value.coerceIn(0, max).toString().padStart(digits, '0')
}

/** Скорость на LED в формате "00,0" — два целых разряда, запятая, один десятичный. */
private fun ledSpeed(value: Float?): String {
    val v = (value ?: 0f).coerceIn(0f, 99.9f)
    val whole = v.toInt()
    val tenth = ((v - whole) * 10).roundToInt().coerceIn(0, 9)
    return "%02d,%d".format(whole, tenth)
}


@Composable
private fun AlertBanner(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 12.dp,
        ) {
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

private fun WorkoutRating.color(): Color = when (this) {
    WorkoutRating.EXCELLENT -> Color(0xFF3DDC84)
    WorkoutRating.GOOD -> Color(0xFFFFC107)
    WorkoutRating.FAIR -> Color(0xFFFF5252)
}

@Composable
private fun WorkoutSummaryDialog(
    rating: WorkoutRating,
    summary: WorkoutSummary?,
    trendMessage: String?,
    onDismiss: () -> Unit,
) {
    val accent = rating.color()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "ТРЕНИРОВКА ЗАВЕРШЕНА",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Text(
                    rating.label.uppercase(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 32.sp),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                trendMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                if (summary != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryStat("ОЧКИ", "+${summary.pointsEarned}", accent)
                        SummaryStat("ВРЕМЯ", "%d:%02d".format(summary.durationSeconds / 60, summary.durationSeconds % 60))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryStat("ДИСТАНЦИЯ", "%.2f км".format(summary.distanceMeters / 1000f))
                        SummaryStat("КАЛОРИИ", "${summary.caloriesKcal} ккал")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryStat("СР. ПУЛЬС", summary.avgHeartRateBpm?.let { "$it уд/мин" } ?: "—")
                        Spacer(Modifier.weight(1f))
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                ) {
                    Text("ПРОДОЛЖИТЬ")
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}