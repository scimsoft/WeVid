package com.scimsoft.wevid.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class UsernameTakenException(username: String) :
    Exception("@$username is already taken")

class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    /**
     * Atomically claims [username]: creates `usernames/{username}` as a reservation
     * and writes it on `users/{uid}`. Fails with [UsernameTakenException] on collision.
     */
    suspend fun claimUsername(username: String) {
        val user = auth.currentUser ?: error("Not signed in")
        val normalized = username.trim().lowercase()
        require(normalized.matches(Regex("[a-z0-9_]{3,20}"))) {
            "Username must be 3–20 characters (a–z, 0–9, _)"
        }

        val usernameRef = firestore.collection("usernames").document(normalized)
        val userRef = firestore.collection("users").document(user.uid)

        try {
            firestore.runTransaction { tx ->
                val existing = tx.get(usernameRef)
                if (existing.exists() && existing.getString("uid") != user.uid) {
                    throw UsernameTakenException(normalized)
                }
                tx.set(usernameRef, mapOf("uid" to user.uid))
                tx.set(
                    userRef,
                    mapOf(
                        "username" to normalized,
                        "displayName" to user.displayName.orEmpty(),
                        "photoUrl" to user.photoUrl?.toString(),
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
            }.await()
        } catch (e: Exception) {
            // Firestore wraps transaction body exceptions; surface ours directly.
            throw (e.cause as? UsernameTakenException) ?: e
        }
    }

    /** Registers this device's FCM token on the signed-in user's profile. */
    suspend fun registerFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        firestore.collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .await()
    }

    /** Finds a user by exact username, or null if it doesn't exist. */
    suspend fun findByUsername(username: String): ChatUser? {
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isEmpty()) return null

        val claim = firestore.collection("usernames").document(normalized).get().await()
        val uid = claim.getString("uid") ?: return null

        val snap = firestore.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        return ChatUser(
            uid = uid,
            displayName = snap.getString("displayName").orEmpty(),
            username = snap.getString("username") ?: normalized,
            photoUrl = snap.getString("photoUrl"),
        )
    }
}
