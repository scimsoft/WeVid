package com.scimsoft.wevid.data

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class PostRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val myUid: String
        get() = auth.currentUser?.uid ?: error("Not signed in")

    /**
     * Uploads a clip dropped at [lat]/[lng] and writes a `posts/{id}` document
     * with a geohash for nearby queries. Progress is 0..1.
     */
    suspend fun publishPost(
        videoFile: File,
        lat: Double,
        lng: Double,
        onProgress: (Float) -> Unit = {},
    ) {
        val user = auth.currentUser ?: error("Not signed in")
        val postRef = firestore.collection("posts").document()
        val (thumbBytes, durationMs) = VideoMetadata.extractThumbAndDuration(videoFile)

        val videoRef = storage.reference.child("posts/${postRef.id}.mp4")
        val thumbRef = storage.reference.child("posts/${postRef.id}.jpg")

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

        // Prefer the Firestore profile username if present.
        val profile = firestore.collection("users").document(user.uid).get().await()
        val displayName = profile.getString("displayName")
            ?.takeIf { it.isNotBlank() }
            ?: user.displayName.orEmpty()
        val username = profile.getString("username").orEmpty()

        val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(lat, lng))
        postRef.set(
            mapOf(
                "authorId" to user.uid,
                "authorInfo" to mapOf(
                    "displayName" to displayName,
                    "username" to username,
                ),
                "videoUrl" to videoUrl,
                "thumbUrl" to thumbUrl,
                "durationMs" to durationMs,
                "createdAt" to FieldValue.serverTimestamp(),
                "lat" to lat,
                "lng" to lng,
                "geohash" to geohash,
            ),
        ).await()
    }

    /**
     * One-shot fetch of posts within [radiusMeters] of [centerLat]/[centerLng],
     * filtered by true distance and sorted newest first.
     */
    suspend fun nearbyPosts(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double = DEFAULT_RADIUS_METERS,
    ): List<FeedPost> = coroutineScope {
        val center = GeoLocation(centerLat, centerLng)
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusMeters)
        val snapshots = bounds.map { bound ->
            async {
                firestore.collection("posts")
                    .orderBy("geohash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash)
                    .limit(PER_BOUND_LIMIT)
                    .get()
                    .await()
            }
        }.awaitAll()

        val seen = mutableSetOf<String>()
        snapshots.flatMap { it.documents }
            .mapNotNull { doc ->
                if (!seen.add(doc.id)) return@mapNotNull null
                val lat = doc.getDouble("lat") ?: return@mapNotNull null
                val lng = doc.getDouble("lng") ?: return@mapNotNull null
                val distance = GeoFireUtils.getDistanceBetween(
                    center,
                    GeoLocation(lat, lng),
                )
                if (distance > radiusMeters) return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val authorInfo = doc.get("authorInfo") as? Map<String, Any?>
                FeedPost(
                    id = doc.id,
                    authorId = doc.getString("authorId").orEmpty(),
                    authorDisplayName = authorInfo?.get("displayName") as? String ?: "",
                    authorUsername = authorInfo?.get("username") as? String ?: "",
                    videoUrl = doc.getString("videoUrl").orEmpty(),
                    thumbUrl = doc.getString("thumbUrl"),
                    durationMs = doc.getLong("durationMs") ?: 0L,
                    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time,
                    lat = lat,
                    lng = lng,
                    distanceMeters = distance,
                )
            }
            .sortedWith(
                compareByDescending<FeedPost> { it.createdAt ?: 0L }
                    .thenBy { it.distanceMeters },
            )
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 10_000.0
        private const val PER_BOUND_LIMIT = 50L
    }
}
