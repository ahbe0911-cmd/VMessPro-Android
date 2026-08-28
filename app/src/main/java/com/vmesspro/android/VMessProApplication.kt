package com.vmesspro.android

import android.app.Application
import go.Seq

class VMessProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // gomobile/libXray needs the Android application context. XrayConfigFactory also
        // refreshes this before native operations, but initializing once here matches the
        // upstream Amnezia lifecycle and removes all legacy sing-box/libbox setup.
        Seq.setContext(this)
    }
}
