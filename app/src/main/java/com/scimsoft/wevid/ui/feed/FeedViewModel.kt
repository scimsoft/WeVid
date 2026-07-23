package com.scimsoft.wevid.ui.feed

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scimsoft.wevid.R
import com.scimsoft.wevid.data.FeedPost
import com.scimsoft.wevid.data.LocationProvider
import com.scimsoft.wevid.data.LocationUnavailableException
import com.scimsoft.wevid.data.ModerationRepository
import com.scimsoft.wevid.data.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FeedUiState(
    val isLoading: Boolean = false,
    val needsLocationPermission: Boolean = false,
    val posts: List<FeedPost> = emptyList(),
    val errorMessage: String? = null,
    val pendingUploadCount: Int = 0,
    val uploadProgress: Float? = null,
    @StringRes val noticeRes: Int? = null,
)

class FeedViewModel(
    private val postRepository: PostRepository,
    private val locationProvider: LocationProvider,
    private val moderationRepository: ModerationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(FeedUiState())
    val ui: StateFlow<FeedUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (!locationProvider.hasPermission()) {
            _ui.value = _ui.value.copy(
                isLoading = false,
                needsLocationPermission = true,
                errorMessage = null,
            )
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                isLoading = true,
                needsLocationPermission = false,
                errorMessage = null,
            )
            runCatching {
                val location = locationProvider.currentLocation()
                val blocked = runCatching { moderationRepository.blockedUidsOnce() }
                    .getOrDefault(emptySet())
                postRepository.nearbyPosts(location.latitude, location.longitude)
                    .filterNot { it.authorId in blocked }
            }.onSuccess { posts ->
                _ui.value = _ui.value.copy(
                    isLoading = false,
                    posts = posts,
                    errorMessage = null,
                )
            }.onFailure { error ->
                _ui.value = _ui.value.copy(
                    isLoading = false,
                    errorMessage = when (error) {
                        is LocationUnavailableException -> error.message
                        else -> error.message ?: "Couldn't load nearby videos"
                    },
                )
            }
        }
    }

    fun reportPost(post: FeedPost, reason: String) {
        viewModelScope.launch {
            runCatching { moderationRepository.reportPost(post, reason) }
            _ui.value = _ui.value.copy(noticeRes = R.string.report_thanks)
        }
    }

    fun blockAuthor(post: FeedPost) {
        viewModelScope.launch {
            runCatching { moderationRepository.blockUser(post.authorId) }
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        posts = _ui.value.posts.filterNot { it.authorId == post.authorId },
                        noticeRes = R.string.user_blocked,
                    )
                }
        }
    }

    fun consumeNotice() {
        _ui.value = _ui.value.copy(noticeRes = null)
    }

    fun setUploadState(pendingCount: Int, progress: Float?) {
        _ui.value = _ui.value.copy(
            pendingUploadCount = pendingCount,
            uploadProgress = progress,
        )
    }

    companion object {
        fun factory(
            postRepository: PostRepository,
            locationProvider: LocationProvider,
            moderationRepository: ModerationRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FeedViewModel(
                        postRepository,
                        locationProvider,
                        moderationRepository,
                    ) as T
                }
            }
    }
}
