package com.svensson.titan.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Лог тренировки — пишется в единственный файл на диске (не копится в памяти),
 * чтобы пережить падение/убийство процесса, ради которого этот лог обычно и
 * нужен: каждая строка сразу flush()-ится. Файл перезатирается заново при
 * старте приложения (init) и при старте каждой новой тренировки (reset) —
 * история старее нам не интересна. Экрана со списком строк больше нет,
 * файл целиком уходит через share() в системное окно "поделиться".
 */
object LogBus {

    private const val FILE_NAME = "titan_log.txt"
    private const val PREFS_NAME = "log_bus_prefs"
    private const val KEY_ENABLED = "enabled"

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    private var prefs: android.content.SharedPreferences? = null
    private var logFile: File? = null
    private var writer: FileWriter? = null

    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _enabled.value = p.getBoolean(KEY_ENABLED, true)

        val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        logFile = File(dir, FILE_NAME)
        reset()
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    /** Перезатирает файл лога — вызывается при старте приложения и при старте тренировки. */
    @Synchronized
    fun reset() {
        val file = logFile ?: return
        runCatching { writer?.close() }
        writer = runCatching { FileWriter(file, false) }.getOrNull()
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        append(tag, message + (throwable?.let { " (${it.message})" } ?: ""))
    }

    @Synchronized
    private fun append(tag: String, message: String) {
        if (!_enabled.value) return
        val line = "${timeFormat.format(System.currentTimeMillis())} $tag: $message\n"
        runCatching {
            writer?.write(line)
            writer?.flush()
        }
    }

    /** Отправляет текущий файл лога через системное окно "поделиться". */
    fun share(context: Context) {
        val file = logFile ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Отправить лог"))
    }
}