package com.svensson.titan.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svensson.titan.domain.repository.UserSettingsRepository
import kotlin.math.roundToInt
import androidx.compose.material3.SwitchDefaults
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import com.svensson.titan.util.LogBus

/** Стартовая позиция степпера возраста ДО первого сохранения — не хранится и не считается
 *  ответом пользователя, только точка отсчёта, от которой он двигает значение сам. */
private const val AGE_DRAFT_SEED = 35
private const val MANUAL_HR_OVERRIDE_SEED = 120
private const val MIN_MANUAL_HR_BPM = 40
private const val MAX_MANUAL_HR_BPM = 220

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val logEnabled by LogBus.enabled.collectAsState()

    LaunchedEffect(uiState.exportUri) {
        val uri = uiState.exportUri ?: return@LaunchedEffect
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Экспорт истории тренировок"))
        viewModel.consumeExportUri()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (text != null) viewModel.importWorkouts(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("НАСТРОЙКИ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileSection(
                weightKg = uiState.weightKg,
                ageYears = uiState.ageYears,
                onWeightChange = viewModel::setWeightKg,
                onAgeChange = viewModel::setAgeYears,
            )

            HeartRateZonesSection(
                ageYears = uiState.ageYears,
                calculatedLower = uiState.calculatedLowerThreshold,
                calculatedUpper = uiState.calculatedUpperThreshold,
                lowerOverride = uiState.heartRateLowerOverride,
                upperOverride = uiState.heartRateUpperOverride,
                alertsEnabled = uiState.heartRateAlertsEnabled,
                onLowerOverrideChange = viewModel::setHeartRateLowerOverride,
                onUpperOverrideChange = viewModel::setHeartRateUpperOverride,
                onAlertsEnabledChange = viewModel::setHeartRateAlertsEnabled,
            )

            TrainerSection(
                enabled = uiState.trainerEnabled,
                apiKey = uiState.trainerApiKey,
                model = uiState.trainerModel,
                baseUrl = uiState.trainerBaseUrl,
                onEnabledChange = viewModel::setTrainerEnabled,
                onApiKeyChange = viewModel::setTrainerApiKey,
                onModelChange = viewModel::setTrainerModel,
                onBaseUrlChange = viewModel::setTrainerBaseUrl,
            )

            DataSection(
                importResultMessage = uiState.importResultMessage,
                onExport = viewModel::exportWorkouts,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDismissImportResult = viewModel::consumeImportResult,
            )
            LogSection(enabled = logEnabled, onEnabledChange = LogBus::setEnabled)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ProfileSection(
    weightKg: Int,
    ageYears: Int?,
    onWeightChange: (Int) -> Unit,
    onAgeChange: (Int?) -> Unit,
) {
    SectionCard(title = "Профиль") {
        Text(
            "Нужны для расчёта потраченной энергии, эквивалентной пешей дистанции и зон пульса.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Вес", style = MaterialTheme.typography.labelLarge)
        SteppedValueRow(
            value = weightKg,
            unitLabel = "кг",
            min = UserSettingsRepository.MIN_WEIGHT_KG,
            max = UserSettingsRepository.MAX_WEIGHT_KG,
            onChange = onWeightChange,
        )

        HorizontalDivider()

        Text("Возраст", style = MaterialTheme.typography.labelLarge)
        if (ageYears == null) {
            var draftAge by remember { mutableStateOf(AGE_DRAFT_SEED) }
            Text(
                "Не задан — зоны пульса не считаются, пока вы его не укажете и не сохраните.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SteppedValueRow(
                value = draftAge,
                unitLabel = "лет",
                min = UserSettingsRepository.MIN_AGE_YEARS,
                max = UserSettingsRepository.MAX_AGE_YEARS,
                onChange = { draftAge = it },
            )
            Button(onClick = { onAgeChange(draftAge) }) { Text("Сохранить возраст") }
        } else {
            SteppedValueRow(
                value = ageYears,
                unitLabel = "лет",
                min = UserSettingsRepository.MIN_AGE_YEARS,
                max = UserSettingsRepository.MAX_AGE_YEARS,
                onChange = onAgeChange,
            )
            TextButton(onClick = { onAgeChange(null) }) { Text("Сбросить возраст") }
        }
    }
}

@Composable
private fun HeartRateZonesSection(
    ageYears: Int?,
    calculatedLower: Int?,
    calculatedUpper: Int?,
    lowerOverride: Int?,
    upperOverride: Int?,
    alertsEnabled: Boolean,
    onLowerOverrideChange: (Int?) -> Unit,
    onUpperOverrideChange: (Int?) -> Unit,
    onAlertsEnabledChange: (Boolean) -> Unit,
) {
    SectionCard(title = "Пульс") {
        if (ageYears == null) {
            Text(
                "Укажите возраст в разделе «Профиль», чтобы зоны пульса рассчитались автоматически.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val moderateUpper = UserSettingsRepository.moderateUpperBound(ageYears)
            Text(
                "Максимум ≈ 220 − возраст = ${UserSettingsRepository.maxHeartRate(ageYears)} уд/мин",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Умеренная зона: $calculatedLower–$moderateUpper уд/мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Высокая зона: $moderateUpper–$calculatedUpper уд/мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        Text("Ручное переопределение порогов", style = MaterialTheme.typography.labelLarge)
        OverrideRow(label = "Нижний порог", value = lowerOverride, calculatedFallback = calculatedLower, onChange = onLowerOverrideChange)
        OverrideRow(label = "Верхний порог", value = upperOverride, calculatedFallback = calculatedUpper, onChange = onUpperOverrideChange)

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Звуковое предупреждение", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Сигнал, когда пульс выходит за пороги во время тренировки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = alertsEnabled,
                onCheckedChange = onAlertsEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                ),
            )
        }

        HorizontalDivider()

        Text(
            "Формула American Heart Association (heart.org): максимальный пульс ≈ 220 − возраст; " +
                "умеренная зона — 50–70% от максимума, высокая — 70–85%. Это усреднённый общий ориентир, " +
                "а не медицинская норма — если вы принимаете препараты, влияющие на сердце, " +
                "проконсультируйтесь с врачом перед тем как ориентироваться на эти зоны.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrainerSection(
    enabled: Boolean,
    apiKey: String,
    model: String,
    baseUrl: String,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    SectionCard(title = "Персональный тренер") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Включить", style = MaterialTheme.typography.labelLarge)
                Text(
                    "При запросе приложение отправляет историю тренировок и профиль (возраст/вес) " +
                        "в выбранную нейросеть и получает профессиональный AI разбор с рекомендациями. Данные уходят на сторонний сервер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                ),
            )
        }

        if (enabled) {
            HorizontalDivider()

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API-ключ") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text("Модель") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL (для другого провайдера)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "По умолчанию — DeepSeek. Можно указать любой OpenAI-совместимый API (OpenAI, OpenRouter, свой сервер).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataSection(
    importResultMessage: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissImportResult: () -> Unit,
) {
    SectionCard(title = "Данные") {
        Text(
            "История тренировок хранится только на этом телефоне. Экспорт делает резервную копию файлом CSV.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Копия базы обновляется сама после каждой тренировки: Downloads/TitanTrainer/titan-backup.csv. " +
                "Файл остаётся на телефоне, даже если удалить приложение — после переустановки нажмите " +
                "«Импорт из CSV» и выберите его.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onExport) { Text("Экспорт в CSV") }
            OutlinedButton(onClick = onImport) { Text("Импорт из CSV") }
        }
        importResultMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onDismissImportResult) { Text("Скрыть") }
        }
    }
}

@Composable
private fun OverrideRow(
    label: String,
    value: Int?,
    calculatedFallback: Int?,
    onChange: (Int?) -> Unit,
) {
    val isManual = value != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                if (isManual) {
                    onChange(null)
                } else {
                    onChange(calculatedFallback ?: MANUAL_HR_OVERRIDE_SEED)
                }
            }) {
                Text(if (isManual) "СБРОСИТЬ НА АВТО" else "ЗАДАТЬ ВРУЧНУЮ")
            }
        }
        if (value != null) {
            SteppedValueRow(
                value = value,
                unitLabel = "уд/мин",
                min = MIN_MANUAL_HR_BPM,
                max = MAX_MANUAL_HR_BPM,
                onChange = { onChange(it) },
            )
        }
    }
}

@Composable
private fun SteppedValueRow(
    value: Int,
    unitLabel: String,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("–") }
            Text("$value $unitLabel", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun LogSection(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    SectionCard(title = "Лог") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Писать лог", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Подробный лог тренировки для отладки — отправляется кнопкой «ЛОГ» на экране тренировки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                ),
            )
        }
    }
}