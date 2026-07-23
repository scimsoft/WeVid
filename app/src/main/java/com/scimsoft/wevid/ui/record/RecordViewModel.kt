package com.scimsoft.wevid.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scimsoft.wevid.data.LocationProvider
import com.scimsoft.wevid.data.LocationUnavailableException
import com.scimsoft.wevid.data.UploadQueue
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordUiState(
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val sent: Boolean = false,
)

sealed interface RecordTarget {
    data class Chat(val chatId: String) : RecordTarget
    data object Post : RecordTarget
}

class RecordViewModel(
    private val uploadQueue: UploadQueue,
    private val locationProvider: LocationProvider?,
    private val target: RecordTarget,
) : ViewModel() {

    private val _ui = MutableStateFlow(RecordUiState())
    val ui: StateFlow<RecordUiState> = _ui.asStateFlow()

    /** Queues the clip for chat or location post; upload continues in background. */
    fun send(videoFile: File) {
        if (_ui.value.isSending) return
        when (val t = target) {
            is RecordTarget.Chat -> {
                runCatching {
                    uploadQueue.enqueueChat(t.chatId, videoFile)
                }.onSuccess {
                    _ui.value = RecordUiState(sent = true)
                }.onFailure { error ->
                    _ui.value = RecordUiState(
                        errorMessage = error.message ?: "Couldn't queue the clip",
                    )
                }
            }
            RecordTarget.Post -> {
                val location = locationProvider
                    ?: run {
                        _ui.value = RecordUiState(errorMessage = "Location not available")
                        return
                    }
                viewModelScope.launch {
                    _ui.value = RecordUiState(isSending = true)
                    runCatching {
                        val loc = location.currentLocation()
                        uploadQueue.enqueuePost(videoFile, loc.latitude, loc.longitude)
                    }.onSuccess {
                        _ui.value = RecordUiState(sent = true)
                    }.onFailure { error ->
                        _ui.value = RecordUiState(
                            errorMessage = when (error) {
                                is LocationUnavailableException -> error.message
                                else -> error.message ?: "Couldn't queue the clip"
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(
            uploadQueue: UploadQueue,
            locationProvider: LocationProvider?,
            target: RecordTarget,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordViewModel(uploadQueue, locationProvider, target) as T
                }
            }
    }
}
