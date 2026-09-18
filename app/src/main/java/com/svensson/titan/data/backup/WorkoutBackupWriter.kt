// app/src/main/java/com/svensson/titan/data/backup/WorkoutBackupWriter.kt
package com.svensson.titan.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.svensson.titan.domain.repository.WorkoutRepository
import com.svensson.titan.util.LogBus
import com.svensson.titan.util.WorkoutCsv
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Теневая копия истории в Downloads — папка переживает удаление приложения,
 * в отличие от базы и от /Android/data/<пакет>/. Перезаписываем один и тот же
 * файл после каждой сохранённой тренировки; год ежедневных занятий — около 40 КБ.
 *
 * Прочитать файл обратно после переустановки приложение само НЕ сможет: система
 * считает его принадлежащим прошлой установке. Поэтому восстановление идёт через
 * обычный "Импорт из CSV" с ручным выбором файла.
 */
@Singleton
class WorkoutBackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
) {

    suspend fun backup() = withContext(Dispatchers.IO) {
        // MediaStore.Downloads — с Android 10. На более старых нужен
        // WRITE_EXTERNAL_STORAGE с запросом в рантайме; ради них не городим.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        runCatching {
            val workouts = workoutRepository.observeAllWorkouts().first()
            if (workouts.isEmpty()) return@runCatching
            val uri = existingUri() ?: createUri() ?: return@runCatching
            // "wt" — с усечением: без t от прошлой, более длинной версии остался бы хвост.
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(UTF8_BOM)
                out.write(WorkoutCsv.toCsv(workouts).toByteArray(Charsets.UTF_8))
            }
            LogBus.d(TAG, "Резервная копия обновлена: ${workouts.size} тренировок")
        }.onFailure { error ->
            // Бекап фоновый: не записался — тренировку это ломать не должно.
            LogBus.w(TAG, "Резервная копия не записана: ${error.message}")
        }
    }

    private fun existingUri(): Uri? {
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            arrayOf(FILE_NAME, RELATIVE_PATH),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun createUri(): Uri? = context.contentResolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
        },
    )

    private companion object {
        const val FILE_NAME = "titan-backup.csv"
        // MediaStore хранит путь со слешем на конце — иначе запрос не найдёт файл.
        val RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/TitanTrainer/"
        const val TAG = "TitanBackup"
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}