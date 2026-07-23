package com.scimsoft.wevid.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.scimsoft.wevid.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String,
    val displayName: String,
    val username: String?,
    val photoUrl: String?,
)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: FirebaseUser, val profile: UserProfile?) : AuthState
}

class AuthRepository(
    private val appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    val authState: Flow<AuthState> = callbackFlow {
        trySend(AuthState.Loading)
        var profileRegistration: ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            profileRegistration?.remove()
            profileRegistration = null
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(AuthState.SignedOut)
            } else {
                trySend(AuthState.SignedIn(user, profile = null))
                // Realtime so username claims / profile edits propagate immediately.
                profileRegistration = firestore.collection("users").document(user.uid)
                    .addSnapshotListener { snap, _ ->
                        val profile = UserProfile(
                            uid = user.uid,
                            displayName = snap?.getString("displayName")
                                ?.takeIf { it.isNotBlank() }
                                ?: user.displayName.orEmpty(),
                            username = snap?.getString("username"),
                            photoUrl = snap?.getString("photoUrl")
                                ?: user.photoUrl?.toString(),
                        )
                        trySend(AuthState.SignedIn(user, profile))
                    }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            profileRegistration?.remove()
            auth.removeAuthStateListener(listener)
        }
    }

    fun hasGoogleWebClientId(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signInWithGoogle(activity: Activity) {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        require(webClientId.isNotBlank()) {
            "GOOGLE_WEB_CLIENT_ID is not configured"
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(appContext)

        try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request,
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken,
                    null,
                )
                auth.signInWithCredential(firebaseCredential).await()
            } else {
                error("Unexpected credential type")
            }
        } catch (e: GetCredentialException) {
            throw e
        }
    }

    /** Removes this device's FCM token first so a signed-out device stops getting pushes. */
    suspend fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                firestore.collection("users").document(uid)
                    .update("fcmTokens", FieldValue.arrayRemove(token))
                    .await()
            }
        }
        auth.signOut()
    }
}
