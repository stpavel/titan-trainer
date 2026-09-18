package com.svensson.titan.domain.model

/** Характеристика 0x2ADA — тренажёр сам сообщает о смене своего состояния. */
class FitnessMachineStatus(val opCode: Int, val paramsHex: String) {

    val isControlLost: Boolean get() = opCode == OP_CONTROL_PERMISSION_LOST
    val isStoppedByUser: Boolean get() = opCode == OP_STOPPED_OR_PAUSED_BY_USER
    val isStartedByUser: Boolean get() = opCode == OP_STARTED_OR_RESUMED_BY_USER

    fun describe(): String = when (opCode) {
        OP_RESET -> "RESET"
        OP_STOPPED_OR_PAUSED_BY_USER -> "STOPPED_OR_PAUSED_BY_USER"
        OP_STOPPED_BY_SAFETY_KEY -> "STOPPED_BY_SAFETY_KEY"
        OP_STARTED_OR_RESUMED_BY_USER -> "STARTED_OR_RESUMED_BY_USER"
        OP_TARGET_RESISTANCE_CHANGED -> "TARGET_RESISTANCE_CHANGED"
        OP_TARGET_POWER_CHANGED -> "TARGET_POWER_CHANGED"
        OP_CONTROL_PERMISSION_LOST -> "CONTROL_PERMISSION_LOST"
        else -> "0x%02X".format(opCode)
    } + if (paramsHex.isEmpty()) "" else " [$paramsHex]"

    companion object {
        const val OP_RESET = 0x01
        const val OP_STOPPED_OR_PAUSED_BY_USER = 0x02
        const val OP_STOPPED_BY_SAFETY_KEY = 0x03
        const val OP_STARTED_OR_RESUMED_BY_USER = 0x04
        const val OP_TARGET_SPEED_CHANGED = 0x05
        const val OP_TARGET_INCLINE_CHANGED = 0x06
        const val OP_TARGET_RESISTANCE_CHANGED = 0x07
        const val OP_TARGET_POWER_CHANGED = 0x08
        const val OP_CONTROL_PERMISSION_LOST = 0xFF
    }
}