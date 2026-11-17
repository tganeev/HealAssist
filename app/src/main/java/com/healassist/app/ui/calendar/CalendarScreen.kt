package com.healassist.app.ui.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healassist.app.data.ScheduledStatus
import com.healassist.app.ui.SessionActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ColorGreen = Color(0xFF4CAF50)

/** Понедельник недели, содержащей [date]. */
private fun weekStartMonday(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedDate by vm.selectedDate.collectAsState()
    val dailyExercises by vm.dailyExercises.collectAsState()

    val weekStart = remember(selectedDate) { weekStartMonday(selectedDate) }
    val weekDays = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("HealAssist") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    vm.selectDate(selectedDate.minusWeeks(1))
                }) { Icon(Icons.Default.ChevronLeft, "Предыдущая неделя") }

                Text(
                    text = selectedDate.month
                        .getDisplayName(TextStyle.FULL, Locale("ru"))
                        .replaceFirstChar { it.uppercase() } + " " + selectedDate.year,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    vm.selectDate(selectedDate.plusWeeks(1))
                }) { Icon(Icons.Default.ChevronRight, "Следующая неделя") }
            }

            DaysStrip(days = weekDays, selectedDate = selectedDate, onDateClick = { vm.selectDate(it) })
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (dailyExercises.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("На этот день упражнений нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val doneCount = dailyExercises.count { it.status == ScheduledStatus.DONE }
                val totalCount = dailyExercises.size
                val isAllDone = doneCount == totalCount

                Column(modifier = Modifier.padding(16.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAllDone) {
                                val pendingIds = dailyExercises
                                    .filter { it.status == ScheduledStatus.PLANNED }
                                    .map { it.exerciseId }
                                if (pendingIds.isNotEmpty()) {
                                    // Фраза "Поставьте телефон..." нужна только если ПЕРВОЕ
                                    // упражнение (ex1) ещё не выполнено — т.е. присутствует
                                    // среди запланированных. Если ex1 уже сделан и сессия
                                    // начинается со 2-го — фразу не произносим.
                                    val isFirstExercisePending = pendingIds.contains("ex1")

                                    val intent = Intent(context, SessionActivity::class.java).apply {
                                        putExtra(SessionActivity.EXTRA_EXERCISE_ID, pendingIds.first())
                                        putExtra(SessionActivity.EXTRA_PENDING_LIST, ArrayList(pendingIds))
                                        putExtra(SessionActivity.EXTRA_IS_FIRST, isFirstExercisePending)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAllDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Утренний комплекс",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Выполнено $doneCount из $totalCount упражнений",
                                    fontSize = 14.sp,
                                    color = if (isAllDone) ColorGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isAllDone) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ColorGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Начать",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
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
    days: List<LocalDate>,
    selectedDate: LocalDate,
    onDateClick: (LocalDate) -> Unit
) {
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
                    text = date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale("ru"))
                        .take(2)
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}