package com.vmesspro.android

import android.app.Application
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import java.util.Locale

class VMessProApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val workingDir = File(filesDir, "sing-box").apply { mkdirs() }
        val tempDir = File(cacheDir, "sing-box").apply { mkdirs() }

        Libbox.setLocale(Locale.getDefault().toLanguageTag().replace('-', '_'))
        Libbox.setup(
            SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = workingDir.absolutePath
                tempPath = tempDir.absolutePath
                fixAndroidStack = true
                logMaxLines = 2_000
                debug = BuildConfig.DEBUG
            }
        )
    }
}
