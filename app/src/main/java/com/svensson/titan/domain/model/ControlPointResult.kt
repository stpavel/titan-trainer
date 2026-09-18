package com.svensson.titan.domain.model

data class ControlPointResult(val requestOpCode: Int, val resultCode: Int) {
    val isSuccess: Boolean get() = resultCode == RESULT_SUCCESS

    fun describe(): String = when (resultCode) {
        RESULT_SUCCESS -> "SUCCESS"
        RESULT_OP_CODE_NOT_SUPPORTED -> "OP_CODE_NOT_SUPPORTED"
        RESULT_INVALID_PARAMETER -> "INVALID_PARAMETER"
        RESULT_OPERATION_FAILED -> "OPERATION_FAILED"
        RESULT_CONTROL_NOT_PERMITTED -> "CONTROL_NOT_PERMITTED"
        else -> "UNKNOWN($resultCode)"
    }

    companion object {
        const val RESULT_SUCCESS = 0x01
        const val RESULT_OP_CODE_NOT_SUPPORTED = 0x02
        const val RESULT_INVALID_PARAMETER = 0x03
        const val RESULT_OPERATION_FAILED = 0x04
        const val RESULT_CONTROL_NOT_PERMITTED = 0x05
    }
}