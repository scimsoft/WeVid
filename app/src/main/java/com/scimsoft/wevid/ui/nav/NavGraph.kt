package com.scimsoft.wevid.ui.nav

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.scimsoft.wevid.data.AuthState
import com.scimsoft.wevid.data.UploadQueue
import com.scimsoft.wevid.di.AppContainer
import com.scimsoft.wevid.ui.auth.AuthViewModel
import com.scimsoft.wevid.ui.auth.SignInScreen
import com.scimsoft.wevid.ui.chats.ChatsScreen
import com.scimsoft.wevid.ui.chats.ChatsViewModel
import com.scimsoft.wevid.ui.feed.FeedScreen
import com.scimsoft.wevid.ui.feed.FeedViewModel
import com.scimsoft.wevid.ui.onboarding.OnboardingScreen
import com.scimsoft.wevid.ui.onboarding.OnboardingViewModel
import com.scimsoft.wevid.ui.record.RecordScreen
import com.scimsoft.wevid.ui.record.RecordTarget
import com.scimsoft.wevid.ui.record.RecordViewModel
import com.scimsoft.wevid.ui.settings.SettingsScreen
import com.scimsoft.wevid.ui.thread.ThreadScreen
import com.scimsoft.wevid.ui.thread.ThreadViewModel
import com.scimsoft.wevid.ui.theme.Coral
import com.scimsoft.wevid.work.VideoUploadWorker

/** Where a tapped push notification should take the user. */
data class PushDestination(val chatId: String, val title: String)

