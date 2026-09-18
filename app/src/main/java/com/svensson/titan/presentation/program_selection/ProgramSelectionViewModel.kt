package com.svensson.titan.presentation.program_selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svensson.titan.domain.model.PredefinedTemplates
import com.svensson.titan.domain.model.ProgramSegment
import com.svensson.titan.domain.model.ResistanceLevelRange
import com.svensson.titan.domain.model.WorkoutProgram
import com.svensson.titan.domain.model.WorkoutProgramTemplate
import com.svensson.titan.domain.repository.WorkoutProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Шаг регулировки длительности сегмента. Секундная точность на эллипсе смысла не имеет. */
private const val SEGMENT_DURATION_STEP_S = 15
private const val SEGMENT_DURATION_MIN_S = 15
private const val SEGMENT_DURATION_MAX_S = 30 * 60

/**
 * Состояние редактора программы. null во внешнем состоянии = редактор закрыт.
 *
 * programId == null означает "создаём новую". При правке существующей id сохраняется,
 * чтобы запись перезаписалась, а не размножилась.
 */
data class ProgramEditorState(
    val programId: String? = null,
    val name: String = "",
    val segments: List<ProgramSegment> = emptyList(),
    /** Пояснение под заголовком: откуда взялся черновик (копия пресета и т.п.). */
    val sourceLabel: String? = null,
) {
    val isEditingExisting: Boolean get() = programId != null
    val totalDurationSeconds: Int get() = segments.sumOf { it.durationSeconds }
    val canSave: Boolean get() = name.isNotBlank() && segments.isNotEmpty()

    /** Программа для превью-графика: настоящая программа из черновика, в базу не пишется. */
    val preview: WorkoutProgram
        get() = WorkoutProgram(
            id = programId ?: "draft",
            name = name,
            segments = segments,
            isCustom = true,
        )
}

data class ProgramSelectionUiState(
    val predefinedTemplates: List<WorkoutProgramTemplate> = PredefinedTemplates.all,
    val customPrograms: List<WorkoutProgram> = emptyList(),
    val selectedTemplateId: String = PredefinedTemplates.all.first().id,
    val selectedDurationMinutes: Int = 15,
    val editor: ProgramEditorState? = null,
) {
    val selectedTemplate: WorkoutProgramTemplate
        get() = predefinedTemplates.find { it.id == selectedTemplateId } ?: predefinedTemplates.first()
}

