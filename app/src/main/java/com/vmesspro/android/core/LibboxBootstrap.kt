package com.vmesspro.android.core

import android.content.Context
import android.util.Log
import com.vmesspro.android.BuildConfig
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import java.util.Locale

/**
 * Initializes libbox only inside the dedicated VPN process.
 *
 * Keeping native sing-box loading out of the UI process prevents a native/runtime
 * bootstrap failure from taking down the whole application before MainActivity can render.
 */
object LibboxBootstrap {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext
        val baseDir = appContext.filesDir.apply { mkdirs() }
        val workingDir = (appContext.getExternalFilesDir(null) ?: File(baseDir, "sing-box")).apply { mkdirs() }
        val tempDir = File(appContext.cacheDir, "sing-box").apply { mkdirs() }

        runCatching {
            // sing-box expects the normal Android/BCP-47 language tag here.
            Libbox.setLocale(Locale.getDefault().toLanguageTag())
        }.onFailure { error ->
            // Locale selection is optional; keep the core usable with its default locale.
            Log.w(TAG, "Unable to apply libbox locale", error)
        }

        Libbox.setup(
            SetupOptions().apply {
                basePath = baseDir.absolutePath
                workingPath = workingDir.absolutePath
                tempPath = tempDir.absolutePath
                fixAndroidStack = true
                logMaxLines = 2_000
                debug = BuildConfig.DEBUG
            }
        )

        initialized = true
        Log.i(TAG, "libbox initialized in dedicated VPN process")
    }

    private const val TAG = "LibboxBootstrap"
}
