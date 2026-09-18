// app/src/main/java/com/svensson/titan/service/WorkoutForegroundService.kt
package com.svensson.titan.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.svensson.titan.MainActivity
import com.svensson.titan.domain.model.LiveWorkoutData
import com.svensson.titan.domain.repository.BleRepository
import com.svensson.titan.util.Constants
import com.svensson.titan.util.LogBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG_SERVICE = "TitanService"

/**
 * Держит процесс живым во время тренировки и показывает метрики в уведомлении.
 * Само BLE-соединение и подсчёт очков живут в BleRepository и ActiveWorkoutViewModel —
 * сервис их не дублирует, а просто наблюдает за тем же liveData.
 */
@AndroidEntryPoint
class WorkoutForegroundService : Service() {

    @Inject lateinit var bleRepository: BleRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var notifyJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Последний кадр телеметрии. Уведомление перерисовываем по таймеру, а не на каждый кадр. */
    @Volatile private var lastLiveData: LiveWorkoutData? = null

    /** Момент старта заезда по монотонным часам — для таймера в уведомлении. */
    @Volatile private var startedAtRealtimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        acquireWakeLock()

        collectJob = serviceScope.launch {
            bleRepository.liveData.collect { data -> lastLiveData = data }
        }
        notifyJob = serviceScope.launch {
            while (true) {
                updateNotification()
                delay(NOTIFICATION_REFRESH_MS)
            }
        }
    }

    private fun startAsForeground() {
        // На Android 14+ тип обязателен именно в вызове, одного манифеста уже мало.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Constants.WORKOUT_NOTIFICATION_ID, buildNotification(), type)
    }

    /** Foreground-сервис сам по себе не мешает системе усыпить CPU — держим его явно. */
    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_LOCK_MS)
            }
        }.onFailure { LogBus.w(TAG_SERVICE, "WakeLock не взят", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getLongExtra(EXTRA_STARTED_AT_REALTIME, 0L)
            ?.takeIf { it > 0L }
            ?.let { startedAtRealtimeMs = it }
        // START_NOT_STICKY: сервис нужен ровно на время тренировки. При START_STICKY система
        // поднимала его заново после закрытия приложения — отсюда вечное "Тренировка идёт".
        return START_NOT_STICKY
    }

    /** Пользователь смахнул приложение из недавних — гасим сервис вместе с уведомлением. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        notifyJob?.cancel()
        serviceScope.cancel()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val data = lastLiveData
        val contentText = buildString {
            if (startedAtRealtimeMs > 0L) {
                val elapsed = ((SystemClock.elapsedRealtime() - startedAtRealtimeMs) / 1000L).toInt()
                append("%d:%02d  ".format(elapsed / 60, elapsed % 60))
            }
            if (data == null) {
                append("подключение к тренажёру...")
            } else {
                data.instantaneousSpeedKmh?.let { append("%.1f км/ч  ".format(it)) }
                data.totalDistanceMeters?.let { append("$it м  ") }
                data.totalEnergyKcal?.let { append("$it ккал") }
            }
        }

        // ГЛАВНАЯ ПРАВКА. Без этих флагов система клала поверх задачи ВТОРУЮ копию
        // MainActivity с чистым навстеком: пользователь видел экран выбора программ,
        // а его тренировка продолжала идти в невидимой Activity под ним.
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, Constants.WORKOUT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Тренировка идёт")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.WORKOUT_NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val WAKE_LOCK_TAG = "TitanTrainer::workout"
        /** Страховка от вечного wake lock, если сервис как-то переживёт тренировку. */
        private const val MAX_WAKE_LOCK_MS = 4L * 60 * 60 * 1000
        private const val NOTIFICATION_REFRESH_MS = 1_000L
        const val EXTRA_STARTED_AT_REALTIME = "started_at_realtime"

        fun start(context: Context, startedAtRealtimeMs: Long = SystemClock.elapsedRealtime()) {
            val intent = Intent(context, WorkoutForegroundService::class.java)
                .putExtra(EXTRA_STARTED_AT_REALTIME, startedAtRealtimeMs)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }
    }
}