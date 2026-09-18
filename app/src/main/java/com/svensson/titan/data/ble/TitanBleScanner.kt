package com.svensson.titan.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Nordic BLE library (:ble) не занимается сканированием — только соединением
 * и GATT-операциями (см. их же README). Поэтому скан делаем нативным Android
 * API, фильтруя по service UUID FTMS (0x1826): так находим TITAN-650, не
 * привязываясь к MAC-адресу или имени устройства.
 *
 * Для MVP берём первое найденное устройство — если появится сценарий
 * "несколько тренажёров рядом", здесь нужно будет заменить одноразовый
 * suspend-скан на Flow со списком результатов и экран выбора устройства.
 */
class TitanBleScanner(private val context: Context) {

    @SuppressLint("MissingPermission") // вызывающая сторона обязана запросить разрешения заранее
    suspend fun scanForTitan(timeoutMs: Long = 10_000L): BluetoothDevice? =
        suspendCancellableCoroutine { continuation ->
            val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
            val scanner = bluetoothManager?.adapter?.bluetoothLeScanner

            if (scanner == null) {
                continuation.resumeIfActive(null)
                return@suspendCancellableCoroutine
            }

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(TitanBleManager.FTMS_SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    continuation.resumeIfActive(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    continuation.resumeIfActive(null)
                }
            }

            continuation.invokeOnCancellation {
                runCatching { scanner.stopScan(callback) }
            }

            try {
                scanner.startScan(listOf(filter), settings, callback)
            } catch (e: SecurityException) {
                continuation.resumeIfActive(null)
                return@suspendCancellableCoroutine
            }

            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { scanner.stopScan(callback) }
                continuation.resumeIfActive(null)
            }, timeoutMs)
        }

    private fun <T> kotlinx.coroutines.CancellableContinuation<T>.resumeIfActive(value: T) {
        if (isActive) resume(value) { }
    }
}
