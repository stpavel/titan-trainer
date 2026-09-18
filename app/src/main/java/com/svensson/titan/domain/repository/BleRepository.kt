package com.svensson.titan.domain.repository

import com.svensson.titan.domain.model.BleConnectionState
import com.svensson.titan.domain.model.LiveWorkoutData
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.svensson.titan.domain.model.ControlPointResult
import com.svensson.titan.domain.model.ResistanceLevelRange
import com.svensson.titan.domain.model.FitnessMachineStatus

interface BleRepository {
    val connectionState: StateFlow<BleConnectionState>
    val liveData: SharedFlow<LiveWorkoutData>
    val controlPointResult: SharedFlow<ControlPointResult>
	val resistanceLevelRange: StateFlow<ResistanceLevelRange>
    suspend fun connect(): Boolean
    fun disconnect()
    suspend fun requestControl(): Boolean
    suspend fun setTargetResistanceLevel(level: Int, quiet: Boolean = false): Boolean
    suspend fun startOrResume(): Boolean
    val machineStatus: SharedFlow<FitnessMachineStatus>
    suspend fun stopOrPause(pause: Boolean = false): Boolean
    /** Огонь-и-забыл: закрыть сессию на тренажёре, когда ждать ответ уже негде (onCleared). */
    fun stopAndRelease()
}