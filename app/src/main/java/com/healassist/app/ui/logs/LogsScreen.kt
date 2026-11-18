package com.healassist.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healassist.app.data.PoseCounterDatabase
import com.healassist.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }

    var tab by remember { mutableIntStateOf(0) } // 0 = Все события, 1 = Краши
    var allLogs by remember { mutableStateOf("") }
    var crashLogs by remember { mutableStateOf("") }

    suspend fun refresh() {
        allLogs = withContext(Dispatchers.IO) { AppLogger.getLogs() }
        crashLogs = withContext(Dispatchers.IO) {
            try {
                val db = PoseCounterDatabase.get(context)
                val items = db.appLogDao().getLatest()
                if (items.isEmpty()) {
                    "Крашей пока нет."
                } else {
                    items.joinToString("\n\n") { log ->
                        val time = dateFormat.format(Date(log.timestamp))
                        buildString {
                            append("[$time] ${log.level}: ${log.message}")
                            log.stackTrace?.let { append("\n").append(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                "Ошибка чтения БД: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val displayText = if (tab == 0) allLogs else crashLogs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Логи" else "Краши") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("HealAssist logs", displayText))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    }) { Text("Копировать") }
                    TextButton(onClick = {
                        if (tab == 0) {
                            AppLogger.clearLogs(context)
                            allLogs = ""
                        } else {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    PoseCounterDatabase.get(context).appLogDao().clear()
                                } catch (_: Exception) {}
                                withContext(Dispatchers.Main) { crashLogs = "" }
                            }
                        }
                    }) { Text("Очистить", color = MaterialTheme.colorScheme.error) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Все события") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Краши") }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = displayText.ifEmpty { "Логов пока нет" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}