package com.scimsoft.wevid.data

/** Public profile of any user, as embedded in chats and lookups. */
data class ChatUser(
    val uid: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
)

/** One row in the chat list. */
data class ChatSummary(
    val id: String,
    val other: ChatUser,
    val lastMessageAt: Long?,
    val lastSenderId: String?,
    val lastThumbUrl: String?,
    val hasUnseen: Boolean,
)

/** One video message inside a chat thread. */
data class VideoMessage(
    val id: String,
    val senderId: String,
    val videoUrl: String,
    val thumbUrl: String?,
    val durationMs: Long,
    val createdAt: Long?,
    val seenBy: List<String>,
)

/** A location-dropped video on the nearby feed. */
data class FeedPost(
    val id: String,
    val authorId: String,
    val authorDisplayName: String,
    val authorUsername: String,
    val videoUrl: String,
    val thumbUrl: String?,
    val durationMs: Long,
    val createdAt: Long?,
    val lat: Double,
    val lng: Double,
    /** Distance from the viewer in meters, set after the nearby query. */
    val distanceMeters: Double = 0.0,
)
