package com.scimsoft.wevid.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scimsoft.wevid.data.AuthRepository
import com.scimsoft.wevid.data.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthState.Loading,
        )

    private val _signInUi = MutableStateFlow(SignInUiState())
    val signInUi: StateFlow<SignInUiState> = _signInUi.asStateFlow()

    fun hasGoogleWebClientId(): Boolean = authRepository.hasGoogleWebClientId()

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _signInUi.value = SignInUiState(isSigningIn = true)
            runCatching {
                authRepository.signInWithGoogle(activity)
            }.onSuccess {
                _signInUi.value = SignInUiState()
            }.onFailure { error ->
                _signInUi.value = SignInUiState(
                    isSigningIn = false,
                    errorMessage = error.message ?: "Sign-in failed",
                )
            }
        }
    }

    fun clearError() {
        _signInUi.value = _signInUi.value.copy(errorMessage = null)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
    }
}
