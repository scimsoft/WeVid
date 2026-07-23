package com.scimsoft.wevid.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.scimsoft.wevid.WeVidApp
import java.io.File

class VideoUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val file = File(path)
        if (!file.exists()) return Result.failure()

        val target = inputData.getString(KEY_TARGET) ?: TARGET_CHAT
        val container = (applicationContext as WeVidApp).container

        return try {
            when (target) {
                TARGET_POST -> {
                    val lat = inputData.getDouble(KEY_LAT, Double.NaN)
                    val lng = inputData.getDouble(KEY_LNG, Double.NaN)
                    if (lat.isNaN() || lng.isNaN()) return Result.failure()
                    container.postRepository.publishPost(
                        videoFile = file,
                        lat = lat,
                        lng = lng,
                        onProgress = { progress ->
                            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                        },
                    )
                }
                else -> {
                    val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
                    container.chatRepository.sendVideoMessage(
                        chatId = chatId,
                        videoFile = file,
                        onProgress = { progress ->
                            setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                        },
                    )
                }
            }
            file.delete()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount >= MAX_ATTEMPTS) {
                file.delete()
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_TARGET = "target"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
        const val KEY_PROGRESS = "progress"
        const val TARGET_CHAT = "chat"
        const val TARGET_POST = "post"
        private const val MAX_ATTEMPTS = 5
    }
}
