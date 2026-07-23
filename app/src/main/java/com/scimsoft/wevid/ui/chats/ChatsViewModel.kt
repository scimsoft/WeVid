package com.scimsoft.wevid.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scimsoft.wevid.data.ChatRepository
import com.scimsoft.wevid.data.ChatSummary
import com.scimsoft.wevid.data.ChatUser
import com.scimsoft.wevid.data.ModerationRepository
import com.scimsoft.wevid.data.UserProfile
import com.scimsoft.wevid.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewChatUiState(
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val openChatId: String? = null,
)

class ChatsViewModel(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    moderationRepository: ModerationRepository,
) : ViewModel() {

    val chats: StateFlow<List<ChatSummary>?> = chatRepository.chatList()
        .combine(
            moderationRepository.blockedUids().onStart { emit(emptySet()) },
        ) { chats, blocked ->
            chats.filterNot { it.other.uid in blocked }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _newChat = MutableStateFlow(NewChatUiState())
    val newChat: StateFlow<NewChatUiState> = _newChat.asStateFlow()

    fun startChat(me: UserProfile, username: String) {
        if (_newChat.value.isSearching) return
        viewModelScope.launch {
            _newChat.value = NewChatUiState(isSearching = true)
            runCatching {
                val other = userRepository.findByUsername(username)
                    ?: error("@${username.trim().removePrefix("@").lowercase()} not found")
                val myUsername = me.username ?: error("Your profile has no username yet")
                chatRepository.openChatWith(
                    me = ChatUser(
                        uid = me.uid,
                        displayName = me.displayName,
                        username = myUsername,
                        photoUrl = me.photoUrl,
                    ),
                    other = other,
                )
            }.onSuccess { chatId ->
                _newChat.value = NewChatUiState(openChatId = chatId)
            }.onFailure { error ->
                _newChat.value = NewChatUiState(
                    errorMessage = error.message ?: "Couldn't start chat",
                )
            }
        }
    }

    fun consumeOpenChat() {
        _newChat.value = NewChatUiState()
    }

    companion object {
        fun factory(
            userRepository: UserRepository,
            chatRepository: ChatRepository,
            moderationRepository: ModerationRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatsViewModel(
                        userRepository,
                        chatRepository,
                        moderationRepository,
                    ) as T
                }
            }
    }
}
