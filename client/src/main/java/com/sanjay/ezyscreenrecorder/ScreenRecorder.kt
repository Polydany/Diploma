package com.sanjay.ezyscreenrecorder

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.CamcorderProfile
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import java.io.File
import java.io.IOException

import android.os.Handler
import android.os.Looper
import java.io.FileOutputStream
import java.util.*
import kotlin.concurrent.timerTask


import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaCodec.BufferInfo


import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Environment
import com.sanjay.ezyscreenrecorder.MainActivity.Companion.DIRECTORY
import com.sanjay.ezyscreenrecorder.MainActivity.Companion.PARENT_DIRECTORY


// Класс ScreenRecorder предназначен для записи экрана и осуществления всех связанных операций.

// Параметры для записи экрана и необходимые ресурсы.
class ScreenRecorder(
    context: Context
) {
    // Переменные для хранения размеров экрана, профиля записи, ориентации и плотности экрана.
    private lateinit var mScreenSize: Size
    private lateinit var mProfile: CamcorderProfile
    private var mRotation: Int = 0
    private var mScreenDensity: Int = 0


    // Переменные для работы с проекцией мультимедиа, виртуальным дисплеем и MediaRecorder.
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private val mMediaRecorder: MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    private lateinit var mFile: File

    // Callback для обработки события остановки проекции мультимедиа.
    private val mMediaProjectionCallback: MediaProjection.Callback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Остановка и сброс MediaRecorder, освобождение виртуального дисплея.
            mMediaRecorder.stop()
            mMediaRecorder.reset()
            mVirtualDisplay?.release()
        }
    }

    // Метод initRecorder используется для инициализации MediaRecorder для записи экрана.
    @Throws(IOException::class)
    // Метод initRecorder инициализирует MediaRecorder для записи экрана с указанным файлом.
    private fun initRecorder(file: File) {
        mFile = file
        // Установка источника аудио и видео для записи.
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        // Установка ориентации видео в соответствии с устройством.
        val orientation = ORIENTATIONS[mRotation + 90]
        mMediaRecorder.setOrientationHint(orientation)
        // Установка профиля записи и пути для сохранения записанного видео.
        mMediaRecorder.setProfile(mProfile)
        mMediaRecorder.setOutputFile(file.absolutePath)
        // Подготовка MediaRecorder к записи.
        mMediaRecorder.prepare()
    }

    // Метод prepare устанавливает параметры для записи экрана (плотность экрана, ориентацию и размер).
    fun prepare(screenDensity: Int, rotation: Int, screenSize: Size) {
        // Определение совместимого размера экрана для записи.
        val compatibleScreenSize = Utils.compatibleScreenSize(screenSize.width, screenSize.height)
        // Получение профиля записи с высоким качеством и установка соответствующих размеров.
        val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).also {
            it.videoFrameWidth = compatibleScreenSize.width
            it.videoFrameHeight = compatibleScreenSize.height
        }
        // Установка параметров записи экрана.
        mScreenSize = compatibleScreenSize
        mProfile = profile
        mRotation = rotation
        mScreenDensity = screenDensity
    }

    // Метод start запускает запись экрана с заданным файлом и проекцией мультимедиа.
    // Он инициализирует MediaProjection, создает виртуальный дисплей, начинает запись и возвращает результат.
    fun start(file: File, context: Context, screenCaptureIntent: Intent, mediaProjection: MediaProjection): Boolean {
        // Устанавливаем переданную проекцию в mMediaProjection.
        mMediaProjection = mediaProjection
        // Регистрируем обратный вызов для MediaProjection.
        mediaProjection.registerCallback(mMediaProjectionCallback, null)

        try {
            // Инициализируем запись экрана с переданным файлом.
            initRecorder(file)
            // Создаем виртуальный дисплей для записи с заданными параметрами.
            mVirtualDisplay = mediaProjection.createVirtualDisplay(
                "Emotions of the conference",
                mScreenSize.width,
                mScreenSize.height,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.surface,
                null,
                null
            )
            // Начинаем запись экрана.
            mMediaRecorder.start()

            return true
        } catch (e: IOException) {
            // При возникновении IOException выводим ошибку в консоль и возвращаем false.
            e.printStackTrace()
        } catch (e: Exception) {
            // При других исключениях также выводим ошибку в консоль и возвращаем false.
            e.printStackTrace()
        }
        // Если запись не удалась, возвращаем false.
        return false
    }



    private fun takeScreenshot(videoFile: File, context: Context, mediaProjection: MediaProjection, screenCaptureIntent: Intent) {
        val width = mScreenSize.width
        val height = mScreenSize.height

        val mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = mediaCodec.createInputSurface()
        mediaCodec.start()

        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, screenCaptureIntent) ?: return
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "screenshot",
            width,
            height,
            mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10000L

        val parentDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Ezy Recordings")
        if (!parentDir.exists()){
            parentDir.mkdirs()
        }
        val screenshotFile = File(parentDir, "screenshot_${System.currentTimeMillis()}.png")
        val fos = FileOutputStream(screenshotFile)

        var isEOS = false
        while (!isEOS) {
            val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferIndex >= 0) {
                val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                val data = ByteArray(bufferInfo.size)
                outputBuffer?.get(data)
                fos.write(data)
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Игнорируем этот случай
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // Попробуем снова позже
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // Игнорируем этот случай
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                isEOS = true
            }
        }

        fos.flush()
        fos.close()

        mediaCodec.stop()
        mediaCodec.release()
        virtualDisplay.release()
        mediaProjection.stop()
    }

    // Метод stop останавливает запись экрана и возвращает файл с записанным видео.
    // Если запись не производилась, метод генерирует IllegalStateException.
    fun stop(): File {
        // Проверяем, идет ли запись. Если нет, генерируем исключение.
        if (!isRecording) {
            throw IllegalStateException("Screen Recorder is not in recording state. Cannot stop")
        }
        // Останавливаем запись, освобождаем ресурсы и убираем виртуальный дисплей.
        mMediaRecorder.stop()
        mMediaRecorder.reset()
        mVirtualDisplay?.release()
        // Отменяем обратный вызов и останавливаем проекцию мультимедиа.
        mMediaProjection?.unregisterCallback(mMediaProjectionCallback)
        mMediaProjection?.stop()
        // Сбрасываем переменные в null и возвращаем файл с записью.
        mVirtualDisplay = null
        mMediaProjection = null
        return mFile
    }

    // Свойство isRecording показывает, идет ли в данный момент запись экрана.
    val isRecording: Boolean
        get() = mMediaProjection != null

    companion object {
        private val ORIENTATIONS = SparseIntArray()

        // Инициализация матрицы ORIENTATIONS для коррекции ориентации видео в зависимости от поворота экрана.
        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }
}

