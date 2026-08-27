package com.vmesspro.android.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.vmesspro.android.MainActivity
import com.vmesspro.android.R
import com.vmesspro.android.data.local.AppDatabase
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.security.SecureConfigStore
import com.vmesspro.android.domain.config.ConfigParser
import com.vmesspro.android.domain.config.ParseResult
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as LibboxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.KeyStore
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

class VpnCoreService : VpnService(), PlatformInterface, CommandServerHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()
    private val secureStore = SecureConfigStore()

    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "vmesspro.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private var commandServer: CommandServer? = null
    private var tunnel: ParcelFileDescriptor? = null
    private var connectedSince: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CoreContract.ACTION_DISCONNECT -> {
                scope.launch {
                    connectionMutex.withLock {
                        shutdownLocked(closeNative = true, broadcast = true, stopService = true)
                    }
                }
            }
            CoreContract.ACTION_CONNECT -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification("در حال آماده‌سازی اتصال…", false))
                val request = ConnectRequest.from(intent, packageName)
                scope.launch {
                    connectionMutex.withLock {
                        runCatching { connectLocked(request) }
                            .onFailure { error ->
                                Log.e(TAG, "Core connect failed", error)
                                sendState(CoreContract.STATE_ERROR, error.message ?: "خطای Core")
                                updateNotification("اتصال ناموفق بود", false)
                                shutdownLocked(closeNative = true, broadcast = false, stopService = true)
                            }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onRevoke() {
        scope.launch {
            connectionMutex.withLock {
                shutdownLocked(closeNative = true, broadcast = true, stopService = true)
            }
        }
    }

    override fun onDestroy() {
        runCatching { tunnel?.close() }
        tunnel = null
        runCatching { commandServer?.close() }
        commandServer = null
        PhysicalNetworkMonitor.stop()
        runCatching { database.close() }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connectLocked(request: ConnectRequest) {
        shutdownLocked(closeNative = true, broadcast = false, stopService = false)
        sendState(CoreContract.STATE_PREPARING)

        check(prepare(this) == null) { "مجوز VPN صادر نشده است" }
        val node = database.nodeDao().getById(request.profileId) ?: error("سرور انتخاب‌شده پیدا نشد")
        val rawConfig = secureStore.decrypt(node.encryptedConfig)
        val profile = when (val parsed = ConfigParser.parse(rawConfig)) {
            is ParseResult.Success -> parsed.profile
            is ParseResult.Failure -> error(parsed.reason)
        }
        val config = SingBoxConfigBuilder.build(profile, request.customDns)

        PhysicalNetworkMonitor.start(this)
        sendState(CoreContract.STATE_CONNECTING)
        updateNotification("در حال ساخت تونل امن…", false)

        val server = CommandServer(this, this)
        server.start()
        commandServer = server

        val overrides = OverrideOptions().apply {
            if (request.splitMode == SplitTunnelMode.ONLY_SELECTED) {
                includePackage = StringArray(request.includedPackages.iterator())
            } else {
                excludePackage = StringArray(request.excludedPackages.iterator())
            }
        }
        server.startOrReloadService(config, overrides)
        check(tunnel != null) { "ساخت رابط TUN توسط Core انجام نشد" }

        sendState(CoreContract.STATE_VERIFYING)
        updateNotification("در حال بررسی مسیر واقعی VPN…", false)
        check(verifyTunnelEgress()) { "تونل ساخته شد اما دسترسی اینترنت از مسیر VPN تأیید نشد" }

        connectedSince = System.currentTimeMillis()
        sendState(CoreContract.STATE_CONNECTED, since = connectedSince)
        updateNotification("VPN متصل است • ${node.name}", true)
    }

    private fun verifyTunnelEgress(): Boolean {
        val endpoints = listOf(
            InetSocketAddress("1.1.1.1", 443),
            InetSocketAddress("8.8.8.8", 443),
        )
        return endpoints.any { endpoint ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(endpoint, 5_000)
                    socket.isConnected
                }
            }.getOrDefault(false)
        }
    }

    private fun shutdownLocked(closeNative: Boolean, broadcast: Boolean, stopService: Boolean) {
        val server = commandServer
        commandServer = null
        if (closeNative) runCatching { server?.closeService() }
        runCatching { server?.close() }
        runCatching { tunnel?.close() }
        tunnel = null
        connectedSince = 0L
        PhysicalNetworkMonitor.stop()
        if (broadcast) sendState(CoreContract.STATE_DISCONNECTED)
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun serviceStop() {
        scope.launch {
            connectionMutex.withLock {
                shutdownLocked(closeNative = false, broadcast = true, stopService = true)
            }
        }
    }

    override fun serviceReload() {
        // Generated configs are immutable for one connection. UI changes trigger a controlled reconnect.
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit

    override fun writeDebugMessage(message: String?) {
        if (!message.isNullOrBlank()) Log.d("sing-box", message)
    }

    override fun localDNSTransport(): LocalDNSTransport = AndroidLocalDnsTransport

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        check(protect(fd)) { "android: failed to protect outbound socket" }
    }

    override fun openTun(options: TunOptions): Int {
        check(prepare(this) == null) { "android: missing vpn permission" }
        runCatching { tunnel?.close() }

        val builder = Builder()
            .setSession("VMess Pro")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        var hasV4 = false
        var hasV6 = false
        val v4Addresses = options.inet4Address
        while (v4Addresses.hasNext()) {
            val address = v4Addresses.next()
            builder.addAddress(address.address(), address.prefix())
            hasV4 = true
        }
        val v6Addresses = options.inet6Address
        while (v6Addresses.hasNext()) {
            val address = v6Addresses.next()
            builder.addAddress(address.address(), address.prefix())
            hasV6 = true
        }

        if (options.autoRoute) {
            options.dnsServerAddress?.value?.takeIf { it.isNotBlank() }?.let(builder::addDnsServer)

            var hasV4Route = false
            val v4Routes = options.inet4RouteRange
            while (v4Routes.hasNext()) {
                val route = v4Routes.next()
                builder.addRoute(route.address(), route.prefix())
                hasV4Route = true
            }
            var hasV6Route = false
            val v6Routes = options.inet6RouteRange
            while (v6Routes.hasNext()) {
                val route = v6Routes.next()
                builder.addRoute(route.address(), route.prefix())
                hasV6Route = true
            }
            if (hasV4 && !hasV4Route) builder.addRoute("0.0.0.0", 0)
            if (hasV6 && !hasV6Route) builder.addRoute("::", 0)

            val includePackages = options.includePackage
            if (includePackages.hasNext()) {
                while (includePackages.hasNext()) {
                    val app = includePackages.next()
                    try {
                        builder.addAllowedApplication(app)
                    } catch (e: NameNotFoundException) {
                        Log.w(TAG, "Allowed package disappeared: $app", e)
                    }
                }
            }

            val excludePackages = options.excludePackage
            if (excludePackages.hasNext()) {
                while (excludePackages.hasNext()) {
                    val app = excludePackages.next()
                    try {
                        builder.addDisallowedApplication(app)
                    } catch (e: NameNotFoundException) {
                        Log.w(TAG, "Excluded package disappeared: $app", e)
                    }
                }
            }
        }

        val pfd = builder.establish() ?: error("android: VpnService.Builder.establish returned null")
        tunnel = pfd
        return pfd.fd
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "connection owner requires Android 10+" }
        val cm = getSystemService(ConnectivityManager::class.java)
        val uid = cm.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort),
        )
        check(uid != Process.INVALID_UID) { "android: connection owner not found" }
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        return ConnectionOwner().apply {
            userId = uid
            userName = packages.firstOrNull().orEmpty()
            setAndroidPackageNames(StringArray(packages.iterator()))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        PhysicalNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        PhysicalNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val cm = getSystemService(ConnectivityManager::class.java)
        val javaInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val result = mutableListOf<LibboxNetworkInterface>()

        cm.allNetworks.forEach { network ->
            val capabilities = cm.getNetworkCapabilities(network) ?: return@forEach
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@forEach
            val link = cm.getLinkProperties(network) ?: return@forEach
            val name = link.interfaceName ?: return@forEach
            val javaInterface = javaInterfaces.firstOrNull { it.name == name } ?: return@forEach

            result += LibboxNetworkInterface().apply {
                this.name = name
                index = javaInterface.index
                mtu = runCatching { javaInterface.mtu }.getOrDefault(1500)
                addresses = StringArray(javaInterface.interfaceAddresses.map { it.toPrefix() }.iterator())
                dnsServer = StringArray(link.dnsServers.mapNotNull { it.hostAddress }.iterator())
                type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> io.nekohasekai.libbox.Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> io.nekohasekai.libbox.Libbox.InterfaceTypeCellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> io.nekohasekai.libbox.Libbox.InterfaceTypeEthernet
                    else -> io.nekohasekai.libbox.Libbox.InterfaceTypeOther
                }
                var interfaceFlags = 0
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    interfaceFlags = interfaceFlags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
                }
                if (javaInterface.isLoopback) interfaceFlags = interfaceFlags or OsConstants.IFF_LOOPBACK
                if (javaInterface.isPointToPoint) interfaceFlags = interfaceFlags or OsConstants.IFF_POINTOPOINT
                if (javaInterface.supportsMulticast()) interfaceFlags = interfaceFlags or OsConstants.IFF_MULTICAST
                flags = interfaceFlags
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        return InterfaceArray(result.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        val keyStore = KeyStore.getInstance("AndroidCAStore")
        keyStore.load(null, null)
        val aliases = keyStore.aliases()
        val encoder = Base64.getMimeEncoder(64, "\n".toByteArray())
        while (aliases.hasMoreElements()) {
            val certificate = keyStore.getCertificate(aliases.nextElement()) ?: continue
            certificates += "-----BEGIN CERTIFICATE-----\n" +
                encoder.encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----"
        }
        return StringArray(certificates.iterator())
    }

    override fun clearDNSCache() = Unit

    override fun sendNotification(notification: LibboxNotification) {
        val manager = getSystemService(NotificationManager::class.java)
        val item = NotificationCompat.Builder(this, CORE_EVENT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(notification.title.ifBlank { "VMess Pro" })
            .setContentText(notification.body)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(notification.identifier, notification.typeID, item)
    }

    private fun sendState(state: String, reason: String? = null, since: Long = 0L) {
        sendBroadcast(
            Intent(CoreContract.ACTION_STATE).setPackage(packageName).apply {
                putExtra(CoreContract.EXTRA_STATE, state)
                putExtra(CoreContract.EXTRA_REASON, reason)
                if (since > 0L) putExtra(CoreContract.EXTRA_SINCE, since)
            }
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(VPN_CHANNEL, "VPN Connection", NotificationManager.IMPORTANCE_LOW).apply {
                description = "وضعیت اتصال امن VMess Pro"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CORE_EVENT_CHANNEL, "VPN Events", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun buildForegroundNotification(text: String, connected: Boolean): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, VpnCoreService::class.java).setAction(CoreContract.ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, VPN_CHANNEL)
            .setSmallIcon(if (connected) android.R.drawable.presence_online else android.R.drawable.stat_sys_download)
            .setContentTitle(if (connected) "VMess Pro • متصل" else "VMess Pro")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, "قطع اتصال", stopIntent)
            .build()
    }

    private fun updateNotification(text: String, connected: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildForegroundNotification(text, connected))
    }

    private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
    } else {
        "${address.hostAddress}/$networkPrefixLength"
    }

    private class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    internal class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private data class ConnectRequest(
        val profileId: String,
        val splitMode: SplitTunnelMode,
        val includedPackages: Set<String>,
        val excludedPackages: Set<String>,
        val customDns: String?,
    ) {
        companion object {
            fun from(intent: Intent, ownPackage: String): ConnectRequest {
                val mode = runCatching {
                    SplitTunnelMode.valueOf(
                        intent.getStringExtra(CoreContract.EXTRA_SPLIT_MODE)
                            ?: SplitTunnelMode.EXCLUDE_SELECTED.name
                    )
                }.getOrDefault(SplitTunnelMode.EXCLUDE_SELECTED)
                val banking = intent.getStringArrayListExtra(CoreContract.EXTRA_BANKING).orEmpty().toSet()
                val included = intent.getStringArrayListExtra(CoreContract.EXTRA_INCLUDED).orEmpty().toMutableSet()
                val excluded = intent.getStringArrayListExtra(CoreContract.EXTRA_EXCLUDED).orEmpty().toMutableSet()
                if (mode == SplitTunnelMode.ONLY_SELECTED) {
                    included += ownPackage
                    included -= banking
                } else {
                    excluded += banking
                    excluded -= ownPackage
                }
                return ConnectRequest(
                    profileId = requireNotNull(intent.getStringExtra(CoreContract.EXTRA_PROFILE_ID)) { "profile id is missing" },
                    splitMode = mode,
                    includedPackages = included,
                    excludedPackages = excluded,
                    customDns = intent.getStringExtra(CoreContract.EXTRA_CUSTOM_DNS),
                )
            }
        }
    }

    companion object {
        private const val TAG = "VpnCoreService"
        private const val VPN_CHANNEL = "vpn_core"
        private const val CORE_EVENT_CHANNEL = "vpn_core_events"
        private const val NOTIFICATION_ID = 4101
    }
}
