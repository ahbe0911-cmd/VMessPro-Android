package com.vmesspro.android.core

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import androidx.room.Room
import com.vmesspro.android.data.local.AppDatabase
import com.vmesspro.android.data.security.SecureConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs real Xray probes outside the app and VPN processes. Native Xray probe runtimes are
 * isolated per worker process so one-click group tests can be parallel without sharing native
 * singleton state.
 */
open class XrayProbeWorkerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestMutex = Mutex()
    private val secureStore = SecureConfigStore()
    private val tester by lazy { XrayProfileTester(applicationContext) }
    private val databaseDelegate = lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "vmesspro.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build()
    }
    private val database by databaseDelegate

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_PROBE) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val receiver = intent.resultReceiverExtra(EXTRA_RESULT_RECEIVER)
        if (profileId.isBlank() || receiver == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            val result = requestMutex.withLock {
                runCatching { probe(profileId) }
                    .getOrElse { error ->
                        Log.w(TAG, "Xray probe worker failed for $profileId", error)
                        ProbeResult(
                            tcpLatencyMs = null,
                            httpRttMs = null,
                            success = false,
                            error = error.message ?: "خطای پردازش تست Xray",
                        )
                    }
            }
            receiver.send(
                if (result.success) RESULT_SUCCESS else RESULT_FAILURE,
                result.toBundle(),
            )
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (databaseDelegate.isInitialized()) runCatching { database.close() }
        super.onDestroy()
    }

    private suspend fun probe(profileId: String): ProbeResult {
        val node = database.nodeDao().getById(profileId)
            ?: return ProbeResult(null, null, false, "سرور پیدا نشد")
        val rawConfig = runCatching { secureStore.decrypt(node.encryptedConfig) }
            .getOrElse { error ->
                return ProbeResult(
                    tcpLatencyMs = null,
                    httpRttMs = null,
                    success = false,
                    error = error.message ?: "خواندن کانفیگ ناموفق بود",
                )
            }
        return tester.test(rawConfig, node.host)
    }

    private fun ProbeResult.toBundle() = Bundle().apply {
        putBoolean(EXTRA_SUCCESS, success)
        tcpLatencyMs?.let { putLong(EXTRA_TCP_LATENCY, it) }
        httpRttMs?.let { putLong(EXTRA_HTTP_RTT, it) }
        error?.let { putString(EXTRA_ERROR, it) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.resultReceiverExtra(key: String): ResultReceiver? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, ResultReceiver::class.java)
        } else {
            getParcelableExtra(key)
        }

    internal companion object {
        const val ACTION_PROBE = "com.vmesspro.android.core.PROBE_XRAY"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_TCP_LATENCY = "tcp_latency"
        const val EXTRA_HTTP_RTT = "http_rtt"
        const val EXTRA_ERROR = "error"
        const val RESULT_SUCCESS = 1
        const val RESULT_FAILURE = 2
        private const val TAG = "XrayProbeWorker"
    }
}

class XrayProbeWorker0Service : XrayProbeWorkerService()
class XrayProbeWorker1Service : XrayProbeWorkerService()
class XrayProbeWorker2Service : XrayProbeWorkerService()
