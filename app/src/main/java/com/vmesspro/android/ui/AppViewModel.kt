package com.vmesspro.android.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.vmesspro.android.core.AndroidCoreAdapter
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.core.SmartNodeSelector
import com.vmesspro.android.core.VpnTelemetry
import com.vmesspro.android.data.local.AppDatabase
import com.vmesspro.android.data.local.ConnectionHistoryEntity
import com.vmesspro.android.data.local.FavoriteEntity
import com.vmesspro.android.data.local.NodeEntity
import com.vmesspro.android.data.local.SubscriptionEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferences
import com.vmesspro.android.data.preferences.VpnPreferencesRepository
import com.vmesspro.android.data.security.SecureConfigStore
import com.vmesspro.android.domain.config.BulkImportParser
import com.vmesspro.android.domain.config.ImportPreview
import com.vmesspro.android.domain.config.ProxyProfile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
)

data class NodeTestProgress(
    val completed: Int = 0,
    val total: Int = 0,
    val successful: Int = 0,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "vmesspro.db",
    ).fallbackToDestructiveMigration().build()
    private val preferencesRepository = VpnPreferencesRepository(application)
    private val secureStore = SecureConfigStore()
    private val coreAdapter = AndroidCoreAdapter(application)

    val connectionState: StateFlow<ConnectionState> = coreAdapter.state
    val telemetry: StateFlow<VpnTelemetry> = coreAdapter.telemetry

    val nodes: StateFlow<List<NodeEntity>> = database.nodeDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val subscriptions: StateFlow<List<SubscriptionEntity>> = database.subscriptionDao().observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val connectionHistory: StateFlow<List<ConnectionHistoryEntity>> =
        database.connectionHistoryDao().observeRecent(limit = 50).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val favoriteIds: StateFlow<Set<String>> = database.favoriteDao().observeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val preferences: StateFlow<VpnPreferences> = preferencesRepository.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        VpnPreferences(),
    )

    val selectedNode: StateFlow<NodeEntity?> = combine(nodes, preferences) { nodeList, prefs ->
        nodeList.firstOrNull { it.stableId == prefs.selectedNodeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading

    private val _testingAllNodes = MutableStateFlow(false)
    val testingAllNodes: StateFlow<Boolean> = _testingAllNodes

    private val _testProgress = MutableStateFlow(NodeTestProgress())
    val testProgress: StateFlow<NodeTestProgress> = _testProgress

    val events = MutableSharedFlow<String>(extraBufferCapacity = 12)

    fun connectSelected() {
        val id = selectedNode.value?.stableId
        if (id == null) {
            events.tryEmit("ابتدا یک سرور انتخاب کنید")
            return
        }
        if (_testingAllNodes.value) {
            events.tryEmit("تست واقعی سرورها در حال اجراست")
            return
        }
        viewModelScope.launch {
            runCatching { coreAdapter.connect(id) }
                .onFailure { events.emit("شروع VPN ناموفق بود: ${it.message ?: "خطای Core"}") }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            runCatching { coreAdapter.disconnect() }
                .onFailure { events.emit("قطع VPN ناموفق بود") }
        }
    }

    fun toggleConnection() {
        when (connectionState.value) {
            is ConnectionState.Connected,
            ConnectionState.Preparing,
            ConnectionState.Connecting,
            ConnectionState.Verifying,
            ConnectionState.Reconnecting -> disconnect()
            else -> connectSelected()
        }
    }

    fun probeNode(id: String) {
        if (_testingAllNodes.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val node = database.nodeDao().getById(id) ?: return@launch
            val result = coreAdapter.probe(id)
            val now = System.currentTimeMillis()
            val verifiedLatency = result.httpRttMs ?: result.tcpLatencyMs
            database.nodeDao().upsert(
                listOf(
                    node.copy(
                        lastLatencyMs = verifiedLatency ?: node.lastLatencyMs,
                        lastProbeSucceeded = result.success,
                        lastTestedAt = now,
                        consecutiveFailures = if (result.success) 0 else node.consecutiveFailures + 1,
                        updatedAt = now,
                    )
                )
            )
            events.emit(
                if (result.success) "تست Xray واقعی موفق: ${verifiedLatency ?: 0} ms"
                else "Xray این کانفیگ نتوانست HTTP واقعی عبور دهد: ${result.error ?: "ناموفق"}"
            )
        }
    }

    fun testAllNodes() = testAllNodesInternal(selectBestAfter = false)

    fun testAllAndSelectBest() = testAllNodesInternal(selectBestAfter = true)

    private fun testAllNodesInternal(selectBestAfter: Boolean) {
        if (_testingAllNodes.value) return
        val snapshot = nodes.value
        if (snapshot.isEmpty()) {
            events.tryEmit("سروری برای تست وجود ندارد")
            return
        }
        if (connectionState.value !is ConnectionState.Disconnected && connectionState.value !is ConnectionState.Error) {
            events.tryEmit("برای تست گروهی Xray ابتدا اتصال VPN را قطع کنید")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _testingAllNodes.value = true
            _testProgress.value = NodeTestProgress(total = snapshot.size)
            try {
                events.emit("تست واقعی Xray برای ${snapshot.size} کانفیگ شروع شد")
                val semaphore = Semaphore(MAX_PARALLEL_XRAY_TESTS)
                val completed = AtomicInteger(0)
                val successful = AtomicInteger(0)

                val results = snapshot.map { node ->
                    async {
                        semaphore.withPermit {
                            val result = coreAdapter.probe(node.stableId)
                            val done = completed.incrementAndGet()
                            val ok = if (result.success) successful.incrementAndGet() else successful.get()
                            _testProgress.value = NodeTestProgress(
                                completed = done,
                                total = snapshot.size,
                                successful = ok,
                            )
                            node to result
                        }
                    }
                }.awaitAll()

                val now = System.currentTimeMillis()
                val updatedNodes = results.map { (node, result) ->
                    val verifiedLatency = result.httpRttMs ?: result.tcpLatencyMs
                    node.copy(
                        lastLatencyMs = verifiedLatency ?: node.lastLatencyMs,
                        lastProbeSucceeded = result.success,
                        lastTestedAt = now,
                        consecutiveFailures = if (result.success) 0 else node.consecutiveFailures + 1,
                        updatedAt = now,
                    )
                }
                database.nodeDao().upsert(updatedNodes)

                val successfulNodes = updatedNodes.filter { it.lastProbeSucceeded == true }
                val successfulCount = successfulNodes.size
                if (selectBestAfter && successfulNodes.isNotEmpty()) {
                    val best = SmartNodeSelector
                        .order(successfulNodes, preferredId = null, nowEpochMillis = now)
                        .firstOrNull()
                    if (best != null) {
                        preferencesRepository.setSelectedNode(best.stableId)
                        events.emit(
                            "تست Xray تمام شد: $successfulCount از ${snapshot.size} سالم • بهترین: ${best.name} • ${best.lastLatencyMs ?: 0} ms"
                        )
                    }
                } else {
                    events.emit("تست Xray تمام شد: $successfulCount از ${snapshot.size} کانفیگ HTTP واقعی عبور دادند")
                }

                if (successfulNodes.isEmpty()) {
                    events.emit("هیچ کانفیگی در تست واقعی Xray سالم نبود؛ TCP باز به‌تنهایی موفق محسوب نمی‌شود")
                }
            } catch (error: Throwable) {
                events.emit("تست Xray کامل نشد: ${error.message ?: "خطای شبکه"}")
            } finally {
                _testingAllNodes.value = false
            }
        }
    }

    fun selectNode(id: String) {
        viewModelScope.launch {
            preferencesRepository.setSelectedNode(id)
            events.emit("سرور انتخاب شد")
        }
    }

    fun deleteNode(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.nodeDao().deleteById(id)
            if (preferences.value.selectedNodeId == id) preferencesRepository.setSelectedNode(null)
            events.emit("سرور حذف شد")
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (id in favoriteIds.value) {
                database.favoriteDao().remove(id)
                events.emit("از علاقه‌مندی‌ها حذف شد")
            } else {
                database.favoriteDao().add(FavoriteEntity(id, System.currentTimeMillis()))
                events.emit("به علاقه‌مندی‌ها اضافه شد")
            }
        }
    }

    fun importText(input: String): ImportPreview {
        val preview = BulkImportParser.parse(input)
        if (preview.profiles.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    database.withTransaction {
                        val entities = preview.profiles.map { profile ->
                            val previous = database.nodeDao().getById(profile.stableId)
                            profile.toNodeEntity(subscriptionId = null, previous = previous)
                        }
                        database.nodeDao().upsert(entities)
                    }
                }.onSuccess {
                    events.emit("${preview.profiles.size} سرور با موفقیت ذخیره شد")
                }.onFailure {
                    events.emit("ذخیره کانفیگ انجام نشد")
                }
            }
        }
        preview.subscriptionUrls.forEachIndexed { index, url ->
            addSubscription("اشتراک ${index + 1}", url)
        }
        return preview
    }

    fun addSubscription(name: String, url: String) {
        val normalized = url.trim()
        val parsed = runCatching { URI(normalized) }.getOrNull()
        if (parsed?.host.isNullOrBlank() || parsed?.scheme?.lowercase() !in setOf("http", "https")) {
            events.tryEmit("لینک اشتراک معتبر نیست")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val id = sha256(normalized)
            runCatching {
                refreshSubscriptionInternal(id, name.trim().ifBlank { "اشتراک" }, normalized)
            }.onSuccess { count ->
                events.emit("اشتراک به‌روزرسانی شد؛ $count سرور دریافت شد")
            }.onFailure {
                events.emit("دریافت اشتراک ناموفق بود: ${it.message ?: "خطای شبکه"}")
            }
        }
    }

    fun refreshSubscription(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val subscription = database.subscriptionDao().getById(id) ?: return@launch
            runCatching {
                val url = secureStore.decrypt(subscription.encryptedUrl)
                refreshSubscriptionInternal(id, subscription.name, url)
            }.onSuccess { count ->
                events.emit("${subscription.name}: $count سرور به‌روزرسانی شد")
            }.onFailure {
                val now = System.currentTimeMillis()
                database.subscriptionDao().upsert(
                    subscription.copy(lastRefreshError = it.message ?: "خطای شبکه", updatedAt = now)
                )
                events.emit("به‌روزرسانی اشتراک ناموفق بود")
            }
        }
    }

    fun deleteSubscription(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val subscription = database.subscriptionDao().getById(id) ?: return@launch
            database.withTransaction {
                database.nodeDao().deleteBySubscription(id)
                database.subscriptionDao().delete(subscription)
            }
            if (nodes.value.none { it.stableId == preferences.value.selectedNodeId }) {
                preferencesRepository.setSelectedNode(null)
            }
            events.emit("اشتراک حذف شد")
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoReconnect(enabled) }
    }

    fun setDns(value: String?) {
        viewModelScope.launch {
            preferencesRepository.setCustomDns(value)
            events.emit("DNS ذخیره شد؛ در اتصال بعدی اعمال می‌شود")
        }
    }

    fun setSplitMode(mode: SplitTunnelMode) {
        val prefs = preferences.value
        viewModelScope.launch {
            preferencesRepository.setSplitTunnel(
                mode = mode,
                includedPackages = prefs.includedPackages,
                excludedPackages = prefs.excludedPackages,
                bankingPackages = prefs.bankingPackages,
            )
        }
    }

    fun toggleSplitPackage(packageName: String, enabled: Boolean) {
        val prefs = preferences.value
        val included = prefs.includedPackages.toMutableSet()
        val excluded = prefs.excludedPackages.toMutableSet()
        when (prefs.splitTunnelMode) {
            SplitTunnelMode.ONLY_SELECTED -> if (enabled) included += packageName else included -= packageName
            SplitTunnelMode.EXCLUDE_SELECTED -> if (enabled) excluded += packageName else excluded -= packageName
        }
        viewModelScope.launch {
            preferencesRepository.setSplitTunnel(
                mode = prefs.splitTunnelMode,
                includedPackages = included,
                excludedPackages = excluded,
                bankingPackages = prefs.bankingPackages,
            )
        }
    }

    fun loadInstalledApps() {
        if (_appsLoading.value || _installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _appsLoading.value = true
            val pm = getApplication<Application>().packageManager
            val apps = getInstalledApplications(pm)
                .asSequence()
                .filter { it.packageName != getApplication<Application>().packageName }
                .map {
                    InstalledAppInfo(
                        label = runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName),
                        packageName = it.packageName,
                        isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
            _installedApps.value = apps
            _appsLoading.value = false
        }
    }

    private suspend fun refreshSubscriptionInternal(id: String, name: String, url: String): Int {
        val response = fetchSubscription(url)
        val preview = BulkImportParser.parse(response.body)
        require(preview.profiles.isNotEmpty()) { "هیچ کانفیگ معتبری در اشتراک پیدا نشد" }
        val now = System.currentTimeMillis()
        val oldSubscription = database.subscriptionDao().getById(id)

        return database.withTransaction {
            val existingNodes = database.nodeDao().getBySubscription(id).associateBy { it.stableId }
            val entities = preview.profiles.map { profile ->
                profile.toNodeEntity(
                    subscriptionId = id,
                    previous = existingNodes[profile.stableId],
                )
            }

            database.nodeDao().upsert(entities)
            database.nodeDao().deleteMissingFromSubscription(id, entities.map { it.stableId })
            database.subscriptionDao().upsert(
                SubscriptionEntity(
                    id = id,
                    name = name,
                    encryptedUrl = oldSubscription?.encryptedUrl ?: secureStore.encrypt(url),
                    enabled = oldSubscription?.enabled ?: true,
                    autoRefresh = oldSubscription?.autoRefresh ?: true,
                    etag = response.etag,
                    lastModified = response.lastModified,
                    usedBytes = oldSubscription?.usedBytes,
                    totalBytes = oldSubscription?.totalBytes,
                    expiresAt = oldSubscription?.expiresAt,
                    lastRefreshAt = now,
                    lastRefreshError = null,
                    createdAt = oldSubscription?.createdAt ?: now,
                    updatedAt = now,
                )
            )
            entities.size
        }
    }

    /**
     * Preserve the original share URI exactly. The Xray engine consumes rawUri directly so
     * Reality/transport parameters are not reconstructed by our Kotlin model.
     */
    private fun ProxyProfile.toNodeEntity(
        subscriptionId: String?,
        previous: NodeEntity? = null,
    ): NodeEntity {
        val now = System.currentTimeMillis()
        return NodeEntity(
            stableId = stableId,
            subscriptionId = subscriptionId,
            name = name,
            countryCode = previous?.countryCode,
            protocol = protocol.name,
            host = server,
            port = port,
            encryptedConfig = secureStore.encrypt(rawUri),
            lastLatencyMs = previous?.lastLatencyMs,
            lastProbeSucceeded = previous?.lastProbeSucceeded,
            consecutiveFailures = previous?.consecutiveFailures ?: 0,
            lastTestedAt = previous?.lastTestedAt,
            lastUsedAt = previous?.lastUsedAt,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private data class SubscriptionResponse(
        val body: String,
        val etag: String?,
        val lastModified: String?,
    )

    private suspend fun fetchSubscription(url: String): SubscriptionResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VMessPro/0.5 Android Xray")
            setRequestProperty("Accept", "text/plain,*/*")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(8_192)
                var total = 0
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 5_000_000) { "حجم اشتراک بیش از حد مجاز است" }
                    builder.append(buffer, 0, read)
                }
                builder.toString()
            }
            SubscriptionResponse(
                body = body,
                etag = connection.getHeaderField("ETag"),
                lastModified = connection.getHeaderField("Last-Modified"),
            )
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledApplications(pm: PackageManager): List<ApplicationInfo> {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    override fun onCleared() {
        coreAdapter.close()
        database.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_PARALLEL_XRAY_TESTS = 3
    }
}
