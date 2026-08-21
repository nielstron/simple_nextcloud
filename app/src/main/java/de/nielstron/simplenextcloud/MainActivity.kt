package de.nielstron.simplenextcloud

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.nielstron.simplenextcloud.data.NextcloudPath
import de.nielstron.simplenextcloud.data.isLocalNetworkAddress
import de.nielstron.simplenextcloud.ui.SimpleNextcloudApp
import de.nielstron.simplenextcloud.ui.SimpleNextcloudTheme
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: FileViewModel by viewModels()
    private val sharedUris = mutableStateOf<List<Uri>>(emptyList())
    private var pendingLocalNetworkAction: (() -> Unit)? = null
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingLocalNetworkAction
        pendingLocalNetworkAction = null
        if (granted) action?.invoke() else model.localNetworkPermissionDenied()
    }
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
                val state by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.account?.serverUrl) {
                    state.account?.serverUrl?.let { server ->
                        requestLocalNetworkAccessForStoredAccount(server)
                    }
                }
                SimpleNextcloudApp(
                    state = state,
                    model = model,
                    sharedUris = sharedUris.value,
                    onSharedUrisConsumed = { sharedUris.value = emptyList() },
                    onStartBrowserLogin = { server ->
                        withLocalNetworkAccess(server) { model.startBrowserLogin(server) }
                    },
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

    private fun withLocalNetworkAccess(server: String, action: () -> Unit) {
        if (Build.VERSION.SDK_INT < ANDROID_17_API || hasLocalNetworkPermission()) {
            action()
            return
        }
        lifecycleScope.launch {
            val resolvesLocally = serverResolvesLocally(server)
            if (!resolvesLocally || hasLocalNetworkPermission()) {
                action()
            } else {
                pendingLocalNetworkAction = action
                localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
            }
        }
    }

    private fun requestLocalNetworkAccessForStoredAccount(server: String) {
        if (Build.VERSION.SDK_INT < ANDROID_17_API || hasLocalNetworkPermission()) return
        lifecycleScope.launch {
            if (serverResolvesLocally(server) && !hasLocalNetworkPermission()) {
                pendingLocalNetworkAction = model::refresh
                localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
            }
        }
    }

    private suspend fun serverResolvesLocally(server: String) = withContext(Dispatchers.IO) {
        runCatching {
            val host = URI(NextcloudPath.normalizeServerUrl(server)).host
            host != null && InetAddress.getAllByName(host).any(InetAddress::isLocalNetworkAddress)
        }.getOrDefault(false)
    }

    private fun hasLocalNetworkPermission() = ContextCompat.checkSelfPermission(
        this,
        LOCAL_NETWORK_PERMISSION,
    ) == PackageManager.PERMISSION_GRANTED

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
        const val ANDROID_17_API = 37
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
