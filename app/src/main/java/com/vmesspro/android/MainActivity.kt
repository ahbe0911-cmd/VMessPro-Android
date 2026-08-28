package com.vmesspro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vmesspro.android.ui.AppViewModel
import com.vmesspro.android.ui.CompactAppRoot
import com.vmesspro.android.ui.theme.VMessProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VMessProTheme {
                val appViewModel: AppViewModel = viewModel()
                CompactAppRoot(appViewModel)
            }
        }
    }
}
