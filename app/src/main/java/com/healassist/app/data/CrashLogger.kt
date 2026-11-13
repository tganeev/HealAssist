package com.healassist.app.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.io.StringWriter

object CrashLogger {
    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                // Сохраняем в БД (синхронно, так как приложение падает)
                runBlocking {
                    PoseCounterDatabase.get(context).appLogDao().insert(
                        AppLogEntity(
                            level = "CRASH",
                            message = throwable.message ?: throwable.javaClass.simpleName,
                            stackTrace = stackTrace
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}