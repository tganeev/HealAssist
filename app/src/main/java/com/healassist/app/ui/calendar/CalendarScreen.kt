package com.healassist.app.ui.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healassist.app.data.RehabSeed
import com.healassist.app.ui.SessionActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedDate by vm.selectedDate.collectAsState()
    val exercises by vm.exercises.collectAsState()

    var displayedMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    // Синхронизируем месяц, если выбрали другой день
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

            // --- Заголовок с месяцем и стрелками ---
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

            // --- Горизонтальная лента дней месяца ---
            DaysStrip(
                month = displayedMonth,
                selectedDate = selectedDate,
                onDateClick = { vm.selectDate(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Список заданий ---
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
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = RehabSeed.phaseName(item.exercise.orderIndex),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Подходы: ${item.exercise.targetSets} x ${item.exercise.targetReps}")

                                Button(
                                    onClick = {
                                        // Запуск экрана с камерой
                                        val intent = Intent(context, SessionActivity::class.java)
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