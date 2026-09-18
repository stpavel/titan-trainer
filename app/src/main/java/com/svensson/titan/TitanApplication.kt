// app/src/main/java/com/svensson/titan/TitanApplication.kt
package com.svensson.titan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.svensson.titan.util.Constants
import com.svensson.titan.util.LogBus
import com.svensson.titan.worker.PenaltyCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TitanApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        LogBus.init(this)
        createNotificationChannel()
        // Штрафы за пропуск отключены вместе с курсами. cancelUniqueWork гасит задачу,
        // которая уже стоит в очереди на устройствах со старой версией приложения.
        WorkManager.getInstance(this).cancelUniqueWork(PenaltyCheckWorker.WORK_NAME)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.WORKOUT_NOTIFICATION_CHANNEL_ID,
            "Активная тренировка",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}