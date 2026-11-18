package de.nielstron.simplenextcloud

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import de.nielstron.simplenextcloud.data.Account
import de.nielstron.simplenextcloud.data.CloudFile
import de.nielstron.simplenextcloud.data.RecentAction
import de.nielstron.simplenextcloud.data.RecentEntry
import de.nielstron.simplenextcloud.ui.FilesScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class QuickAccessTest {
    @get:Rule
    val compose = createComposeRule()

    private val account = Account("https://example.com", "test", "")
    private val taxes = CloudFile("Taxes", "Documents/Taxes", true, 0, null, null, null, fileId = "7", favorite = true)
    private val plan = CloudFile("Plan.pdf", "Plan.pdf", false, 2_000, "application/pdf", null, null, fileId = "8")

    private fun show(state: FileUiState, sharedUris: List<Uri> = emptyList()): FileViewModel {
        val model = FileViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            MaterialTheme { FilesScreen(state = state, model = model, sharedUris = sharedUris, onSharedUrisConsumed = {}) }
        }
        return model
    }

    @Test
    fun starredListsLocationsAndChipsSwitchViews() {
        val model = show(
            FileUiState(account = account, view = BrowseView.STARRED, favorites = listOf(taxes, plan.copy(favorite = true))),
        )
        compose.onNodeWithText("Taxes").assertIsDisplayed()
        compose.onNodeWithText("Documents").assertIsDisplayed()
        compose.onNodeWithText("All files · 2.0 kB").assertIsDisplayed()
        compose.onNodeWithText("Recent").performClick()
        compose.runOnIdle { assertEquals(BrowseView.RECENT, model.state.value.view) }
    }

    @Test
    fun sharingInOffersOnlyRecentFolders() {
        val now = System.currentTimeMillis()
        show(
            FileUiState(
                account = account,
                view = BrowseView.RECENT,
                recents = listOf(
                    RecentEntry(plan, RecentAction.OPENED, now),
                    RecentEntry(taxes, RecentAction.UPLOADED, now - 5 * 60_000),
                ),
            ),
            sharedUris = listOf(Uri.parse("content://test/1"), Uri.parse("content://test/2")),
        )
        compose.onNodeWithText("Last used folders").assertIsDisplayed()
        compose.onNodeWithText("Documents · uploaded here 5 min ago").assertIsDisplayed()
        compose.onNodeWithText("Plan.pdf").assertDoesNotExist()
        compose.onNodeWithText("2 files to upload").assertIsDisplayed()
    }

    @Test
    fun onlyFoldersCanBeAddedToTheHomeScreen() {
        show(FileUiState(account = account, files = listOf(taxes, plan)))
        compose.onNodeWithContentDescription("Options for Taxes").performClick()
        compose.onNodeWithText("Unstar").assertIsDisplayed()
        compose.onNodeWithText("Add to home screen").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Options for Plan.pdf").performClick()
        compose.onNodeWithText("Star").assertIsDisplayed()
        compose.onNodeWithText("Add to home screen").assertDoesNotExist()
    }

    @Test
    fun openFolderHasTheRowActionsAndSettingsHaveTheirOwnMenu() {
        show(FileUiState(account = account, path = taxes.path, files = listOf(plan), favorites = listOf(taxes)))
        compose.onNodeWithContentDescription("Folder options").performClick()
        listOf("Unstar", "Add to home screen", "Delete", "Copy", "Move", "Rename", "Share", "Download")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Show hidden files").assertDoesNotExist()
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Show hidden files").assertDoesNotExist()
    }

    @Test
    fun settingsMenuHoldsOnlyAppSettingsAndAllFilesHasNoFolderOptions() {
        show(FileUiState(account = account, files = listOf(taxes, plan)))
        compose.onNodeWithContentDescription("Folder options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").performClick()
        listOf("Show hidden files", "Upload queue", "Disconnect").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun intentsRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(
            QuickAccessRequest.OpenFolder(account.serverUrl, account.username, "Documents/Taxes", "7"),
            QuickAccessIntents.parse(QuickAccessIntents.openFolder(context, account, "Documents/Taxes", "7")),
        )
        assertEquals(
            QuickAccessRequest.OpenFile(account.serverUrl, account.username, plan),
            QuickAccessIntents.parse(QuickAccessIntents.openFile(context, account, plan)),
        )
        assertEquals(
            QuickAccessRequest.ShowView(account.serverUrl, account.username, BrowseView.STARRED),
            QuickAccessIntents.parse(QuickAccessIntents.showView(context, account, BrowseView.STARRED)),
        )
        assertNull(QuickAccessIntents.parse(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun shortcutForAnotherAccountExplainsInsteadOfOpening() {
        val model = FileViewModel(ApplicationProvider.getApplicationContext<Application>())
        model.handleQuickAccess(QuickAccessRequest.OpenFolder("https://other.example", "someone", "Taxes", "7"))
        val error = requireNotNull(model.state.value.error)
        assert("someone on https://other.example" in error) { error }
    }
}
