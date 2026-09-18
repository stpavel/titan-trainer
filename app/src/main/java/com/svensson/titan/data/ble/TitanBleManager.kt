package com.svensson.titan.data.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.svensson.titan.domain.model.BleConnectionState
import com.svensson.titan.domain.model.LiveWorkoutData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID
import com.svensson.titan.domain.model.ControlPointResult
import com.svensson.titan.domain.model.ResistanceLevelRange
import no.nordicsemi.android.ble.WriteRequest
import com.svensson.titan.util.LogBus
import com.svensson.titan.domain.model.FitnessMachineStatus

// app/src/main/java/com/svensson/titan/data/ble/TitanBleManager.kt

/**
 * Обёртка над Nordic BleManager: находит сервис FTMS (0x1826), подписывается на телеметрию
 * (Cross Trainer Data / Indoor Bike Data) и публикует значения через SharedFlow, а также
 * управляет тренажёром через FTMS Control Point (0x2AD9): запрос контроля (0x00) и
 * установка целевого уровня сопротивления (0x04).
 */
class TitanBleManager(context: Context) : BleManager(context), ConnectionObserver {

    private var crossTrainerDataChar: BluetoothGattCharacteristic? = null
    private var indoorBikeDataChar: BluetoothGattCharacteristic? = null
    private var controlPointChar: BluetoothGattCharacteristic? = null
	private var resistanceRangeChar: BluetoothGattCharacteristic? = null
		private var machineStatusChar: BluetoothGattCharacteristic? = null
	private var trainingStatusChar: BluetoothGattCharacteristic? = null
	private var featureChar: BluetoothGattCharacteristic? = null

	/** Op Code, на которые тренажёр ответил OP_CODE_NOT_SUPPORTED — больше не шлём. */
	private val unsupportedOps = mutableSetOf<Int>()

	private val _machineStatus = MutableSharedFlow<FitnessMachineStatus>(extraBufferCapacity = 16)
	val machineStatus: SharedFlow<FitnessMachineStatus> = _machineStatus.asSharedFlow()

	// StateFlow рядом с _controlPointResult
	private val _resistanceLevelRange = MutableStateFlow(ResistanceLevelRange.FALLBACK)
	val resistanceLevelRange: StateFlow<ResistanceLevelRange> = _resistanceLevelRange.asStateFlow()
    private val _liveData = MutableSharedFlow<LiveWorkoutData>(replay = 1, extraBufferCapacity = 16)
    val liveData: SharedFlow<LiveWorkoutData> = _liveData.asSharedFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _controlPointResult = MutableSharedFlow<ControlPointResult>(extraBufferCapacity = 4)
    val controlPointResult: SharedFlow<ControlPointResult> = _controlPointResult.asSharedFlow()


    private var probeCount = 0
	    /** Последний СКЛЕЕННЫЙ снимок телеметрии: FTMS-кадры бывают частичными. */
    private var lastTelemetry: LiveWorkoutData? = null
	    /** Троттлинг лога телеметрии: кадры идут раз в секунду и почти всегда одинаковые. */
    private var lastFrameLogMs = 0L
    private var lastLoggedResistance: Float? = null

    private var quietPendingOpCode: Int? = null
    private var ctFrames = 0
    private var ctMovingFrames = 0
	
	
    init {
        connectionObserver = this
    }

    override fun getGattCallback(): BleManagerGattCallback = TitanGattCallback()

