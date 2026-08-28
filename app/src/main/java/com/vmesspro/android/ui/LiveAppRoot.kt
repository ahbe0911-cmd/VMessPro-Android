package com.vmesspro.android.ui

import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.core.VpnTelemetry
import com.vmesspro.android.data.local.NodeEntity
import com.vmesspro.android.data.local.SubscriptionEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferences
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DeepBlue = Color(0xFF022A93)
private val ElectricBlue = Color(0xFF075BD8)
private val Cyan = Color(0xFF00D9FF)
private val Mint = Color(0xFF43EFA0)
private val Lime = Color(0xFF97F44E)
private val Glass = Color(0x2DFFFFFF)
private val GlassStrong = Color(0x45FFFFFF)
private val GlassBorder = Color(0x65FFFFFF)
private val SoftWhite = Color(0xFFF5F8FF)

private enum class LiveTab(val title: String, val glyph: String) {
    Home("خانه", "⌂"),
    Locations("مکان‌ها", "◎"),
    Stats("آمار", "◴"),
    Settings("تنظیمات", "⚙"),
}

@Composable
fun LiveAppRoot(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showSubscription by rememberSaveable { mutableStateOf(false) }
    var showSplit by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }

    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val testingAll by viewModel.testingAllNodes.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (VpnService.prepare(context) == null) viewModel.connectSelected()
        else scope.launch { snackbarHostState.showSnackbar("مجوز VPN صادر نشد") }
    }

    fun toggleConnection() {
        when (connectionState) {
            is ConnectionState.Connected,
            ConnectionState.Preparing,
            ConnectionState.Connecting,
            ConnectionState.Verifying,
            ConnectionState.Reconnecting -> viewModel.disconnect()

            else -> {
                if (selectedNode == null) {
                    selectedTab = LiveTab.Locations.ordinal
                    scope.launch { snackbarHostState.showSnackbar("ابتدا یک سرور انتخاب کنید") }
                } else {
                    val permission = VpnService.prepare(context)
                    if (permission == null) viewModel.connectSelected() else permissionLauncher.launch(permission)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    BackHandler(enabled = showSplit) { showSplit = false }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF041F7B),
                            Color(0xFF0346B9),
                            Color(0xFF0869DF),
                            Color(0xFF1A5DD0),
                        )
                    )
                ),
        ) {
            LiveNetworkBackground()

            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!showSplit) {
                        LiveBottomBar(
                            selectedTab = selectedTab,
                            onSelect = { selectedTab = it },
                        )
                    }
                },
            ) { padding ->
                if (showSplit) {
                    LiveSplitScreen(
                        modifier = Modifier.padding(padding),
                        preferences = preferences,
                        apps = apps,
                        loading = appsLoading,
                        onBack = { showSplit = false },
                        onLoad = viewModel::loadInstalledApps,
                        onModeChange = viewModel::setSplitMode,
                        onToggle = viewModel::toggleSplitPackage,
                    )
                } else {
                    when (LiveTab.entries[selectedTab]) {
                        LiveTab.Home -> LiveHomeScreen(
                            modifier = Modifier.padding(padding),
                            state = connectionState,
                            selectedNode = selectedNode,
                            telemetry = telemetry,
                            nodeCount = nodes.size,
                            testingAll = testingAll,
                            onPower = ::toggleConnection,
                            onLocations = { selectedTab = LiveTab.Locations.ordinal },
                            onTestAll = viewModel::testAllAndSelectBest,
                            onSplit = {
                                showSplit = true
                                viewModel.loadInstalledApps()
                            },
                            onImport = { showImport = true },
                        )

                        LiveTab.Locations -> LiveLocationsScreen(
                            modifier = Modifier.padding(padding),
                            nodes = nodes,
                            subscriptions = subscriptions,
                            selectedNodeId = selectedNode?.stableId,
                            testingAll = testingAll,
                            progress = testProgress,
                            onSelect = viewModel::selectNode,
                            onProbe = viewModel::probeNode,
                            onTestAll = viewModel::testAllAndSelectBest,
                            onImport = { showImport = true },
                            onAddSubscription = { showSubscription = true },
                            onRefreshSubscription = viewModel::refreshSubscription,
                            onDeleteSubscription = viewModel::deleteSubscription,
                        )

                        LiveTab.Stats -> LiveStatsScreen(
                            modifier = Modifier.padding(padding),
                            telemetry = telemetry,
                            state = connectionState,
                            selectedNode = selectedNode,
                            nodeCount = nodes.size,
                            subscriptionCount = subscriptions.size,
                        )

                        LiveTab.Settings -> LiveSettingsScreen(
                            modifier = Modifier.padding(padding),
                            preferences = preferences,
                            selectedNode = selectedNode,
                            onAutoReconnect = viewModel::setAutoReconnect,
                            onOpenSplit = {
                                showSplit = true
                                viewModel.loadInstalledApps()
                            },
                            onDns = { showDns = true },
                            onTestAll = viewModel::testAllAndSelectBest,
                            onLocations = { selectedTab = LiveTab.Locations.ordinal },
                        )
                    }
                }
            }
        }

        if (showImport) {
            LiveImportDialog(
                onDismiss = { showImport = false },
                onImport = {
                    viewModel.importText(it)
                    showImport = false
                    selectedTab = LiveTab.Locations.ordinal
                },
            )
        }

        if (showSubscription) {
            LiveSubscriptionDialog(
                onDismiss = { showSubscription = false },
                onAdd = { name, url ->
                    viewModel.addSubscription(name, url)
                    showSubscription = false
                },
            )
        }

        if (showDns) {
            LiveDnsDialog(
                initial = preferences.customDns.orEmpty(),
                onDismiss = { showDns = false },
                onSave = {
                    viewModel.setDns(it.ifBlank { null })
                    showDns = false
                },
            )
        }
    }
}

