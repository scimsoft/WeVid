package com.scimsoft.wevid.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val myUid: String
        get() = auth.currentUser?.uid ?: error("Not signed in")

    /** Deterministic chat id so both members create/open the same document. */
    private fun chatIdFor(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_")

    /**
     * Creates the 1:1 chat with [other] if it doesn't exist and returns its id.
     * Member profiles are denormalized onto the chat doc for cheap list rendering.
     */
    suspend fun openChatWith(me: ChatUser, other: ChatUser): String {
        require(other.uid != me.uid) { "You can't start a chat with yourself" }
        val chatId = chatIdFor(me.uid, other.uid)
        val ref = firestore.collection("chats").document(chatId)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(
                mapOf(
                    "members" to listOf(me.uid, other.uid).sorted(),
                    "memberInfo" to mapOf(
                        me.uid to memberInfoMap(me),
                        other.uid to memberInfoMap(other),
                    ),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageAt" to null,
                    "lastSenderId" to null,
                    "lastThumbUrl" to null,
                    "lastMessageSeenBy" to emptyList<String>(),
                ),
            ).await()
        }
        return chatId
    }

    private fun memberInfoMap(user: ChatUser) = mapOf(
        "displayName" to user.displayName,
        "username" to user.username,
        "photoUrl" to user.photoUrl,
    )

    /** Realtime chat list for the signed-in user, newest activity first. */
    fun chatList(): Flow<List<ChatSummary>> = callbackFlow {
        val uid = myUid
        val registration = firestore.collection("chats")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val chats = snapshot.documents.mapNotNull { doc ->
                    val members = doc.get("members") as? List<*> ?: return@mapNotNull null
                    val otherUid = members.firstOrNull { it != uid } as? String
                        ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val info = (doc.get("memberInfo") as? Map<String, Map<String, Any?>>)
                        ?.get(otherUid)
                    val lastSenderId = doc.getString("lastSenderId")
                    val seenBy = doc.get("lastMessageSeenBy") as? List<*> ?: emptyList<Any>()
                    ChatSummary(
                        id = doc.id,
                        other = ChatUser(
                            uid = otherUid,
                            displayName = info?.get("displayName") as? String ?: "",
                            username = info?.get("username") as? String ?: "",
                            photoUrl = info?.get("photoUrl") as? String,
                        ),
                        lastMessageAt = doc.getTimestamp("lastMessageAt")?.toDate()?.time,
                        lastSenderId = lastSenderId,
                        lastThumbUrl = doc.getString("lastThumbUrl"),
                        hasUnseen = lastSenderId != null &&
                            lastSenderId != uid &&
                            !seenBy.contains(uid),
                    )
                }.sortedWith(
                    compareByDescending { it.lastMessageAt ?: 0L },
                )
                trySend(chats)
            }
        awaitClose { registration.remove() }
    }

    /** Realtime message stream for one chat, oldest first. */
    fun messages(chatId: String): Flow<List<VideoMessage>> = callbackFlow {
        val registration = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val items = snapshot.documents.map { doc ->
                    VideoMessage(
                        id = doc.id,
                        senderId = doc.getString("senderId").orEmpty(),
                        videoUrl = doc.getString("videoUrl").orEmpty(),
                        thumbUrl = doc.getString("thumbUrl"),
                        durationMs = doc.getLong("durationMs") ?: 0L,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time,
                        seenBy = (doc.get("seenBy") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?: emptyList(),
                    )
                }
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Uploads the recorded clip plus a generated thumbnail, then writes the
     * message document and updates the chat preview. Reports upload progress
     * in 0..1 through [onProgress].
     */
    suspend fun sendVideoMessage(
        chatId: String,
        videoFile: File,
        onProgress: (Float) -> Unit = {},
    ) {
        val uid = myUid
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document()

        val (thumbBytes, durationMs) = VideoMetadata.extractThumbAndDuration(videoFile)

        val videoRef = storage.reference.child("videos/$chatId/${messageRef.id}.mp4")
        val thumbRef = storage.reference.child("videos/$chatId/${messageRef.id}.jpg")

        val uploadTask = videoRef.putFile(android.net.Uri.fromFile(videoFile))
        uploadTask.addOnProgressListener { snap ->
            if (snap.totalByteCount > 0) {
                onProgress(snap.bytesTransferred.toFloat() / snap.totalByteCount)
            }
        }
        uploadTask.await()
        val videoUrl = videoRef.downloadUrl.await().toString()

        val thumbUrl = if (thumbBytes != null) {
            thumbRef.putBytes(thumbBytes).await()
            thumbRef.downloadUrl.await().toString()
        } else {
            null
        }

        firestore.runBatch { batch ->
            batch.set(
                messageRef,
                mapOf(
                    "senderId" to uid,
                    "videoUrl" to videoUrl,
                    "thumbUrl" to thumbUrl,
                    "durationMs" to durationMs,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "seenBy" to listOf(uid),
                ),
            )
            batch.update(
                firestore.collection("chats").document(chatId),
                mapOf(
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to uid,
                    "lastThumbUrl" to thumbUrl,
                    "lastMessageSeenBy" to listOf(uid),
                ),
            )
        }.await()
    }

    /** Marks the chat and all messages from the other member as seen by me. */
    suspend fun markSeen(chatId: String) {
        val uid = myUid
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.update("lastMessageSeenBy", FieldValue.arrayUnion(uid)).await()

        val unseen = chatRef.collection("messages")
            .whereNotEqualTo("senderId", uid)
            .get()
            .await()
            .documents
            .filter { doc ->
                val seenBy = doc.get("seenBy") as? List<*> ?: emptyList<Any>()
                !seenBy.contains(uid)
            }
        if (unseen.isEmpty()) return
        firestore.runBatch { batch ->
            unseen.forEach { doc ->
                batch.update(doc.reference, "seenBy", FieldValue.arrayUnion(uid))
            }
        }.await()
    }
}
