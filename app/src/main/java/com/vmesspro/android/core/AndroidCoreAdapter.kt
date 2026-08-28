package com.vmesspro.android.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.vmesspro.android.data.local.AppDatabase
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferencesRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidCoreAdapter(context: Context) : CoreAdapter, AutoCloseable {
    private val appContext = context.applicationContext
    private val preferencesRepository = VpnPreferencesRepository(appContext)
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeWorkerCounter = AtomicInteger(0)
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "vmesspro.db")
        .enableMultiInstanceInvalidation()
        .fallbackToDestructiveMigration()
        .build()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _telemetry = MutableStateFlow(VpnTelemetry())
    override val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CoreContract.ACTION_TELEMETRY -> {
                    _telemetry.value = VpnTelemetry(
                        uploadBytesPerSecond = intent.getLongExtra(CoreContract.EXTRA_UPLINK, 0L).coerceAtLeast(0L),
                        downloadBytesPerSecond = intent.getLongExtra(CoreContract.EXTRA_DOWNLINK, 0L).coerceAtLeast(0L),
                        uploadedBytesTotal = intent.getLongExtra(CoreContract.EXTRA_UPLINK_TOTAL, 0L).coerceAtLeast(0L),
                        downloadedBytesTotal = intent.getLongExtra(CoreContract.EXTRA_DOWNLINK_TOTAL, 0L).coerceAtLeast(0L),
                        activeConnectionsIn = intent.getIntExtra(CoreContract.EXTRA_CONNECTIONS_IN, 0).coerceAtLeast(0),
                        activeConnectionsOut = intent.getIntExtra(CoreContract.EXTRA_CONNECTIONS_OUT, 0).coerceAtLeast(0),
                        trafficAvailable = intent.getBooleanExtra(CoreContract.EXTRA_TRAFFIC_AVAILABLE, false),
                    )
                }

                CoreContract.ACTION_STATE -> {
                    val nextState = when (intent.getStringExtra(CoreContract.EXTRA_STATE)) {
                        CoreContract.STATE_PREPARING -> ConnectionState.Preparing
                        CoreContract.STATE_CONNECTING -> ConnectionState.Connecting
                        CoreContract.STATE_VERIFYING -> ConnectionState.Verifying
                        CoreContract.STATE_RECONNECTING -> ConnectionState.Reconnecting
                        CoreContract.STATE_CONNECTED -> {
                            val activeProfileId = intent.getStringExtra(CoreContract.EXTRA_PROFILE_ID)
                            if (!activeProfileId.isNullOrBlank()) {
                                adapterScope.launch {
                                    preferencesRepository.setSelectedNode(activeProfileId)
                                }
                            }
                            ConnectionState.Connected(
                                intent.getLongExtra(
                                    CoreContract.EXTRA_SINCE,
                                    System.currentTimeMillis(),
                                )
                            )
                        }

                        CoreContract.STATE_ERROR -> ConnectionState.Error(
                            intent.getStringExtra(CoreContract.EXTRA_REASON) ?: "خطای نامشخص Xray"
                        )

                        else -> ConnectionState.Disconnected
                    }
                    _state.value = nextState
                    if (nextState is ConnectionState.Disconnected || nextState is ConnectionState.Error) {
                        _telemetry.value = VpnTelemetry()
                    }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            stateReceiver,
            IntentFilter().apply {
                addAction(CoreContract.ACTION_STATE)
                addAction(CoreContract.ACTION_TELEMETRY)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override suspend fun connect(profileId: String) {
        val rankedCandidates = SmartNodeSelector
            .order(database.nodeDao().getAllOnce(), preferredId = profileId)
            .map { it.stableId }
            .distinct()
            .take(MAX_FAILOVER_CANDIDATES)
            .toMutableList()
            .apply {
                if (profileId !in this) add(0, profileId)
            }

        startServiceConnection(profileId, rankedCandidates)
    }

    private suspend fun startServiceConnection(profileId: String, candidates: List<String>) {
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
            putStringArrayListExtra(CoreContract.EXTRA_CANDIDATE_IDS, ArrayList(candidates.distinct()))
            putExtra(CoreContract.EXTRA_SPLIT_MODE, preferences.splitTunnelMode.name)
            putStringArrayListExtra(CoreContract.EXTRA_INCLUDED, ArrayList(include))
            putStringArrayListExtra(CoreContract.EXTRA_EXCLUDED, ArrayList(exclude))
            putStringArrayListExtra(CoreContract.EXTRA_BANKING, ArrayList(preferences.bankingPackages))
            putExtra(CoreContract.EXTRA_CUSTOM_DNS, preferences.customDns)
            putExtra(CoreContract.EXTRA_AUTO_RECONNECT, preferences.autoReconnect)
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

    /**
     * A real Xray test: libXray starts the actual VMess/VLESS/Reality/Trojan outbound and
     * measures an HTTP request through its local SOCKS inbound. No raw TCP port probe is used.
     */
    override suspend fun probe(profileId: String): ProbeResult {
        if (_state.value !is ConnectionState.Disconnected && _state.value !is ConnectionState.Error) {
            return ProbeResult(
                tcpLatencyMs = null,
                httpRttMs = null,
                success = false,
                error = "برای تست مستقل سرورها ابتدا VPN را قطع کنید",
            )
        }

        val node = database.nodeDao().getById(profileId)
            ?: return ProbeResult(null, null, false, "سرور پیدا نشد")
        if (node.encryptedConfig.isBlank()) {
            return ProbeResult(null, null, false, "کانفیگ سرور خالی است")
        }

        return withTimeoutOrNull(PROBE_REQUEST_TIMEOUT_MS) {
            dispatchProbe(profileId)
        } ?: ProbeResult(
            tcpLatencyMs = null,
            httpRttMs = null,
            success = false,
            error = "مهلت تست واقعی Xray تمام شد",
        )
    }

    private suspend fun dispatchProbe(profileId: String): ProbeResult =
        suspendCancellableCoroutine { continuation ->
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (!continuation.isActive) return
                    val data = resultData ?: Bundle.EMPTY
                    continuation.resume(
                        ProbeResult(
                            tcpLatencyMs = data.optionalLong(XrayProbeWorkerService.EXTRA_TCP_LATENCY),
                            httpRttMs = data.optionalLong(XrayProbeWorkerService.EXTRA_HTTP_RTT),
                            success = resultCode == XrayProbeWorkerService.RESULT_SUCCESS &&
                                data.getBoolean(XrayProbeWorkerService.EXTRA_SUCCESS, false),
                            error = data.getString(XrayProbeWorkerService.EXTRA_ERROR),
                        )
                    )
                }
            }
            val workerClass = PROBE_WORKERS[
                Math.floorMod(probeWorkerCounter.getAndIncrement(), PROBE_WORKERS.size)
            ]
            val started = runCatching {
                appContext.startService(
                    Intent(appContext, workerClass).apply {
                        action = XrayProbeWorkerService.ACTION_PROBE
                        putExtra(XrayProbeWorkerService.EXTRA_PROFILE_ID, profileId)
                        putExtra(XrayProbeWorkerService.EXTRA_RESULT_RECEIVER, receiver)
                    }
                )
            }.getOrNull()

            if (started == null && continuation.isActive) {
                continuation.resume(
                    ProbeResult(
                        tcpLatencyMs = null,
                        httpRttMs = null,
                        success = false,
                        error = "سرویس تست Xray اجرا نشد",
                    )
                )
            }
        }

    private fun Bundle.optionalLong(key: String): Long? =
        if (containsKey(key)) getLong(key).takeIf { it > 0L } else null

    // Kept as a compatibility method for AppViewModel while the group-test UI is migrated.
    suspend fun validateProfileTraffic(profileId: String): ProbeResult = probe(profileId)

    override fun close() {
        runCatching { appContext.unregisterReceiver(stateReceiver) }
        adapterScope.cancel()
        database.close()
    }

    private companion object {
        const val MAX_FAILOVER_CANDIDATES = 5
        const val PROBE_REQUEST_TIMEOUT_MS = 12_000L
        val PROBE_WORKERS = listOf(
            XrayProbeWorker0Service::class.java,
            XrayProbeWorker1Service::class.java,
            XrayProbeWorker2Service::class.java,
        )
    }
}
