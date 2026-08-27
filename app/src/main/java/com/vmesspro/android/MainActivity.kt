package com.vmesspro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vmesspro.android.ui.AppRoot
import com.vmesspro.android.ui.theme.VMessProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VMessProTheme {
                AppRoot()
            }
        }
    }
}
