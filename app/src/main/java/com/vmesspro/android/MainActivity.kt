package com.vmesspro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vmesspro.android.ui.AppRoot
import com.vmesspro.android.ui.AppViewModel
import com.vmesspro.android.ui.qr.QrQuickImport
import com.vmesspro.android.ui.runtime.RuntimeStatusOverlay
import com.vmesspro.android.ui.theme.VMessProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VMessProTheme {
                val appViewModel: AppViewModel = viewModel()
                Box(Modifier.fillMaxSize()) {
                    AppRoot(appViewModel)
                    QrQuickImport(appViewModel)
                    RuntimeStatusOverlay(appViewModel)
                }
            }
        }
    }
}
