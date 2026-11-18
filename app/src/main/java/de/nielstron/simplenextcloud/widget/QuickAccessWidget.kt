package de.nielstron.simplenextcloud.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.nielstron.simplenextcloud.BrowseView
import de.nielstron.simplenextcloud.QuickAccessIntents
import de.nielstron.simplenextcloud.R
import de.nielstron.simplenextcloud.data.Account
import de.nielstron.simplenextcloud.data.AccountStore
import de.nielstron.simplenextcloud.data.CloudFile
import de.nielstron.simplenextcloud.data.FavoritesStore
import de.nielstron.simplenextcloud.data.NextcloudClient
import de.nielstron.simplenextcloud.data.NextcloudException
import de.nielstron.simplenextcloud.data.RecentStore
import de.nielstron.simplenextcloud.data.TrustedCertificates
import de.nielstron.simplenextcloud.data.decodeCloudFiles
import de.nielstron.simplenextcloud.data.encodeCloudFiles
import de.nielstron.simplenextcloud.sortedFiles
import de.nielstron.simplenextcloud.ui.DarkColors
import de.nielstron.simplenextcloud.ui.LightColors
import de.nielstron.simplenextcloud.ui.formatSize
import de.nielstron.simplenextcloud.ui.locationSubtitle
import de.nielstron.simplenextcloud.ui.recentSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WidgetSource { STARRED, RECENT, FOLDER }

/** What one widget shows. For [WidgetSource.FOLDER], [fileId] lets it follow the folder when moved. */
data class WidgetConfig(
    val source: WidgetSource,
    val folderPath: String = "",
    val folderName: String = "",
    val fileId: String? = null,
)

/** Per-widget settings and the last list shown, so a widget still shows something offline. */
class WidgetStore(context: Context) {
    private val preferences = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun config(widgetId: Int): WidgetConfig? {
        val source = preferences.getString("$widgetId.source", null) ?: return null
        return WidgetConfig(
            WidgetSource.valueOf(source),
            preferences.getString("$widgetId.path", null)!!,
            preferences.getString("$widgetId.name", null)!!,
            preferences.getString("$widgetId.fileId", null),
        )
    }

    fun save(widgetId: Int, config: WidgetConfig) = preferences.edit()
        .putString("$widgetId.source", config.source.name)
        .putString("$widgetId.path", config.folderPath)
        .putString("$widgetId.name", config.folderName)
        .putString("$widgetId.fileId", config.fileId)
        .apply()

    fun lastItems(widgetId: Int): List<CloudFile> =
        decodeCloudFiles(preferences.getString("$widgetId.items", null) ?: return emptyList())

    fun saveItems(widgetId: Int, files: List<CloudFile>) =
        preferences.edit().putString("$widgetId.items", encodeCloudFiles(files)).apply()

    fun remove(widgetId: Int) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("$widgetId.") }.forEach(editor::remove)
        editor.apply()
    }
}

private data class WidgetItem(val file: CloudFile, val subtitle: String)

private sealed interface WidgetContent {
    data object LoggedOut : WidgetContent
    data object NotConfigured : WidgetContent
    data class Ready(
        val account: Account,
        val config: WidgetConfig,
        val title: String,
        val items: List<WidgetItem>,
        val emptyMessage: String,
    ) : WidgetContent
}

class QuickAccessWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // update() only recomposes a running session, so the list is loaded here, keyed on a
            // stamp that refresh() bumps, rather than once before provideContent.
            val stamp = currentState(REFRESH_STAMP)
            val content by produceState<WidgetContent?>(null, stamp) {
                value = withContext(Dispatchers.IO) { load(context, widgetId) }
            }
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                WidgetBody(context, content)
            }
        }
    }

    companion object {
        private val REFRESH_STAMP = longPreferencesKey("refresh")

        /** Reloads one widget's list, also when its session is still running. */
        suspend fun refresh(context: Context, id: GlanceId) {
            updateAppWidgetState(context, id) { it[REFRESH_STAMP] = System.nanoTime() }
            QuickAccessWidget().update(context, id)
        }
    }

    private fun load(context: Context, widgetId: Int): WidgetContent {
        val account = AccountStore(context).load() ?: return WidgetContent.LoggedOut
        val store = WidgetStore(context)
        val config = store.config(widgetId) ?: return WidgetContent.NotConfigured
        val client = NextcloudClient(TrustedCertificates.client(context))
        val now = System.currentTimeMillis()
        return when (config.source) {
            WidgetSource.RECENT -> WidgetContent.Ready(
                account,
                config,
                "Recent",
                RecentStore(context).list(account).map { WidgetItem(it.file, recentSubtitle(it, now)) },
                "Folders and files you open appear here",
            )
            WidgetSource.STARRED -> {
                val favorites = FavoritesStore(context)
                // Offline, the last fetched list is still worth showing.
                val files = runCatching { client.favorites(account).sortedFiles().also { favorites.put(account, it) } }
                    .getOrElse { favorites.get(account) }
                WidgetContent.Ready(
                    account,
                    config,
                    "Starred",
                    files.map { WidgetItem(it, locationSubtitle(it)) },
                    "Nothing starred yet",
                )
            }
            WidgetSource.FOLDER -> {
                var current = config
                val files = runCatching {
                    try {
                        client.list(account, current.folderPath)
                    } catch (failure: NextcloudException) {
                        val fileId = current.fileId
                        if (failure.statusCode != 404 || fileId == null) throw failure
                        // The folder was moved or renamed: follow it by its file ID.
                        val moved = client.findByFileId(account, fileId) ?: throw failure
                        current = current.copy(folderPath = moved.path, folderName = moved.name)
                        store.save(widgetId, current)
                        client.list(account, moved.path)
                    }.sortedFiles().also { store.saveItems(widgetId, it) }
                }.getOrElse { store.lastItems(widgetId) }
                WidgetContent.Ready(
                    account,
                    current,
                    current.folderName,
                    files.map { WidgetItem(it, if (it.isFolder) "Folder" else formatSize(it.size)) },
                    "This folder is empty",
                )
            }
        }
    }
}

@Composable
private fun WidgetBody(context: Context, content: WidgetContent?) {
    Column(
        GlanceModifier
            .fillMaxSize()
            // The app's color scheme defines no container colors, so widgetBackground would be lavender.
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        when (content) {
            null -> Message("Loading…")
            WidgetContent.LoggedOut -> Message("Log in to Simple Nextcloud to use this widget")
            WidgetContent.NotConfigured -> Message("Long-press this widget to choose what it shows")
            is WidgetContent.Ready -> {
                Header(context, content)
                Spacer(GlanceModifier.height(6.dp))
                if (content.items.isEmpty()) Message(content.emptyMessage)
                else LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(content.items, itemId = { it.file.path.hashCode().toLong() }) { item ->
                        ItemRow(context, content.account, item)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(context: Context, content: WidgetContent.Ready) {
    val config = content.config
    val open = when (config.source) {
        WidgetSource.STARRED -> QuickAccessIntents.showView(context, content.account, BrowseView.STARRED)
        WidgetSource.RECENT -> QuickAccessIntents.showView(context, content.account, BrowseView.RECENT)
        WidgetSource.FOLDER -> QuickAccessIntents.openFolder(context, content.account, config.folderPath, config.fileId)
    }
    val icon = when (config.source) {
        WidgetSource.STARRED -> R.drawable.ic_widget_star
        WidgetSource.RECENT -> R.drawable.ic_widget_recent
        WidgetSource.FOLDER -> R.drawable.ic_widget_folder
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().clickable(actionStartActivity(open)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(icon),
                null,
                GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                content.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Image(
            ImageProvider(R.drawable.ic_widget_refresh),
            "Refresh",
            GlanceModifier.size(36.dp).padding(8.dp).clickable(actionRunCallback<RefreshWidget>()),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun ItemRow(context: Context, account: Account, item: WidgetItem) {
    val file = item.file
    val open = if (file.isFolder) QuickAccessIntents.openFolder(context, account, file.path, file.fileId)
    else QuickAccessIntents.openFile(context, account, file)
    val icon = when {
        file.isFolder -> R.drawable.ic_widget_folder
        file.mimeType?.startsWith("image/") == true -> R.drawable.ic_widget_image
        else -> R.drawable.ic_widget_file
    }
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(icon),
            null,
            GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(if (file.isFolder) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(file.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1)
            Text(item.subtitle, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        GlanceModifier.fillMaxWidth().padding(vertical = 12.dp),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
    )
}

class RefreshWidget : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        QuickAccessWidget.refresh(context, glanceId)
    }
}

class QuickAccessWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickAccessWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glance = GlanceAppWidgetManager(context)
                appWidgetIds.forEach { QuickAccessWidget.refresh(context, glance.getGlanceIdBy(it)) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = WidgetStore(context)
        appWidgetIds.forEach(store::remove)
    }

    companion object {
        /** Redraws every widget, for example after the app changed recents or stars. */
        fun requestUpdate(context: Context) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, QuickAccessWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, QuickAccessWidgetReceiver::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
