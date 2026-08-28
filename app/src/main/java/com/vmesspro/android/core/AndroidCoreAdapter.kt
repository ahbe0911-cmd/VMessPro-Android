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
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidCoreAdapter(context: Context) : CoreAdapter, AutoCloseable {
    private val appContext = context.applicationContext
    private val preferencesRepository = VpnPreferencesRepository(appContext)
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                        CoreContract.STATE_CONNECTED -> {
                            val activeProfileId = intent.getStringExtra(CoreContract.EXTRA_PROFILE_ID)
                            val connectedSince = intent.getLongExtra(
                                CoreContract.EXTRA_SINCE,
                                System.currentTimeMillis(),
                            )
                            if (!activeProfileId.isNullOrBlank()) {
                                adapterScope.launch {
                                    preferencesRepository.setSelectedNode(activeProfileId)
                                }
                            }

                            // The native service may have a TUN file descriptor while web traffic is
                            // still unusable (DNS/TLS/routing/protocol failure). Do not expose a false
                            // Connected state. Verify a real HTTPS request from this app first; the app
                            // is always included in its own VPN route in connect().
                            adapterScope.launch {
                                val verified = verifyRealWebTraffic()
                                if (_state.value != ConnectionState.Verifying) return@launch
                                if (verified) {
                                    _state.value = ConnectionState.Connected(connectedSince)
                                } else {
                                    _state.value = ConnectionState.Error(
                                        "تونل ساخته شد اما ترافیک واقعی وب از VPN عبور نکرد"
                                    )
                                    _telemetry.value = VpnTelemetry()
                                    runCatching { disconnect() }
                                }
                            }
                            ConnectionState.Verifying
                        }
                        CoreContract.STATE_RECONNECTING -> ConnectionState.Reconnecting
                        CoreContract.STATE_ERROR -> ConnectionState.Error(
                            intent.getStringExtra(CoreContract.EXTRA_REASON) ?: "خطای نامشخص Core"
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
        val preferences = preferencesRepository.preferences.first()
        _state.value = ConnectionState.Preparing

        val include = preferences.includedPackages.toMutableSet()
        val exclude = preferences.excludedPackages.toMutableSet()
        when (preferences.splitTunnelMode) {
            SplitTunnelMode.ONLY_SELECTED -> include += appContext.packageName
            SplitTunnelMode.EXCLUDE_SELECTED -> exclude -= appContext.packageName
        }

        val rankedCandidates = SmartNodeSelector
            .order(database.nodeDao().getAllOnce(), preferredId = profileId)
            .map { it.stableId }
            .distinct()
            .take(MAX_FAILOVER_CANDIDATES)
            .toMutableList()
            .apply {
                if (profileId !in this) add(0, profileId)
            }

        val intent = Intent(appContext, VpnCoreService::class.java).apply {
            action = CoreContract.ACTION_CONNECT
            putExtra(CoreContract.EXTRA_PROFILE_ID, profileId)
            putStringArrayListExtra(CoreContract.EXTRA_CANDIDATE_IDS, ArrayList(rankedCandidates))
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
            error = if (success) null else "TCP reachability probe failed",
        )
    }

    private suspend fun verifyRealWebTraffic(): Boolean = withContext(Dispatchers.IO) {
        // Give Android routing and the sing-box TUN stack a short moment to settle.
        delay(250)
        repeat(REAL_WEB_VERIFY_ATTEMPTS) { attempt ->
            REAL_WEB_VERIFY_URLS.forEach { endpoint ->
                if (requestConnectivityEndpoint(endpoint)) return@withContext true
            }
            if (attempt + 1 < REAL_WEB_VERIFY_ATTEMPTS) delay(350)
        }
        false
    }

    private fun requestConnectivityEndpoint(endpoint: String): Boolean {
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = REAL_WEB_CONNECT_TIMEOUT_MS
                readTimeout = REAL_WEB_READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "VMessPro/Android connectivity-check")
            }
            val code = connection?.responseCode ?: -1
            code == HttpURLConnection.HTTP_NO_CONTENT || code in 200..399
        }.getOrDefault(false).also {
            runCatching { connection?.disconnect() }
        }
    }

    override fun close() {
        runCatching { appContext.unregisterReceiver(stateReceiver) }
        adapterScope.cancel()
        database.close()
    }

    private companion object {
        const val MAX_FAILOVER_CANDIDATES = 5
        const val REAL_WEB_VERIFY_ATTEMPTS = 2
        const val REAL_WEB_CONNECT_TIMEOUT_MS = 5_000
        const val REAL_WEB_READ_TIMEOUT_MS = 5_000
        val REAL_WEB_VERIFY_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )
    }
}