@HiltViewModel
class ProgramSelectionViewModel @Inject constructor(
    private val workoutProgramRepository: WorkoutProgramRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgramSelectionUiState())
    val uiState: StateFlow<ProgramSelectionUiState> = _uiState.asStateFlow()

    private val range = ResistanceLevelRange.FALLBACK

    init {
        viewModelScope.launch {
            workoutProgramRepository.observeCustomPrograms().collect { list ->
                _uiState.update { it.copy(customPrograms = list) }
            }
        }
        // Инстансы шаблонов копятся при каждом запуске готовой программы и пользователю
        // не видны — подчищаем при заходе на экран программ.
        viewModelScope.launch { workoutProgramRepository.purgeOldTemplateInstances() }
    }

    // --- выбор готового шаблона ---

    fun selectTemplate(id: String) = _uiState.update { it.copy(selectedTemplateId = id) }

    fun selectDuration(minutes: Int) = _uiState.update { it.copy(selectedDurationMinutes = minutes) }

    /** Растягивает выбранный шаблон на выбранную длительность и сразу отдаёт готовую программу. */
    fun startSelectedTemplate(onCreated: (WorkoutProgram) -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val program = state.selectedTemplate.instantiate(state.selectedDurationMinutes * 60)
            workoutProgramRepository.saveCustomProgram(program)
            onCreated(program)
        }
    }

    // --- редактор ---

    fun openEditorForNew() {
        _uiState.update {
            it.copy(
                editor = ProgramEditorState(
                    name = "",
                    segments = listOf(ProgramSegment(60, range.min + 1)),
                ),
            )
        }
    }

    fun openEditorForProgram(program: WorkoutProgram) {
        _uiState.update {
            it.copy(
                editor = ProgramEditorState(
                    programId = program.id,
                    name = program.name,
                    segments = program.segments,
                ),
            )
        }
    }

    /**
     * Пресет не редактируется на месте — он копируется в новую свою программу.
     * Так путь правки ровно один, а заводские шаблоны можно обновлять с версиями
     * приложения, ничего не мигрируя.
     */
    fun openEditorFromTemplate() {
        val state = _uiState.value
        val instance = state.selectedTemplate.instantiate(state.selectedDurationMinutes * 60)
        _uiState.update {
            it.copy(
                editor = ProgramEditorState(
                    programId = null,
                    name = state.selectedTemplate.name,
                    segments = instance.segments,
                    sourceLabel = "копия пресета «${state.selectedTemplate.name}»",
                ),
            )
        }
    }

    fun closeEditor() = _uiState.update { it.copy(editor = null) }

    fun updateEditorName(name: String) = updateEditor { it.copy(name = name) }

    /** Новый сегмент — копия последнего: собирать профиль так вдвое быстрее. */
    fun addEditorSegment() = updateEditor { editor ->
        val template = editor.segments.lastOrNull() ?: ProgramSegment(60, range.min + 1)
        editor.copy(segments = editor.segments + template)
    }

    fun duplicateEditorSegment(index: Int) = updateEditor { editor ->
        val segment = editor.segments.getOrNull(index) ?: return@updateEditor editor
        editor.copy(segments = editor.segments.toMutableList().also { it.add(index + 1, segment) })
    }

    fun removeEditorSegment(index: Int) = updateEditor { editor ->
        if (index !in editor.segments.indices) return@updateEditor editor
        editor.copy(segments = editor.segments.toMutableList().also { it.removeAt(index) })
    }

    fun moveEditorSegment(index: Int, offset: Int) = updateEditor { editor ->
        val target = index + offset
        if (index !in editor.segments.indices || target !in editor.segments.indices) return@updateEditor editor
        editor.copy(
            segments = editor.segments.toMutableList().also {
                val segment = it.removeAt(index)
                it.add(target, segment)
            },
        )
    }

    fun adjustSegmentDuration(index: Int, deltaSteps: Int) = updateEditor { editor ->
        val segment = editor.segments.getOrNull(index) ?: return@updateEditor editor
        val updated = (segment.durationSeconds + deltaSteps * SEGMENT_DURATION_STEP_S)
            .coerceIn(SEGMENT_DURATION_MIN_S, SEGMENT_DURATION_MAX_S)
        editor.copy(
            segments = editor.segments.toMutableList().also {
                it[index] = segment.copy(durationSeconds = updated)
            },
        )
    }

    fun adjustSegmentResistance(index: Int, delta: Int) = updateEditor { editor ->
        val segment = editor.segments.getOrNull(index) ?: return@updateEditor editor
        val updated = (segment.targetResistance + delta).coerceIn(range.min, range.max)
        editor.copy(
            segments = editor.segments.toMutableList().also {
                it[index] = segment.copy(targetResistance = updated)
            },
        )
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (!editor.canSave) return
        viewModelScope.launch {
            workoutProgramRepository.saveCustomProgram(
                WorkoutProgram(
                    id = editor.programId ?: UUID.randomUUID().toString(),
                    name = editor.name.trim(),
                    segments = editor.segments,
                    isCustom = true,
                ),
            )
            _uiState.update { it.copy(editor = null) }
        }
    }

    fun deleteCustomProgram(id: String) {
        viewModelScope.launch { workoutProgramRepository.deleteCustomProgram(id) }
    }

    private inline fun updateEditor(transform: (ProgramEditorState) -> ProgramEditorState) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
    }
}