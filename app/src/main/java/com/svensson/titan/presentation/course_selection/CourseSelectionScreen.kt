package com.svensson.titan.presentation.course_selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svensson.titan.domain.model.CourseType

@Composable
fun CourseSelectionScreen(
    onCourseStarted: () -> Unit,
    viewModel: CourseSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigateToWorkout.collect { onCourseStarted() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Выбери курс тренировок", style = MaterialTheme.typography.headlineSmall)
		        if (uiState.activeCourseName != null) {
            Text(
                "Сейчас активен курс «${uiState.activeCourseName}» (${uiState.activeCoursePoints} очков). " +
                    "Выбор нового курса начнёт прогресс заново.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        CourseType.entries.forEach { type ->
            CourseCard(
                type = type,
                selected = uiState.selectedType == type,
                onClick = { viewModel.selectCourse(type) },
            )
        }

        if (uiState.selectedType != null) {
            Spacer(Modifier.height(8.dp))
            StepperRow(
                label = "Тренировок в неделю",
                value = uiState.sessionsPerWeek,
                onValueChange = viewModel::updateSessionsPerWeek,
            )
            StepperRow(
                label = "Цель на месяц (тренировок)",
                value = uiState.monthlyGoalSessions,
                onValueChange = viewModel::updateMonthlyGoal,
            )
            Button(onClick = viewModel::startCourse, modifier = Modifier.fillMaxWidth()) {
                Text("Начать курс")
            }
        }
    }
}

@Composable
private fun CourseCard(type: CourseType, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(type.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Рекомендуется: ${type.recommendedSessionsPerWeek} трен./нед.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 1) }) { Text("–") }
            Text(value.toString(), modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onValueChange(value + 1) }) { Text("+") }
        }
    }
}