@Composable
private fun LiveHomeScreen(
    modifier: Modifier,
    state: ConnectionState,
    selectedNode: NodeEntity?,
    telemetry: VpnTelemetry,
    nodeCount: Int,
    testingAll: Boolean,
    onPower: () -> Unit,
    onLocations: () -> Unit,
    onTestAll: () -> Unit,
    onSplit: () -> Unit,
    onImport: () -> Unit,
) {
    val connected = state is ConnectionState.Connected
    val busy = state == ConnectionState.Preparing || state == ConnectionState.Connecting ||
        state == ConnectionState.Verifying || state == ConnectionState.Reconnecting
    var publicIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connected) {
        publicIp = if (connected) fetchPublicIpThroughCurrentRoute() else null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveGlassBadge("♛")
                Column(horizontalAlignment = Alignment.End) {
                    Text("اتصال امن", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("سریع، واقعی و بدون اتصال نمایشی", color = Color(0xFFB9D9FF), style = MaterialTheme.typography.bodyMedium)
                }
                ShieldMark()
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LivePowerButton(
                    connected = connected,
                    busy = busy,
                    enabled = !testingAll,
                    onClick = onPower,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) Lime else if (busy) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f)))
                Spacer(Modifier.size(8.dp))
                Text(
                    connectionStatusText(state),
                    color = if (connected) Lime else Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            LiveServerCard(selectedNode = selectedNode, onClick = onLocations)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                LiveMetricCard(
                    title = "امنیت",
                    value = selectedNode?.protocol?.uppercase() ?: "Xray",
                    subtitle = if (connected) "فعال" else "آماده",
                    glyph = "◆",
                    accent = Color(0xFFC678FF),
                    modifier = Modifier.weight(1f),
                )
                LiveMetricCard(
                    title = "سرعت",
                    value = formatMbps(telemetry.downloadBytesPerSecond),
                    subtitle = "Mbps",
                    glyph = "◴",
                    accent = Cyan,
                    modifier = Modifier.weight(1f),
                )
                LiveMetricCard(
                    title = "پینگ",
                    value = selectedNode?.lastLatencyMs?.toString() ?: "—",
                    subtitle = "ms",
                    glyph = "⌁",
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                )
                LiveMetricCard(
                    title = "آی‌پی خروجی",
                    value = publicIp?.let(::shortIp) ?: if (connected) "…" else "—",
                    subtitle = if (connected) "واقعی" else "قطع",
                    glyph = "◎",
                    accent = Color(0xFF2BE3D1),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LiveQuickAction("اتصال هوشمند", "↔", onTestAll, Modifier.weight(1f))
                LiveQuickAction("Xray", "⬡", onLocations, Modifier.weight(1f))
                LiveQuickAction("Split", "↗", onSplit, Modifier.weight(1f))
                LiveQuickAction("تست همه", if (testingAll) "…" else "◴", onTestAll, Modifier.weight(1f))
                LiveQuickAction("افزودن", "+", onImport, Modifier.weight(1f))
            }
        }

        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("پروفایل‌های آماده", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("$nodeCount کانفیگ در برنامه", color = Color(0xFFBBD9FF), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (connected) "دیتا در حال عبور است" else "برای اتصال یک سرور سالم انتخاب کنید",
                        color = if (connected) Lime else Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePowerButton(
    connected: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val buttonBrush = if (connected) {
        Brush.radialGradient(listOf(Color(0xFF88F873), Color(0xFF25D9B8), Color(0xFF0EBEA9)))
    } else {
        Brush.radialGradient(listOf(Color(0xFF76F4B3), Color(0xFF21D3B6), Color(0xFF0CAAA6)))
    }

    Box(
        modifier = Modifier
            .size(246.dp)
            .shadow(34.dp, CircleShape, ambientColor = Cyan.copy(alpha = 0.8f), spotColor = Cyan.copy(alpha = 0.9f))
            .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .padding(10.dp)
            .border(4.dp, Cyan.copy(alpha = 0.75f), CircleShape)
            .padding(13.dp)
            .clip(CircleShape)
            .background(buttonBrush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.20f),
                radius = size.minDimension * 0.43f,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawArc(
                color = Color.White,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(size.width * 0.34f, size.height * 0.28f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.32f),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width * 0.50f, size.height * 0.24f),
                end = Offset(size.width * 0.50f, size.height * 0.43f),
                strokeWidth = 9.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(70.dp))
            Text(
                when {
                    connected -> "قطع اتصال"
                    busy -> "در حال اتصال…"
                    else -> "اتصال"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            if (busy) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun LiveServerCard(selectedNode: NodeEntity?, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White) {
                Text(
                    text = countryFlag(selectedNode?.countryCode),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("موقعیت فعلی", color = Color(0xFFB9D7FF), style = MaterialTheme.typography.bodySmall)
                Text(
                    selectedNode?.name ?: "انتخاب سرور",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selectedNode?.let { "${it.protocol.uppercase()} • ${it.lastLatencyMs?.let { ms -> "$ms ms" } ?: "تست نشده"}" }
                        ?: "برای اتصال یکی از پروفایل‌ها را انتخاب کنید",
                    color = if (selectedNode?.lastProbeSucceeded == true) Mint else Color(0xFFC3DCFF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(shape = CircleShape, color = GlassStrong) {
                Text("‹", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun LiveMetricCard(
    title: String,
    value: String,
    subtitle: String,
    glyph: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(128.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Surface(shape = RoundedCornerShape(9.dp), color = accent.copy(alpha = 0.30f)) {
                    Text(glyph, color = accent, modifier = Modifier.padding(6.dp), fontWeight = FontWeight.Bold)
                }
                Text(title, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun LiveQuickAction(
    title: String,
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.93f),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(shape = RoundedCornerShape(13.dp), color = ElectricBlue.copy(alpha = 0.10f)) {
                Text(glyph, modifier = Modifier.padding(8.dp), color = ElectricBlue, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
            Text(title, color = Color(0xFF202B42), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
private fun LiveLocationsScreen(
    modifier: Modifier,
    nodes: List<NodeEntity>,
    subscriptions: List<SubscriptionEntity>,
    selectedNodeId: String?,
    testingAll: Boolean,
    progress: NodeTestProgress,
    onSelect: (String) -> Unit,
    onProbe: (String) -> Unit,
    onTestAll: () -> Unit,
    onImport: () -> Unit,
    onAddSubscription: () -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LiveScreenHeader("مکان‌ها و سرورها", "انتخاب بهترین Xray با تست واقعی")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onTestAll, enabled = !testingAll, modifier = Modifier.weight(1f)) {
                    Text(if (testingAll) "${progress.completed}/${progress.total}" else "تست همه و انتخاب بهترین")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("افزودن کانفیگ", color = Color.White) }
            }
        }
        if (testingAll) {
            item {
                GlassCard {
                    Text(
                        "تست Xray واقعی: ${progress.completed} از ${progress.total} • سالم ${progress.successful}",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (subscriptions.isNotEmpty()) {
            item { Text("اشتراک‌ها", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
            items(subscriptions, key = { it.id }) { sub ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(sub.lastRefreshError ?: "آماده به‌روزرسانی", color = if (sub.lastRefreshError == null) Mint else Color(0xFFFFB4B4), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        TextButton(onClick = { onRefreshSubscription(sub.id) }) { Text("↻", color = Color.White) }
                        TextButton(onClick = { onDeleteSubscription(sub.id) }) { Text("حذف", color = Color(0xFFFFC1C1)) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onAddSubscription, modifier = Modifier.fillMaxWidth()) {
                Text("+ افزودن Subscription", color = Color.White)
            }
        }
        item { Text("پروفایل‌ها", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
        if (nodes.isEmpty()) {
            item {
                GlassCard {
                    Text("هنوز کانفیگی اضافه نشده است.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = Color.White, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(nodes, key = { it.stableId }) { node ->
                LiveNodeCard(
                    node = node,
                    selected = node.stableId == selectedNodeId,
                    onSelect = { onSelect(node.stableId) },
                    onProbe = { onProbe(node.stableId) },
                )
            }
        }
    }
}

@Composable
private fun LiveNodeCard(node: NodeEntity, selected: Boolean, onSelect: () -> Unit, onProbe: () -> Unit) {
    GlassCard(onClick = onSelect, strong = selected) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(countryFlag(node.countryCode), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f)) {
                Text(node.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${node.protocol.uppercase()} • ${node.lastLatencyMs?.let { "$it ms" } ?: "تست نشده"}",
                    color = if (node.lastProbeSucceeded == true) Mint else Color(0xFFC0D9FF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (selected) Text("✓", color = Lime, fontWeight = FontWeight.Black)
            TextButton(onClick = onProbe) { Text("تست", color = Cyan) }
        }
    }
}

@Composable
private fun LiveSettingsScreen(
    modifier: Modifier,
    preferences: VpnPreferences,
    selectedNode: NodeEntity?,
    onAutoReconnect: (Boolean) -> Unit,
    onOpenSplit: () -> Unit,
    onDns: () -> Unit,
    onTestAll: () -> Unit,
    onLocations: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LiveScreenHeader("تنظیمات", "مدیریت اتصال و رفتار برنامه") }
        item {
            GlassCard(strong = true) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF5138D8).copy(alpha = 0.65f)) {
                        Text("♛", modifier = Modifier.padding(14.dp), color = Color(0xFFFFD653), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("هسته اتصال", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text("Xray + tun2socks • اتصال واقعی", color = Mint, style = MaterialTheme.typography.bodySmall)
                    }
                    ShieldMark()
                }
            }
        }
        item {
            SettingsGroup("اتصال و شبکه") {
                LiveSwitchRow("اتصال خودکار", "در صورت قطع، اتصال دوباره تلاش شود", "ϟ", preferences.autoReconnect, onAutoReconnect)
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveActionRow("پروتکل اتصال", selectedNode?.protocol?.uppercase() ?: "Xray", "⬡", onLocations)
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveActionRow("تونل تفکیکی (Split Tunnel)", splitModeLabel(preferences.splitTunnelMode), "↗", onOpenSplit)
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveActionRow("DNS سفارشی", preferences.customDns ?: "خودکار", "◎", onDns)
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveActionRow("تست سرعت سرورها", "Xray HTTP", "◴", onTestAll)
            }
        }
        item {
            SettingsGroup("پیشرفته و پشتیبانی") {
                LiveInfoRow("موتور VPN", "Amnezia libXray", "◇")
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveInfoRow("اعتبار اتصال", "HTTPS واقعی قبل از Connected", "✓")
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveInfoRow("حفاظت از Loop", "VpnService.protect(fd)", "◈")
            }
        }
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("محافظت پیشرفته فعال است", color = Color.White, fontWeight = FontWeight.Black)
                        Text("وضعیت Connected فقط بعد از عبور واقعی دیتا ثبت می‌شود.", color = Color(0xFFC1DEFF), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("✓", color = Lime, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun LiveStatsScreen(
    modifier: Modifier,
    telemetry: VpnTelemetry,
    state: ConnectionState,
    selectedNode: NodeEntity?,
    nodeCount: Int,
    subscriptionCount: Int,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LiveScreenHeader("آمار اتصال", "اعداد زنده از Core برنامه") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("دانلود لحظه‌ای", formatRate(telemetry.downloadBytesPerSecond), "↓", Modifier.weight(1f))
                BigStat("آپلود لحظه‌ای", formatRate(telemetry.uploadBytesPerSecond), "↑", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("دانلود کل", formatBytes(telemetry.downloadedBytesTotal), "⇩", Modifier.weight(1f))
                BigStat("آپلود کل", formatBytes(telemetry.uploadedBytesTotal), "⇧", Modifier.weight(1f))
            }
        }
        item {
            SettingsGroup("خلاصه") {
                LiveInfoRow("وضعیت", connectionStatusText(state), "●")
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveInfoRow("سرور", selectedNode?.name ?: "انتخاب نشده", "◎")
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveInfoRow("پینگ", selectedNode?.lastLatencyMs?.let { "$it ms" } ?: "—", "◴")
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                LiveInfoRow("پروفایل / اشتراک", "$nodeCount / $subscriptionCount", "≡")
            }
        }
    }
}

@Composable
private fun BigStat(title: String, value: String, glyph: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(glyph, color = Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(title, color = Color(0xFFC1DCFF), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LiveSplitScreen(
    modifier: Modifier,
    preferences: VpnPreferences,
    apps: List<InstalledAppInfo>,
    loading: Boolean,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = onBack, shape = CircleShape, color = GlassStrong) { Text("›", modifier = Modifier.padding(12.dp), color = Color.White) }
                LiveScreenHeader("تونل تفکیکی", "انتخاب برنامه‌های داخل یا خارج VPN")
            }
        }
        item {
            GlassCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حالت اجرا", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onModeChange(SplitTunnelMode.ONLY_SELECTED) },
                            modifier = Modifier.weight(1f),
                            enabled = preferences.splitTunnelMode != SplitTunnelMode.ONLY_SELECTED,
                        ) { Text("فقط انتخاب‌شده‌ها") }
                        Button(
                            onClick = { onModeChange(SplitTunnelMode.EXCLUDE_SELECTED) },
                            modifier = Modifier.weight(1f),
                            enabled = preferences.splitTunnelMode != SplitTunnelMode.EXCLUDE_SELECTED,
                        ) { Text("به‌جز انتخاب‌شده‌ها") }
                    }
                }
            }
        }
        if (loading) {
            item { CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(18.dp)) }
        }
        items(apps, key = { it.packageName }) { app ->
            val enabled = when (preferences.splitTunnelMode) {
                SplitTunnelMode.ONLY_SELECTED -> app.packageName in preferences.includedPackages
                SplitTunnelMode.EXCLUDE_SELECTED -> app.packageName in preferences.excludedPackages
            }
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, color = Color(0xFFBDD8FF), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = enabled, onCheckedChange = { onToggle(app.packageName, it) })
                }
            }
        }
    }
}

@Composable
private fun LiveBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            LiveTab.entries.forEachIndexed { index, tab ->
                val selected = selectedTab == index
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) ElectricBlue.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tab.glyph, color = if (selected) ElectricBlue else Color(0xFF4A5368), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(tab.title, color = if (selected) ElectricBlue else Color(0xFF4A5368), style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun LiveScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color(0xFFBDD9FF), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (strong) GlassStrong else Glass)
        .border(1.dp, GlassBorder, shape)
    Box(modifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier) {
        content()
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFFBBD8FF), style = MaterialTheme.typography.bodyMedium)
        GlassCard {
            Column { content() }
        }
    }
}

@Composable
private fun LiveSwitchRow(
    title: String,
    subtitle: String,
    glyph: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = Mint.copy(alpha = 0.20f)) { Text(glyph, color = Mint, modifier = Modifier.padding(9.dp), fontWeight = FontWeight.Black) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFBDD8FF), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun LiveActionRow(title: String, value: String, glyph: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = Cyan.copy(alpha = 0.18f)) { Text(glyph, color = Cyan, modifier = Modifier.padding(9.dp), fontWeight = FontWeight.Black) }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = Mint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("‹", color = Color.White)
    }
}

@Composable
private fun LiveInfoRow(title: String, value: String, glyph: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.10f)) { Text(glyph, color = Color.White, modifier = Modifier.padding(9.dp), fontWeight = FontWeight.Black) }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xFFBFE0FF), style = MaterialTheme.typography.bodySmall, maxLines = 2, textAlign = TextAlign.End)
    }
}

@Composable
private fun LiveGlassBadge(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Glass, border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)) {
        Text(text, modifier = Modifier.padding(14.dp), color = Color(0xFFFFD558), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ShieldMark() {
    Canvas(modifier = Modifier.size(58.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.04f)
            lineTo(size.width * 0.88f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.70f)
            quadraticBezierTo(size.width * 0.70f, size.height * 0.90f, size.width * 0.5f, size.height * 0.98f)
            quadraticBezierTo(size.width * 0.30f, size.height * 0.90f, size.width * 0.18f, size.height * 0.70f)
            lineTo(size.width * 0.12f, size.height * 0.18f)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(Cyan, Color(0xFF1A7BFF), Color(0xFF5B4CFF))))
        drawCircle(Color(0xFF071C66), radius = size.minDimension * 0.14f, center = center)
        drawLine(Color.White, Offset(size.width * 0.50f, size.height * 0.47f), Offset(size.width * 0.50f, size.height * 0.69f), 4.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun LiveNetworkBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in 0..7) {
            val y = h * (0.30f + i * 0.07f)
            drawArc(
                color = Cyan.copy(alpha = 0.05f + i * 0.004f),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(-w * 0.45f, y - h * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 1.9f, h * 0.40f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        for (i in 0..18) {
            val x = ((i * 73) % 100) / 100f * w
            val y = ((i * 47) % 100) / 100f * h
            drawCircle(Color.White.copy(alpha = 0.10f), radius = (1 + (i % 3)).dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun LiveImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانفیگ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("VMess / VLESS / Reality / Trojan یا لینک Subscription") },
            )
        },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onImport(text) }) { Text("وارد کردن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun LiveSubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن Subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("نام") })
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("لینک") })
            }
        },
        confirmButton = { Button(onClick = { if (url.isNotBlank()) onAdd(name, url) }) { Text("افزودن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun LiveDnsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DNS سفارشی") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("مثلاً 1.1.1.1") }) },
        confirmButton = { Button(onClick = { onSave(value.trim()) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun connectionStatusText(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "آماده برای اتصال"
    ConnectionState.Preparing -> "در حال آماده‌سازی VPN"
    ConnectionState.Connecting -> "در حال راه‌اندازی Xray"
    ConnectionState.Verifying -> "در حال تأیید عبور واقعی دیتا"
    is ConnectionState.Connected -> "متصل و تأییدشده"
    ConnectionState.Reconnecting -> "در حال اتصال مجدد"
    is ConnectionState.Error -> "خطا: ${state.reason}"
}

private fun splitModeLabel(mode: SplitTunnelMode): String = when (mode) {
    SplitTunnelMode.ONLY_SELECTED -> "فقط انتخاب‌شده‌ها"
    SplitTunnelMode.EXCLUDE_SELECTED -> "به‌جز انتخاب‌شده‌ها"
}

private fun countryFlag(code: String?): String {
    val c = code?.trim()?.uppercase().orEmpty()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    return buildString {
        c.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) }
    }
}

private fun shortIp(ip: String): String = if (ip.length <= 14) ip else ip.take(12) + "…"

private fun formatMbps(bytesPerSecond: Long): String =
    String.format(java.util.Locale.US, "%.1f", bytesPerSecond.coerceAtLeast(0L) * 8.0 / 1_000_000.0)

private fun formatRate(bytesPerSecond: Long): String {
    val b = bytesPerSecond.coerceAtLeast(0L).toDouble()
    return when {
        b >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", b / (1024 * 1024))
        b >= 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", b / 1024)
        else -> "${b.toLong()} B/s"
    }
}

private fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L).toDouble()
    return when {
        b >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", b / (1024 * 1024 * 1024))
        b >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", b / (1024 * 1024))
        b >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", b / 1024)
        else -> "${b.toLong()} B"
    }
}

private suspend fun fetchPublicIpThroughCurrentRoute(): String? = withContext(Dispatchers.IO) {
    val endpoints = listOf("https://api.ipify.org", "https://checkip.amazonaws.com")
    for (endpoint in endpoints) {
        val result = runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("Connection", "close")
            }
            try {
                if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.isNotBlank() }
                } else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        if (!result.isNullOrBlank()) return@withContext result
    }
    null
}
