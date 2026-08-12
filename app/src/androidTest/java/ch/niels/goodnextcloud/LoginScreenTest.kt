package ch.niels.goodnextcloud

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsFocusedLoginAndRequiresAllCredentials() {
        compose.onNodeWithText("Your cloud. Simply.").assertIsDisplayed()
        compose.onNodeWithText("Nextcloud address").assertIsDisplayed()
        compose.onNodeWithText("Username").assertIsDisplayed()
        compose.onNodeWithText("App password").assertIsDisplayed()
        compose.onNodeWithText("Connect").assertIsNotEnabled()
    }
}