@Composable
fun WeVidNavGraph(
    container: AppContainer,
    pushDestination: PushDestination? = null,
    onPushConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(container.authRepository),
    )
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val signInUi by authViewModel.signInUi.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    LaunchedEffect(authState, pushDestination) {
        when (val state = authState) {
            AuthState.Loading -> Unit
            AuthState.SignedOut -> {
                navController.navigate(Routes.SignIn) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.SignedIn -> {
                val route = navController.currentDestination?.route
                val needsOnboarding = state.profile?.username.isNullOrBlank()
                val onAuthShell = route == null ||
                    route == Routes.SignIn ||
                    route == Routes.Onboarding
                if (onAuthShell) {
                    val target = if (needsOnboarding) Routes.Onboarding else Routes.Feed
                    if (route != target) {
                        navController.navigate(target) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                if (pushDestination != null && !needsOnboarding) {
                    onPushConsumed()
                    navController.navigate(
                        Routes.thread(pushDestination.chatId, pushDestination.title),
                    )
                }
            }
        }
    }

    when (authState) {
        AuthState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Coral)
            }
        }
        else -> {
            NavHost(
                navController = navController,
                startDestination = Routes.SignIn,
            ) {
                composable(Routes.SignIn) {
                    SignInScreen(
                        isSigningIn = signInUi.isSigningIn,
                        errorMessage = signInUi.errorMessage,
                        clientIdConfigured = authViewModel.hasGoogleWebClientId(),
                        onSignInClick = { authViewModel.signInWithGoogle(activity) },
                    )
                }

                composable(Routes.Onboarding) {
                    val onboardingViewModel: OnboardingViewModel = viewModel(
                        factory = OnboardingViewModel.factory(container.userRepository),
                    )
                    val ui by onboardingViewModel.ui.collectAsStateWithLifecycle()
                    OnboardingScreen(
                        isSaving = ui.isSaving || ui.done,
                        errorMessage = ui.errorMessage,
                        onClaim = onboardingViewModel::claim,
                    )
                }

                composable(Routes.Feed) {
                    val feedViewModel: FeedViewModel = viewModel(
                        factory = FeedViewModel.factory(
                            postRepository = container.postRepository,
                            locationProvider = container.locationProvider,
                            moderationRepository = container.moderationRepository,
                        ),
                    )
                    val feedUi by feedViewModel.ui.collectAsStateWithLifecycle()

                    val context = LocalContext.current
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { }
                    LaunchedEffect(Unit) {
                        runCatching { container.userRepository.registerFcmToken() }
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                    }

                    val uploads by WorkManager.getInstance(context)
                        .getWorkInfosByTagFlow(UploadQueue.TAG_POST)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val activeUploads = uploads.filter { !it.state.isFinished }
                    val runningProgress = activeUploads
                        .firstOrNull { it.state == WorkInfo.State.RUNNING }
                        ?.progress
                        ?.getFloat(VideoUploadWorker.KEY_PROGRESS, 0f)
                    LaunchedEffect(activeUploads.size, runningProgress) {
                        feedViewModel.setUploadState(activeUploads.size, runningProgress)
                    }
                    // Refresh when an upload finishes so the new post appears.
                    val finishedCount = uploads.count { it.state.isFinished }
                    LaunchedEffect(finishedCount) {
                        if (finishedCount > 0) feedViewModel.refresh()
                    }

                    FeedScreen(
                        state = feedUi,
                        myUid = (authState as? AuthState.SignedIn)?.user?.uid.orEmpty(),
                        onRefresh = feedViewModel::refresh,
                        onRecord = { navController.navigate(Routes.recordPost()) },
                        onOpenChats = { navController.navigate(Routes.Chats) },
                        onOpenSettings = { navController.navigate(Routes.Settings) },
                        onReportPost = feedViewModel::reportPost,
                        onBlockAuthor = feedViewModel::blockAuthor,
                        onNoticeShown = feedViewModel::consumeNotice,
                    )
                }

                composable(Routes.Chats) {
                    val signedIn = authState as? AuthState.SignedIn
                    val chatsViewModel: ChatsViewModel = viewModel(
                        factory = ChatsViewModel.factory(
                            userRepository = container.userRepository,
                            chatRepository = container.chatRepository,
                            moderationRepository = container.moderationRepository,
                        ),
                    )
                    val chats by chatsViewModel.chats.collectAsStateWithLifecycle()
                    val newChat by chatsViewModel.newChat.collectAsStateWithLifecycle()

                    LaunchedEffect(newChat.openChatId) {
                        val chatId = newChat.openChatId ?: return@LaunchedEffect
                        chatsViewModel.consumeOpenChat()
                        val other = chats?.firstOrNull { it.id == chatId }?.other
                        navController.navigate(
                            Routes.thread(chatId, other?.displayName ?: ""),
                        )
                    }

                    ChatsScreen(
                        displayName = signedIn?.profile?.displayName
                            ?: signedIn?.user?.displayName,
                        chats = chats,
                        newChatState = newChat,
                        onStartChat = { username ->
                            signedIn?.profile?.let { profile ->
                                chatsViewModel.startChat(profile, username)
                            }
                        },
                        onDismissNewChatError = chatsViewModel::consumeOpenChat,
                        onOpenChat = { chatId, otherName ->
                            navController.navigate(Routes.thread(chatId, otherName))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.Thread,
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.StringType },
                        navArgument("title") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val chatId = entry.arguments?.getString("chatId").orEmpty()
                    val title = entry.arguments?.getString("title").orEmpty()
                    val signedIn = authState as? AuthState.SignedIn

                    val threadViewModel: ThreadViewModel = viewModel(
                        key = "thread_$chatId",
                        factory = ThreadViewModel.factory(
                            chatRepository = container.chatRepository,
                            moderationRepository = container.moderationRepository,
                            chatId = chatId,
                        ),
                    )
                    val messages by threadViewModel.messages.collectAsStateWithLifecycle()
                    val threadNotice by threadViewModel.noticeRes.collectAsStateWithLifecycle()

                    val context = LocalContext.current
                    val uploads by WorkManager.getInstance(context)
                        .getWorkInfosByTagFlow(UploadQueue.tagFor(chatId))
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val activeUploads = uploads.filter { !it.state.isFinished }
                    val runningProgress = activeUploads
                        .firstOrNull { it.state == WorkInfo.State.RUNNING }
                        ?.progress
                        ?.getFloat(VideoUploadWorker.KEY_PROGRESS, 0f)

                    LaunchedEffect(messages?.size) {
                        if (!messages.isNullOrEmpty()) threadViewModel.markSeen()
                    }

                    ThreadScreen(
                        title = title.ifBlank { "Chat" },
                        myUid = signedIn?.user?.uid.orEmpty(),
                        messages = messages,
                        pendingUploadCount = activeUploads.size,
                        uploadProgress = runningProgress,
                        noticeRes = threadNotice,
                        onRecord = { navController.navigate(Routes.record(chatId)) },
                        onBack = { navController.popBackStack() },
                        onReportPeer = threadViewModel::reportPeer,
                        onBlockPeer = {
                            threadViewModel.blockPeer {
                                navController.popBackStack()
                            }
                        },
                        onNoticeShown = threadViewModel::consumeNotice,
                    )
                }

                composable(
                    route = Routes.Record,
                    arguments = listOf(
                        navArgument("chatId") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        },
                    ),
                ) { entry ->
                    val chatId = entry.arguments?.getString("chatId").orEmpty()
                    val target = if (chatId.isBlank()) {
                        RecordTarget.Post
                    } else {
                        RecordTarget.Chat(chatId)
                    }
                    val recordViewModel: RecordViewModel = viewModel(
                        key = "record_${chatId.ifBlank { "post" }}",
                        factory = RecordViewModel.factory(
                            uploadQueue = container.uploadQueue,
                            locationProvider = if (target is RecordTarget.Post) {
                                container.locationProvider
                            } else {
                                null
                            },
                            target = target,
                        ),
                    )
                    val ui by recordViewModel.ui.collectAsStateWithLifecycle()

                    RecordScreen(
                        ui = ui,
                        onSend = recordViewModel::send,
                        onClose = { navController.popBackStack() },
                    )
                }

                composable(Routes.Settings) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSignOut = authViewModel::signOut,
                    )
                }
            }
        }
    }
}
