package com.vmesspro.android.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidCoreAdapter(context: Context) : CoreAdapter, AutoCloseable {
    private val appContext = context.applicationContext
    private val preferencesRepository = VpnPreferencesRepository(appContext)
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val validationMutex = Mutex()
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "vmesspro.db")
        .enableMultiInstanceInvalidation()
        .fallbackToDestructiveMigration()
        .build()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _telemetry = MutableStateFlow(VpnTelemetry())
    override val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

    @Volatile private var connectionGeneration: Long = 0L
    @Volatile private var pendingCandidateIds: List<String> = emptyList()
    @Volatile private var lastVerifiedRttMs: Long? = null

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
                            val generation = connectionGeneration

                            // A TUN file descriptor or a successful TCP socket is not enough.
                            // The UI becomes Connected only after a real HTTPS response traverses
                            // the VPN route. If that fails, automatically continue with the next
                            // candidate instead of exposing a false-positive connection.
                            adapterScope.launch {
                                val verifiedRtt = verifyRealWebTraffic()
                                if (generation != connectionGeneration || _state.value != ConnectionState.Verifying) {
                                    return@launch
                                }
                                if (verifiedRtt != null) {
                                    lastVerifiedRttMs = verifiedRtt
                                    if (!activeProfileId.isNullOrBlank()) {
                                        markRealTrafficSuccess(activeProfileId, verifiedRtt)
                                        preferencesRepository.setSelectedNode(activeProfileId)
                                    }
                                    _state.value = ConnectionState.Connected(connectedSince)
                                } else {
                                    handleRealTrafficFailure(activeProfileId, generation)
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
        val rankedCandidates = SmartNodeSelector
            .order(database.nodeDao().getAllOnce(), preferredId = profileId)
            .map { it.stableId }
            .distinct()
            .take(MAX_FAILOVER_CANDIDATES)
            .toMutableList()
            .apply {
                if (profileId !in this) add(0, profileId)
            }
        beginConnection(profileId, rankedCandidates, ConnectionState.Preparing)
    }

    /** Starts exactly one profile, with no service-side fallback. Used for real per-profile tests. */
    suspend fun connectExact(profileId: String) {
        beginConnection(profileId, listOf(profileId), ConnectionState.Preparing)
    }

    private suspend fun beginConnection(
        profileId: String,
        candidates: List<String>,
        initialState: ConnectionState,
    ) {
        val preferences = preferencesRepository.preferences.first()
        connectionGeneration += 1L
        pendingCandidateIds = candidates.distinct()
        lastVerifiedRttMs = null
        _state.value = initialState

        val include = preferences.includedPackages.toMutableSet()
        val exclude = preferences.excludedPackages.toMutableSet()
        when (preferences.splitTunnelMode) {
            SplitTunnelMode.ONLY_SELECTED -> include += appContext.packageName
            SplitTunnelMode.EXCLUDE_SELECTED -> exclude -= appContext.packageName
        }

        val intent = Intent(appContext, VpnCoreService::class.java).apply {
            action = CoreContract.ACTION_CONNECT
            putExtra(CoreContract.EXTRA_PROFILE_ID, profileId)
            putStringArrayListExtra(CoreContract.EXTRA_CANDIDATE_IDS, ArrayList(pendingCandidateIds))
            putExtra(CoreContract.EXTRA_SPLIT_MODE, preferences.splitTunnelMode.name)
            putStringArrayListExtra(CoreContract.EXTRA_INCLUDED, ArrayList(include))
            putStringArrayListExtra(CoreContract.EXTRA_EXCLUDED, ArrayList(exclude))
            putStringArrayListExtra(CoreContract.EXTRA_BANKING, ArrayList(preferences.bankingPackages))
            putExtra(CoreContract.EXTRA_CUSTOM_DNS, preferences.customDns)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    override suspend fun disconnect() {
        connectionGeneration += 1L
        pendingCandidateIds = emptyList()
        lastVerifiedRttMs = null
        requestServiceDisconnect()
    }

    private fun requestServiceDisconnect() {
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

    /**
     * Real node validation used by "test all". It brings up only the requested profile,
     * waits until the same HTTPS verification used by normal connections succeeds, then
     * tears the tunnel down before testing the next profile.
     */
    suspend fun validateProfileTraffic(profileId: String): ProbeResult = validationMutex.withLock {
        ensureDisconnectedForValidation()
        val startedAt = SystemClock.elapsedRealtime()
        lastVerifiedRttMs = null

        val terminalState = runCatching {
            connectExact(profileId)
            withTimeoutOrNull(REAL_PROFILE_TEST_TIMEOUT_MS) {
                state.first { value -> value is ConnectionState.Connected || value is ConnectionState.Error }
            }
        }.getOrNull()

        val verifiedRtt = lastVerifiedRttMs
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val result = when (terminalState) {
            is ConnectionState.Connected -> ProbeResult(
                tcpLatencyMs = verifiedRtt ?: elapsed,
                httpRttMs = verifiedRtt,
                success = true,
                error = null,
            )
            is ConnectionState.Error -> ProbeResult(
                tcpLatencyMs = null,
                httpRttMs = null,
                success = false,
                error = terminalState.reason,
            )
            else -> ProbeResult(
                tcpLatencyMs = null,
                httpRttMs = null,
                success = false,
                error = "مهلت تست واقعی اتصال تمام شد",
            )
        }

        runCatching { disconnect() }
        withTimeoutOrNull(VALIDATION_DISCONNECT_TIMEOUT_MS) {
            state.first { it is ConnectionState.Disconnected || it is ConnectionState.Error }
        }
        delay(VALIDATION_SETTLE_DELAY_MS)
        result
    }

    private suspend fun ensureDisconnectedForValidation() {
        when (_state.value) {
            ConnectionState.Disconnected,
            is ConnectionState.Error -> Unit
            else -> {
                runCatching { disconnect() }
                withTimeoutOrNull(VALIDATION_DISCONNECT_TIMEOUT_MS) {
                    state.first { it is ConnectionState.Disconnected || it is ConnectionState.Error }
                }
                delay(VALIDATION_SETTLE_DELAY_MS)
            }
        }
    }

    private suspend fun handleRealTrafficFailure(activeProfileId: String?, generation: Long) {
        if (generation != connectionGeneration) return
        if (!activeProfileId.isNullOrBlank()) markRealTrafficFailure(activeProfileId)

        val current = pendingCandidateIds
        val activeIndex = activeProfileId?.let(current::indexOf) ?: -1
        val remaining = when {
            activeIndex >= 0 -> current.drop(activeIndex + 1)
            activeProfileId.isNullOrBlank() -> emptyList()
            else -> current.filterNot { it == activeProfileId }
        }

        _telemetry.value = VpnTelemetry()
        if (remaining.isNotEmpty()) {
            _state.value = ConnectionState.Reconnecting
            delay(REAL_FAILOVER_DELAY_MS)
            if (generation != connectionGeneration) return
            beginConnection(remaining.first(), remaining, ConnectionState.Reconnecting)
        } else {
            _state.value = ConnectionState.Error(
                "اتصال شبکه ساخته شد اما هیچ کانفیگی نتوانست ترافیک واقعی HTTPS عبور دهد"
            )
            pendingCandidateIds = emptyList()
            requestServiceDisconnect()
        }
    }

    private suspend fun markRealTrafficSuccess(profileId: String, rttMs: Long) {
        runCatching {
            val node = database.nodeDao().getById(profileId) ?: return
            val now = System.currentTimeMillis()
            database.nodeDao().upsert(
                listOf(
                    node.copy(
                        lastLatencyMs = rttMs,
                        lastProbeSucceeded = true,
                        consecutiveFailures = 0,
                        lastTestedAt = now,
                        updatedAt = now,
                    )
                )
            )
        }
    }

    private suspend fun markRealTrafficFailure(profileId: String) {
        runCatching {
            val node = database.nodeDao().getById(profileId) ?: return
            val now = System.currentTimeMillis()
            database.nodeDao().upsert(
                listOf(
                    node.copy(
                        lastProbeSucceeded = false,
                        consecutiveFailures = node.consecutiveFailures + 1,
                        lastTestedAt = now,
                        updatedAt = now,
                    )
                )
            )
        }
    }

    private suspend fun verifyRealWebTraffic(): Long? = withContext(Dispatchers.IO) {
        // Give Android routing and the sing-box TUN stack a short moment to settle.
        delay(REAL_WEB_SETTLE_DELAY_MS)
        repeat(REAL_WEB_VERIFY_ATTEMPTS) { attempt ->
            REAL_WEB_VERIFY_URLS.forEach { endpoint ->
                val rtt = requestConnectivityEndpoint(endpoint)
                if (rtt != null) return@withContext rtt
            }
            if (attempt + 1 < REAL_WEB_VERIFY_ATTEMPTS) delay(REAL_WEB_RETRY_DELAY_MS)
        }
        null
    }

    private fun requestConnectivityEndpoint(endpoint: String): Long? {
        var connection: HttpURLConnection? = null
        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = REAL_WEB_CONNECT_TIMEOUT_MS
                readTimeout = REAL_WEB_READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("Connection", "close")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "VMessPro/Android connectivity-check")
            }
            val code = connection?.responseCode ?: -1
            if (code == HttpURLConnection.HTTP_NO_CONTENT || code in 200..399) {
                (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
            } else {
                null
            }
        }.getOrNull().also {
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
        const val REAL_WEB_SETTLE_DELAY_MS = 300L
        const val REAL_WEB_RETRY_DELAY_MS = 400L
        const val REAL_FAILOVER_DELAY_MS = 300L
        const val REAL_PROFILE_TEST_TIMEOUT_MS = 25_000L
        const val VALIDATION_DISCONNECT_TIMEOUT_MS = 3_000L
        const val VALIDATION_SETTLE_DELAY_MS = 150L
        val REAL_WEB_VERIFY_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )
    }
}
