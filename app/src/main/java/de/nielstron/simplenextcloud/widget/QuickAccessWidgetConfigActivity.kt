package de.nielstron.simplenextcloud.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import de.nielstron.simplenextcloud.data.AccountStore
import de.nielstron.simplenextcloud.data.FavoritesStore
import de.nielstron.simplenextcloud.data.RecentStore
import de.nielstron.simplenextcloud.ui.SimpleNextcloudTheme
import kotlinx.coroutines.launch

/** Lets the person choose what a newly added (or reconfigured) widget shows. */
class QuickAccessWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val account = AccountStore(this).load()
        // Folders to offer: starred ones first, then recently used ones.
        val folders = account?.let { current ->
            (FavoritesStore(this).get(current) + RecentStore(this).list(current).map { it.file })
                .filter { it.isFolder }
                .distinctBy { it.path }
        }.orEmpty()

        fun choose(config: WidgetConfig) {
            WidgetStore(this).save(widgetId, config)
            lifecycleScope.launch {
                val context = this@QuickAccessWidgetConfigActivity
                QuickAccessWidget.refresh(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId))
                setResult(RESULT_OK, result)
                finish()
            }
        }

        setContent {
            SimpleNextcloudTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding()) {
                        Text(
                            "Show in widget",
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (account == null) {
                            Text(
                                "Log in to Simple Nextcloud first.",
                                Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            return@Column
                        }
                        LazyColumn {
                            item {
                                Choice(Icons.Outlined.Star, "Starred", "Files and folders you starred") {
                                    choose(WidgetConfig(WidgetSource.STARRED))
                                }
                                Choice(Icons.Outlined.Schedule, "Recent", "What you opened or uploaded to lately") {
                                    choose(WidgetConfig(WidgetSource.RECENT))
                                }
                                Text(
                                    "A folder",
                                    Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Choice(Icons.Outlined.Folder, "All files", "The top folder") {
                                    choose(WidgetConfig(WidgetSource.FOLDER, "", "All files"))
                                }
                            }
                            items(folders, key = { it.path }) { folder ->
                                Choice(Icons.Outlined.Folder, folder.name, "/${folder.path}") {
                                    choose(WidgetConfig(WidgetSource.FOLDER, folder.path, folder.name, folder.fileId))
                                }
                            }
                            item {
                                Text(
                                    "To show another folder here, star it or open it in the app first.",
                                    Modifier.padding(20.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
