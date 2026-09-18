package com.svensson.titan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_programs")
data class WorkoutProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    /**
     * JSON-массив сегментов: [{"duration":60,"resistance":5}, ...] — см. ProgramSegmentsCodec.
     *
     * Имя колонки в базе осталось прежним (segmentsRaw): так схема не меняется,
     * миграция не нужна и история тренировок не сносится. Формат содержимого
     * определяет кодек, он же понимает старые строки "60:5;120:8".
     */
    @ColumnInfo(name = "segmentsRaw") val segmentsJson: String,
    val createdAtEpochMillis: Long,
    /** true — авто-сгенерирована из шаблона (Начать → выбор сложности/времени),
     *  false — собрана вручную через "Своя программа". Только false показывается
     *  в разделе "Мои программы". */
    val isTemplateInstance: Boolean = false,
)