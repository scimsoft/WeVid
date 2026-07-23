package com.scimsoft.wevid.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.scimsoft.wevid.data.AuthRepository
import com.scimsoft.wevid.data.ChatRepository
import com.scimsoft.wevid.data.LocationProvider
import com.scimsoft.wevid.data.ModerationRepository
import com.scimsoft.wevid.data.PostRepository
import com.scimsoft.wevid.data.UploadQueue
import com.scimsoft.wevid.data.UserRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            appContext = appContext,
            auth = auth,
            firestore = firestore,
        )
    }

    val userRepository: UserRepository by lazy {
        UserRepository(auth = auth, firestore = firestore)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(auth = auth, firestore = firestore, storage = storage)
    }

    val postRepository: PostRepository by lazy {
        PostRepository(auth = auth, firestore = firestore, storage = storage)
    }

    val moderationRepository: ModerationRepository by lazy {
        ModerationRepository(auth = auth, firestore = firestore)
    }

    val locationProvider: LocationProvider by lazy {
        LocationProvider(appContext)
    }

    val uploadQueue: UploadQueue by lazy { UploadQueue(appContext) }
}
