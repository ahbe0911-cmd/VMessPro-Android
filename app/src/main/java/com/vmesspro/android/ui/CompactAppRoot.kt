package com.vmesspro.android.ui

import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.data.local.NodeEntity
import com.vmesspro.android.data.local.SubscriptionEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferences
import kotlinx.coroutines.launch

private enum class CompactTab(val title: String, val glyph: String) {
    Home("خانه", "●"),
    Servers("پروفایل‌ها", "≡"),
    Settings("تنظیمات", "⚙"),
}

@Composable
fun CompactAppRoot(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showAddSubscription by rememberSaveable { mutableStateOf(false) }
    var showSplitTunnel by rememberSaveable { mutableStateOf(false) }

    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val testingAll by viewModel.testingAllNodes.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val connectPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (VpnService.prepare(context) == null) viewModel.connectSelected()
        else scope.launch { snackbar.showSnackbar("مجوز VPN صادر نشد") }
    }

    val testPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (VpnService.prepare(context) == null) viewModel.testAllAndSelectBest()
        else scope.launch { snackbar.showSnackbar("برای تست واقعی سرورها مجوز VPN لازم است") }
    }

    fun toggleVpn() {
        when (connectionState) {
            is ConnectionState.Connected,
            ConnectionState.Preparing,
            ConnectionState.Connecting,
            ConnectionState.Verifying,
            ConnectionState.Reconnecting -> viewModel.disconnect()

            else -> {
                if (selectedNode == null) {
                    selectedTab = CompactTab.Servers.ordinal
                    scope.launch { snackbar.showSnackbar("ابتدا یک پروفایل انتخاب کنید") }
                } else {
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) viewModel.connectSelected()
                    else connectPermissionLauncher.launch(permissionIntent)
                }
            }
        }
    }

    fun testAllReal() {
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent == null) viewModel.testAllAndSelectBest()
        else testPermissionLauncher.launch(permissionIntent)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = showSplitTunnel) { showSplitTunnel = false }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!showSplitTunnel) {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        CompactTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Text(tab.glyph, fontWeight = FontWeight.Black) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            if (showSplitTunnel) {
                CompactSplitTunnelScreen(
                    modifier = Modifier.padding(padding),
                    preferences = preferences,
                    apps = installedApps,
                    loading = appsLoading,
                    onBack = { showSplitTunnel = false },
                    onLoad = viewModel::loadInstalledApps,
                    onModeChange = viewModel::setSplitMode,
                    onTogglePackage = viewModel::toggleSplitPackage,
                )
            } else {
                when (CompactTab.entries[selectedTab]) {
                    CompactTab.Home -> CompactHomeScreen(
                        modifier = Modifier.padding(padding),
                        state = connectionState,
                        selectedNode = selectedNode,
                        nodeCount = nodes.size,
                        subscriptionCount = subscriptions.size,
                        downBytesPerSecond = telemetry.downloadBytesPerSecond,
                        upBytesPerSecond = telemetry.uploadBytesPerSecond,
                        testingAll = testingAll,
                        onPower = ::toggleVpn,
                        onSelectServer = { selectedTab = CompactTab.Servers.ordinal },
                        onImport = { showImport = true },
                    )

                    CompactTab.Servers -> CompactServersScreen(
                        modifier = Modifier.padding(padding),
                        nodes = nodes,
                        subscriptions = subscriptions,
                        selectedNodeId = selectedNode?.stableId,
                        testingAll = testingAll,
                        testProgress = testProgress,
                        onSelect = viewModel::selectNode,
                        onTestAll = ::testAllReal,
                        onImport = { showImport = true },
                        onAddSubscription = { showAddSubscription = true },
                        onRefreshSubscription = viewModel::refreshSubscription,
                        onDeleteSubscription = viewModel::deleteSubscription,
                    )

                    CompactTab.Settings -> CompactSettingsScreen(
                        modifier = Modifier.padding(padding),
                        preferences = preferences,
                        onAutoReconnect = viewModel::setAutoReconnect,
                        onSaveDns = viewModel::setDns,
                        onOpenSplitTunnel = {
                            showSplitTunnel = true
                            viewModel.loadInstalledApps()
                        },
                    )
                }
            }
        }

        if (showImport) {
            CompactImportDialog(
                onDismiss = { showImport = false },
                onImport = {
                    viewModel.importText(it)
                    showImport = false
                    selectedTab = CompactTab.Servers.ordinal
                },
            )
        }

        if (showAddSubscription) {
            CompactSubscriptionDialog(
                onDismiss = { showAddSubscription = false },
                onAdd = { name, url ->
                    viewModel.addSubscription(name, url)
                    showAddSubscription = false
                },
            )
        }
    }
}

