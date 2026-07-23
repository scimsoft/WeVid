package com.scimsoft.wevid.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.scimsoft.wevid.work.VideoUploadWorker
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Queues clip uploads through WorkManager so they survive process death and
 * retry automatically when the network comes back.
 */
class UploadQueue(private val context: Context) {

    fun enqueueChat(chatId: String, videoFile: File) {
        val pendingFile = moveToPending(videoFile)
        val request = OneTimeWorkRequestBuilder<VideoUploadWorker>()
            .setInputData(
                workDataOf(
                    VideoUploadWorker.KEY_TARGET to VideoUploadWorker.TARGET_CHAT,
                    VideoUploadWorker.KEY_CHAT_ID to chatId,
                    VideoUploadWorker.KEY_FILE_PATH to pendingFile.absolutePath,
                ),
            )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(tagForChat(chatId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun enqueuePost(videoFile: File, lat: Double, lng: Double) {
        val pendingFile = moveToPending(videoFile)
        val request = OneTimeWorkRequestBuilder<VideoUploadWorker>()
            .setInputData(
                workDataOf(
                    VideoUploadWorker.KEY_TARGET to VideoUploadWorker.TARGET_POST,
                    VideoUploadWorker.KEY_FILE_PATH to pendingFile.absolutePath,
                    VideoUploadWorker.KEY_LAT to lat,
                    VideoUploadWorker.KEY_LNG to lng,
                ),
            )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG_POST)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** @deprecated Prefer [enqueueChat]. Kept for call-site clarity during migration. */
    fun enqueue(chatId: String, videoFile: File) = enqueueChat(chatId, videoFile)

    private fun moveToPending(videoFile: File): File {
        val pendingDir = File(context.filesDir, "pending_uploads").apply { mkdirs() }
        val pendingFile = File(pendingDir, videoFile.name)
        if (!videoFile.renameTo(pendingFile)) {
            videoFile.copyTo(pendingFile, overwrite = true)
            videoFile.delete()
        }
        return pendingFile
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val TAG_POST = "upload_post"
        fun tagForChat(chatId: String) = "upload_$chatId"
        fun tagFor(chatId: String) = tagForChat(chatId)
    }
}
