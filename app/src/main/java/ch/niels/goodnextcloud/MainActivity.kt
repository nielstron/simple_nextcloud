package ch.niels.goodnextcloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.niels.goodnextcloud.ui.GoodNextcloudApp
import ch.niels.goodnextcloud.ui.GoodNextcloudTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoodNextcloudTheme {
                val model: FileViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                GoodNextcloudApp(state, model)
            }
        }
    }
}
