package com.sanjay.ezyscreenrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Класс RecordingEventReceiver является Broadcast Receiver для обработки событий остановки записи.

class RecordingEventReceiver : BroadcastReceiver() {

    // Статическое свойство для определения события остановки записи.
    companion object {
        const val EVENT_STOP_RECORDING = "Event:Stop_Recording"
    }

    // Метод onReceive вызывается при получении события.

    override fun onReceive(context: Context, intent: Intent) {
        // Проверка действия в интенте.
        when (intent.action) {
            // Если событие - остановка записи, отправляем широковещательное сообщение для обработки в других компонентах.
            EVENT_STOP_RECORDING -> {
                context.sendBroadcast(Intent("${context.packageName}.RECORDING_EVENT").also {
                    it.putExtra("action", "STOP")
                })
            }
        }
    }
}