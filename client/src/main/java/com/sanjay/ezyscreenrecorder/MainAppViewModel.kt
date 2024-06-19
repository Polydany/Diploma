package com.sanjay.ezyscreenrecorder

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjection
import androidx.lifecycle.AndroidViewModel
import com.sanjay.ezyscreenrecorder.Utils.screenDensity
import com.sanjay.ezyscreenrecorder.Utils.screenRotation
import com.sanjay.ezyscreenrecorder.Utils.showVideoSavedNotification
import com.sanjay.ezyscreenrecorder.Utils.windowSize
import java.io.File

// Класс MainAppViewModel расширяет AndroidViewModel и предоставляет логику для записи экрана.

class MainAppViewModel(private val application: Application) : AndroidViewModel(application) {
    // Создание экземпляра ScreenRecorder для работы с записью экрана.
    private val screenRecorder = ScreenRecorder(application)

    // Метод для начала записи экрана.
    fun startRecording(file: File, activity: Activity, screenCaptureIntent: Intent, mediaProjection: MediaProjection): Boolean {
        val context = activity.applicationContext

        // Получение плотности экрана, ориентации и размера окна активности.
        val screenDensity = activity.screenDensity()
        val rotation = activity.screenRotation()
        val screenSize = activity.windowSize()

        // Подготовка к записи экрана с заданными параметрами.
        screenRecorder.prepare(screenDensity, rotation, screenSize)

        // Начало записи экрана.
        return screenRecorder.start(file, context, screenCaptureIntent, mediaProjection)
    }

    // Метод для остановки записи экрана и возврата файла с записанным видео.
    fun stopRecording(): File {
        return screenRecorder.stop()
    }

    // Метод для проверки состояния записи (идет ли запись экрана).
    fun isRecording() = screenRecorder.isRecording

    // Метод onCleared вызывается при удалении ViewModel.
    override fun onCleared() {
        super.onCleared()
        // Если идет запись, остановить и показать уведомление об успешном сохранении видео.
        if (isRecording()) {
            val outFile = stopRecording()
            application.showVideoSavedNotification(outFile)
        }
    }
}
