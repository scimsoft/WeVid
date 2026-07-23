package com.scimsoft.wevid.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scimsoft.wevid.R
import com.scimsoft.wevid.data.ChatSummary
import com.scimsoft.wevid.ui.theme.Coral
import com.scimsoft.wevid.ui.theme.InkElevated
import com.scimsoft.wevid.ui.theme.InkLine
import com.scimsoft.wevid.ui.theme.Mint
import com.scimsoft.wevid.ui.theme.PaperMuted
import java.text.DateFormat
import java.util.Date

@Composable
fun ChatsScreen(
    displayName: String?,
    chats: List<ChatSummary>?,
    newChatState: NewChatUiState,
    onStartChat: (username: String) -> Unit,
    onDismissNewChatError: () -> Unit,
    onOpenChat: (chatId: String, otherName: String) -> Unit,
    onBack: () -> Unit,
) {
    var showNewChat by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChat = true },
                containerColor = Coral,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.new_chat_title),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.chats_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!displayName.isNullOrBlank()) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PaperMuted,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                chats == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Coral)
                    }
                }
                chats.isEmpty() -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.chats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PaperMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(chats, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                onClick = {
                                    onOpenChat(chat.id, chat.other.displayName)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            state = newChatState,
            onStartChat = onStartChat,
            onDismiss = {
                showNewChat = false
                onDismissNewChatError()
            },
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkElevated)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (chat.lastThumbUrl != null) {
                AsyncImage(
                    model = chat.lastThumbUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(InkLine),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(InkLine),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chat.other.displayName.take(1).uppercase()
                            .ifEmpty { "@" },
                        style = MaterialTheme.typography.titleMedium,
                        color = PaperMuted,
                    )
                }
            }
            if (chat.hasUnseen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Mint),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.other.displayName.ifBlank { "@${chat.other.username}" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (chat.hasUnseen) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "@${chat.other.username}",
                style = MaterialTheme.typography.bodySmall,
                color = PaperMuted,
            )
        }

        val time = chat.lastMessageAt
        Text(
            text = if (time != null) {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
            } else {
                stringResource(R.string.chat_new)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (chat.hasUnseen) Mint else PaperMuted,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    state: NewChatUiState,
    onStartChat: (username: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = InkElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = stringResource(R.string.new_chat_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.new_chat_body),
                style = MaterialTheme.typography.bodyMedium,
                color = PaperMuted,
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it.lowercase()
                        .filter { ch -> ch.isLetterOrDigit() || ch == '_' }
                        .take(20)
                },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("@") },
                singleLine = true,
                enabled = !state.isSearching,
                placeholder = { Text(stringResource(R.string.onboarding_placeholder)) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Coral,
                    unfocusedBorderColor = InkLine,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    cursorColor = Coral,
                ),
            )
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onStartChat(username) },
                enabled = username.length >= 3 && !state.isSearching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Coral,
                    disabledContainerColor = Coral.copy(alpha = 0.4f),
                ),
            ) {
                if (state.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.new_chat_start),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
