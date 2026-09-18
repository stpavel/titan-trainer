package com.svensson.titan.data.ble

import com.svensson.titan.domain.model.FtmsSource
import com.svensson.titan.domain.model.LiveWorkoutData

/** Читалка little-endian примитивов с курсором по ByteArray. */
private class ByteCursor(private val data: ByteArray) {
    var offset = 0
        private set

    fun u8(): Int = (data[offset].toInt() and 0xFF).also { offset += 1 }

    fun u16(): Int {
        val v = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        return v
    }

    fun s16(): Int = u16().toShort().toInt()

    fun u24(): Int {
        val v = (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16)
        offset += 3
        return v
    }
}

/** Hex-дамп сырого пакета — нужен только для диагностики аномалий нагрузки. */
private fun ByteArray.toHexDump(): String = joinToString(" ") { "%02X".format(it) }

/**
 * Парсинг двух FTMS-характеристик тренажёра: Cross Trainer Data (0x2ACE)
 * и Indoor Bike Data (0x2AD2). Разбор идёт строго по битам Flags —
 * это единственно верный способ по спецификации Bluetooth SIG, т.к.
 * набор полей в конкретном пакете зависит именно от них, а не фиксирован.
 */
object FtmsParser {

    /** UUID 0x2ACE — Cross Trainer Data. Flags: 24 бита (3 байта). */
    fun parseCrossTrainerData(data: ByteArray): LiveWorkoutData {
        require(data.size >= 3) { "Cross Trainer Data packet too short: ${data.size}" }

        val flags = (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16)
        fun bit(i: Int) = (flags shr i) and 1 == 1

        val cursor = ByteCursor(data)
        repeat(3) { cursor.u8() } // пропускаем сами байты флагов

        var speed: Float? = null
        var avgSpeed: Float? = null
        var distance: Int? = null
        var resistance: Float? = null
        var instPower: Int? = null
        var avgPower: Int? = null
        var totalEnergy: Int? = null
        var energyPerHour: Int? = null
        var energyPerMinute: Int? = null
        var heartRate: Int? = null
        var elapsedTime: Int? = null
        var remainingTime: Int? = null
		var stepsPerMinute: Float? = null
        var strideCount: Int? = null

        if (!bit(0)) speed = cursor.u16() / 100f // bit0=0 -> Instantaneous Speed присутствует
        if (bit(1)) avgSpeed = cursor.u16() / 100f
        if (bit(2)) distance = cursor.u24()
        if (bit(3)) { stepsPerMinute = cursor.u16().toFloat(); cursor.u16() } // Step Per Minute + Average Step Rate
         if (bit(4)) strideCount = cursor.u16() / 10 // экспонента -1 по спеке
        if (bit(5)) { cursor.u16(); cursor.u16() } // Elevation Gain +/- — не используем
        if (bit(6)) { cursor.s16(); cursor.s16() } // Inclination + Ramp Angle — не используем
        if (bit(7)) resistance = cursor.s16().toFloat()
        if (bit(8)) instPower = cursor.s16()
        if (bit(9)) avgPower = cursor.s16()
        if (bit(10)) {
            totalEnergy = cursor.u16()
            energyPerHour = cursor.u16()
            energyPerMinute = cursor.u8()
        }
        if (bit(11)) heartRate = cursor.u8()
        if (bit(12)) cursor.u8() // Metabolic Equivalent — не используем
        if (bit(13)) elapsedTime = cursor.u16()
        if (bit(14)) remainingTime = cursor.u16()

        return LiveWorkoutData(
            source = FtmsSource.CROSS_TRAINER,
            instantaneousSpeedKmh = speed,
            averageSpeedKmh = avgSpeed,
            totalDistanceMeters = distance,
            resistanceLevel = resistance,
            instantaneousPowerWatts = instPower,
            averagePowerWatts = avgPower,
            totalEnergyKcal = totalEnergy,
            energyPerHourKcal = energyPerHour,
            energyPerMinuteKcal = energyPerMinute,
            heartRateBpm = heartRate,
            elapsedTimeSeconds = elapsedTime,
            remainingTimeSeconds = remainingTime,
            flags = flags,
            rawHex = data.toHexDump(),
            strideCount = strideCount,
            stepsPerMinute = stepsPerMinute,
        )
    }

    /** UUID 0x2AD2 — Indoor Bike Data. Flags: 16 бит (2 байта). */
    fun parseIndoorBikeData(data: ByteArray): LiveWorkoutData {
        require(data.size >= 2) { "Indoor Bike Data packet too short: ${data.size}" }

        val flags = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        fun bit(i: Int) = (flags shr i) and 1 == 1

        val cursor = ByteCursor(data)
        repeat(2) { cursor.u8() }

        var speed: Float? = null
        var avgSpeed: Float? = null
        var cadence: Float? = null
        var avgCadence: Float? = null
        var distance: Int? = null
        var resistance: Float? = null
        var instPower: Int? = null
        var avgPower: Int? = null
        var totalEnergy: Int? = null
        var energyPerHour: Int? = null
        var energyPerMinute: Int? = null
        var heartRate: Int? = null
        var elapsedTime: Int? = null
        var remainingTime: Int? = null

        if (!bit(0)) speed = cursor.u16() / 100f
        if (bit(1)) avgSpeed = cursor.u16() / 100f
        if (bit(2)) cadence = cursor.u16() * 0.5f // резолюция 0.5
        if (bit(3)) avgCadence = cursor.u16() * 0.5f
        if (bit(4)) distance = cursor.u24()
        if (bit(5)) resistance = cursor.s16().toFloat()
        if (bit(6)) instPower = cursor.s16()
        if (bit(7)) avgPower = cursor.s16()
        if (bit(8)) {
            totalEnergy = cursor.u16()
            energyPerHour = cursor.u16()
            energyPerMinute = cursor.u8()
        }
        if (bit(9)) heartRate = cursor.u8()
        if (bit(10)) cursor.u8() // Metabolic Equivalent — не используем
        if (bit(11)) elapsedTime = cursor.u16()
        if (bit(12)) remainingTime = cursor.u16()

        return LiveWorkoutData(
            source = FtmsSource.INDOOR_BIKE,
            instantaneousSpeedKmh = speed,
            averageSpeedKmh = avgSpeed,
            instantaneousCadenceRpm = cadence,
            averageCadenceRpm = avgCadence,
            totalDistanceMeters = distance,
            resistanceLevel = resistance,
            instantaneousPowerWatts = instPower,
            averagePowerWatts = avgPower,
            totalEnergyKcal = totalEnergy,
            energyPerHourKcal = energyPerHour,
            energyPerMinuteKcal = energyPerMinute,
            heartRateBpm = heartRate,
            elapsedTimeSeconds = elapsedTime,
            remainingTimeSeconds = remainingTime,
            flags = flags,
            rawHex = data.toHexDump(),
        )
    }
}