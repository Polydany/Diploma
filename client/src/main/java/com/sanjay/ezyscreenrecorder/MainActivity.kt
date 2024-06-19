package com.sanjay.ezyscreenrecorder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sanjay.ezyscreenrecorder.Utils.hasPermissions
import com.sanjay.ezyscreenrecorder.Utils.showVideoSavedNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    // Константа, устанавливающая обратный отсчет перед началом записи.
    companion object {
        const val COUNT_DOWN = 3
        // Переменная, указывающая родительскую директорию для сохранения записей.
        val PARENT_DIRECTORY: String = Environment.DIRECTORY_DOCUMENTS
        // Константа, указывающая поддиректорию для сохранения записей.
        const val DIRECTORY = "Ezy Recordings"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var countText: TextView

    private val mainAppViewModel: MainAppViewModel by viewModels()

    // Свойство, которое проверяет, предоставлено ли разрешение на отправку уведомлений.
    private val isNotificationPermissionGranted: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    // Свойство, которое проверяет, предоставлено ли разрешение на запись аудио.
    private val isRecordAudioPermissionGranted: Boolean
        get() = hasPermissions(Manifest.permission.RECORD_AUDIO)

    // Переменная, которая используется для запроса разрешений у пользователя.
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (Manifest.permission.RECORD_AUDIO in permissions) {
                if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                    startRecording()
                }
            }
            if (Manifest.permission.POST_NOTIFICATIONS in permissions) {
                if (permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
                    startButton.isEnabled = true
                    stopButton.isEnabled = false
                }
            }
        }

    private fun startForegroundService() {
        // Создание и запуск фонового сервиса для отображения уведомления о записи.
        val serviceIntent = Intent(InProgressRecordingNotificationService.START_RECORDING).also {
            it.setClass(this, InProgressRecordingNotificationService::class.java)
        }
        ActivityCompat.startForegroundService(this, serviceIntent)
    }

    private fun startForegroundServiceReally() {
        // Создание и запуск фонового сервиса для записи реального экрана.
        val serviceIntent =
            Intent(InProgressRecordingNotificationService.START_RECORDING_REALLY).also {
                it.setClass(this, InProgressRecordingNotificationService::class.java)
            }
        ActivityCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopForegroundService() {
        // Остановка фонового сервиса.
        val serviceIntent = Intent(InProgressRecordingNotificationService.STOP_RECORDING).also {
            it.setClass(this, InProgressRecordingNotificationService::class.java)
        }
        ActivityCompat.startForegroundService(this, serviceIntent)
    }
    private fun uploadVideo(file: File) {
        val requestFile = RequestBody.create("video/mp4".toMediaTypeOrNull(), file)
        val body = MultipartBody.Part.createFormData("video", file.name, requestFile)

        val call = RetrofitClient.instance.uploadVideo(body)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Log.d("Upload", "Success")
                } else {
                    Log.d("Upload", "Failed: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("Upload", "Error: ${t.message}")
            }
        })
    }

    // Метод для обработки результата запроса захвата экрана с использованием активности для результата.
    private val requestScreenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultCode = result.resultCode
            if (resultCode != RESULT_OK) {
                startButton.isEnabled = true
                return@registerForActivityResult
            }
            val data = result.data ?: return@registerForActivityResult
            startForegroundService()
            // Запуск таймера обратного отсчета перед записью экрана.
            lifecycleScope.launch {
                repeat(COUNT_DOWN) {
                    countText.text = (COUNT_DOWN - it).toString()
                    delay(1000)
                }
                countText.text = ""
                val lMediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                val fileName = String.format(
                    "Recording_%s.mp4",
                    SimpleDateFormat("dd_MM_yyyy_hh_mm_ss_a", Locale.ENGLISH).format(
                        Calendar.getInstance().time
                    )
                )
                val folder =
                    File(
                        Environment.getExternalStoragePublicDirectory(PARENT_DIRECTORY),
                        DIRECTORY
                    )
                if (!folder.exists()) {
                    folder.mkdir()
                }
                val file = File(folder, fileName)
                uploadVideo(file)
                val screenCaptureIntent = Intent(/* ваш код для создания Intent */)
                val isStarted = mainAppViewModel.startRecording(file, this@MainActivity, screenCaptureIntent, lMediaProjection)
                // Если запись успешно начата, активируем кнопку остановки и запускаем службу в первом плане.
                if (isStarted) {
                    stopButton.isEnabled = true
                    startForegroundServiceReally()
                } else {
                    startButton.isEnabled = true
                    stopForegroundService()
                }
            }
        }

    // BroadcastReceiver для обработки события остановки записи.

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("action") == "STOP") {
                stopRecording()
            }
        }
    }

    // Метод onCreate инициализирует UI и проверяет разрешения.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startButton = findViewById(R.id.start_record_button)
        stopButton = findViewById(R.id.stop_record_button)
        countText = findViewById(R.id.count_text)

        startButton.isEnabled = false
        stopButton.isEnabled = false
        val intentFilter = IntentFilter("$packageName.RECORDING_EVENT")
        val receiverFlags = ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(this, broadcastReceiver, intentFilter, receiverFlags)

        mediaProjectionManager =
            ContextCompat.getSystemService(this, MediaProjectionManager::class.java)!!

        // Обработчик кнопки "Старт" для запроса разрешения на запись аудио и начала записи.

        startButton.setOnClickListener {
            if (!isRecordAudioPermissionGranted) {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                return@setOnClickListener
            }
            startRecording()
        }

        // Обработчик кнопки "Стоп" для остановки записи.

        stopButton.setOnClickListener {
            stopRecording()
        }

        // Проверка разрешений на уведомления и установка доступности кнопок.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isNotificationPermissionGranted) {
                startButton.isEnabled = true
                stopButton.isEnabled = false
            } else {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        } else {
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
        // Проверка на активный процесс записи и установка доступности кнопок.
        if (mainAppViewModel.isRecording()) {
            startButton.isEnabled = false
            stopButton.isEnabled = true
        }

    }

    // Метод startRecording запускает процесс начала записи экрана.
    private fun startRecording() {
        // Запуск активности запроса захвата экрана с помощью медиа-проекции.
        requestScreenCapture.launch(mediaProjectionManager.createScreenCaptureIntent())
        // Отключение кнопки "Старт", чтобы избежать повторного запуска записи.
        startButton.isEnabled = false
    }

    // Метод stopRecording выполняет остановку процесса записи экрана,
    // отключение службы и вывод уведомления о сохранении видео.
    private fun stopRecording() {
        // Остановка службы в первом плане, связанной с записью экрана.
        stopForegroundService()
        // Остановка записи и получение файла с записанным видео.
        val outFile = mainAppViewModel.stopRecording()
        // Отключение кнопки "Стоп", так как запись уже завершена.
        stopButton.isEnabled = false
        // Включение кнопки "Старт" для возможности начать новую запись.
        startButton.isEnabled = true
        // Отображение уведомления об успешном сохранении видео.
        showVideoSavedNotification(outFile)
    }

    // Метод onDestroy используется для освобождения ресурсов и отписки от BroadcastReceiver при уничтожении активити.
    override fun onDestroy() {
        super.onDestroy()
        // Отписка от регистрации BroadcastReceiver, чтобы избежать утечек памяти.
        unregisterReceiver(broadcastReceiver)
    }

}