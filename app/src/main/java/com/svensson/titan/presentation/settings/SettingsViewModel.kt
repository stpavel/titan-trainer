package com.svensson.titan.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svensson.titan.domain.repository.TrainerSettingsRepository
import com.svensson.titan.domain.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.svensson.titan.domain.repository.WorkoutRepository
import com.svensson.titan.util.WorkoutCsv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File


data class SettingsUiState(
    val weightKg: Int = UserSettingsRepository.DEFAULT_WEIGHT_KG,
    val ageYears: Int? = null,
    val heartRateAlertsEnabled: Boolean = false,
    val heartRateLowerOverride: Int? = null,
    val heartRateUpperOverride: Int? = null,
    /** Не null ровно на один кадр — экран открывает системное окно "поделиться" и сбрасывает. */
    val exportUri: Uri? = null,
    val importResultMessage: String? = null,
    val trainerEnabled: Boolean = false,
    val trainerApiKey: String = "",
    val trainerModel: String = TrainerSettingsRepository.DEFAULT_MODEL,
    val trainerBaseUrl: String = TrainerSettingsRepository.DEFAULT_BASE_URL,
) {
    val calculatedLowerThreshold: Int? get() = ageYears?.let { UserSettingsRepository.moderateLowerBound(it) }
    val calculatedUpperThreshold: Int? get() = ageYears?.let { UserSettingsRepository.vigorousUpperBound(it) }
}

private const val EXPORT_FILE_NAME = "titan-trainer-history.csv"
private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val workoutRepository: WorkoutRepository,
    private val trainerSettingsRepository: TrainerSettingsRepository,
    @ApplicationContext private val appContext: Context,

) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userSettingsRepository.weightKg.collect { kg -> _uiState.update { it.copy(weightKg = kg) } }
        }
        viewModelScope.launch {
            userSettingsRepository.ageYears.collect { age -> _uiState.update { it.copy(ageYears = age) } }
        }
        viewModelScope.launch {
            userSettingsRepository.heartRateAlertsEnabled.collect { enabled ->
                _uiState.update { it.copy(heartRateAlertsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userSettingsRepository.heartRateLowerOverride.collect { v -> _uiState.update { it.copy(heartRateLowerOverride = v) } }
        }
        viewModelScope.launch {
            userSettingsRepository.heartRateUpperOverride.collect { v -> _uiState.update { it.copy(heartRateUpperOverride = v) } }
        }
        viewModelScope.launch {
            trainerSettingsRepository.isEnabled.collect { v -> _uiState.update { it.copy(trainerEnabled = v) } }
        }
        viewModelScope.launch {
            trainerSettingsRepository.apiKey.collect { v -> _uiState.update { it.copy(trainerApiKey = v) } }
        }
        viewModelScope.launch {
            trainerSettingsRepository.model.collect { v -> _uiState.update { it.copy(trainerModel = v) } }
        }
        viewModelScope.launch {
            trainerSettingsRepository.baseUrl.collect { v -> _uiState.update { it.copy(trainerBaseUrl = v) } }
        }
    }

    fun setWeightKg(kg: Int) = userSettingsRepository.setWeightKg(kg)
    fun setAgeYears(age: Int?) = userSettingsRepository.setAgeYears(age)
    fun setHeartRateAlertsEnabled(enabled: Boolean) = userSettingsRepository.setHeartRateAlertsEnabled(enabled)
    fun setHeartRateLowerOverride(bpm: Int?) = userSettingsRepository.setHeartRateLowerOverride(bpm)
    fun setHeartRateUpperOverride(bpm: Int?) = userSettingsRepository.setHeartRateUpperOverride(bpm)

    fun setTrainerEnabled(enabled: Boolean) = trainerSettingsRepository.setEnabled(enabled)
    fun setTrainerApiKey(key: String) = trainerSettingsRepository.setApiKey(key)
    fun setTrainerModel(model: String) = trainerSettingsRepository.setModel(model)
    fun setTrainerBaseUrl(url: String) = trainerSettingsRepository.setBaseUrl(url)

    /** Пишет CSV в кэш и отдаёт content:// Uri через FileProvider — экрану остаётся
     *  только показать системное окно "поделиться". */
    fun exportWorkouts() {
        viewModelScope.launch {
            val workouts = workoutRepository.observeAllWorkouts().first()
            val csv = WorkoutCsv.toCsv(workouts)
            val uri = withContext(Dispatchers.IO) {
                val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, EXPORT_FILE_NAME)
                file.writeBytes(UTF8_BOM + csv.toByteArray(Charsets.UTF_8))
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            }
            _uiState.update { it.copy(exportUri = uri) }
        }
    }

    fun consumeExportUri() {
        _uiState.update { it.copy(exportUri = null) }
    }

    /** @param csvText уже прочитанный текст файла — Uri/ContentResolver остаются на
     *  стороне экрана, ViewModel про файловый доступ Android знать не должен. */
    fun importWorkouts(csvText: String) {
        viewModelScope.launch {
            val message = runCatching {
                val result = withContext(Dispatchers.Default) { WorkoutCsv.fromCsv(csvText) }
                result.workouts.forEach { workoutRepository.saveWorkout(it) }
                buildString {
                    append("Импортировано тренировок: ${result.workouts.size}")
                    if (result.skippedLines > 0) append(", пропущено строк: ${result.skippedLines}")
                }
            }.getOrElse { error ->
                "Импорт не удался: ${error.message ?: error::class.simpleName}"
            }
            _uiState.update { it.copy(importResultMessage = message) }
        }
    }

    fun consumeImportResult() {
        _uiState.update { it.copy(importResultMessage = null) }
    }
}