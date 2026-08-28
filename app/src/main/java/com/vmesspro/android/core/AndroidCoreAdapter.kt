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
import com.vmesspro.android.data.security.SecureConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidCoreAdapter(context: Context) : CoreAdapter, AutoCloseable {
    private val appContext = context.applicationContext
    private val preferencesRepository = VpnPreferencesRepository(appContext)
    private val secureStore = SecureConfigStore()
    private val profileTester = XrayProfileTester(appContext)
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
    override suspend fun probe(profileId: String): ProbeResult = withContext(Dispatchers.IO) {
        if (_state.value !is ConnectionState.Disconnected && _state.value !is ConnectionState.Error) {
            return@withContext ProbeResult(
                tcpLatencyMs = null,
                httpRttMs = null,
                success = false,
                error = "برای تست مستقل سرورها ابتدا VPN را قطع کنید",
            )
        }

        val node = database.nodeDao().getById(profileId)
            ?: return@withContext ProbeResult(null, null, false, "سرور پیدا نشد")
        val rawConfig = runCatching { secureStore.decrypt(node.encryptedConfig) }
            .getOrElse { error ->
                return@withContext ProbeResult(
                    tcpLatencyMs = null,
                    httpRttMs = null,
                    success = false,
                    error = error.message ?: "خواندن کانفیگ ناموفق بود",
                )
            }

        profileTester.test(rawConfig, node.host)
    }

    // Kept as a compatibility method for AppViewModel while the group-test UI is migrated.
    suspend fun validateProfileTraffic(profileId: String): ProbeResult = probe(profileId)

    override fun close() {
        runCatching { appContext.unregisterReceiver(stateReceiver) }
        adapterScope.cancel()
        database.close()
    }

    private companion object {
        const val MAX_FAILOVER_CANDIDATES = 5
    }
}
