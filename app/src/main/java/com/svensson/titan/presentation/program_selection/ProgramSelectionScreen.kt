package com.svensson.titan.presentation.program_selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svensson.titan.domain.model.ProgramSegment
import com.svensson.titan.domain.model.WorkoutProgram
import com.svensson.titan.presentation.components.HudPanel
import com.svensson.titan.presentation.components.ProgramProfileChart

private val DURATION_OPTIONS_MIN = listOf(10, 15, 20, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSelectionScreen(
    onProgramSelected: (WorkoutProgram) -> Unit,
    onSkip: () -> Unit,
    viewModel: ProgramSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.editor

    val previewProgram = remember(uiState.selectedTemplateId, uiState.selectedDurationMinutes) {
        uiState.selectedTemplate.instantiate(uiState.selectedDurationMinutes * 60)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editor == null -> "ПРОГРАММЫ ТРЕНИРОВОК"
                            editor.isEditingExisting -> "ПРАВКА ПРОГРАММЫ"
                            else -> "НОВАЯ ПРОГРАММА"
                        },
                    )
                },
            )
        },
        bottomBar = {
            HudPanel(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                if (editor == null) {
                    Button(
                        onClick = { viewModel.startSelectedTemplate(onProgramSelected) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("НАЧАТЬ (${uiState.selectedDurationMinutes} МИН)")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::closeEditor,
                            modifier = Modifier.weight(1f),
                        ) { Text("ОТМЕНА") }
                        Button(
                            onClick = viewModel::saveEditor,
                            enabled = editor.canSave,
                            modifier = Modifier.weight(1f),
                        ) { Text("СОХРАНИТЬ") }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (editor != null) {
            EditorContent(
                editor = editor,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                onNameChange = viewModel::updateEditorName,
                onAddSegment = viewModel::addEditorSegment,
                onDuplicate = viewModel::duplicateEditorSegment,
                onRemove = viewModel::removeEditorSegment,
                onMove = viewModel::moveEditorSegment,
                onDuration = viewModel::adjustSegmentDuration,
                onResistance = viewModel::adjustSegmentResistance,
            )
        } else {
            SelectionContent(
                uiState = uiState,
                previewProgram = previewProgram,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                onSkip = onSkip,
                onProgramSelected = onProgramSelected,
                onSelectTemplate = viewModel::selectTemplate,
                onSelectDuration = viewModel::selectDuration,
                onCopyTemplate = viewModel::openEditorFromTemplate,
                onCreateNew = viewModel::openEditorForNew,
                onEditProgram = viewModel::openEditorForProgram,
                onDeleteProgram = viewModel::deleteCustomProgram,
            )
        }
    }
}

@Composable
private fun SelectionContent(
    uiState: ProgramSelectionUiState,
    previewProgram: WorkoutProgram,
    modifier: Modifier,
    onSkip: () -> Unit,
    onProgramSelected: (WorkoutProgram) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onCopyTemplate: () -> Unit,
    onCreateNew: () -> Unit,
    onEditProgram: (WorkoutProgram) -> Unit,
    onDeleteProgram: (String) -> Unit,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Тренировка без программы (свободный заезд) →")
            }
        }

        item { SectionLabel("ПРОГРАММА") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(uiState.predefinedTemplates, key = { it.id }) { template ->
                    FilterChip(
                        selected = template.id == uiState.selectedTemplateId,
                        onClick = { onSelectTemplate(template.id) },
                        label = { Text(template.name.uppercase()) },
                    )
                }
            }
        }

        item {
            HudPanel(modifier = Modifier.fillMaxWidth()) {
                ProgramProfileChart(program = previewProgram, progressFraction = 0f)
            }
        }

        item { SectionLabel("ДЛИТЕЛЬНОСТЬ, МИН") }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DURATION_OPTIONS_MIN.forEach { minutes ->
                    FilterChip(
                        selected = uiState.selectedDurationMinutes == minutes,
                        onClick = { onSelectDuration(minutes) },
                        label = { Text("$minutes") },
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopyTemplate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(" КОПИЯ")
                }
                OutlinedButton(onClick = onCreateNew, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" С НУЛЯ")
                }
            }
        }

        if (uiState.customPrograms.isNotEmpty()) {
            item { Text("МОИ ПРОГРАММЫ", style = MaterialTheme.typography.titleMedium) }
            items(uiState.customPrograms, key = { it.id }) { program ->
                ProgramCard(
                    program = program,
                    onClick = { onProgramSelected(program) },
                    onEdit = { onEditProgram(program) },
                    onDelete = { onDeleteProgram(program.id) },
                )
            }
        }
    }
}

@Composable
private fun EditorContent(
    editor: ProgramEditorState,
    modifier: Modifier,
    onNameChange: (String) -> Unit,
    onAddSegment: () -> Unit,
    onDuplicate: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDuration: (Int, Int) -> Unit,
    onResistance: (Int, Int) -> Unit,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        editor.sourceLabel?.let { label ->
            item {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            HudPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgramProfileChart(program = editor.preview, progressFraction = 0f)
                    Text(
                        "${editor.segments.size} СЕГМЕНТОВ • ${formatDuration(editor.totalDurationSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(editor.segments) { index, segment ->
            SegmentEditorCard(
                index = index,
                segment = segment,
                isFirst = index == 0,
                isLast = index == editor.segments.lastIndex,
                onDuration = { delta -> onDuration(index, delta) },
                onResistance = { delta -> onResistance(index, delta) },
                onMoveUp = { onMove(index, -1) },
                onMoveDown = { onMove(index, 1) },
                onDuplicate = { onDuplicate(index) },
                onRemove = { onRemove(index) },
            )
        }

        item {
            OutlinedButton(onClick = onAddSegment, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  ДОБАВИТЬ СЕГМЕНТ")
            }
        }
    }
}

@Composable
private fun SegmentEditorCard(
    index: Int,
    segment: ProgramSegment,
    isFirst: Boolean,
    isLast: Boolean,
    onDuration: (Int) -> Unit,
    onResistance: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    HudPanel(modifier = Modifier.fillMaxWidth(), contentPadding = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Выше")
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Ниже")
                    }
                    TextButton(onClick = onDuplicate) { Text("×2") }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить сегмент")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stepper(
                    label = "ВРЕМЯ",
                    value = formatDuration(segment.durationSeconds),
                    onMinus = { onDuration(-1) },
                    onPlus = { onDuration(1) },
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = "НАГРУЗКА",
                    value = "${segment.targetResistance}",
                    onMinus = { onResistance(-1) },
                    onPlus = { onResistance(1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(4.dp)) { Text("–") }
            Text(value, style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(4.dp)) { Text("+") }
        }
    }
}

@Composable
private fun ProgramCard(
    program: WorkoutProgram,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    HudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Править программу")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить программу")
                }
            }
            ProgramProfileChart(program = program, progressFraction = 0f)
            Text(
                "${program.segments.size} СЕГМЕНТОВ • ${formatDuration(program.totalDurationSeconds)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}