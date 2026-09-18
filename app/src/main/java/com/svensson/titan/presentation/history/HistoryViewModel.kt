package com.svensson.titan.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svensson.titan.domain.model.Workout
import com.svensson.titan.domain.repository.TrainerSettingsRepository
import com.svensson.titan.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class HistoryUiState(
    val totalPoints: Int = 0,
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthPoints: Int = 0,
    val monthWorkouts: List<Workout> = emptyList(),
    /** Числа месяца (1..31), в которые была хотя бы одна тренировка — для маркеров в календаре. */
    val workoutDays: Set<Int> = emptySet(),
    /** Включена фича "Персональный тренер" и задан API-ключ — можно показывать кнопку. */
    val trainerAvailable: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val trainerSettingsRepository: TrainerSettingsRepository,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    private val workouts: StateFlow<List<Workout>> = workoutRepository.observeAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val trainerAvailable: StateFlow<Boolean> = combine(
        trainerSettingsRepository.isEnabled,
        trainerSettingsRepository.apiKey,
    ) { enabled, apiKey -> enabled && apiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val uiState: StateFlow<HistoryUiState> = combine(workouts, selectedMonth, trainerAvailable) { list, month, available ->
        val monthWorkouts = list.filter { YearMonth.from(it.startedAt.atZone(zone)) == month }
        HistoryUiState(
            totalPoints = list.sumOf { it.pointsEarned },
            selectedMonth = month,
            monthPoints = monthWorkouts.sumOf { it.pointsEarned },
            monthWorkouts = monthWorkouts,
            workoutDays = monthWorkouts.map { it.startedAt.atZone(zone).dayOfMonth }.toSet(),
            trainerAvailable = available,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun previousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { workoutRepository.deleteWorkout(id) }
    }
}