package com.svensson.titan.data.repository

import android.content.Context
import com.svensson.titan.data.ble.TitanBleManager
import com.svensson.titan.data.ble.TitanBleScanner
import com.svensson.titan.domain.model.BleConnectionState
import com.svensson.titan.domain.model.LiveWorkoutData
import com.svensson.titan.domain.repository.BleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import no.nordicsemi.android.ble.ktx.suspend
import javax.inject.Inject
import javax.inject.Singleton
import com.svensson.titan.domain.model.ControlPointResult
import com.svensson.titan.domain.model.ResistanceLevelRange
import com.svensson.titan.util.LogBus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.svensson.titan.domain.model.FitnessMachineStatus

@Singleton
class BleRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : BleRepository {

    private val bleManager = TitanBleManager(context)
    private val scanner = TitanBleScanner(context)

    override val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    override val liveData: SharedFlow<LiveWorkoutData> = bleManager.liveData
	override val resistanceLevelRange: StateFlow<ResistanceLevelRange> = bleManager.resistanceLevelRange
    override suspend fun connect(): Boolean {
        val device = scanner.scanForTitan() ?: return false
        return try {
            bleManager.connect(device)
                .retry(3, 200)
                .useAutoConnect(false)
                .timeout(15_000)
                .suspend()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun disconnect() {
        bleManager.disconnect().enqueue()
    }

	override val controlPointResult: SharedFlow<ControlPointResult> = bleManager.controlPointResult
	override val machineStatus: SharedFlow<FitnessMachineStatus> = bleManager.machineStatus

	override suspend fun requestControl(): Boolean {
		val request = bleManager.requestControl() ?: return false
		return try {
			coroutineScope {
				val indication = async {
					withTimeoutOrNull(CONTROL_POINT_TIMEOUT_MS) {
						bleManager.controlPointResult.first { it.requestOpCode == TitanBleManager.OP_REQUEST_CONTROL.toInt() }
					}
				}
				request.suspend()
				val result = indication.await()
				if (result == null) {
					LogBus.w(TAG, "requestControl: нет indication от тренажёра за ${CONTROL_POINT_TIMEOUT_MS}мс")
					false
				} else {
					LogBus.d(TAG, "requestControl: ${result.describe()}")
					result.isSuccess
				}
			}
		} catch (e: Exception) {
			LogBus.w(TAG, "requestControl: ошибка записи", e)
			false
		}
	}


	override suspend fun startOrResume(): Boolean {
		val request = bleManager.startOrResume() ?: return false
		return try {
			coroutineScope {
				val indication = async {
					withTimeoutOrNull(CONTROL_POINT_TIMEOUT_MS) {
						bleManager.controlPointResult.first { it.requestOpCode == TitanBleManager.OP_START_OR_RESUME.toInt() }
					}
				}
				request.suspend()
				val result = indication.await()
				if (result == null) {
					LogBus.w(TAG, "startOrResume: нет indication от тренажёра за ${CONTROL_POINT_TIMEOUT_MS}мс")
					false
				} else {
					LogBus.d(TAG, "startOrResume: ${result.describe()}")
					result.isSuccess
				}
			}
		} catch (e: Exception) {
			LogBus.w(TAG, "startOrResume: ошибка записи", e)
			false
		}
	}
	override suspend fun setTargetResistanceLevel(level: Int, quiet: Boolean): Boolean {
		val request = bleManager.setTargetResistanceLevel(level, quiet) ?: return false
		return try {
			coroutineScope {
				val indication = async {
					withTimeoutOrNull(CONTROL_POINT_TIMEOUT_MS) {
						bleManager.controlPointResult.first { it.requestOpCode == TitanBleManager.OP_SET_TARGET_RESISTANCE_LEVEL.toInt() }
					}
				}
				request.suspend()
				val result = indication.await()
				if (result == null) {
					LogBus.w(TAG, "setTargetResistanceLevel($level): нет indication от тренажёра за ${CONTROL_POINT_TIMEOUT_MS}мс")
					false
				} else {
					if (!quiet || !result.isSuccess) {
						LogBus.d(TAG, "setTargetResistanceLevel($level): ${result.describe()}")
					}
					result.isSuccess
				}
			}
		} catch (e: Exception) {
			LogBus.w(TAG, "setTargetResistanceLevel($level): ошибка записи", e)
			false
		}
	}
	

	override suspend fun stopOrPause(pause: Boolean): Boolean {
		val request = bleManager.stopOrPause(pause) ?: return false
		return try {
			coroutineScope {
				val indication = async {
					withTimeoutOrNull(CONTROL_POINT_TIMEOUT_MS) {
						bleManager.controlPointResult.first { it.requestOpCode == TitanBleManager.OP_STOP_OR_PAUSE.toInt() }
					}
				}
				request.suspend()
				val result = indication.await()
				if (result == null) {
					LogBus.w(TAG, "stopOrPause: нет indication от тренажёра за ${CONTROL_POINT_TIMEOUT_MS}мс")
					false
				} else {
					LogBus.d(TAG, "stopOrPause: ${result.describe()}")
					result.isSuccess
				}
			}
		} catch (e: Exception) {
			LogBus.w(TAG, "stopOrPause: ошибка записи", e)
			false
		}
	}

	override fun stopAndRelease() {
		bleManager.stopOrPause()?.enqueue()
	}
	
	
	companion object {
    private const val TAG = "BleRepository"
    private const val CONTROL_POINT_TIMEOUT_MS = 3000L
	}
}


