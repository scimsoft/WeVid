package com.scimsoft.wevid.ui.feed

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.scimsoft.wevid.R
import com.scimsoft.wevid.data.FeedPost
import com.scimsoft.wevid.ui.common.ReportDialog
import com.scimsoft.wevid.ui.theme.Coral
import com.scimsoft.wevid.ui.theme.CoralDeep
import com.scimsoft.wevid.ui.theme.Ink
import com.scimsoft.wevid.ui.theme.InkElevated
import com.scimsoft.wevid.ui.theme.InkLine
import com.scimsoft.wevid.ui.theme.Mint
import com.scimsoft.wevid.ui.theme.MintSoft
import com.scimsoft.wevid.ui.theme.PaperMuted
import com.scimsoft.wevid.ui.theme.Scrim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    myUid: String,
    onRefresh: () -> Unit,
    onRecord: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenSettings: () -> Unit,
    onReportPost: (FeedPost, String) -> Unit,
    onBlockAuthor: (FeedPost) -> Unit,
    onNoticeShown: () -> Unit,
) {
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var reportTarget by remember { mutableStateOf<FeedPost?>(null) }
    var blockTarget by remember { mutableStateOf<FeedPost?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.noticeRes) {
        val notice = state.noticeRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(notice))
        onNoticeShown()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onRefresh()
    }

    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fabScale",
    )

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.needsLocationPermission) {
                FloatingActionButton(
                    onClick = onRecord,
                    interactionSource = fabInteraction,
                    containerColor = Coral,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.scale(fabScale),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.feed_drop_clip),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.feed_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.feed_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaperMuted,
                    )
                }
                Row {
                    IconButton(
                        onClick = onOpenChats,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(InkElevated.copy(alpha = 0.7f)),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = stringResource(R.string.chats_title),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(InkElevated.copy(alpha = 0.7f)),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.pendingUploadCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkElevated)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (state.pendingUploadCount == 1) {
                            stringResource(R.string.feed_uploading_one)
                        } else {
                            stringResource(R.string.feed_uploading_many, state.pendingUploadCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.uploadProgress != null && state.uploadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { state.uploadProgress },
                            color = Coral,
                            trackColor = InkLine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = Coral,
                            trackColor = InkLine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.needsLocationPermission -> {
                    LocationPermissionRationale(
                        onRequest = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                    )
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            state.isLoading && state.posts.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = Coral)
                                }
                            }
                            state.errorMessage != null && state.posts.isEmpty() -> {
                                EmptyMessage(
                                    text = state.errorMessage,
                                    error = true,
                                )
                            }
                            state.posts.isEmpty() -> {
                                FeedEmptyState()
                            }
                            else -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 96.dp),
                                ) {
                                    items(state.posts, key = { it.id }) { post ->
                                        FeedCard(
                                            post = post,
                                            showActions = post.authorId != myUid,
                                            onPlay = { playingUrl = post.videoUrl },
                                            onReport = { reportTarget = post },
                                            onBlock = { blockTarget = post },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    reportTarget?.let { post ->
        ReportDialog(
            title = stringResource(R.string.report_video),
            onDismiss = { reportTarget = null },
            onSubmit = { reason ->
                reportTarget = null
                onReportPost(post, reason)
            },
        )
    }

    blockTarget?.let { post ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text(stringResource(R.string.block_dialog_title)) },
            text = { Text(stringResource(R.string.block_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        blockTarget = null
                        onBlockAuthor(post)
                    },
                ) {
                    Text(stringResource(R.string.block_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val url = playingUrl
    AnimatedVisibility(
        visible = url != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (url != null) {
            FullScreenPlayer(url = url, onClose = { playingUrl = null })
        }
    }
}

@Composable
private fun FeedEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Coral.copy(alpha = 0.55f),
                            CoralDeep.copy(alpha = 0.85f),
                            InkElevated,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MintSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Place,
                    contentDescription = null,
                    tint = Mint,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.feed_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.feed_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = PaperMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyMessage(text: String, error: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) MaterialTheme.colorScheme.error else PaperMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LocationPermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Place,
            contentDescription = null,
            tint = Coral,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.feed_location_permissions),
            style = MaterialTheme.typography.bodyLarge,
            color = PaperMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Coral),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_location_grant),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun FeedCard(
    post: FeedPost,
    showActions: Boolean,
    onPlay: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(22.dp))
                .background(InkLine)
                .clickable(onClick = onPlay),
        ) {
            if (post.thumbUrl != null) {
                AsyncImage(
                    model = post.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Scrim,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.thread_play),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = post.authorDisplayName.ifBlank { "@${post.authorUsername}" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(formatDistance(post.distanceMeters))
                        post.createdAt?.let {
                            append(" · ")
                            append(formatAge(it))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperMuted,
                )
            }
            if (post.durationMs > 0) {
                Text(
                    text = formatDuration(post.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (showActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onReport) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.report_video))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.post_actions),
                            tint = PaperMuted,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_video)) },
                            onClick = {
                                menuOpen = false
                                onReport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.block_user)) },
                            onClick = {
                                menuOpen = false
                                onBlock()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenPlayer(
    url: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.98f)),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(CircleShape)
                .background(InkLine.copy(alpha = 0.7f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.thread_close),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms + 500) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        "${meters.toInt()} m"
    } else {
        "%.1f km".format(meters / 1000.0)
    }

private fun formatAge(createdAtMs: Long): String {
    val agoMs = (System.currentTimeMillis() - createdAtMs).coerceAtLeast(0)
    val minutes = agoMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}