    private inner class TitanGattCallback : BleManagerGattCallback() {

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val ftms = gatt.getService(FTMS_SERVICE_UUID) ?: return false
            crossTrainerDataChar = ftms.getCharacteristic(CROSS_TRAINER_DATA_UUID)
            indoorBikeDataChar = ftms.getCharacteristic(INDOOR_BIKE_DATA_UUID)
            controlPointChar = ftms.getCharacteristic(CONTROL_POINT_UUID)
			resistanceRangeChar = ftms.getCharacteristic(RESISTANCE_LEVEL_RANGE_UUID)
						machineStatusChar = ftms.getCharacteristic(MACHINE_STATUS_UUID)
			trainingStatusChar = ftms.getCharacteristic(TRAINING_STATUS_UUID)
			featureChar = ftms.getCharacteristic(FEATURE_UUID)
			unsupportedOps.clear()
            // Требуем хотя бы одну из характеристик телеметрии; Control Point опционален —
            // без него просто не будет работать авто-режим/ручная корректировка нагрузки.
            LogBus.d(
                TAG,
                "FTMS найден: crossTrainer=${crossTrainerDataChar != null}, indoorBike=${indoorBikeDataChar != null}, " +
                    "controlPoint=${controlPointChar != null}, range=${resistanceRangeChar != null} , status=${machineStatusChar != null}, training=${trainingStatusChar != null}",
            )
            return crossTrainerDataChar != null || indoorBikeDataChar != null
        }

