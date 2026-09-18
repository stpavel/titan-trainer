package com.svensson.titan.presentation.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svensson.titan.domain.model.TrainerReport
import com.svensson.titan.domain.repository.TrainerReportRepository
import com.svensson.titan.domain.usecase.BuildReportResult
import com.svensson.titan.domain.usecase.BuildTrainerReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainerReportUiState(
    val isLoading: Boolean = false,
    val report: TrainerReport? = null,
    val errorMessage: String? = null,
    val notice: String? = null,
)

@HiltViewModel
class TrainerReportViewModel @Inject constructor(
    private val buildTrainerReportUseCase: BuildTrainerReportUseCase,
    trainerReportRepository: TrainerReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainerReportUiState())
    val uiState: StateFlow<TrainerReportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            trainerReportRepository.observeLatest().collect { report ->
                _uiState.update { it.copy(report = report) }
            }
        }
    }

    fun generateReport() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, notice = null) }
        viewModelScope.launch {
            when (val result = buildTrainerReportUseCase()) {
                is BuildReportResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        report = result.report,
                        errorMessage = null,
                        notice = if (result.fromCache) "Тренировки за период не изменились — показан прошлый отчёт" else null,
                    )
                }
                is BuildReportResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                BuildReportResult.NoWorkouts -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "За последние 30 дней нет тренировок для анализа")
                }
            }
        }
    }
}