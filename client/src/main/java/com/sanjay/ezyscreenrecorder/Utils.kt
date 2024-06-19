package com.sanjay.ezyscreenrecorder

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.util.TypedValue
import android.view.WindowInsets
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File


// Утилитный класс Utils содержит различные утилиты для работы с уведомлениями и другими операциями.

object Utils {
    // Приватный метод openIntentForVideo создает Intent для открытия видеофайла с использованием FileProvider.

    private fun Context.openIntentForVideo(outFile: File): Intent {
        val intent = Intent(Intent.ACTION_VIEW).also {
            val fileUri = FileProvider.getUriForFile(this, "$packageName.file_provider", outFile)
            it.setDataAndType(fileUri, "video/*")
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Open With")
    }

    // Метод showVideoSavedNotification отображает уведомление о сохраненном видео и открывает файл при нажатии.

    fun Context.showVideoSavedNotification(file: File) {
        // Создание PendingIntent для открытия видеофайла.
        val pendingIntent = PendingIntentCompat.getActivity(
            this, 1, openIntentForVideo(file),
            PendingIntent.FLAG_ONE_SHOT, false
        )

        // Проверка наличия PendingIntent и показ уведомления.
        if (pendingIntent != null) {
            val notification = buildRecordingSavedNotification(
                pendingIntent,
                MainActivity.PARENT_DIRECTORY,
                MainActivity.DIRECTORY
            )
            val nm = ContextCompat.getSystemService(this, NotificationManager::class.java)
            nm?.notify(786, notification)
        }

        // Вывод сообщения о сохраненном файле с директорией сохранения.
        Toast.makeText(
            this,
            "Saved to ${MainActivity.PARENT_DIRECTORY} > ${MainActivity.DIRECTORY}",
            Toast.LENGTH_LONG
        ).show()
    }

    // Перечисление NotificationChannelType для различных типов каналов уведомлений.

    enum class NotificationChannelType(
        val channelId: String,
        val channelTitle: String,
        val importance: Int,
    ) {
        RecordingService(
            "Recording_Service",
            "Recording in-progress notification",
            NotificationManagerCompat.IMPORTANCE_MIN
        ),
        RecordingCompleted(
            "Recording_Completed",
            "Recording Preview notification",
            NotificationManagerCompat.IMPORTANCE_HIGH
        ),
    }


// Метод buildNotification используется для создания уведомления с заданными параметрами.
// Он создает канал уведомлений (для версий Android 8.0 и выше) и строит уведомление с указанными текстом, иконкой и действиями.

    fun Context.buildNotification(
        title: String,  // Заголовок уведомления.
        text: String,   // Текст уведомления.
        isOngoing: Boolean,  // Флаг, указывающий, что уведомление продолжительное.
        channelType: NotificationChannelType,  // Тип канала уведомлений.
        pendingIntent: PendingIntent? = null,  // PendingIntent для основного действия уведомления.
        action: String = "",  // Текст дополнительного действия уведомления.
        actionPendingIntent: PendingIntent? = null,  // PendingIntent для дополнительного действия уведомления.
    ): Notification {
        // Получение менеджера уведомлений.
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java)
        // Создание канала уведомлений на устройствах с Android 8.0 и выше.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(
                    channelType.channelId,
                    channelType.channelTitle,
                    channelType.importance
                )
            )
        }

        // Построение уведомления с указанными параметрами.
        return NotificationCompat.Builder(this, channelType.channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(isOngoing)
            .apply {
                // Добавление дополнительного действия, если указано.
                if (actionPendingIntent != null) {
                    addAction(R.drawable.baseline_stop_24, action, actionPendingIntent)
                }
                // Установка PendingIntent основного действия и установка автоматического закрытия уведомления при нажатии.
                if (pendingIntent != null) {
                    setContentIntent(pendingIntent)
                    setAutoCancel(true)
                }
            }
            .build()  // Возвращение построенного уведомления.
    }

// Метод buildInProgressRecordingNotification создает уведомление о текущем процессе записи с действием "Stop".
// Возвращает уведомление типа RecordingService.

    fun Context.buildInProgressRecordingNotification(
        actionPendingIntent: PendingIntent? = null,  // PendingIntent для действия "Stop".
    ): Notification {
        return buildNotification(
            title = "Ezy Recording",
            text = "Recording is in progress",
            isOngoing = true,
            channelType = NotificationChannelType.RecordingService,  // Канал для записи в процессе.
            pendingIntent = null,  // Основное действие нулевое (нет основного действия).
            action = "Stop",  // Текст для дополнительного действия "Stop".
            actionPendingIntent = actionPendingIntent,  // PendingIntent для дополнительного действия.
        )
    }
    // Метод buildRecordingSavedNotification создает уведомление о сохраненной записи с переданными параметрами.
    // Возвращает уведомление типа RecordingCompleted.
    fun Context.buildRecordingSavedNotification(
        pendingIntent: PendingIntent,
        parentDir: String,
        outDir: String,
    ): Notification {
        return buildNotification(
            title = "Recording saved",
            text = "Click to open the Recording",
            isOngoing = false,
            channelType = NotificationChannelType.RecordingCompleted,
            pendingIntent = pendingIntent,
        )
    }

    // Метод hasPermissions проверяет, есть ли у приложения указанное разрешение.
    fun Context.hasPermissions(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    // Метод screenDensity возвращает плотность экрана активности.
    fun Activity.screenDensity(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val floatDensity = windowManager.currentWindowMetrics.density
            val floatDpi = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                floatDensity,
                resources.displayMetrics
            )
            floatDpi.toInt()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayMetrics = DisplayMetrics()
            (display ?: windowManager.defaultDisplay).getMetrics(displayMetrics)
            displayMetrics.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.densityDpi
        }
    }
    // Метод screenRotation возвращает ориентацию экрана активности.
    fun Activity.screenRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: windowManager.defaultDisplay.rotation
        } else {
            windowManager.defaultDisplay.rotation
        }
    }
    // Метод windowSize возвращает размер окна активности.
    fun Activity.windowSize(): Size {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val windowInsets: WindowInsets = metrics.windowInsets
            val insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars()
                        or WindowInsets.Type.displayCutout()
            )

            val insetsWidth: Int = insets.right + insets.left
            val insetsHeight: Int = insets.top + insets.bottom

            val bounds: Rect = metrics.bounds
            Size(
                bounds.width() - insetsWidth,
                bounds.height() - insetsHeight
            )
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay?.getMetrics(displayMetrics)
            Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    private val validWidthSizes = listOf(
        4320,
        2160,
        1440,
        1080,
        1088,
        720,
        480,
    ).sortedDescending()

    private val validHeightSizes = listOf(
        7680,
        4096,
        3840,
        2560,
        2048,
        1280,
        720,
        704,
        640,
    ).sortedDescending()

    // Метод compatibleScreenSize определяет совместимый размер экрана на основе переданных ширины и высоты.
    fun compatibleScreenSize(width: Int, height: Int): Size {
        var outWidth = width
        var outHeight = height
        for (validWidth in validWidthSizes) {
            if (outWidth >= validWidth) {
                outWidth = validWidth
                break
            }
        }
        for (validHeight in validHeightSizes) {
            if (outHeight >= validHeight) {
                outHeight = validHeight
                break
            }
        }
        return Size(outWidth, outHeight)
    }
}