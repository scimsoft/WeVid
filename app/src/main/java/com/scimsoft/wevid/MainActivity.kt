package com.scimsoft.wevid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.scimsoft.wevid.ui.nav.PushDestination
import com.scimsoft.wevid.ui.nav.WeVidNavGraph
import com.scimsoft.wevid.ui.theme.WeVidTheme

class MainActivity : ComponentActivity() {

    private var pushDestination by mutableStateOf<PushDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            false

        pushDestination = intent?.toPushDestination()

        val app = application as WeVidApp
        setContent {
            WeVidTheme {
                WeVidNavGraph(
                    container = app.container,
                    pushDestination = pushDestination,
                    onPushConsumed = { pushDestination = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pushDestination = intent.toPushDestination()
    }

    private fun Intent.toPushDestination(): PushDestination? {
        val chatId = getStringExtra("chatId") ?: return null
        return PushDestination(
            chatId = chatId,
            title = getStringExtra("chatTitle").orEmpty(),
        )
    }
}