        override fun initialize() {
            LogBus.d(TAG, "initialize(): сборка MARK-5")

            // Один источник телеметрии: Indoor Bike приоритетный, Cross Trainer — фолбэк.
            // Проверено по сырым кадрам: обе характеристики несут идентичные данные.
            val telemetryChar = indoorBikeDataChar ?: crossTrainerDataChar
            telemetryChar?.let { characteristic ->
                val isIndoorBike = characteristic.uuid == INDOOR_BIKE_DATA_UUID
                setNotificationCallback(characteristic).with { _, data ->
                    data.value?.let { bytes ->
                        val parsed = if (isIndoorBike) {
                            FtmsParser.parseIndoorBikeData(bytes)
                        } else {
                            FtmsParser.parseCrossTrainerData(bytes)
                        }
                        val merged = parsed.mergedWith(lastTelemetry)
                        lastTelemetry = merged
                        val emitted = _liveData.tryEmit(merged)

                        // tryEmit возвращает false и молча выбрасывает кадр, если подписчик
                        // не успевает. Это единственное, что тут стоит знать всегда.
                        if (!emitted) LogBus.w(TAG, "Кадр телеметрии потерян: буфер liveData полон")

                        val now = System.currentTimeMillis()
                        val resistanceChanged = merged.resistanceLevel != lastLoggedResistance
                        if (resistanceChanged || now - lastFrameLogMs >= FRAME_LOG_INTERVAL_MS) {
                            lastFrameLogMs = now
                            lastLoggedResistance = merged.resistanceLevel
                            LogBus.d(
                                TAG,
                                "RX кадр: скор=${merged.instantaneousSpeedKmh} каденс=${merged.instantaneousCadenceRpm} " +
                                    "сопр=${merged.resistanceLevel} мощн=${merged.instantaneousPowerWatts} " +
                                    "пульс=${merged.heartRateBpm} дист=${merged.totalDistanceMeters}",
                            )
                        }
                    }
                }
                enableNotifications(characteristic).enqueue()
            } ?: LogBus.w(TAG, "Характеристика телеметрии не найдена")

            // Cross Trainer подписываем ДОПОЛНИТЕЛЬНО — ради аппаратного счётчика шагов:
            // в Indoor Bike его нет, а дистанция консоли завышена примерно в 6 раз.
            // Скорость/дистанцию/сопротивление отсюда НЕ берём, чтобы не смешивать счётчики.
            if (telemetryChar !== crossTrainerDataChar) {
                crossTrainerDataChar?.let { characteristic ->
                    setNotificationCallback(characteristic).with { _, data ->
                        val bytes = data.value ?: return@with
                        val ct = FtmsParser.parseCrossTrainerData(bytes)

                        val spm = ct.stepsPerMinute ?: 0f
                        val logIt = if (spm > 0f) ctMovingFrames < 5 else ctFrames < 5
                        if (logIt) {
                            if (spm > 0f) ctMovingFrames++ else ctFrames++
                            LogBus.d(
                                TAG,
                                "RX CrossTrainer: темп=$spm шаги=${ct.strideCount} " +
                                    "[${bytes.joinToString(" ") { "%02X".format(it) }}]",
                            )
                        }

                        val base = lastTelemetry
                        if (base != null && (ct.strideCount != null || ct.stepsPerMinute != null)) {
                            val merged = base.copy(
                                strideCount = ct.strideCount ?: base.strideCount,
                                stepsPerMinute = ct.stepsPerMinute ?: base.stepsPerMinute,
                                receivedAtMs = System.currentTimeMillis(),
                            )
                            lastTelemetry = merged
                            _liveData.tryEmit(merged)
                        }
                    }
                    enableNotifications(characteristic).enqueue()
                }
            }
            resistanceRangeChar?.let { characteristic ->
                readCharacteristic(characteristic)
                    .with { _, data ->
                        data.value?.let { bytes ->
                            parseResistanceLevelRange(bytes)?.let { _resistanceLevelRange.value = it }
                        }
                    }
                    .fail { _, _ -> _resistanceLevelRange.value = ResistanceLevelRange.FALLBACK }
                    .enqueue()
            } ?: run {
                _resistanceLevelRange.value = ResistanceLevelRange.FALLBACK
            }
            featureChar?.let { characteristic ->
                readCharacteristic(characteristic)
                    .with { _, data ->
                        data.value?.let { b ->
                            LogBus.d(TAG, "0x2ACC Feature raw=${b.joinToString(" ") { "%02X".format(it) }}")
                        }
                    }
                    .enqueue()
            }

            // 0x2ADA — единственный штатный канал, по которому тренажёр сам сообщает,
            // что забрал управление обратно (0xFF) или что пользователь нажал Стоп (0x02).
            machineStatusChar?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    data.value?.let { bytes ->
                        if (bytes.isNotEmpty()) {
                            val status = FitnessMachineStatus(
                                opCode = bytes[0].toInt() and 0xFF,
                                paramsHex = bytes.drop(1).joinToString(" ") { "%02X".format(it) },
                            )
                            LogBus.d(TAG, "RX MachineStatus: ${status.describe()}")
                            _machineStatus.tryEmit(status)
                        }
                    }
                }
                enableNotifications(characteristic).enqueue()
            } ?: LogBus.w(TAG, "0x2ADA не найдена — статусы тренажёра недоступны")

            trainingStatusChar?.let { characteristic ->
                setNotificationCallback(characteristic).with { _, data ->
                    data.value?.let { bytes ->
                        if (bytes.size >= 2) LogBus.d(TAG, "RX TrainingStatus: 0x%02X".format(bytes[1].toInt() and 0xFF))
                    }
                }
                enableNotifications(characteristic).enqueue()
            }
            controlPointChar?.let { characteristic ->
                setIndicationCallback(characteristic).with { _, data ->
                    data.value?.let { bytes ->
                        parseControlPointResponse(bytes)?.let { _controlPointResult.tryEmit(it) }
                    }
                }
                enableIndications(characteristic)
                    .done { LogBus.d(TAG, "Индикации Control Point включены") }
                    .fail { _, status -> LogBus.w(TAG, "Индикации Control Point НЕ включены: status=$status") }
                    .enqueue()
            } ?: LogBus.w(TAG, "Control Point не найден — управление недоступно")
        }

        override fun onServicesInvalidated() {
            crossTrainerDataChar = null
            indoorBikeDataChar = null
            controlPointChar = null
			resistanceRangeChar = null
			machineStatusChar = null
			trainingStatusChar = null
			featureChar = null
			lastTelemetry = null
			ctFrames = 0
			// Список неподдерживаемых Op Code относится к конкретной сессии:
			// после переподключения проверяем заново.
			unsupportedOps.clear()
			ctMovingFrames = 0
			lastFrameLogMs = 0L
			lastLoggedResistance = null
        }
    }

    /** Op Code 0x00 — Request Control. Нужно вызвать один раз перед любыми командами управления. */
	fun requestControl(): WriteRequest? {
		val characteristic = controlPointChar
		if (characteristic == null) {
			LogBus.w(TAG, "requestControl: controlPointChar == null — тренажёр не готов/не поддерживает Control Point")
			return null
		}
		LogBus.d(TAG, "TX RequestControl (0x00)")
		return writeCharacteristic(characteristic, byteArrayOf(OP_REQUEST_CONTROL), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
	}

	fun setTargetResistanceLevel(level: Int, quiet: Boolean = false): WriteRequest? {
		val characteristic = controlPointChar
		if (characteristic == null) {
			LogBus.w(TAG, "setTargetResistanceLevel: controlPointChar == null")
			return null
		}
		val raw = level * 10
		val payload = byteArrayOf(
			OP_SET_TARGET_RESISTANCE_LEVEL,
			(raw and 0xFF).toByte(),
			((raw shr 8) and 0xFF).toByte(),
		)
		if (quiet) {
			quietPendingOpCode = OP_SET_TARGET_RESISTANCE_LEVEL.toInt()
		} else {
			LogBus.d(TAG, "TX SetTargetResistance level=$level raw=$raw payload=${payload.joinToString(" ") { "%02X".format(it) }}")
		}
		return writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
	}
	
	/** Op Code 0x07 — Start or Resume. Без этого многие тренажёры принимают Set Target
	 *  Resistance как валидную команду, но физически нагрузку не крутят, пока машина в Idle. */
	fun startOrResume(): WriteRequest? {
		val characteristic = controlPointChar
		if (characteristic == null) {
			LogBus.w(TAG, "startOrResume: controlPointChar == null")
			return null
		}
		LogBus.d(TAG, "TX StartOrResume (0x07)")
		return writeCharacteristic(characteristic, byteArrayOf(OP_START_OR_RESUME), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
	}

	
	/** Op Code 0x08 — Stop or Pause. TITAN его не поддерживает: после первого отказа не шлём. */
	fun stopOrPause(pause: Boolean = false): WriteRequest? {
		val characteristic = controlPointChar ?: return null
		if (OP_STOP_OR_PAUSE.toInt() in unsupportedOps) {
			LogBus.d(TAG, "StopOrPause пропущен — тренажёр объявил его неподдерживаемым")
			return null
		}
		val param: Byte = if (pause) 0x02 else 0x01
		LogBus.d(TAG, "TX StopOrPause (0x08) param=0x${"%02X".format(param)}")
		return writeCharacteristic(characteristic, byteArrayOf(OP_STOP_OR_PAUSE, param), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
	}

	private fun parseControlPointResponse(data: ByteArray): ControlPointResult? {
		if (data.size < 3 || (data[0].toInt() and 0xFF) != 0x80) {
			LogBus.w(TAG, "RX ControlPoint: неожиданный формат ${data.joinToString(" ") { "%02X".format(it) }}")
			return null
		}
		val result = ControlPointResult(requestOpCode = data[1].toInt() and 0xFF, resultCode = data[2].toInt() and 0xFF)
		if (result.resultCode == ControlPointResult.RESULT_OP_CODE_NOT_SUPPORTED) {
			unsupportedOps += result.requestOpCode
		}
		val wasQuiet = quietPendingOpCode == result.requestOpCode
		if (wasQuiet) quietPendingOpCode = null
		if (!wasQuiet || !result.isSuccess) {
			LogBus.d(TAG, "RX ControlPoint: opCode=0x${"%02X".format(result.requestOpCode)} result=${result.describe()}")
		}
		return result
	}

    // --- ConnectionObserver ---
    override fun onDeviceConnecting(device: BluetoothDevice) {
        LogBus.d(TAG, "Соединение: подключаемся")
        _connectionState.value = BleConnectionState.Connecting
    }
    override fun onDeviceConnected(device: BluetoothDevice) {
        LogBus.d(TAG, "Соединение: подключено, ищем сервисы")
        _connectionState.value = BleConnectionState.Connected
    }
    override fun onDeviceReady(device: BluetoothDevice) {
        LogBus.d(TAG, "Соединение: готово (инициализация завершена)")
        _connectionState.value = BleConnectionState.Ready
    }
    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        LogBus.d(TAG, "Соединение: отключаемся")
        _connectionState.value = BleConnectionState.Disconnected
    }
    override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
        LogBus.w(TAG, "Соединение: разорвано, reason=$reason")
        _connectionState.value = BleConnectionState.Disconnected
    }
    override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
        LogBus.w(TAG, "Соединение: не удалось подключиться, reason=$reason")
        _connectionState.value = BleConnectionState.Failed("connect failed, reason=$reason")
    }
	


	/** 0x2AD6 — Supported Resistance Level Range: min/max (SINT16, резолюция 0.1),
	 *  increment (UINT16, резолюция 0.1) — итого 6 байт, little-endian. */
	private fun parseResistanceLevelRange(data: ByteArray): ResistanceLevelRange? {
		if (data.size < 6) {
			LogBus.w(TAG, "0x2AD6: короткий пакет (${data.size} байт)")
			return null
		}
		fun s16(offset: Int): Int {
			val raw = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
			return raw.toShort().toInt()
		}
		val declaredMin = Math.round(s16(0) / 10f).coerceAtLeast(1)
		val declaredMax = Math.round(s16(2) / 10f)
		val step = Math.round(((data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)) / 10f).coerceAtLeast(1)
		val effectiveMax = declaredMax.coerceAtMost(HARDWARE_MAX_LEVEL)

		LogBus.d(
			TAG,
			"0x2AD6 raw=${data.joinToString(" ") { "%02X".format(it) }} → объявлено min=$declaredMin max=$declaredMax step=$step, используем max=$effectiveMax",
		)
		return ResistanceLevelRange(min = declaredMin, max = effectiveMax, step = step)
	}



	companion object {
		private const val TAG = "TitanBle"

		val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
		val CROSS_TRAINER_DATA_UUID: UUID = UUID.fromString("00002ace-0000-1000-8000-00805f9b34fb")
		val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
		val CONTROL_POINT_UUID: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
		val RESISTANCE_LEVEL_RANGE_UUID: UUID = UUID.fromString("00002ad6-0000-1000-8000-00805f9b34fb")
		// не private — нужны в BleRepositoryImpl, чтобы сматчить indication с командой
		const val OP_REQUEST_CONTROL: Byte = 0x00
		const val OP_SET_TARGET_RESISTANCE_LEVEL: Byte = 0x04
		const val OP_START_OR_RESUME: Byte = 0x07
		// Эмпирически по телеметрии: какое бы значение мы ни отправили, фактическая нагрузка
		// не поднимается выше 16, хотя 0x2AD6 объявляет диапазон шире. Правь, если железо сменится.
		private const val HARDWARE_MAX_LEVEL = 16

		val MACHINE_STATUS_UUID: UUID = UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb")
		val TRAINING_STATUS_UUID: UUID = UUID.fromString("00002ad3-0000-1000-8000-00805f9b34fb")
		val FEATURE_UUID: UUID = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
		const val OP_STOP_OR_PAUSE: Byte = 0x08		
		private const val FRAME_LOG_INTERVAL_MS = 30_000L
	}
}


	private fun LiveWorkoutData.mergedWith(prev: LiveWorkoutData?): LiveWorkoutData =
    if (prev == null) this else copy(
        instantaneousSpeedKmh = instantaneousSpeedKmh ?: prev.instantaneousSpeedKmh,
        averageSpeedKmh = averageSpeedKmh ?: prev.averageSpeedKmh,
        instantaneousCadenceRpm = instantaneousCadenceRpm ?: prev.instantaneousCadenceRpm,
        averageCadenceRpm = averageCadenceRpm ?: prev.averageCadenceRpm,
        totalDistanceMeters = totalDistanceMeters ?: prev.totalDistanceMeters,
        resistanceLevel = resistanceLevel ?: prev.resistanceLevel,
        instantaneousPowerWatts = instantaneousPowerWatts ?: prev.instantaneousPowerWatts,
        averagePowerWatts = averagePowerWatts ?: prev.averagePowerWatts,
        totalEnergyKcal = totalEnergyKcal ?: prev.totalEnergyKcal,
        heartRateBpm = heartRateBpm ?: prev.heartRateBpm,
		        strideCount = strideCount ?: prev.strideCount,
        stepsPerMinute = stepsPerMinute ?: prev.stepsPerMinute,
    )	