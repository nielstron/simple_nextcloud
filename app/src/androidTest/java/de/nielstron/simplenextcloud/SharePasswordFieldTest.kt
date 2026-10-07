package de.nielstron.simplenextcloud

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import de.nielstron.simplenextcloud.ui.SharePasswordField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SharePasswordFieldTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun passwordStartsVisibleAndCanBeHiddenWithoutChangingItsValue() {
        val password = mutableStateOf("share-secret")
        compose.setContent {
            MaterialTheme {
                SharePasswordField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = "Password (optional)",
                )
            }
        }

        val field = compose.onNodeWithText("Password (optional)")
        field.assertTextEquals("Password (optional)", "share-secret")
        compose.onNodeWithContentDescription("Hide password").performClick()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("•".repeat(12))))
        field.performTextReplacement("changed-secret")
        compose.onNodeWithContentDescription("Show password").performClick()
        field.assertTextEquals("Password (optional)", "changed-secret")
        compose.runOnIdle { assertEquals("changed-secret", password.value) }
    }
}
