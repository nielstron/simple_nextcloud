package de.nielstron.simplenextcloud

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.nielstron.simplenextcloud.ui.UploadQueueDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UploadQueueDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overviewShowsProgressAndDetailsRevealNestedFilesWithCorrectNavigation() {
        val folder = UploadQueueItem(
            id = 1,
            name = "Photos",
            targetPath = "Uploads",
            isFolder = true,
            status = UploadStatus.UPLOADING,
            files = listOf(
                UploadFileProgress("first.jpg", "Uploads/Photos/first.jpg", 100, 100, UploadStatus.COMPLETED),
                UploadFileProgress("Nested/second.jpg", "Uploads/Photos/Nested/second.jpg", 900, 400, UploadStatus.UPLOADING),
            ),
        )
        var opened: UploadQueueItem? = null
        compose.setContent {
            MaterialTheme {
                UploadQueueDialog(listOf(folder), {}, {}, {}, { opened = it })
            }
        }
        compose.onNodeWithText("Uploading · 50%").assertIsDisplayed()
        compose.onNodeWithText("1/2 files uploaded").assertIsDisplayed()
        compose.onNodeWithText("Nested/second.jpg").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show files in Photos").performClick()
        compose.runOnIdle { assertEquals(null, opened) }
        compose.onNodeWithText("Nested/second.jpg").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Uploads/Photos/Nested", opened!!.targetPath)
            assertEquals("second.jpg", opened!!.name)
        }
        compose.onNodeWithContentDescription("Hide files in Photos").performScrollTo().performClick()
        compose.onNodeWithText("Nested/second.jpg").assertDoesNotExist()
    }

    @Test
    fun failedQueueOffersRetryFailed() {
        var retries = 0
        val failed = UploadQueueItem(2, "report.pdf", "", false, UploadStatus.FAILED, "Unable to resolve host")
        compose.setContent {
            MaterialTheme {
                UploadQueueDialog(listOf(failed), {}, {}, { retries++ }, {})
            }
        }

        compose.onNodeWithText("Retry failed").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
