package com.vmesspro.android.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.vmesspro.android.data.local.AppDatabase
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferencesRepository
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AndroidCoreAdapter(context: Context) : CoreAdapter, AutoCloseable {
    private val appContext = context.applicationContext
    private val preferencesRepository = VpnPreferencesRepository(appContext)
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "vmesspro.db")
        .fallbackToDestructiveMigration()
        .build()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != CoreContract.ACTION_STATE) return
            _state.value = when (intent.getStringExtra(CoreContract.EXTRA_STATE)) {
                CoreContract.STATE_PREPARING -> ConnectionState.Preparing
                CoreContract.STATE_CONNECTING -> ConnectionState.Connecting
                CoreContract.STATE_VERIFYING -> ConnectionState.Verifying
                CoreContract.STATE_CONNECTED -> ConnectionState.Connected(
                    intent.getLongExtra(CoreContract.EXTRA_SINCE, System.currentTimeMillis())
                )
                CoreContract.STATE_RECONNECTING -> ConnectionState.Reconnecting
                CoreContract.STATE_ERROR -> ConnectionState.Error(
                    intent.getStringExtra(CoreContract.EXTRA_REASON) ?: "خطای نامشخص Core"
                )
                else -> ConnectionState.Disconnected
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            stateReceiver,
            IntentFilter(CoreContract.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override suspend fun connect(profileId: String) {
        val preferences = preferencesRepository.preferences.first()
        _state.value = ConnectionState.Preparing

        val include = preferences.includedPackages.toMutableSet()
        val exclude = preferences.excludedPackages.toMutableSet()
        when (preferences.splitTunnelMode) {
            SplitTunnelMode.ONLY_SELECTED -> include += appContext.packageName
            SplitTunnelMode.EXCLUDE_SELECTED -> exclude -= appContext.packageName
        }

        val intent = Intent(appContext, VpnCoreService::class.java).apply {
            action = CoreContract.ACTION_CONNECT
            putExtra(CoreContract.EXTRA_PROFILE_ID, profileId)
            putExtra(CoreContract.EXTRA_SPLIT_MODE, preferences.splitTunnelMode.name)
            putStringArrayListExtra(CoreContract.EXTRA_INCLUDED, ArrayList(include))
            putStringArrayListExtra(CoreContract.EXTRA_EXCLUDED, ArrayList(exclude))
            putStringArrayListExtra(CoreContract.EXTRA_BANKING, ArrayList(preferences.bankingPackages))
            putExtra(CoreContract.EXTRA_CUSTOM_DNS, preferences.customDns)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    override suspend fun disconnect() {
        appContext.startService(
            Intent(appContext, VpnCoreService::class.java).apply {
                action = CoreContract.ACTION_DISCONNECT
            }
        )
    }

    override suspend fun probe(profileId: String): ProbeResult = withContext(Dispatchers.IO) {
        val node = database.nodeDao().getById(profileId)
            ?: return@withContext ProbeResult(null, null, false, "سرور پیدا نشد")
        var success = false
        val latency = runCatching {
            measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(node.host, node.port), 4_000)
                    success = socket.isConnected
                }
            }
        }.getOrNull()
        ProbeResult(
            tcpLatencyMs = latency,
            httpRttMs = null,
            success = success,
            error = if (success) null else "TCP probe failed",
        )
    }

    override fun close() {
        runCatching { appContext.unregisterReceiver(stateReceiver) }
        database.close()
    }
}
