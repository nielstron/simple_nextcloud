package de.nielstron.simplenextcloud

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.nielstron.simplenextcloud.data.AccountStore
import de.nielstron.simplenextcloud.data.NextcloudClient
import de.nielstron.simplenextcloud.data.ServerNotification
import de.nielstron.simplenextcloud.data.TrustedCertificates
import java.util.concurrent.TimeUnit

/**
 * Shows the user's Nextcloud notifications (app updates, shares, mentions…) as Android
 * notifications. There is no push without Google services, so the server is polled about every
 * 15 minutes while the setting is on.
 */
object ServerNotifications {
    private const val PREFS = "server_notifications"
    private const val ENABLED = "enabled"
    private const val LAST_SEEN = "last_seen_id"
    private const val WORK = "server-notifications"
    private const val CHANNEL = "nextcloud"

    fun isEnabled(context: Context) = prefs(context).getBoolean(ENABLED, false)

    fun canPost(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, enabled).remove(LAST_SEEN).apply()
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK)
            return
        }
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        work.enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<Worker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
        )
        // Records what's already there right away, so only notifications from now on are shown.
        work.enqueue(OneTimeWorkRequestBuilder<Worker>().setConstraints(network).build())
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The notifications newer than [lastSeen]; none on the first check ([lastSeen] null). */
    internal fun newSince(lastSeen: Long?, all: List<ServerNotification>): List<ServerNotification> =
        if (lastSeen == null) emptyList() else all.filter { it.id > lastSeen }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val context = applicationContext
            if (!isEnabled(context)) return Result.success()
            val account = AccountStore(context).load() ?: return Result.success()
            val all = try {
                NextcloudClient(TrustedCertificates.client(context)).notifications(account)
            } catch (_: Exception) {
                return Result.retry()
            }
            val prefs = prefs(context)
            val lastSeen = if (prefs.contains(LAST_SEEN)) prefs.getLong(LAST_SEEN, 0) else null
            newSince(lastSeen, all).asReversed().forEach { show(context, it) }
            val newest = maxOf(all.maxOfOrNull(ServerNotification::id) ?: 0L, lastSeen ?: 0L)
            prefs.edit().putLong(LAST_SEEN, newest).apply()
            return Result.success()
        }
    }

    private fun show(context: Context, notification: ServerNotification) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nextcloud notifications", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = notification.link?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: Intent(context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(
            context,
            notification.id.toInt(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.subject.ifBlank { "Nextcloud" })
            .setContentText(notification.message.takeIf(String::isNotBlank))
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message.ifBlank { notification.subject }))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notification.id.toInt(), built)
        } catch (_: SecurityException) {
            // The permission was revoked between the check and posting.
        }
    }
}
