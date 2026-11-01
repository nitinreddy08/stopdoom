package app.unrot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.unrot.ui.debug.DebugScreen
import app.unrot.ui.home.TodayScreen
import app.unrot.ui.theme.UnrotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as UnrotApp).container
        setContent {
            UnrotTheme {
                // The debug panel can never appear in a release build.
                if (BuildConfig.DEBUG) {
                    DebugScreen(container)
                } else {
                    TodayScreen()
                }
            }
        }
    }
}
