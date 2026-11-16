package com.healassist.app.ui.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle // Импорт для галочки
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healassist.app.data.ScheduledStatus
import com.healassist.app.ui.SessionActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ColorGreen = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedDate by vm.selectedDate.collectAsState()
    val exercises by vm.exercises.collectAsState()

    var displayedMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    // Слушатель возврата на экран (чтобы обновить статус "Выполнено")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedDate) {
        if (YearMonth.from(selectedDate) != displayedMonth) {
            displayedMonth = YearMonth.from(selectedDate)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("HealAssist") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Заголовок с месяцем и стрелками
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newDate = selectedDate.minusWeeks(1)
                    displayedMonth = YearMonth.from(newDate)
                    vm.selectDate(newDate)
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Назад")
                }

                Text(
                    text = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale("ru"))
                        .replaceFirstChar { it.uppercase() } + " " + displayedMonth.year,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    val newDate = selectedDate.plusWeeks(1)
                    displayedMonth = YearMonth.from(newDate)
                    vm.selectDate(newDate)
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Вперед")
                }
            }

            // Лента дней
            DaysStrip(
                month = displayedMonth,
                selectedDate = selectedDate,
                onDateClick = { vm.selectDate(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Список заданий
            if (exercises.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("На этот день упражнений нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exercises) { item ->
                        val isDone = item.scheduled.status == ScheduledStatus.DONE

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Утренний комплекс", // <-- ИЗМЕНЕНО НАЗВАНИЕ
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Подходы: ${item.exercise.targetSets} x ${item.exercise.targetReps}")

                                if (isDone) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = ColorGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Выполнено",
                                            color = ColorGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            val intent = Intent(context, SessionActivity::class.java).apply {
                                                putExtra(SessionActivity.EXTRA_EXERCISE_ID, item.exercise.id)
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text("Начать")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysStrip(
    month: YearMonth,
    selectedDate: LocalDate,
    onDateClick: (LocalDate) -> Unit
) {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { date ->
            val isSelected = date == selectedDate

            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(CircleShape)
                    .clickable { onDateClick(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).take(2)
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}