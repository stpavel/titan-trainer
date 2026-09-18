package com.svensson.titan.presentation.trainer

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svensson.titan.domain.model.TrainerReport
import com.svensson.titan.presentation.components.HudPanel
import java.time.format.DateTimeFormatter

private val PERIOD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerReportScreen(onBack: () -> Unit, viewModel: TrainerReportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val report = uiState.report

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ПЕРСОНАЛЬНЫЙ ТРЕНЕР") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (report != null) {
                        IconButton(onClick = { shareReport(context, report) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Поделиться отчётом")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (report != null) {
                Text(
                    "Отчёт за ${report.periodStart.format(PERIOD_FORMAT)} – ${report.periodEnd.format(PERIOD_FORMAT)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HudPanel(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    Text(report.reportText, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (!uiState.isLoading) {
                Text(
                    "Отчётов пока нет — нажмите кнопку ниже, чтобы получить первый разбор тренировок за последние 30 дней.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.errorMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            uiState.notice?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            "Отправляем данные тренеру, это может занять до минуты...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                Button(onClick = viewModel::generateReport, modifier = Modifier.fillMaxWidth()) {
                    Text(if (report == null) "Получить рекомендации" else "Обновить отчёт")
                }
            }
        }
    }
}

private fun shareReport(context: Context, report: TrainerReport) {
    val shareText = "Отчёт персонального тренера (${report.periodStart.format(PERIOD_FORMAT)} – " +
        "${report.periodEnd.format(PERIOD_FORMAT)})\n\n${report.reportText}"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Поделиться отчётом"))
}