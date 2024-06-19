
package com.sanjay.ezyscreenrecorder

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sanjay.ezyscreenrecorder.Utils.buildInProgressRecordingNotification

// Класс InProgressRecordingNotificationService расширяет класс Service для работы с уведомлениями в процессе записи.

class InProgressRecordingNotificationService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // Метод onTaskRemoved вызывается при удалении задачи и останавливает уведомление.

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForegroundNotification()
    }

    // Метод onStartCommand обрабатывает различные действия, связанные с уведомлениями. 

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action ?: return START_NOT_STICKY
        when (action) {
            START_RECORDING -> {
                startForegroundNotification()
            }

            START_RECORDING_REALLY -> {
                updateNotificationWithStopButton()
            }

            STOP_RECORDING -> {
                stopForegroundNotification()
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        const val START_RECORDING = "Action:Start_Recording"
        const val STOP_RECORDING = "Action:Stop_Recording"
        const val START_RECORDING_REALLY = "Action:Start_Recording_Really"

        const val NOTIFICATION_ID = 1
    }

    // Метод для остановки уведомления.

    private fun stopForegroundNotification() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Метод для обновления уведомления с кнопкой остановки.

    private fun updateNotificationWithStopButton() {
        val stopBroadcastIntent = Intent(this, RecordingEventReceiver::class.java).also {
            it.action = RecordingEventReceiver.EVENT_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntentCompat.getBroadcast(
            this,
            0,
            stopBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false
        )
        val notification = buildInProgressRecordingNotification(stopPendingIntent)
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, notification)
    }

    // Метод для запуска уведомления в фоновом режиме.

    private fun startForegroundNotification() {

        val notification = buildInProgressRecordingNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else 0
        )
    }
}