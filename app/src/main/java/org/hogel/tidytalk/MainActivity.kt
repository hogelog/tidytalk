package org.hogel.tidytalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.hogel.tidytalk.ui.TidyTalkApp
import org.hogel.tidytalk.ui.theme.TidyTalkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TidyTalkTheme {
                TidyTalkApp()
            }
        }
    }
}
