package com.scimsoft.wevid.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scimsoft.wevid.data.UserRepository
import com.scimsoft.wevid.data.UsernameTakenException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
)

class OnboardingViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(OnboardingUiState())
    val ui: StateFlow<OnboardingUiState> = _ui.asStateFlow()

    fun claim(username: String) {
        if (_ui.value.isSaving) return
        viewModelScope.launch {
            _ui.value = OnboardingUiState(isSaving = true)
            runCatching {
                userRepository.claimUsername(username)
            }.onSuccess {
                _ui.value = OnboardingUiState(done = true)
            }.onFailure { error ->
                _ui.value = OnboardingUiState(
                    errorMessage = when (error) {
                        is UsernameTakenException -> error.message
                        else -> error.message ?: "Couldn't save username"
                    },
                )
            }
        }
    }

    companion object {
        fun factory(userRepository: UserRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OnboardingViewModel(userRepository) as T
                }
            }
    }
}
