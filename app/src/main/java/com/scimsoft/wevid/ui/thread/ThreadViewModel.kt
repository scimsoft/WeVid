package com.scimsoft.wevid.ui.thread

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scimsoft.wevid.R
import com.scimsoft.wevid.data.ChatRepository
import com.scimsoft.wevid.data.ModerationRepository
import com.scimsoft.wevid.data.VideoMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThreadViewModel(
    private val chatRepository: ChatRepository,
    private val moderationRepository: ModerationRepository,
    private val chatId: String,
) : ViewModel() {

    val messages: StateFlow<List<VideoMessage>?> = chatRepository.messages(chatId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _noticeRes = MutableStateFlow<Int?>(null)
    @get:StringRes
    val noticeRes: StateFlow<Int?> = _noticeRes.asStateFlow()

    fun markSeen() {
        viewModelScope.launch {
            runCatching { chatRepository.markSeen(chatId) }
        }
    }

    fun reportPeer(reason: String) {
        viewModelScope.launch {
            runCatching {
                val other = moderationRepository.otherMemberOf(chatId) ?: return@launch
                moderationRepository.reportUser(other, reason, chatId)
            }
            _noticeRes.value = R.string.report_thanks
        }
    }

    fun blockPeer(onBlocked: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val other = moderationRepository.otherMemberOf(chatId) ?: return@launch
                moderationRepository.blockUser(other)
            }.onSuccess { onBlocked() }
        }
    }

    fun consumeNotice() {
        _noticeRes.value = null
    }

    companion object {
        fun factory(
            chatRepository: ChatRepository,
            moderationRepository: ModerationRepository,
            chatId: String,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ThreadViewModel(chatRepository, moderationRepository, chatId) as T
                }
            }
    }
}
