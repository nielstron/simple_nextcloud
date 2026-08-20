package de.nielstron.simplenextcloud

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import de.nielstron.simplenextcloud.ui.SimpleNextcloudApp
import de.nielstron.simplenextcloud.ui.SimpleNextcloudTheme

class MainActivity : ComponentActivity() {
    private val sharedUris = mutableStateOf<List<Uri>>(emptyList())
    private val pendingLoginTabSession by lazy {
        CustomTabsClient.newPendingSession(this, CustomTabsCallback(), LOGIN_TAB_SESSION_ID)
    }
    private var loginTabSession: CustomTabsSession? = null
    private var loginTabsBound = false
    private val loginTabsConnection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            client.warmup(0)
            loginTabSession = client.attachSession(pendingLoginTabSession)
                ?: client.newSession(CustomTabsCallback(), LOGIN_TAB_SESSION_ID)
                ?: client.newSession(CustomTabsCallback())
        }

        override fun onServiceDisconnected(name: ComponentName) {
            loginTabSession = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindLoginTabs()
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
                    onOpenLoginUrl = ::openLoginTab,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShareIntent(intent)
    }

    override fun onDestroy() {
        if (loginTabsBound) unbindService(loginTabsConnection)
        super.onDestroy()
    }

    private fun bindLoginTabs() {
        val browserPackage = CustomTabsClient.getPackageName(this, null) ?: return
        loginTabsBound = CustomTabsClient.bindCustomTabsService(
            this,
            browserPackage,
            loginTabsConnection,
        )
    }

    private fun openLoginTab(url: String) {
        val builder = loginTabSession?.let { CustomTabsIntent.Builder(it) }
            ?: CustomTabsIntent.Builder().setPendingSession(pendingLoginTabSession)
        builder
            .setShowTitle(true)
            .build()
            .launchUrl(this, Uri.parse(url))
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

    private companion object {
        const val LOGIN_TAB_SESSION_ID = 1
    }
}
