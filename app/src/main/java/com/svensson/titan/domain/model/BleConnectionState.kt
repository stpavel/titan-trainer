package com.svensson.titan.domain.model

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Connecting : BleConnectionState
    data object Connected : BleConnectionState
    data object Ready : BleConnectionState
    data class Failed(val reason: String) : BleConnectionState
}
