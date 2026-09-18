package com.svensson.titan.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svensson.titan.domain.model.Workout
import com.svensson.titan.presentation.components.HudPanel
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Star

private val MONTH_NAMES = listOf(
    "ЯНВАРЬ", "ФЕВРАЛЬ", "МАРТ", "АПРЕЛЬ", "МАЙ", "ИЮНЬ",
    "ИЮЛЬ", "АВГУСТ", "СЕНТЯБРЬ", "ОКТЯБРЬ", "НОЯБРЬ", "ДЕКАБРЬ",
)

private fun YearMonth.ruLabel(): String = "${MONTH_NAMES[monthValue - 1]} $year"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenTrainerReport: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Workout?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.selectedMonth) { selectedDay = null }

    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("Запись тренировки будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(workout.id)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ИСТОРИЯ ТРЕНИРОВОК") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TotalsHeroCard(
                totalPoints = uiState.totalPoints,
                monthLabel = uiState.selectedMonth.ruLabel(),
                monthPoints = uiState.monthPoints,
                onTrainerClick = if (uiState.trainerAvailable) onOpenTrainerReport else null,
            )

            MonthNavigator(
                label = uiState.selectedMonth.ruLabel(),
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )

            HudPanel(modifier = Modifier.fillMaxWidth(), contentPadding = 10.dp) {
                CalendarGrid(
                    yearMonth = uiState.selectedMonth,
                    markedDays = uiState.workoutDays,
                    selectedDay = selectedDay,
                    onDayClick = { day -> selectedDay = if (selectedDay == day) null else day },
                )
            }
            val displayedWorkouts = remember(uiState.monthWorkouts, selectedDay) {
                if (selectedDay == null) {
                    uiState.monthWorkouts
                } else {
                    uiState.monthWorkouts.filter {
                        it.startedAt.atZone(ZoneId.systemDefault()).dayOfMonth == selectedDay
                    }
                }
            }

            if (displayedWorkouts.isEmpty()) {
                Text(
                    if (uiState.monthWorkouts.isEmpty()) "В этом месяце пока пусто" else "Нет тренировок за выбранный день",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayedWorkouts, key = { it.id }) { workout ->
                        WorkoutRow(workout = workout, onDeleteClick = { pendingDelete = workout })
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsHeroCard(totalPoints: Int, monthLabel: String, monthPoints: Int, onTrainerClick: (() -> Unit)?) {
    HudPanel(modifier = Modifier.fillMaxWidth(), contentPadding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ВСЕГО ОЧКОВ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totalPoints.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (onTrainerClick != null) {
                IconButton(onClick = onTrainerClick) {
                    Icon(Icons.Filled.Star, contentDescription = "Персональный тренер", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ЗА $monthLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+$monthPoints", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun MonthNavigator(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
        }
    }
}

@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    markedDays: Set<Int>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
) {
    val weekDays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value - 1 // 0 = понедельник
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = firstDayOffset + daysInMonth
    val rows = (totalCells + 6) / 7
    val cellHeight = 32.dp
    val circleSize = 26.dp

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekDays.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - firstDayOffset + 1
                    Box(
                        modifier = Modifier.weight(1f).height(cellHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day in 1..daysInMonth) {
                            val hasWorkout = day in markedDays
                            val isSelected = day == selectedDay
                            Box(
                                modifier = Modifier
                                    .size(circleSize)
                                    .clip(CircleShape)
                                    .then(
                                        if (hasWorkout) {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 1f else 0.55f),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                                    )
                                    .clickable(enabled = hasWorkout) { onDayClick(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasWorkout) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(workout: Workout, onDeleteClick: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    val dateText = remember(workout.startedAt) {
        LocalDateTime.ofInstant(workout.startedAt, ZoneId.systemDefault()).format(formatter)
    }
    HudPanel(
        modifier = Modifier.fillMaxWidth(),
        accentColor = if (workout.isPenalty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (workout.isPenalty) {
                    Text(
                        "ШТРАФ ЗА ПРОПУСК: ${workout.pointsEarned} ОЧКОВ",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "${workout.durationSeconds / 60} МИН · ${workout.distanceMeters} М · ${workout.caloriesKcal} ККАЛ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val extraStats = listOfNotNull(
                        workout.avgHeartRateBpm?.let { "СР. ПУЛЬС $it" },
                        workout.avgPowerWatts?.let { "СР. МОЩНОСТЬ $it ВТ" },
                    )
                    if (extraStats.isNotEmpty()) {
                        Text(
                            extraStats.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "+${workout.pointsEarned} ОЧКОВ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить запись")
            }
        }
    }
}