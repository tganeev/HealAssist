package com.healassist.app.ui.programs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healassist.app.data.PoseCounterDatabase
import com.healassist.app.data.ProgramInstaller
import com.healassist.app.data.RehabProgramEntity
import com.healassist.app.data.RehabProgramTemplate
import com.healassist.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ProgramsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PoseCounterDatabase.get(app)

    private val _installedProgram = MutableStateFlow<RehabProgramEntity?>(null)
    val installedProgram: StateFlow<RehabProgramEntity?> = _installedProgram

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _installedProgram.value = withContext(Dispatchers.IO) {
                db.rehabProgramDao().getActive().firstOrNull()
            }
        }
    }

    fun install(
        template: RehabProgramTemplate,
        startDate: LocalDate,
        onResult: (Boolean) -> Unit,
    ) {
        if (_isInstalling.value) return
        _isInstalling.value = true
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    ProgramInstaller.install(db, template, startDate)
                    AppLogger.log(
                        "PROGRAM",
                        "Installed ${template.id} from $startDate for ${template.durationDays} days"
                    )
                    true
                } catch (e: Exception) {
                    AppLogger.log("PROGRAM", "Install failed: ${e.message}")
                    false
                }
            }
            _installedProgram.value = withContext(Dispatchers.IO) {
                db.rehabProgramDao().getActive().firstOrNull()
            }
            _isInstalling.value = false
            onResult(success)
        }
    }
}
