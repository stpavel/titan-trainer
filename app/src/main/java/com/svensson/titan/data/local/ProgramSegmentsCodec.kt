package com.svensson.titan.data.local

import com.svensson.titan.domain.model.ProgramSegment
import org.json.JSONArray
import org.json.JSONObject

/**
 * Сериализация сегментов программы в колонку workout_programs.segmentsRaw.
 *
 * Пишем JSON: [{"duration":60,"resistance":5}, ...]. Читаем и JSON, и старый
 * строчный формат "60:5;120:8" — поэтому менять схему и мигрировать базу не нужно.
 *
 * Разбор принципиально не бросает исключений: раньше кривая строка роняла
 * деструктуризацию внутри Flow и вместе с ней весь список программ. Теперь
 * битый сегмент просто отбрасывается, остальные читаются.
 */
object ProgramSegmentsCodec {

    private const val KEY_DURATION = "duration"
    private const val KEY_RESISTANCE = "resistance"

    fun encode(segments: List<ProgramSegment>): String {
        val array = JSONArray()
        segments.forEach { segment ->
            array.put(
                JSONObject()
                    .put(KEY_DURATION, segment.durationSeconds)
                    .put(KEY_RESISTANCE, segment.targetResistance),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<ProgramSegment> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) decodeJson(trimmed) else decodeLegacy(trimmed)
    }

    private fun decodeJson(raw: String): List<ProgramSegment> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val duration = obj.optInt(KEY_DURATION, 0)
            val resistance = obj.optInt(KEY_RESISTANCE, 0)
            if (duration <= 0) null else ProgramSegment(duration, resistance)
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Легаси-формат "durationSeconds:targetResistance;..." — читаем, но больше не пишем. */
    private fun decodeLegacy(raw: String): List<ProgramSegment> =
        raw.split(";").mapNotNull { part ->
            val fields = part.split(":")
            if (fields.size != 2) return@mapNotNull null
            val duration = fields[0].trim().toIntOrNull() ?: return@mapNotNull null
            val resistance = fields[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (duration <= 0) null else ProgramSegment(duration, resistance)
        }
}