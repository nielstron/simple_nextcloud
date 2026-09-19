package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.NextcloudException
import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudErrorMessageTest {
    @Test
    fun `explains common WebDAV failures`() {
        val expected = mapOf(
            401 to "Nextcloud rejected the saved login. Disconnect and log in again.",
            404 to "The file or folder no longer exists. Refresh the folder and try again.",
            413 to "The file is larger than this Nextcloud server allows.",
            423 to "The file or folder is locked by another operation. Try again shortly.",
            429 to "Nextcloud received too many requests. Wait a moment and try again.",
            507 to "Nextcloud does not have enough free storage for this operation.",
        )

        expected.forEach { (status, message) ->
            assertEquals(message, NextcloudException(status, "generic").userMessage())
        }
    }

    @Test
    fun `preserves a specific OCS error`() {
        assertEquals(
            "Sharing is disabled by your administrator",
            NextcloudException(403, "Sharing is disabled by your administrator").userMessage(),
        )
    }
}
