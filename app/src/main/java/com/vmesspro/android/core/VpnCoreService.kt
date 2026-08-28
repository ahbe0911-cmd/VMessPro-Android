package com.vmesspro.android.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.IpPrefix
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.vmesspro.android.MainActivity
import com.vmesspro.android.data.local.AppDatabase
import com.vmesspro.android.data.local.ConnectionHistoryEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.security.SecureConfigStore
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.amnezia.vpn.protocol.xray.libXray.DialerController
import org.amnezia.vpn.protocol.xray.libXray.LibXray
import org.amnezia.vpn.protocol.xray.libXray.Tun2SocksConfig

/**
 * Android VPN service based on the same core topology used by AmneziaVPN for Xray:
 * Android TUN -> tun2socks -> local authenticated SOCKS -> Xray outbound.
 *
 * The original share link is converted by libXray itself. There is deliberately no
 * VMess/VLESS/Reality -> sing-box translation in this service.
 */
class VpnCoreService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()
    private val secureStore = SecureConfigStore()

    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "vmesspro.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build()
    }

    private var activeHistoryId: Long? = null
    private var activeConfigFile: java.io.File? = null
    private var nativeRunning = false
    private var connectedSince = 0L
    private var telemetryJob: Job? = null

    @Volatile private var latestUplink: Long = 0L
    @Volatile private var latestDownlink: Long = 0L
    @Volatile private var latestUplinkTotal: Long = 0L
    @Volatile private var latestDownlinkTotal: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CoreContract.ACTION_DISCONNECT -> scope.launch {
                connectionMutex.withLock {
                    shutdownLocked(
                        broadcast = true,
                        stopService = true,
                        historyStatus = "DISCONNECTED",
                    )
                }
            }

            CoreContract.ACTION_CONNECT -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildForegroundNotification("در حال آماده‌سازی Xray…", connected = false),
                )
                val request = ConnectRequest.from(intent, packageName)
                scope.launch {
                    connectionMutex.withLock {
                        runCatching { connectWithFailoverLocked(request) }
                            .onFailure { error ->
                                Log.e(TAG, "All Xray VPN candidates failed", error)
                                sendState(CoreContract.STATE_ERROR, error.message ?: "خطای Xray")
                                updateNotification("اتصال Xray ناموفق بود", false)
                                shutdownLocked(
                                    broadcast = false,
                                    stopService = true,
                                    historyStatus = "FAILED",
                                    failureReason = error.message ?: "Xray core failure",
                                )
                            }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        scope.launch {
            connectionMutex.withLock {
                shutdownLocked(
                    broadcast = true,
                    stopService = true,
                    historyStatus = "REVOKED",
                    failureReason = "VPN permission revoked by Android",
                )
            }
        }
    }

    override fun onDestroy() {
        stopTelemetry()
        stopNativeEngine()
        runCatching { database.close() }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connectWithFailoverLocked(request: ConnectRequest) {
        val candidates = request.candidateProfileIds
            .ifEmpty { listOf(request.profileId) }
            .distinct()
            .take(MAX_FAILOVER_ATTEMPTS)

        var lastFailure: Throwable? = null
        candidates.forEachIndexed { index, profileId ->
            if (index > 0) {
                sendState(CoreContract.STATE_RECONNECTING)
                updateNotification("آزمایش سرور جایگزین ${index + 1}/${candidates.size}…", false)
                delay(failoverDelayMillis(index))
            }

            val candidate = request.copy(profileId = profileId)
            val attempt = runCatching { connectLocked(candidate) }
            if (attempt.isSuccess) return

            val error = attempt.exceptionOrNull() ?: IllegalStateException("Unknown Xray failure")
            lastFailure = error
            Log.w(TAG, "Xray candidate failed: $profileId", error)
            markNodeFailure(profileId)
            shutdownLocked(
                broadcast = false,
                stopService = false,
                historyStatus = "FAILED",
                failureReason = error.message ?: "Connection failed",
            )
        }

        throw lastFailure ?: IllegalStateException("هیچ کانفیگ Xray سالمی پیدا نشد")
    }

    private suspend fun connectLocked(request: ConnectRequest) {
        shutdownLocked(
            broadcast = false,
            stopService = false,
            historyStatus = "REPLACED",
        )
        sendState(CoreContract.STATE_PREPARING)
        check(prepare(this) == null) { "مجوز VPN صادر نشده است" }

        val node = database.nodeDao().getById(request.profileId)
            ?: error("سرور انتخاب‌شده پیدا نشد")
        val rawConfig = secureStore.decrypt(node.encryptedConfig)

        activeHistoryId = database.connectionHistoryDao().insert(
            ConnectionHistoryEntity(
                nodeId = node.stableId,
                nodeDisplayName = node.name,
                countryCode = node.countryCode,
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                downloadedBytes = 0,
                uploadedBytes = 0,
                status = "CONNECTING",
                failureReason = null,
            )
        )
        resetTelemetry()

        // Resolve the server before the VPN route exists. The resulting IP is injected only into
        // Xray's endpoint address; SNI/Host/Reality serverName remain unchanged.
        val prepared = XrayConfigFactory.prepare(
            context = this,
            rawConfig = rawConfig,
            endpointHost = node.host,
            filePrefix = "vpn",
        )
        activeConfigFile = prepared.configFile

        sendState(CoreContract.STATE_CONNECTING)
        updateNotification("در حال راه‌اندازی Xray و tun2socks…", false)

        registerXraySocketProtection()
        val tunFd = buildVpnInterface(request, prepared.endpointAddress)
        startNativeEngine(prepared, tunFd)

        // This check is intentionally performed inside the VPN service and before STATE_CONNECTED.
        // A socket that merely opens or a TUN that merely exists is never considered connected.
        sendState(CoreContract.STATE_VERIFYING)
        updateNotification("در حال بررسی عبور واقعی HTTPS…", false)
        val verifiedRtt = verifyRealWebTraffic()
            ?: error("Xray اجرا شد اما ترافیک واقعی HTTPS از VPN عبور نکرد")

        connectedSince = System.currentTimeMillis()
        activeHistoryId?.let { database.connectionHistoryDao().setStatus(it, "CONNECTED") }
        database.nodeDao().upsert(
            listOf(
                node.copy(
                    lastLatencyMs = verifiedRtt,
                    lastProbeSucceeded = true,
                    lastTestedAt = connectedSince,
                    lastUsedAt = connectedSince,
                    consecutiveFailures = 0,
                    updatedAt = connectedSince,
                )
            )
        )

        startTelemetry()
        sendState(
            CoreContract.STATE_CONNECTED,
            since = connectedSince,
            profileId = node.stableId,
        )
        updateNotification("VPN متصل است • ${node.name}", true)
    }

    private fun registerXraySocketProtection() {
        val controller = DialerController { fd ->
            check(protect(fd.toInt())) { "Android نتوانست socket خروجی Xray را protect کند" }
        }
        val dialError = LibXray.registerDialerController(controller)
        check(dialError.isNullOrBlank()) { "ثبت Xray dialer controller ناموفق بود: $dialError" }
        val listenerError = LibXray.registerListenerController(controller)
        check(listenerError.isNullOrBlank()) { "ثبت Xray listener controller ناموفق بود: $listenerError" }
    }

    /** Builds the Android TUN similarly to Amnezia's Xray implementation. */
    private fun buildVpnInterface(request: ConnectRequest, endpointAddress: InetAddress): Int {
        val builder = Builder()
            .setSession("VMess Pro Xray")
            .addAddress(TUN_IPV4_ADDRESS, TUN_IPV4_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addRoute("2000::", 3)
            .setMtu(XRAY_MTU)
            .setBlocking(false)
            .setUnderlyingNetworks(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val dnsServers = resolveDnsServers(request.customDns)
        dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
                .onFailure { Log.w(TAG, "Unable to add DNS $dns", it) }
        }

        // Amnezia both protects Xray sockets and excludes the server endpoint where Android
        // supports route exclusion. Protect(fd) remains the primary loop-prevention mechanism.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefix = when (endpointAddress) {
                is Inet4Address -> 32
                is Inet6Address -> 128
                else -> null
            }
            if (prefix != null) {
                runCatching { builder.excludeRoute(IpPrefix(endpointAddress, prefix)) }
                    .onFailure { Log.w(TAG, "Unable to exclude Xray endpoint route", it) }
            }
        }

        when (request.splitMode) {
            SplitTunnelMode.ONLY_SELECTED -> {
                request.includedPackages.forEach { pkg ->
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (error: NameNotFoundException) {
                        Log.w(TAG, "Allowed package disappeared: $pkg")
                    }
                }
            }

            SplitTunnelMode.EXCLUDE_SELECTED -> {
                (request.excludedPackages + request.bankingPackages).forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (error: NameNotFoundException) {
                        Log.w(TAG, "Excluded package disappeared: $pkg")
                    }
                }
            }
        }

        val tun = builder.establish()
            ?: error("ساخت رابط TUN ناموفق بود؛ مجوز VPN را دوباره بررسی کنید")
        return tun.detachFd()
    }

    private fun startNativeEngine(prepared: PreparedXrayConfig, tunFd: Int) {
        val tun2SocksConfig = Tun2SocksConfig().apply {
            mtu = XRAY_MTU.toLong()
            proxy = prepared.socksProxyUrl
            device = "fd://$tunFd"
            logLevel = "warn"
        }

        val tunError = LibXray.startTun2Socks(tun2SocksConfig, tunFd.toLong())
        check(tunError.isNullOrBlank()) { "راه‌اندازی tun2socks ناموفق بود: $tunError" }

        val (_, geoDir) = XrayConfigFactory.initializeXrayAssets(this)
        val xrayError = LibXray.runXray(
            geoDir,
            prepared.configFile.absolutePath,
            XRAY_MEMORY_LIMIT_BYTES,
        )
        if (!xrayError.isNullOrBlank()) {
            runCatching { LibXray.stopTun2Socks() }
            error("راه‌اندازی Xray ناموفق بود: $xrayError")
        }
        nativeRunning = true
    }

    private suspend fun verifyRealWebTraffic(): Long? {
        delay(REAL_WEB_SETTLE_DELAY_MS)
        repeat(REAL_WEB_VERIFY_ATTEMPTS) { attempt ->
            for (endpoint in REAL_WEB_VERIFY_URLS) {
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val ok = requestConnectivityEndpoint(endpoint)
                if (ok) {
                    return (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
                }
            }
            if (attempt + 1 < REAL_WEB_VERIFY_ATTEMPTS) delay(REAL_WEB_RETRY_DELAY_MS)
        }
        return null
    }

    /** This request must NOT be protect()ed: it is supposed to traverse TUN -> tun2socks -> Xray. */
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
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "VMessPro-Xray/Android")
            }
            val code = connection?.responseCode ?: -1
            code == HttpURLConnection.HTTP_NO_CONTENT || code in 200..399
        }.getOrDefault(false).also {
            runCatching { connection?.disconnect() }
        }
    }

    private fun resolveDnsServers(customDns: String?): List<InetAddress> {
        val requested = customDns
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::stripDnsPort)
            ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }

        if (requested != null) return listOf(requested)
        return DEFAULT_DNS.mapNotNull { value ->
            runCatching { InetAddress.getByName(value) }.getOrNull()
        }
    }

    private fun stripDnsPort(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            return trimmed.substringAfter("[").substringBefore("]")
        }
        val colonCount = trimmed.count { it == ':' }
        return if (colonCount == 1 && trimmed.substringAfterLast(':').toIntOrNull() != null) {
            trimmed.substringBeforeLast(':')
        } else {
            trimmed
        }
    }

    private fun startTelemetry() {
        stopTelemetry()
        val uid = applicationInfo.uid
        var previousRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        var previousTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        val baseRx = previousRx
        val baseTx = previousTx

        telemetryJob = scope.launch {
            while (isActive && nativeRunning) {
                delay(1_000L)
                val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(previousRx)
                val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(previousTx)
                latestDownlink = (rx - previousRx).coerceAtLeast(0L)
                latestUplink = (tx - previousTx).coerceAtLeast(0L)
                latestDownlinkTotal = (rx - baseRx).coerceAtLeast(0L)
                latestUplinkTotal = (tx - baseTx).coerceAtLeast(0L)
                previousRx = rx
                previousTx = tx
                sendTelemetry()
            }
        }
    }

    private fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    private fun sendTelemetry() {
        sendBroadcast(
            Intent(CoreContract.ACTION_TELEMETRY)
                .setPackage(packageName)
                .putExtra(CoreContract.EXTRA_UPLINK, latestUplink)
                .putExtra(CoreContract.EXTRA_DOWNLINK, latestDownlink)
                .putExtra(CoreContract.EXTRA_UPLINK_TOTAL, latestUplinkTotal)
                .putExtra(CoreContract.EXTRA_DOWNLINK_TOTAL, latestDownlinkTotal)
                .putExtra(CoreContract.EXTRA_CONNECTIONS_IN, 0)
                .putExtra(CoreContract.EXTRA_CONNECTIONS_OUT, 0)
                .putExtra(CoreContract.EXTRA_TRAFFIC_AVAILABLE, nativeRunning),
        )
    }

    private suspend fun markNodeFailure(profileId: String) {
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
        }.onFailure { Log.w(TAG, "Failed to persist node failure", it) }
    }

    private suspend fun shutdownLocked(
        broadcast: Boolean,
        stopService: Boolean,
        historyStatus: String,
        failureReason: String? = null,
    ) {
        stopTelemetry()
        stopNativeEngine()
        connectedSince = 0L

        activeHistoryId?.let { historyId ->
            runCatching {
                database.connectionHistoryDao().finish(
                    id = historyId,
                    endedAt = System.currentTimeMillis(),
                    downloadedBytes = latestDownlinkTotal.coerceAtLeast(0L),
                    uploadedBytes = latestUplinkTotal.coerceAtLeast(0L),
                    status = historyStatus,
                    failureReason = failureReason,
                )
            }.onFailure { Log.w(TAG, "Failed to persist connection history", it) }
        }
        activeHistoryId = null
        resetTelemetry()
        sendTelemetry()

        if (broadcast) sendState(CoreContract.STATE_DISCONNECTED)
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopNativeEngine() {
        if (nativeRunning) {
            runCatching {
                LibXray.stopXray().takeIf { !it.isNullOrBlank() }?.let {
                    Log.w(TAG, "stopXray: $it")
                }
            }
            runCatching {
                LibXray.stopTun2Socks().takeIf { !it.isNullOrBlank() }?.let {
                    Log.w(TAG, "stopTun2Socks: $it")
                }
            }
        } else {
            // These are idempotent in libXray and also clean up partially-started attempts.
            runCatching { LibXray.stopXray() }
            runCatching { LibXray.stopTun2Socks() }
        }
        nativeRunning = false
        activeConfigFile?.let { file -> runCatching { file.delete() } }
        activeConfigFile = null
    }

    private fun resetTelemetry() {
        latestUplink = 0L
        latestDownlink = 0L
        latestUplinkTotal = 0L
        latestDownlinkTotal = 0L
    }

    private fun sendState(
        state: String,
        reason: String? = null,
        since: Long = 0L,
        profileId: String? = null,
    ) {
        sendBroadcast(
            Intent(CoreContract.ACTION_STATE)
                .setPackage(packageName)
                .putExtra(CoreContract.EXTRA_STATE, state)
                .apply {
                    if (!reason.isNullOrBlank()) putExtra(CoreContract.EXTRA_REASON, reason)
                    if (since > 0L) putExtra(CoreContract.EXTRA_SINCE, since)
                    if (!profileId.isNullOrBlank()) putExtra(CoreContract.EXTRA_PROFILE_ID, profileId)
                },
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "VPN Connection",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildForegroundNotification(text: String, connected: Boolean) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.vmesspro.android.R.drawable.ic_launcher)
            .setContentTitle(if (connected) "VMess Pro • Xray" else "VMess Pro")
            .setContentText(text)
            .setOngoing(connected)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .addAction(
                0,
                "قطع اتصال",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, VpnCoreService::class.java).apply {
                        action = CoreContract.ACTION_DISCONNECT
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun updateNotification(text: String, connected: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildForegroundNotification(text, connected))
    }

    private fun failoverDelayMillis(index: Int): Long =
        (FAILOVER_BASE_DELAY_MS * index.coerceAtLeast(1)).coerceAtMost(FAILOVER_MAX_DELAY_MS)

    private data class ConnectRequest(
        val profileId: String,
        val candidateProfileIds: List<String>,
        val splitMode: SplitTunnelMode,
        val includedPackages: Set<String>,
        val excludedPackages: Set<String>,
        val bankingPackages: Set<String>,
        val customDns: String?,
    ) {
        companion object {
            fun from(intent: Intent, ownPackage: String): ConnectRequest {
                val profileId = intent.getStringExtra(CoreContract.EXTRA_PROFILE_ID)
                    ?: error("شناسه سرور ارسال نشده است")
                val candidateIds = intent.getStringArrayListExtra(CoreContract.EXTRA_CANDIDATE_IDS)
                    .orEmpty()
                    .filter { it.isNotBlank() }
                val splitMode = runCatching {
                    SplitTunnelMode.valueOf(
                        intent.getStringExtra(CoreContract.EXTRA_SPLIT_MODE)
                            ?: SplitTunnelMode.EXCLUDE_SELECTED.name
                    )
                }.getOrDefault(SplitTunnelMode.EXCLUDE_SELECTED)

                val included = intent.getStringArrayListExtra(CoreContract.EXTRA_INCLUDED)
                    .orEmpty()
                    .toMutableSet()
                val excluded = intent.getStringArrayListExtra(CoreContract.EXTRA_EXCLUDED)
                    .orEmpty()
                    .toMutableSet()
                val banking = intent.getStringArrayListExtra(CoreContract.EXTRA_BANKING)
                    .orEmpty()
                    .toSet()

                // Service-side HTTPS verification must follow the same TUN route as user traffic.
                if (splitMode == SplitTunnelMode.ONLY_SELECTED) included += ownPackage
                else excluded -= ownPackage

                return ConnectRequest(
                    profileId = profileId,
                    candidateProfileIds = candidateIds.ifEmpty { listOf(profileId) },
                    splitMode = splitMode,
                    includedPackages = included,
                    excludedPackages = excluded,
                    bankingPackages = banking,
                    customDns = intent.getStringExtra(CoreContract.EXTRA_CUSTOM_DNS),
                )
            }
        }
    }

    private companion object {
        const val TAG = "VpnCoreService-Xray"
        const val NOTIFICATION_CHANNEL_ID = "vpn_connection"
        const val NOTIFICATION_ID = 7101
        const val MAX_FAILOVER_ATTEMPTS = 5
        const val FAILOVER_BASE_DELAY_MS = 250L
        const val FAILOVER_MAX_DELAY_MS = 1_000L

        const val TUN_IPV4_ADDRESS = "10.0.42.2"
        const val TUN_IPV4_PREFIX = 30
        const val XRAY_MTU = 1500
        const val XRAY_MEMORY_LIMIT_BYTES = 64L shl 20

        const val REAL_WEB_VERIFY_ATTEMPTS = 3
        const val REAL_WEB_CONNECT_TIMEOUT_MS = 6_000
        const val REAL_WEB_READ_TIMEOUT_MS = 6_000
        const val REAL_WEB_SETTLE_DELAY_MS = 300L
        const val REAL_WEB_RETRY_DELAY_MS = 350L
        val REAL_WEB_VERIFY_URLS = listOf(
            "https://www.gstatic.com/generate_204",
            "https://cp.cloudflare.com/generate_204",
        )
        val DEFAULT_DNS = listOf("1.1.1.1", "8.8.8.8")
    }
}
