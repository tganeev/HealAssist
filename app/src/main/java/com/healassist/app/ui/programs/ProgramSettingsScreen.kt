package com.healassist.app.ui.programs

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healassist.app.data.DifficultyLevel
import com.healassist.app.data.ExerciseSettings
import com.healassist.app.data.ExerciseThresholds
import com.healassist.app.data.RehabProgramTemplate

private data class FloatThreshold(
    val label: String,
    val get: (ExerciseThresholds) -> Float,
    val set: (ExerciseThresholds, Float) -> ExerciseThresholds,
)

private val floatThresholds = listOf(
    FloatThreshold("Кулак — выпрямление", { it.extensionRatio }) { t, v -> t.copy(extensionRatio = v) },
    FloatThreshold("Веер — разведение", { it.fanOpenRatio }) { t, v -> t.copy(fanOpenRatio = v) },
    FloatThreshold("Веер — сведение", { it.fanCloseRatio }) { t, v -> t.copy(fanCloseRatio = v) },
    FloatThreshold("Когти — сжатие", { it.clawFistRatio }) { t, v -> t.copy(clawFistRatio = v) },
    FloatThreshold("Когти — выпрямление", { it.clawStraightRatio }) { t, v -> t.copy(clawStraightRatio = v) },
    FloatThreshold("Подушечки — касание", { it.thumbTouchDistance }) { t, v -> t.copy(thumbTouchDistance = v) },
    FloatThreshold("Пианино — касание", { it.pianoTouchDistance }) { t, v -> t.copy(pianoTouchDistance = v) },
    FloatThreshold("Стена — подъём пальца", { it.wallLiftDelta }) { t, v -> t.copy(wallLiftDelta = v) },
    FloatThreshold("Стена — подъём большого", { it.wallThumbDelta }) { t, v -> t.copy(wallThumbDelta = v) },
    FloatThreshold("Стена — отпускание", { it.wallReleaseDelta }) { t, v -> t.copy(wallReleaseDelta = v) },
    FloatThreshold("Стена — отпускание большого", { it.wallThumbReleaseDelta }) { t, v -> t.copy(wallThumbReleaseDelta = v) },
    FloatThreshold("Стена — другие пальцы", { it.wallDownDelta }) { t, v -> t.copy(wallDownDelta = v) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSettingsScreen(
    template: RehabProgramTemplate,
    onBack: () -> Unit,
    vm: ProgramSettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Настройки программы") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Изменения сохраняются автоматически",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SettingsSection(title = "Длительность программы") {
                        StepperField(
                            label = "Дней",
                            value = settings.durationDays,
                            min = 1,
                            max = 365,
                            step = 5,
                            onChange = vm::setDuration,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Новая длительность применится при установке программы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Text(
                        text = "Упражнения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(settings.exercises, key = { it.exerciseId }) { exercise ->
                    ExerciseSettingsCard(
                        exercise = exercise,
                        onSetsChange = { vm.setSets(exercise.exerciseId, it) },
                        onRepsChange = { vm.setReps(exercise.exerciseId, it) },
                        onRestChange = { vm.setRestSeconds(exercise.exerciseId, it) },
                        onDifficultyChange = { vm.setDifficulty(exercise.exerciseId, it) },
                        onCustomChange = { vm.setUseCustomThresholds(exercise.exerciseId, it) },
                        onThresholdChange = { update ->
                            vm.setThreshold(exercise.exerciseId, update)
                        },
                    )
                }
                item {
                    TextButton(onClick = { vm.reset() }) {
                        Text("Сбросить настройки")
                    }
                }
            }
            HorizontalDivider()
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Готово")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ExerciseSettingsCard(
    exercise: ExerciseSettings,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    onDifficultyChange: (DifficultyLevel) -> Unit,
    onCustomChange: (Boolean) -> Unit,
    onThresholdChange: ((ExerciseThresholds) -> ExerciseThresholds) -> Unit,
) {
    val thresholds = exercise.customThresholds
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepperField("Подходы", exercise.sets, 1, 20, onChange = onSetsChange)
            StepperField("Повторы в подходе", exercise.reps, 1, 50, onChange = onRepsChange)
            StepperField("Отдых, сек", exercise.restSeconds, 0, 300, step = 5, onChange = onRestChange)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Сложность",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyLevel.entries.forEach { level ->
                    FilterChip(
                        selected = exercise.difficulty == level,
                        onClick = { onDifficultyChange(level) },
                        label = { Text(level.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Свои пороги",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = exercise.useCustomThresholds,
                    onCheckedChange = onCustomChange,
                )
            }
            if (exercise.useCustomThresholds) {
                Spacer(modifier = Modifier.height(8.dp))
                StepperField(
                    label = "Удержание жеста, мс",
                    value = thresholds.gestureStableMs,
                    min = 50,
                    max = 2000,
                    step = 50,
                    onChange = { value -> onThresholdChange { it.copy(gestureStableMs = value) } },
                )
                StepperField(
                    label = "Кадры для стены",
                    value = thresholds.wallStableFrames,
                    min = 1,
                    max = 30,
                    onChange = { value -> onThresholdChange { it.copy(wallStableFrames = value) } },
                )
                floatThresholds.forEach { field ->
                    FloatField(
                        label = field.label,
                        value = field.get(thresholds),
                    ) { value -> onThresholdChange { current -> field.set(current, value) } }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Пороги определяются выбранным уровнем сложности",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepperField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onChange((value - step).coerceIn(min, max)) },
            modifier = Modifier.size(40.dp),
        ) { Text("−") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        TextButton(
            onClick = { onChange((value + step).coerceIn(min, max)) },
            modifier = Modifier.size(40.dp),
        ) { Text("+") }
    }
}

@Composable
private fun FloatField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var text by remember(value) { mutableStateOf(formatFloat(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.replace(',', '.').toFloatOrNull()?.let { parsed ->
                if (parsed.isFinite()) onValueChange(parsed)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatFloat(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString()
    else value.toString()
