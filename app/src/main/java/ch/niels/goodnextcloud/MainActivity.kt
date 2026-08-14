package ch.niels.goodnextcloud

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import ch.niels.goodnextcloud.ui.SimpleNextcloudApp
import ch.niels.goodnextcloud.ui.SimpleNextcloudTheme

class MainActivity : ComponentActivity() {
    private val sharedUris = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveShareIntent(intent)
        setContent {
            SimpleNextcloudTheme {
                val model: FileViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                SimpleNextcloudApp(
                    state = state,
                    model = model,
                    sharedUris = sharedUris.value,
                    onSharedUrisConsumed = { sharedUris.value = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShareIntent(intent)
    }

    private fun receiveShareIntent(intent: Intent) {
        sharedUris.value = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ).orEmpty()
            else -> emptyList()
        }
    }
}
