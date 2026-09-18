package com.svensson.titan.util

import android.Manifest
import android.os.Build

object PermissionUtils {

    /** Разрешения, нужные для скана и подключения по BLE — зависят от версии Android. */
    fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** POST_NOTIFICATIONS нужен только с Android 13 (API 33) — для уведомления foreground-сервиса. */
    fun notificationPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
}