@Composable
private fun CompactHomeScreen(
    modifier: Modifier,
    state: ConnectionState,
    selectedNode: NodeEntity?,
    nodeCount: Int,
    subscriptionCount: Int,
    downBytesPerSecond: Long,
    upBytesPerSecond: Long,
    testingAll: Boolean,
    onPower: () -> Unit,
    onSelectServer: () -> Unit,
    onImport: () -> Unit,
) {
    val active = state is ConnectionState.Connected
    val busy = state == ConnectionState.Preparing ||
        state == ConnectionState.Connecting ||
        state == ConnectionState.Verifying ||
        state == ConnectionState.Reconnecting

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("VMess Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        "اتصال سریع و تأییدشده",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(state)
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ConnectionOrb(
                    active = active,
                    busy = busy,
                    enabled = !testingAll,
                    onClick = onPower,
                )
                Text(
                    text = connectionHeadline(state),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = connectionDetail(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            Card(
                onClick = onSelectServer,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            selectedNode?.countryCode?.takeIf { it.isNotBlank() } ?: "VPN",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            selectedNode?.name ?: "انتخاب پروفایل",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            selectedNode?.let {
                                "${it.protocol.uppercase()} • ${it.lastLatencyMs?.let { ms -> "$ms ms" } ?: "تست‌نشده"}"
                            } ?: "برای اتصال یک پروفایل انتخاب کنید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("‹", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("↓ دانلود", formatRate(downBytesPerSecond), Modifier.weight(1f))
                MetricCard("↑ آپلود", formatRate(upBytesPerSecond), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("پروفایل", nodeCount.toString(), Modifier.weight(1f))
                MetricCard("اشتراک", subscriptionCount.toString(), Modifier.weight(1f))
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    "وضعیت «متصل» فقط بعد از عبور موفق درخواست HTTPS از داخل VPN نمایش داده می‌شود.",
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        item {
            FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("افزودن کانفیگ یا Subscription")
            }
        }
    }
}

@Composable
private fun ConnectionOrb(
    active: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(150.dp),
        shape = CircleShape,
        color = when {
            active -> MaterialTheme.colorScheme.primaryContainer
            busy -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = BorderStroke(
            width = 2.dp,
            color = when {
                active -> MaterialTheme.colorScheme.primary
                busy -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(112.dp), strokeWidth = 3.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (active) "✓" else "⏻",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (active) "قطع" else if (busy) "لغو" else "اتصال",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CompactServersScreen(
    modifier: Modifier,
    nodes: List<NodeEntity>,
    subscriptions: List<SubscriptionEntity>,
    selectedNodeId: String?,
    testingAll: Boolean,
    testProgress: NodeTestProgress,
    onSelect: (String) -> Unit,
    onTestAll: () -> Unit,
    onImport: () -> Unit,
    onAddSubscription: () -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("پروفایل‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "مدیریت اشتراک‌ها و انتخاب بهترین مسیر واقعی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Button(
                onClick = onTestAll,
                enabled = !testingAll && nodes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (testingAll) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("تست واقعی ${testProgress.completed}/${testProgress.total}")
                } else {
                    Text("تست واقعی همه و انتخاب بهترین", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (testingAll) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "${testProgress.successful} سالم از ${testProgress.completed} تست‌شده • معیار: عبور HTTPS واقعی",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("کانفیگ") }
                FilledTonalButton(onClick = onAddSubscription, modifier = Modifier.weight(1f)) { Text("Subscription") }
            }
        }

        if (subscriptions.isNotEmpty()) {
            item { SectionTitle("اشتراک‌ها") }
            items(subscriptions, key = { "sub-${it.id}" }) { subscription ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(subscription.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subscription.lastRefreshError
                                    ?: "${nodes.count { it.subscriptionId == subscription.id }} پروفایل",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (subscription.lastRefreshError == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        TextButton(onClick = { onRefreshSubscription(subscription.id) }) { Text("بروزرسانی") }
                        TextButton(onClick = { onDeleteSubscription(subscription.id) }) { Text("حذف") }
                    }
                }
            }
            item { HorizontalDivider() }
        }

        item { SectionTitle("سرورها") }

        if (nodes.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text(
                        "هنوز کانفیگی اضافه نشده است.",
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(nodes, key = { it.stableId }) { node ->
                CompactNodeCard(
                    node = node,
                    selected = node.stableId == selectedNodeId,
                    subscriptionName = subscriptions.firstOrNull { it.id == node.subscriptionId }?.name,
                    onSelect = { onSelect(node.stableId) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun CompactNodeCard(
    node: NodeEntity,
    selected: Boolean,
    subscriptionName: String?,
    onSelect: () -> Unit,
) {
    val border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (node.lastProbeSucceeded) {
                    true -> MaterialTheme.colorScheme.tertiaryContainer
                    false -> MaterialTheme.colorScheme.errorContainer
                    null -> MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        node.lastLatencyMs?.let { "$it" } ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text("ms", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(node.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(node.protocol.uppercase(), subscriptionName).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                when {
                    selected -> "✓"
                    node.lastProbeSucceeded == true -> "●"
                    node.lastProbeSucceeded == false -> "×"
                    else -> "○"
                },
                fontWeight = FontWeight.Black,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    node.lastProbeSucceeded == true -> MaterialTheme.colorScheme.tertiary
                    node.lastProbeSucceeded == false -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun CompactSettingsScreen(
    modifier: Modifier,
    preferences: VpnPreferences,
    onAutoReconnect: (Boolean) -> Unit,
    onSaveDns: (String?) -> Unit,
    onOpenSplitTunnel: () -> Unit,
) {
    var dns by remember(preferences.customDns) { mutableStateOf(preferences.customDns.orEmpty()) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("تنظیمات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "تنظیمات شبکه و مسیریابی برنامه‌ها",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اتصال مجدد خودکار", fontWeight = FontWeight.Bold)
                        Text("در تغییر شبکه دوباره تلاش شود", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = preferences.autoReconnect, onCheckedChange = onAutoReconnect)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DNS سفارشی", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = dns,
                        onValueChange = { dns = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("مثلاً 1.1.1.1:53") },
                    )
                    Button(onClick = { onSaveDns(dns.trim().ifBlank { null }) }, modifier = Modifier.fillMaxWidth()) {
                        Text("ذخیره DNS")
                    }
                }
            }
        }
        item {
            Card(onClick = onOpenSplitTunnel, shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Split Tunneling", fontWeight = FontWeight.Bold)
                        Text(
                            "انتخاب برنامه‌هایی که داخل یا خارج VPN باشند",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("‹", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun CompactSplitTunnelScreen(
    modifier: Modifier,
    preferences: VpnPreferences,
    apps: List<InstalledAppInfo>,
    loading: Boolean,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Split Tunneling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onModeChange(SplitTunnelMode.EXCLUDE_SELECTED) },
                modifier = Modifier.weight(1f),
                border = if (preferences.splitTunnelMode == SplitTunnelMode.EXCLUDE_SELECTED) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else null,
            ) { Text("خارج از VPN") }
            OutlinedButton(
                onClick = { onModeChange(SplitTunnelMode.ONLY_SELECTED) },
                modifier = Modifier.weight(1f),
                border = if (preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else null,
            ) { Text("فقط داخل VPN") }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val checked = when (preferences.splitTunnelMode) {
                        SplitTunnelMode.ONLY_SELECTED -> app.packageName in preferences.includedPackages
                        SplitTunnelMode.EXCLUDE_SELECTED -> app.packageName in preferences.excludedPackages
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onTogglePackage(app.packageName, !checked) }.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = checked, onCheckedChange = { onTogglePackage(app.packageName, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانفیگ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(190.dp),
                placeholder = { Text("VMess / VLESS / Reality / Trojan یا لینک اشتراک") },
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onImport(text) }, enabled = text.isNotBlank()) { Text("وارد کردن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun CompactSubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("لینک Subscription") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name.trim().ifBlank { "اشتراک" }, url.trim()) }, enabled = url.isNotBlank()) {
                Text("افزودن")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun StatusPill(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Disconnected -> "قطع" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Preparing -> "آماده‌سازی" to MaterialTheme.colorScheme.secondary
        ConnectionState.Connecting -> "اتصال" to MaterialTheme.colorScheme.secondary
        ConnectionState.Verifying -> "تست HTTPS" to MaterialTheme.colorScheme.tertiary
        is ConnectionState.Connected -> "تأییدشده" to MaterialTheme.colorScheme.primary
        ConnectionState.Reconnecting -> "تلاش مجدد" to MaterialTheme.colorScheme.secondary
        is ConnectionState.Error -> "ناموفق" to MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = color, fontWeight = FontWeight.Bold)
    }
}

private fun connectionHeadline(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "برای اتصال لمس کنید"
    ConnectionState.Preparing -> "آماده‌سازی VPN"
    ConnectionState.Connecting -> "در حال ساخت تونل"
    ConnectionState.Verifying -> "در حال تست اینترنت واقعی"
    is ConnectionState.Connected -> "اتصال واقعی برقرار است"
    ConnectionState.Reconnecting -> "سرور بعدی در حال بررسی است"
    is ConnectionState.Error -> "اتصال واقعی برقرار نشد"
}

private fun connectionDetail(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "هیچ اتصال فیکی به‌عنوان موفق نمایش داده نمی‌شود"
    ConnectionState.Preparing -> "بررسی مجوز و تنظیمات شبکه"
    ConnectionState.Connecting -> "راه‌اندازی sing-box و رابط TUN"
    ConnectionState.Verifying -> "درخواست HTTPS باید از داخل VPN پاسخ بگیرد"
    is ConnectionState.Connected -> "ترافیک وب از مسیر VPN با موفقیت تأیید شد"
    ConnectionState.Reconnecting -> "کانفیگ قبلی دیتا نداد؛ تلاش با گزینه بعدی"
    is ConnectionState.Error -> state.reason
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

private fun formatRate(bytesPerSecond: Long): String {
    val value = bytesPerSecond.coerceAtLeast(0L)
    return when {
        value >= 1024L * 1024L -> String.format("%.1f MB/s", value / (1024.0 * 1024.0))
        value >= 1024L -> String.format("%.0f KB/s", value / 1024.0)
        else -> "$value B/s"
    }
}
