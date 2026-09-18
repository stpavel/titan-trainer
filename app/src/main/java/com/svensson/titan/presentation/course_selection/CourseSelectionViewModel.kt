package com.svensson.titan.presentation.course_selection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svensson.titan.domain.model.CourseType
import com.svensson.titan.domain.repository.CourseRepository
import com.svensson.titan.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseSelectionUiState(
    val selectedType: CourseType? = null,
    val sessionsPerWeek: Int = 3,
    val monthlyGoalSessions: Int = 12,
    val activeCourseName: String? = null,
    val activeCoursePoints: Int = 0,
)

@HiltViewModel
class CourseSelectionViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val forceShow: Boolean = savedStateHandle.get<Boolean>(Screen.CourseSelection.ARG_FORCE_SHOW) ?: false

    private val _uiState = MutableStateFlow(CourseSelectionUiState())
    val uiState: StateFlow<CourseSelectionUiState> = _uiState.asStateFlow()

    private val _navigateToWorkout = MutableSharedFlow<Unit>()
    val navigateToWorkout: SharedFlow<Unit> = _navigateToWorkout.asSharedFlow()

    init {
        // Если курс уже выбран и экран открыт НЕ через "Сменить курс" — сразу
        // уходим на дэшборд, минуя этот экран. Если forceShow=true (пользователь
        // сам зашёл менять курс) — остаёмся здесь и показываем предупреждение.
        viewModelScope.launch {
            val active = courseRepository.getActiveCourse()
            if (active != null) {
                if (forceShow) {
                    _uiState.update {
                        it.copy(activeCourseName = active.courseType.displayName, activeCoursePoints = active.totalPoints)
                    }
                } else {
                    _navigateToWorkout.emit(Unit)
                }
            }
        }
    }

    fun selectCourse(type: CourseType) {
        _uiState.update { it.copy(selectedType = type, sessionsPerWeek = type.recommendedSessionsPerWeek) }
    }

    fun updateSessionsPerWeek(value: Int) {
        _uiState.update { it.copy(sessionsPerWeek = value.coerceIn(1, 7)) }
    }

    fun updateMonthlyGoal(value: Int) {
        _uiState.update { it.copy(monthlyGoalSessions = value.coerceIn(1, 31)) }
    }

    fun startCourse() {
        val state = _uiState.value
        val type = state.selectedType ?: return
        viewModelScope.launch {
            courseRepository.startCourse(type, state.sessionsPerWeek, state.monthlyGoalSessions)
            _navigateToWorkout.emit(Unit)
        }
    }
}