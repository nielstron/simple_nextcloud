package de.nielstron.simplenextcloud

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import de.nielstron.simplenextcloud.data.Account
import de.nielstron.simplenextcloud.data.CloudFile
import de.nielstron.simplenextcloud.ui.FilesScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FileSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val files = listOf("first.txt", "second.txt", "third.txt").map {
        CloudFile(it, it, false, 42, "text/plain", null, null)
    }

    private fun showFiles(): FileViewModel {
        val model = FileViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            MaterialTheme {
                FilesScreen(
                    state = FileUiState(account = Account("https://example.com", "test", ""), files = files),
                    model = model,
                    sharedUris = emptyList(),
                    onSharedUrisConsumed = {},
                )
            }
        }
        return model
    }

    @Test
    fun longPressSelectsAndTapsToggleUntilCopiedToClipboard() {
        val model = showFiles()
        compose.onNodeWithText("first.txt").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("second.txt").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("first.txt").performClick()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("third.txt").performClick()
        compose.onNodeWithContentDescription("Copy selected files").performClick()
        compose.onNodeWithText("2 selected").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(files.drop(1), model.state.value.clipboardFiles)
            assertEquals(ClipboardMode.COPY, model.state.value.clipboardMode)
        }
    }

    @Test
    fun cutStagesAllSelectedFilesAndDeleteCanBeCancelled() {
        val model = showFiles()
        compose.onNodeWithText("first.txt").performTouchInput { longClick() }
        compose.onNodeWithText("second.txt").performClick()
        compose.onNodeWithContentDescription("Delete selected files").performClick()
        compose.onNodeWithText("Delete 2 items?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cut selected files").performClick()
        compose.runOnIdle {
            assertEquals(files.take(2), model.state.value.clipboardFiles)
            assertEquals(ClipboardMode.MOVE, model.state.value.clipboardMode)
        }
    }
}
