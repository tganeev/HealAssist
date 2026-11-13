package com.healassist.app.ui.logs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healassist.app.data.AppLogEntity
import com.healassist.app.data.PoseCounterDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val _logs = MutableStateFlow<List<AppLogEntity>>(emptyList())
    val logs: StateFlow<List<AppLogEntity>> = _logs

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = PoseCounterDatabase.get(getApplication())
                _logs.value = db.appLogDao().getLatest()
            } catch (e: Exception) {
                // Если база упала, мы просто ничего не покажем (или можно добавить фейковый лог)
            }
        }
    }
}

class LogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LogsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(vm: LogsViewModel = viewModel()) {
    val logs by vm.logs.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Логирование") })
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Ошибок пока нет")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs) { log ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${log.level}: ${dateFormat.format(Date(log.timestamp))}",
                                color = if (log.level == "CRASH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = log.message, fontSize = 14.sp)
                            log.stackTrace?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = it.take(300),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}