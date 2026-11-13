package com.healassist.app.ui.calendar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.healassist.app.data.RehabSeed
import com.healassist.app.ui.SessionActivity
import com.healassist.app.ui.logs.LogsActivity
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
    var showMonthDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDate) {
        if (YearMonth.from(selectedDate) != displayedMonth) {
            displayedMonth = YearMonth.from(selectedDate)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HealAssist") },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Логирование") },
                                onClick = {
                                    showMenu = false
                                    context.startActivity(Intent(context, LogsActivity::class.java))
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // --- Заголовок с месяцем и стрелками ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Стрелка Назад (минус 7 дней)
                IconButton(onClick = {
                    val newDate = selectedDate.minusWeeks(1)
                    displayedMonth = YearMonth.from(newDate)
                    vm.selectDate(newDate)
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Назад")
                }

                // Кнопка выбора месяца
                Row(
                    modifier = Modifier.clickable { showMonthDialog = true }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale("ru"))
                            .replaceFirstChar { it.uppercase() } + " " + displayedMonth.year,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать месяц")
                }

                // Стрелка Вперед (плюс 7 дней)
                IconButton(onClick = {
                    val newDate = selectedDate.plusWeeks(1)
                    displayedMonth = YearMonth.from(newDate)
                    vm.selectDate(newDate)
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Вперед")
                }
            }

            // --- Лента дней ---
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
                                Text(RehabSeed.phaseName(item.exercise.orderIndex), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Подходы: ${item.exercise.targetSets} x ${item.exercise.targetReps}")

                                Button(
                                    onClick = {
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

    // --- Диалог выбора месяца и года ---
    if (showMonthDialog) {
        MonthYearPickerDialog(
            initialMonth = displayedMonth,
            onDismiss = { showMonthDialog = false },
            onDateSelected = { newMonth ->
                displayedMonth = newMonth
                vm.selectDate(newMonth.atDay(1))
                showMonthDialog = false
            }
        )
    }
}

@Composable
private fun MonthYearPickerDialog(
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onDateSelected: (YearMonth) -> Unit
) {
    var currentYear by remember { mutableStateOf(initialMonth.year) }
    var showYearMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Выбор года
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showYearMenu = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Год: $currentYear", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать год")
                    }
                    DropdownMenu(
                        expanded = showYearMenu,
                        onDismissRequest = { showYearMenu = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        (2020..2035).forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = { currentYear = year; showYearMenu = false }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Выберите месяц", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

                // Матрица месяцев
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(250.dp)
                ) {
                    val months = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
                    items(12) { index ->
                        val isCurrent = initialMonth.year == currentYear && initialMonth.monthValue == (index + 1)
                        val bgColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val textColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(bgColor)
                                .clickable { onDateSelected(YearMonth.of(currentYear, index + 1)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(months[index], color = textColor, fontSize = 14.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
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
                modifier = Modifier.width(48.dp).clip(CircleShape).clickable { onDateClick(date) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).take(2).replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
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