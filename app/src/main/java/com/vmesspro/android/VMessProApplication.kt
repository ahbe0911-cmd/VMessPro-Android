package com.vmesspro.android

import android.app.Application
import android.os.Build
import android.util.Log
import com.vmesspro.android.core.LibboxBootstrap
import java.io.File

class VMessProApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName()
        if (processName != "$packageName:vpn") {
            // The UI process must not load the native sing-box runtime during app startup.
            return
        }

        runCatching {
            LibboxBootstrap.initialize(this)
        }.onFailure { error ->
            // A core bootstrap problem must stay isolated to the VPN process and must not
            // terminate the main application process before the UI is shown.
            Log.e(TAG, "libbox initialization failed in VPN process", error)
        }
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }

        return runCatching {
            File("/proc/self/cmdline").inputStream().use { input ->
                input.readBytes()
                    .toString(Charsets.UTF_8)
                    .substringBefore('\u0000')
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "VMessProApplication"
    }
}
