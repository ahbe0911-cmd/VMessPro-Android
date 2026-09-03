package com.vmesspro.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.vmesspro.android.ui.AppRoot
import com.vmesspro.android.ui.AppViewModel
import com.vmesspro.android.ui.qr.QrQuickImport
import com.vmesspro.android.ui.theme.VMessProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModelResult = runCatching {
            ViewModelProvider(this)[AppViewModel::class.java]
        }

        val appViewModel = viewModelResult.getOrNull()
        if (appViewModel == null) {
            val startupError = viewModelResult.exceptionOrNull()
            setContent {
                VMessProTheme {
                    StartupRecoveryScreen(
                        error = startupError,
                        onResetLocalDatabase = {
                            runCatching { applicationContext.deleteDatabase(DATABASE_NAME) }
                            recreate()
                        },
                        onRetry = { recreate() },
                    )
                }
            }
            return
        }

        runCatching {
            setContent {
                VMessProTheme {
                    Box(Modifier.fillMaxSize()) {
                        AppRoot(appViewModel)
                        QrQuickImport(appViewModel)
                    }
                }
            }
        }.onFailure { startupError ->
            setContent {
                VMessProTheme {
                    StartupRecoveryScreen(
                        error = startupError,
                        onResetLocalDatabase = {
                            runCatching { applicationContext.deleteDatabase(DATABASE_NAME) }
                            recreate()
                        },
                        onRetry = { recreate() },
                    )
                }
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "vmesspro.db"
    }
}

@Composable
private fun StartupRecoveryScreen(
    error: Throwable?,
    onResetLocalDatabase: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "حالت بازیابی VMess Pro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "برنامه هنگام آماده‌سازی داده‌ها با خطا روبه‌رو شد. خود رابط برنامه در حالت امن نگه داشته شده تا کرش تکرار نشود.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = error?.let { "${it::class.java.simpleName}: ${it.message ?: "بدون پیام"}" }
                    ?: "خطای نامشخص در شروع برنامه",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onResetLocalDatabase,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("بازنشانی داده‌های محلی و اجرای دوباره")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("تلاش دوباره بدون حذف اطلاعات")
            }
        }
    }
}
