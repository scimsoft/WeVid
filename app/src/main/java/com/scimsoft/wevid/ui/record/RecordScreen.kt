package com.scimsoft.wevid.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.scimsoft.wevid.R
import com.scimsoft.wevid.ui.theme.Coral
import com.scimsoft.wevid.ui.theme.Danger
import com.scimsoft.wevid.ui.theme.Ink
import com.scimsoft.wevid.ui.theme.InkLine
import com.scimsoft.wevid.ui.theme.PaperMuted
import java.io.File
import kotlinx.coroutines.delay

private const val MAX_CLIP_MS = 30_000L

private fun hasPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun RecordScreen(
    ui: RecordUiState,
    onSend: (File) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasPermissions(context)) }
    var recordedFile by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    LaunchedEffect(ui.sent) {
        if (ui.sent) onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        when {
            !permissionsGranted -> {
                PermissionRationale(
                    onRequest = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                            ),
                        )
                    },
                )
            }
            recordedFile == null -> {
                CameraCapture(
                    onRecorded = { file -> recordedFile = file },
                )
            }
            else -> {
                ClipReview(
                    file = recordedFile!!,
                    ui = ui,
                    onSend = onSend,
                    onDiscard = {
                        recordedFile?.delete()
                        recordedFile = null
                    },
                )
            }
        }

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

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.record_permissions),
            style = MaterialTheme.typography.bodyLarge,
            color = PaperMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Coral),
        ) {
            Text(stringResource(R.string.record_grant))
        }
    }
}

@Composable
private fun CameraCapture(
    onRecorded: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var useFrontCamera by remember { mutableStateOf(true) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartMs by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    val previewView = remember { PreviewView(context) }

    // (Re)bind the camera whenever the lens flips.
    LaunchedEffect(useFrontCamera) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        val selector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        videoCapture = capture
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    // Ticker + hard stop at the 30s cap.
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsedMs = System.currentTimeMillis() - recordStartMs
            if (elapsedMs >= MAX_CLIP_MS) {
                activeRecording?.stop()
            }
            delay(100)
        }
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        val file = File.createTempFile("wevid_", ".mp4", context.cacheDir)
        val options = FileOutputOptions.Builder(file).build()
        recordStartMs = System.currentTimeMillis()
        elapsedMs = 0L
        activeRecording = capture.output
            .prepareRecording(context, options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> isRecording = true
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        activeRecording = null
                        if (!event.hasError() && file.length() > 0) {
                            onRecorded(file)
                        } else {
                            file.delete()
                        }
                    }
                    else -> Unit
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (isRecording) {
            Text(
                text = "%.1fs / 30s".format(elapsedMs / 1000f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (!isRecording) useFrontCamera = !useFrontCamera },
                enabled = !isRecording,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(InkLine.copy(alpha = 0.7f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cameraswitch,
                    contentDescription = stringResource(R.string.record_flip),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.size(28.dp))

            IconButton(
                onClick = {
                    if (isRecording) activeRecording?.stop() else startRecording()
                },
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Danger else Coral),
            ) {
                Icon(
                    imageVector = if (isRecording) {
                        Icons.Rounded.Stop
                    } else {
                        Icons.Rounded.FiberManualRecord
                    },
                    contentDescription = stringResource(R.string.record_toggle),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }

            Spacer(modifier = Modifier.size(28.dp))
            // Balances the flip button so the shutter stays centered.
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun ClipReview(
    file: File,
    ui: RecordUiState,
    onSend: (File) -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(20.dp),
        ) {
            if (ui.errorMessage != null) {
                Text(
                    text = ui.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !ui.isSending,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.record_retake))
                }
                Button(
                    onClick = { onSend(file) },
                    enabled = !ui.isSending,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Coral,
                        disabledContainerColor = Coral.copy(alpha = 0.4f),
                    ),
                ) {
                    Text(
                        text = if (ui.isSending) {
                            stringResource(R.string.record_queuing)
                        } else {
                            stringResource(R.string.record_send)
                        },
                    )
                }
            }
        }
    }
}
