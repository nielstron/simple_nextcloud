package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.NextcloudException
import org.junit.Assert.assertEquals
import org.junit.Test

class RenameFailureMessageTest {
    @Test
    fun `explains a rename conflict`() {
        assertEquals(
            "A file or folder named “report.pdf” already exists here. Choose a different name.",
            renameFailureMessage("report.pdf", NextcloudException(412, "Precondition Failed")),
        )
    }

    @Test
    fun `preserves other server errors`() {
        assertEquals(
            "Nextcloud returned 500: Internal Server Error",
            renameFailureMessage("report.pdf", NextcloudException(500, "Internal Server Error")),
        )
    }
}
