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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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

private enum class CompactTab(val title: String) {
    Home("خانه"),
    Servers("سرورها"),
    Settings("تنظیمات"),
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

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (VpnService.prepare(context) == null) {
            viewModel.connectSelected()
        } else {
            scope.launch { snackbar.showSnackbar("مجوز VPN صادر نشد") }
        }
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
                    scope.launch { snackbar.showSnackbar("ابتدا یک سرور انتخاب کنید") }
                } else {
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) viewModel.connectSelected()
                    else vpnPermissionLauncher.launch(permissionIntent)
                }
            }
        }
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
                                icon = {
                                    Text(
                                        text = when (tab) {
                                            CompactTab.Home -> "●"
                                            CompactTab.Servers -> "≡"
                                            CompactTab.Settings -> "⚙"
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
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
                        onSelect = viewModel::selectNode,
                        onTestAll = viewModel::testAllAndSelectBest,
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
    onPower: () -> Unit,
    onSelectServer: () -> Unit,
    onImport: () -> Unit,
) {
    val active = state is ConnectionState.Connected
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VMess Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "سبک، سریع و متمرکز روی اتصال واقعی",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatusPill(state)
                    Text(
                        selectedNode?.name ?: "سروری انتخاب نشده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    selectedNode?.let {
                        Text(
                            "${it.protocol.uppercase()}  •  ${it.lastLatencyMs?.let { ms -> "$ms ms" } ?: "بدون تست"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onPower,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(if (active) "قطع اتصال" else stateActionLabel(state), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onSelectServer, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب سرور")
                    }
                }
            }
        }

        if (active) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("دانلود", formatRate(downBytesPerSecond), Modifier.weight(1f))
                    MetricCard("آپلود", formatRate(upBytesPerSecond), Modifier.weight(1f))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("سرورها", nodeCount.toString(), Modifier.weight(1f))
                MetricCard("اشتراک‌ها", subscriptionCount.toString(), Modifier.weight(1f))
            }
        }

        item {
            FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("افزودن کانفیگ یا لینک اشتراک")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("سرورها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "یک بار بزن؛ همه کانفیگ‌ها تست و بهترین گزینه انتخاب می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onTestAll, enabled = !testingAll && nodes.isNotEmpty()) {
                    if (testingAll) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                    } else {
                        Text("تست همه")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("افزودن کانفیگ") }
                FilledTonalButton(onClick = onAddSubscription, modifier = Modifier.weight(1f)) { Text("افزودن اشتراک") }
            }
        }

        if (subscriptions.isNotEmpty()) {
            item {
                Text("اشتراک‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(subscriptions, key = { "sub-${it.id}" }) { subscription ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(subscription.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    subscription.lastRefreshError ?: "${nodes.count { it.subscriptionId == subscription.id }} سرور",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (subscription.lastRefreshError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                )
                            }
                            TextButton(onClick = { onRefreshSubscription(subscription.id) }) { Text("بروزرسانی") }
                            TextButton(onClick = { onDeleteSubscription(subscription.id) }) { Text("حذف") }
                        }
                    }
                }
            }
            item { HorizontalDivider() }
        }

        if (nodes.isEmpty()) {
            item {
                Card {
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
private fun CompactNodeCard(
    node: NodeEntity,
    selected: Boolean,
    subscriptionName: String?,
    onSelect: () -> Unit,
) {
    val border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when (node.lastProbeSucceeded) {
                    true -> MaterialTheme.colorScheme.tertiaryContainer
                    false -> MaterialTheme.colorScheme.errorContainer
                    null -> MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    node.lastLatencyMs?.let { "$it ms" } ?: "—",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(node.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(node.protocol.uppercase(), node.countryCode, subscriptionName).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Text("انتخاب‌شده", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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
            Text("تنظیمات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card {
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
            Card {
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
            Card(onClick = onOpenSplitTunnel) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Split Tunneling", fontWeight = FontWeight.Bold)
                    Text(
                        "انتخاب برنامه‌هایی که باید داخل یا خارج VPN باشند",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            Text("Split Tunneling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onModeChange(SplitTunnelMode.EXCLUDE_SELECTED) },
                modifier = Modifier.weight(1f),
                border = if (preferences.splitTunnelMode == SplitTunnelMode.EXCLUDE_SELECTED)
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            ) { Text("خارج از VPN") }
            OutlinedButton(
                onClick = { onModeChange(SplitTunnelMode.ONLY_SELECTED) },
                modifier = Modifier.weight(1f),
                border = if (preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED)
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
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
        title = { Text("افزودن اشتراک") },
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
        ConnectionState.Connecting -> "در حال اتصال" to MaterialTheme.colorScheme.secondary
        ConnectionState.Verifying -> "در حال تأیید اینترنت واقعی" to MaterialTheme.colorScheme.tertiary
        is ConnectionState.Connected -> "متصل و تأییدشده" to MaterialTheme.colorScheme.tertiary
        ConnectionState.Reconnecting -> "اتصال مجدد" to MaterialTheme.colorScheme.secondary
        is ConnectionState.Error -> "خطا" to MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = color, fontWeight = FontWeight.Bold)
    }
}

private fun stateActionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Preparing -> "لغو آماده‌سازی"
    ConnectionState.Connecting -> "لغو اتصال"
    ConnectionState.Verifying -> "لغو بررسی"
    ConnectionState.Reconnecting -> "لغو اتصال مجدد"
    else -> "اتصال"
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
