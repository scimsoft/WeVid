package com.scimsoft.wevid.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** In-app reporting and blocking, required by Play's UGC policy. */
class ModerationRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    private val myUid: String
        get() = auth.currentUser?.uid ?: error("Not signed in")

    private fun blocksCollection(uid: String) =
        firestore.collection("users").document(uid).collection("blocks")

    /** Realtime set of uids the signed-in user has blocked. */
    fun blockedUids(): Flow<Set<String>> = callbackFlow {
        val registration = blocksCollection(myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.map { it.id }.toSet())
            }
        awaitClose { registration.remove() }
    }

    suspend fun blockedUidsOnce(): Set<String> =
        blocksCollection(myUid).get().await().documents.map { it.id }.toSet()

    suspend fun blockUser(uid: String) {
        blocksCollection(myUid).document(uid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun reportPost(post: FeedPost, reason: String) {
        submitReport(
            mapOf(
                "type" to "post",
                "postId" to post.id,
                "targetUserId" to post.authorId,
                "videoUrl" to post.videoUrl,
                "reason" to reason,
            ),
        )
    }

    suspend fun reportUser(targetUid: String, reason: String, chatId: String? = null) {
        submitReport(
            mapOf(
                "type" to "user",
                "targetUserId" to targetUid,
                "chatId" to chatId,
                "reason" to reason,
            ),
        )
    }

    /** The other member of a 1:1 chat, used to report/block from a thread. */
    suspend fun otherMemberOf(chatId: String): String? {
        val doc = firestore.collection("chats").document(chatId).get().await()
        val members = doc.get("members") as? List<*> ?: return null
        return members.filterIsInstance<String>().firstOrNull { it != myUid }
    }

    private suspend fun submitReport(fields: Map<String, Any?>) {
        firestore.collection("reports")
            .add(
                fields + mapOf(
                    "reporterId" to myUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }
}
