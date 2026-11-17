package com.nocturne.player

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nocturne.player.playback.PlayerConnection
import com.nocturne.player.ui.NocturneRoot
import com.nocturne.player.ui.UiEvent
import com.nocturne.player.ui.theme.NocturneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var playerConnection: PlayerConnection

    private val events = MutableStateFlow<UiEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NocturneTheme {
                NocturneRoot(events = events, onEventConsumed = { events.value = null })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        events.value = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { UiEvent.OpenLink(it) }
            Intent.ACTION_VIEW -> intent.dataString?.let { UiEvent.OpenLink(it) }
            ACTION_OPEN_NOW_PLAYING -> UiEvent.OpenNowPlaying
            ACTION_OPEN_DOWNLOADS -> UiEvent.OpenDownloads
            ACTION_OPEN_ADD_FROM_CLIPBOARD -> UiEvent.OpenAddFromClipboard
            else -> null
        } ?: events.value
    }

    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "com.nocturne.player.OPEN_NOW_PLAYING"
        const val ACTION_OPEN_DOWNLOADS = "com.nocturne.player.OPEN_DOWNLOADS"
        const val ACTION_OPEN_ADD_FROM_CLIPBOARD = "com.nocturne.player.OPEN_ADD_FROM_CLIPBOARD"
    }
}
