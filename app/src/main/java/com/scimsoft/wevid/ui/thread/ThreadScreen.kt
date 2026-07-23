package com.scimsoft.wevid.ui.thread

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.scimsoft.wevid.data.VideoMessage
import com.scimsoft.wevid.ui.common.ReportDialog
import com.scimsoft.wevid.ui.theme.Coral
import com.scimsoft.wevid.ui.theme.Ink
import com.scimsoft.wevid.ui.theme.InkElevated
import com.scimsoft.wevid.ui.theme.InkLine
import com.scimsoft.wevid.ui.theme.PaperMuted
import java.text.DateFormat
import java.util.Date

@Composable
fun ThreadScreen(
    title: String,
    myUid: String,
    messages: List<VideoMessage>?,
    pendingUploadCount: Int,
    uploadProgress: Float?,
    noticeRes: Int?,
    onRecord: () -> Unit,
    onBack: () -> Unit,
    onReportPeer: (String) -> Unit,
    onBlockPeer: () -> Unit,
    onNoticeShown: () -> Unit,
) {
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(noticeRes) {
        val notice = noticeRes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(notice))
        onNoticeShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRecord,
                containerColor = Coral,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = stringResource(R.string.record_title),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.post_actions),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_user)) },
                            onClick = {
                                menuOpen = false
                                showReportDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.block_user)) },
                            onClick = {
                                menuOpen = false
                                showBlockDialog = true
                            },
                        )
                    }
                }
            }

            if (pendingUploadCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(InkElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (pendingUploadCount == 1) {
                            stringResource(R.string.thread_uploading_one)
                        } else {
                            stringResource(R.string.thread_uploading_many, pendingUploadCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (uploadProgress != null && uploadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            color = Coral,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = Coral,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            when {
                messages == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Coral)
                    }
                }
                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.thread_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = PaperMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(messages.size) {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isMine = message.senderId == myUid,
                                onPlay = { playingUrl = message.videoUrl },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportDialog(
            title = stringResource(R.string.report_user),
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                showReportDialog = false
                onReportPeer(reason)
            },
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(R.string.block_dialog_title)) },
            text = { Text(stringResource(R.string.block_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockDialog = false
                        onBlockPeer()
                    },
                ) {
                    Text(stringResource(R.string.block_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val url = playingUrl
    if (url != null) {
        FullScreenPlayer(
            url = url,
            onClose = { playingUrl = null },
        )
    }
}

@Composable
private fun MessageBubble(
    message: VideoMessage,
    isMine: Boolean,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(18.dp))
                .background(InkElevated)
                .clickable(onClick = onPlay),
        ) {
            if (message.thumbUrl != null) {
                AsyncImage(
                    model = message.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.thread_play),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(34.dp),
                )
            }
            if (message.durationMs > 0) {
                Text(
                    text = formatDuration(message.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val time = message.createdAt
            Text(
                text = if (time != null) {
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
                } else {
                    stringResource(R.string.thread_sending)
                },
                style = MaterialTheme.typography.labelSmall,
                color = PaperMuted,
            )
            if (isMine && message.seenBy.size > 1) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.thread_seen),
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperMuted,
                )
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
            .background(Ink.copy(alpha = 0.97f)),
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
