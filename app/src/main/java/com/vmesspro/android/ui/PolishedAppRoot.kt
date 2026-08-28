package com.vmesspro.android.ui

import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.core.VpnTelemetry
import com.vmesspro.android.data.local.NodeEntity
import com.vmesspro.android.data.local.SubscriptionEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PDeepBlue = Color(0xFF03277D)
private val PElectricBlue = Color(0xFF0964E5)
private val PCyan = Color(0xFF11DDF8)
private val PMint = Color(0xFF38E7B0)
private val PLime = Color(0xFF8EF45B)
private val PGlass = Color(0x2AFFFFFF)
private val PGlassStrong = Color(0x44FFFFFF)
private val PGlassBorder = Color(0x5FFFFFFF)
private val PSoftBlue = Color(0xFFC6DFFF)

private enum class PolishedTab(val title: String, val glyph: String) {
    Home("خانه", "⌂"),
    Locations("مکان‌ها", "◎"),
    Stats("آمار", "◴"),
    Settings("تنظیمات", "⚙"),
}

private enum class AppCategory(val label: String) {
    USER("نصب‌شده"),
    SYSTEM("سیستمی"),
}

@Composable
fun PolishedAppRoot(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSplit by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showSubscription by rememberSaveable { mutableStateOf(false) }
    var showDns by rememberSaveable { mutableStateOf(false) }

    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val testingAll by viewModel.testingAllNodes.collectAsStateWithLifecycle()
    val progress by viewModel.testProgress.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (VpnService.prepare(context) == null) viewModel.connectSelected()
        else scope.launch { snackbar.showSnackbar("مجوز VPN صادر نشد") }
    }

    fun toggleVpn() {
        when (state) {
            is ConnectionState.Connected,
            ConnectionState.Preparing,
            ConnectionState.Connecting,
            ConnectionState.Verifying,
            ConnectionState.Reconnecting -> viewModel.disconnect()
            else -> {
                if (selectedNode == null) {
                    selectedTab = PolishedTab.Locations.ordinal
                    scope.launch { snackbar.showSnackbar("ابتدا یک سرور انتخاب کنید") }
                } else {
                    val permission = VpnService.prepare(context)
                    if (permission == null) viewModel.connectSelected() else permissionLauncher.launch(permission)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = showSplit) { showSplit = false }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF04237A),
                            Color(0xFF0648B8),
                            Color(0xFF0873E1),
                            Color(0xFF1A63D3),
                        )
                    )
                )
        ) {
            PolishedNetworkBackground()
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!showSplit) {
                        PolishedBottomBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
                    }
                },
            ) { padding ->
                if (showSplit) {
                    PolishedSplitScreen(
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
                    when (PolishedTab.entries[selectedTab]) {
                        PolishedTab.Home -> PolishedHomeScreen(
                            modifier = Modifier.padding(padding),
                            state = state,
                            selectedNode = selectedNode,
                            telemetry = telemetry,
                            nodeCount = nodes.size,
                            testingAll = testingAll,
                            onPower = ::toggleVpn,
                            onLocations = { selectedTab = PolishedTab.Locations.ordinal },
                            onTestAll = viewModel::testAllAndSelectBest,
                            onSplit = {
                                showSplit = true
                                viewModel.loadInstalledApps()
                            },
                            onImport = { showImport = true },
                        )
                        PolishedTab.Locations -> PolishedLocationsScreen(
                            modifier = Modifier.padding(padding),
                            nodes = nodes,
                            subscriptions = subscriptions,
                            selectedNodeId = selectedNode?.stableId,
                            testingAll = testingAll,
                            progress = progress,
                            onSelect = viewModel::selectNode,
                            onProbe = viewModel::probeNode,
                            onTestAll = viewModel::testAllAndSelectBest,
                            onImport = { showImport = true },
                            onAddSubscription = { showSubscription = true },
                            onRefreshSubscription = viewModel::refreshSubscription,
                            onDeleteSubscription = viewModel::deleteSubscription,
                        )
                        PolishedTab.Stats -> PolishedStatsScreen(
                            modifier = Modifier.padding(padding),
                            telemetry = telemetry,
                            state = state,
                            selectedNode = selectedNode,
                            nodeCount = nodes.size,
                            subscriptionCount = subscriptions.size,
                        )
                        PolishedTab.Settings -> PolishedSettingsScreen(
                            modifier = Modifier.padding(padding),
                            preferences = preferences,
                            selectedNode = selectedNode,
                            onAutoReconnect = viewModel::setAutoReconnect,
                            onSplit = {
                                showSplit = true
                                viewModel.loadInstalledApps()
                            },
                            onDns = { showDns = true },
                            onTestAll = viewModel::testAllAndSelectBest,
                            onLocations = { selectedTab = PolishedTab.Locations.ordinal },
                        )
                    }
                }
            }
        }

        if (showImport) {
            PolishedImportDialog(
                onDismiss = { showImport = false },
                onImport = {
                    viewModel.importText(it)
                    showImport = false
                    selectedTab = PolishedTab.Locations.ordinal
                },
            )
        }
        if (showSubscription) {
            PolishedSubscriptionDialog(
                onDismiss = { showSubscription = false },
                onAdd = { name, url ->
                    viewModel.addSubscription(name, url)
                    showSubscription = false
                },
            )
        }
        if (showDns) {
            PolishedDnsDialog(
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
private fun PolishedHomeScreen(
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
        publicIp = if (connected) polishedFetchPublicIp() else null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { LivePersianDateTimeBar() }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PolishedGlassBadge("♛")
                Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.End) {
                    Text("اتصال امن", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("سریع، واقعی و بدون اتصال نمایشی", color = PSoftBlue, style = MaterialTheme.typography.bodySmall)
                }
                PolishedShield()
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PolishedPowerButton(
                    connected = connected,
                    busy = busy,
                    enabled = !testingAll,
                    onClick = onPower,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (connected) PLime else if (busy) Color(0xFFFFD45A) else Color.White.copy(alpha = .65f))
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    polishedConnectionStatus(state),
                    color = if (connected) PLime else Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { PolishedServerCard(selectedNode, onLocations) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PolishedMetric("امنیت", selectedNode?.protocol?.uppercase() ?: "Xray", if (connected) "فعال" else "آماده", "◆", Color(0xFFC77AFF), Modifier.weight(1f))
                PolishedMetric("سرعت", polishedFormatMbps(telemetry.downloadBytesPerSecond), "Mbps", "◴", PCyan, Modifier.weight(1f))
                PolishedMetric("پینگ", selectedNode?.lastLatencyMs?.toString() ?: "—", "ms", "⌁", PMint, Modifier.weight(1f))
                PolishedMetric("آی‌پی", publicIp?.let(::polishedShortIp) ?: if (connected) "…" else "—", if (connected) "واقعی" else "قطع", "◎", Color(0xFF2BE3D1), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolishedQuickAction("هوشمند", "↔", onTestAll, Modifier.weight(1f))
                PolishedQuickAction("Xray", "⬡", onLocations, Modifier.weight(1f))
                PolishedQuickAction("Split", "↗", onSplit, Modifier.weight(1f))
                PolishedQuickAction(if (testingAll) "در حال تست" else "تست همه", "◴", onTestAll, Modifier.weight(1f))
                PolishedQuickAction("افزودن", "+", onImport, Modifier.weight(1f))
            }
        }
        item {
            PolishedGlassCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("$nodeCount پروفایل آماده", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("موتور Xray + tun2socks", color = PSoftBlue, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (connected) "دیتا در حال عبور است" else "آماده اتصال", color = if (connected) PLime else Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LivePersianDateTimeBar() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalDateTime.now()
            delay(1_000L)
        }
    }
    val jalali = gregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    val date = "${persianWeekday(now.dayOfWeek.value)} ${toPersianDigits(jalali.third.toString())} ${jalaliMonth(jalali.second)} ${toPersianDigits(jalali.first.toString())}"
    val time = toPersianDigits(now.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = PGlass, border = androidx.compose.foundation.BorderStroke(1.dp, PGlassBorder)) {
            Text(time, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Text(date, color = Color.White.copy(alpha = .92f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PolishedPowerButton(connected: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "powerPulse").animateFloat(
        initialValue = .94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1_450), RepeatMode.Reverse),
        label = "powerScale",
    )
    val brush = Brush.radialGradient(
        if (connected) listOf(Color(0xFF8FF573), Color(0xFF25D9B8), Color(0xFF0DB9AA))
        else listOf(Color(0xFF75F3B2), Color(0xFF26D2BC), Color(0xFF0FA9AB))
    )
    Box(
        Modifier
            .size(194.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .shadow(20.dp, CircleShape, ambientColor = PCyan.copy(alpha = .55f), spotColor = PCyan.copy(alpha = .7f))
            .border(2.dp, Color.White.copy(alpha = .9f), CircleShape)
            .padding(7.dp)
            .border(3.dp, PCyan.copy(alpha = .75f), CircleShape)
            .padding(10.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .17f), radius = size.minDimension * .43f, style = Stroke(1.5.dp.toPx()))
            drawArc(
                Color.White,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(size.width * .35f, size.height * .27f),
                size = androidx.compose.ui.geometry.Size(size.width * .30f, size.height * .30f),
                style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(Color.White, Offset(size.width * .50f, size.height * .23f), Offset(size.width * .50f, size.height * .42f), 7.dp.toPx(), StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            Text(
                when {
                    connected -> "قطع اتصال"
                    busy -> "در حال اتصال…"
                    else -> "اتصال"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            if (busy) {
                Spacer(Modifier.height(5.dp))
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
            }
        }
    }
}

@Composable
private fun PolishedServerCard(node: NodeEntity?, onClick: () -> Unit) {
    PolishedGlassCard(onClick = onClick, strong = true) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White) {
                Text(polishedCountryFlag(node?.countryCode), modifier = Modifier.padding(9.dp), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("موقعیت فعلی", color = PSoftBlue, style = MaterialTheme.typography.labelSmall)
                Text(node?.name ?: "انتخاب سرور", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    node?.let { "${it.protocol.uppercase()} • ${it.lastLatencyMs?.let { ms -> "$ms ms" } ?: "تست نشده"}" } ?: "برای اتصال یک پروفایل انتخاب کنید",
                    color = if (node?.lastProbeSucceeded == true) PMint else PSoftBlue,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Surface(shape = CircleShape, color = PGlassStrong) { Text("‹", modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun PolishedMetric(title: String, value: String, subtitle: String, glyph: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(94.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PGlass),
        border = androidx.compose.foundation.BorderStroke(1.dp, PGlassBorder),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(glyph, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                Text(title, color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun PolishedQuickAction(title: String, glyph: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = .94f),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(glyph, color = PElectricBlue, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(title, color = Color(0xFF26324B), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PolishedSplitScreen(
    modifier: Modifier,
    preferences: VpnPreferences,
    apps: List<InstalledAppInfo>,
    loading: Boolean,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(AppCategory.USER) }
    LaunchedEffect(Unit) { onLoad() }

    val userApps = remember(apps) { apps.filterNot { it.isSystem } }
    val systemApps = remember(apps) { apps.filter { it.isSystem } }
    val source = if (category == AppCategory.USER) userApps else systemApps
    val filtered = remember(source, query) {
        val q = query.trim()
        if (q.isBlank()) source
        else source.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    val selectedSet = if (preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED) preferences.includedPackages else preferences.excludedPackages
    val selectedVisible = filtered.count { it.packageName in selectedSet }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(onClick = onBack, shape = CircleShape, color = PGlassStrong, border = androidx.compose.foundation.BorderStroke(1.dp, PGlassBorder)) {
                    Text("›", modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("تونل تفکیکی", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("انتخاب دقیق برنامه‌های داخل یا خارج VPN", color = PSoftBlue, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            PolishedGlassCard(strong = true) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حالت عبور ترافیک", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SplitModeChoice(
                            title = "فقط انتخاب‌شده‌ها",
                            subtitle = "فقط برنامه‌های انتخابی از VPN",
                            selected = preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED,
                            modifier = Modifier.weight(1f),
                            onClick = { onModeChange(SplitTunnelMode.ONLY_SELECTED) },
                        )
                        SplitModeChoice(
                            title = "به‌جز انتخاب‌شده‌ها",
                            subtitle = "انتخابی‌ها بدون VPN",
                            selected = preferences.splitTunnelMode == SplitTunnelMode.EXCLUDE_SELECTED,
                            modifier = Modifier.weight(1f),
                            onClick = { onModeChange(SplitTunnelMode.EXCLUDE_SELECTED) },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                label = { Text("جستجوی برنامه") },
                placeholder = { Text("نام برنامه یا نام پکیج") },
                leadingIcon = { Text("⌕", color = PCyan, style = MaterialTheme.typography.titleLarge) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppCategoryTab(
                    label = "نصب‌شده",
                    count = userApps.size,
                    selected = category == AppCategory.USER,
                    modifier = Modifier.weight(1f),
                    onClick = { category = AppCategory.USER },
                )
                AppCategoryTab(
                    label = "سیستمی",
                    count = systemApps.size,
                    selected = category == AppCategory.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { category = AppCategory.SYSTEM },
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${toPersianDigits(filtered.size.toString())} برنامه", color = PSoftBlue, style = MaterialTheme.typography.labelMedium)
                Text("انتخاب‌شده: ${toPersianDigits(selectedVisible.toString())}", color = PMint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                PolishedGlassCard {
                    Text("برنامه‌ای با این جستجو پیدا نشد.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = Color.White, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selectedSet
                SplitAppRow(app = app, checked = checked, onToggle = { onToggle(app.packageName, it) })
            }
        }
    }
}

@Composable
private fun SplitModeChoice(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) PMint.copy(alpha = .22f) else Color.White.copy(alpha = .08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) PMint else PGlassBorder),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(subtitle, color = if (selected) PMint else PSoftBlue, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
private fun AppCategoryTab(label: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) Color.White.copy(alpha = .95f) else PGlass,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.White else PGlassBorder),
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (selected) PElectricBlue else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(6.dp))
            Surface(shape = CircleShape, color = if (selected) PElectricBlue.copy(alpha = .12f) else Color.White.copy(alpha = .10f)) {
                Text(toPersianDigits(count.toString()), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = if (selected) PElectricBlue else PSoftBlue, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SplitAppRow(app: InstalledAppInfo, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    PolishedGlassCard(strong = checked, onClick = { onToggle(!checked) }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(13.dp), color = Color.White.copy(alpha = .94f), modifier = Modifier.size(43.dp)) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(5.dp))
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (app.isSystem) "⚙" else "A", color = PElectricBlue, fontWeight = FontWeight.Black)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = PSoftBlue, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (app.isSystem) "برنامه سیستمی" else "برنامه نصب‌شده", color = if (app.isSystem) Color(0xFFFFD45A) else PMint, style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PolishedLocationsScreen(
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
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { PolishedHeader("مکان‌ها و سرورها", "تست واقعی Xray و انتخاب بهترین") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = onTestAll, enabled = !testingAll, modifier = Modifier.weight(1f)) { Text(if (testingAll) "${progress.completed}/${progress.total}" else "تست همه") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("افزودن", color = Color.White) }
            }
        }
        if (subscriptions.isNotEmpty()) {
            item { Text("اشتراک‌ها", color = Color.White, fontWeight = FontWeight.Black) }
            items(subscriptions, key = { it.id }) { sub ->
                PolishedGlassCard {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(sub.lastRefreshError ?: "آماده", color = if (sub.lastRefreshError == null) PMint else Color(0xFFFFB6B6), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        TextButton(onClick = { onRefreshSubscription(sub.id) }) { Text("↻", color = Color.White) }
                        TextButton(onClick = { onDeleteSubscription(sub.id) }) { Text("حذف", color = Color(0xFFFFC1C1)) }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onAddSubscription, modifier = Modifier.fillMaxWidth()) { Text("+ افزودن Subscription", color = Color.White) } }
        item { Text("پروفایل‌ها", color = Color.White, fontWeight = FontWeight.Black) }
        if (nodes.isEmpty()) {
            item { PolishedGlassCard { Text("هنوز کانفیگی اضافه نشده است.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = Color.White, textAlign = TextAlign.Center) } }
        } else {
            items(nodes, key = { it.stableId }) { node ->
                PolishedGlassCard(strong = node.stableId == selectedNodeId, onClick = { onSelect(node.stableId) }) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(polishedCountryFlag(node.countryCode), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(node.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${node.protocol.uppercase()} • ${node.lastLatencyMs?.let { "$it ms" } ?: "تست نشده"}", color = if (node.lastProbeSucceeded == true) PMint else PSoftBlue, style = MaterialTheme.typography.labelSmall)
                        }
                        if (node.stableId == selectedNodeId) Text("✓", color = PLime, fontWeight = FontWeight.Black)
                        TextButton(onClick = { onProbe(node.stableId) }) { Text("تست", color = PCyan) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolishedStatsScreen(modifier: Modifier, telemetry: VpnTelemetry, state: ConnectionState, selectedNode: NodeEntity?, nodeCount: Int, subscriptionCount: Int) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { PolishedHeader("آمار اتصال", "اطلاعات زنده از Core") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PolishedBigStat("دانلود", polishedFormatRate(telemetry.downloadBytesPerSecond), "↓", Modifier.weight(1f))
                PolishedBigStat("آپلود", polishedFormatRate(telemetry.uploadBytesPerSecond), "↑", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PolishedBigStat("دانلود کل", polishedFormatBytes(telemetry.downloadedBytesTotal), "⇩", Modifier.weight(1f))
                PolishedBigStat("آپلود کل", polishedFormatBytes(telemetry.uploadedBytesTotal), "⇧", Modifier.weight(1f))
            }
        }
        item {
            PolishedSettingsGroup("خلاصه") {
                PolishedInfoRow("وضعیت", polishedConnectionStatus(state), "●")
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedInfoRow("سرور", selectedNode?.name ?: "انتخاب نشده", "◎")
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedInfoRow("پینگ", selectedNode?.lastLatencyMs?.let { "$it ms" } ?: "—", "◴")
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedInfoRow("پروفایل / اشتراک", "$nodeCount / $subscriptionCount", "≡")
            }
        }
    }
}

@Composable
private fun PolishedSettingsScreen(
    modifier: Modifier,
    preferences: VpnPreferences,
    selectedNode: NodeEntity?,
    onAutoReconnect: (Boolean) -> Unit,
    onSplit: () -> Unit,
    onDns: () -> Unit,
    onTestAll: () -> Unit,
    onLocations: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PolishedHeader("تنظیمات", "مدیریت برنامه و اتصال") }
        item {
            PolishedSettingsGroup("اتصال و شبکه") {
                PolishedSwitchRow("اتصال خودکار", "تلاش دوباره در صورت قطع", "ϟ", preferences.autoReconnect, onAutoReconnect)
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedActionRow("پروتکل اتصال", selectedNode?.protocol?.uppercase() ?: "Xray", "⬡", onLocations)
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedActionRow("تونل تفکیکی", polishedSplitModeLabel(preferences.splitTunnelMode), "↗", onSplit)
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedActionRow("DNS سفارشی", preferences.customDns ?: "خودکار", "◎", onDns)
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedActionRow("تست سرورها", "Xray HTTP", "◴", onTestAll)
            }
        }
        item {
            PolishedSettingsGroup("هسته اتصال") {
                PolishedInfoRow("موتور VPN", "Amnezia libXray", "◇")
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedInfoRow("اعتبار اتصال", "HTTPS واقعی", "✓")
                HorizontalDivider(color = Color.White.copy(alpha = .13f))
                PolishedInfoRow("حفاظت Loop", "VpnService.protect(fd)", "◈")
            }
        }
    }
}

@Composable
private fun PolishedBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = .96f),
        shadowElevation = 9.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceAround) {
            PolishedTab.entries.forEachIndexed { index, tab ->
                val selected = selectedTab == index
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) PElectricBlue.copy(alpha = .12f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tab.glyph, color = if (selected) PElectricBlue else Color(0xFF50596D), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                    Text(tab.title, color = if (selected) PElectricBlue else Color(0xFF50596D), style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun PolishedHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(subtitle, color = PSoftBlue, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PolishedBigStat(title: String, value: String, glyph: String, modifier: Modifier = Modifier) {
    PolishedGlassCard(modifier = modifier) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(glyph, color = PCyan, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(title, color = PSoftBlue, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PolishedSettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        PolishedGlassCard(strong = true) { Column { content() } }
    }
}

@Composable
private fun PolishedSwitchRow(title: String, subtitle: String, glyph: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(glyph, color = PCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PSoftBlue, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PolishedActionRow(title: String, value: String, glyph: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(glyph, color = PCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = PMint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("‹", color = Color.White)
    }
}

@Composable
private fun PolishedInfoRow(title: String, value: String, glyph: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(glyph, color = PCyan, fontWeight = FontWeight.Black)
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = PSoftBlue, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PolishedGlassCard(modifier: Modifier = Modifier, strong: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = if (strong) PGlassStrong else PGlass),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (strong) Color.White.copy(alpha = .5f) else PGlassBorder),
    ) { content() }
}

@Composable
private fun PolishedGlassBadge(text: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = PGlass, border = androidx.compose.foundation.BorderStroke(1.dp, PGlassBorder)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Color(0xFFFFD44D), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PolishedShield() {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(PCyan, PElectricBlue))),
        contentAlignment = Alignment.Center,
    ) {
        Text("◈", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PolishedNetworkBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in 0..7) {
            val y = h * (.26f + i * .075f)
            drawArc(
                color = PCyan.copy(alpha = .045f + i * .004f),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(-w * .45f, y - h * .18f),
                size = androidx.compose.ui.geometry.Size(w * 1.9f, h * .40f),
                style = Stroke(1.dp.toPx()),
            )
        }
        for (i in 0..17) {
            val x = ((i * 73) % 100) / 100f * w
            val y = ((i * 47) % 100) / 100f * h
            drawCircle(Color.White.copy(alpha = .09f), radius = (1 + i % 3).dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun PolishedImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانفیگ") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 6, placeholder = { Text("VMess / VLESS / Reality / Trojan یا Subscription") }) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onImport(text) }) { Text("وارد کردن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PolishedSubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
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
private fun PolishedDnsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DNS سفارشی") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("مثلاً 1.1.1.1") }) },
        confirmButton = { Button(onClick = { onSave(value.trim()) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun polishedConnectionStatus(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "آماده برای اتصال"
    ConnectionState.Preparing -> "در حال آماده‌سازی VPN"
    ConnectionState.Connecting -> "در حال راه‌اندازی Xray"
    ConnectionState.Verifying -> "در حال تأیید عبور واقعی دیتا"
    is ConnectionState.Connected -> "متصل و تأییدشده"
    ConnectionState.Reconnecting -> "در حال اتصال مجدد"
    is ConnectionState.Error -> "خطا: ${state.reason}"
}

private fun polishedSplitModeLabel(mode: SplitTunnelMode): String = when (mode) {
    SplitTunnelMode.ONLY_SELECTED -> "فقط انتخاب‌شده‌ها"
    SplitTunnelMode.EXCLUDE_SELECTED -> "به‌جز انتخاب‌شده‌ها"
}

private fun polishedCountryFlag(code: String?): String {
    val c = code?.trim()?.uppercase().orEmpty()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    return buildString { c.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
}

private fun polishedShortIp(ip: String): String = if (ip.length <= 13) ip else ip.take(10) + "…"

private fun polishedFormatMbps(bytesPerSecond: Long): String = String.format(Locale.US, "%.1f", bytesPerSecond.coerceAtLeast(0L) * 8.0 / 1_000_000.0)

private fun polishedFormatRate(bytesPerSecond: Long): String {
    val b = bytesPerSecond.coerceAtLeast(0L).toDouble()
    return when {
        b >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", b / (1024 * 1024))
        b >= 1024 -> String.format(Locale.US, "%.1f KB/s", b / 1024)
        else -> "${b.toLong()} B/s"
    }
}

private fun polishedFormatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L).toDouble()
    return when {
        b >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / (1024 * 1024 * 1024))
        b >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / (1024 * 1024))
        b >= 1024 -> String.format(Locale.US, "%.1f KB", b / 1024)
        else -> "${b.toLong()} B"
    }
}

private suspend fun polishedFetchPublicIp(): String? = withContext(Dispatchers.IO) {
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
                if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.isNotBlank() } else null
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        if (!result.isNullOrBlank()) return@withContext result
    }
    null
}

private fun toPersianDigits(value: String): String {
    val fa = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
    return buildString(value.length) {
        value.forEach { ch -> append(if (ch in '0'..'9') fa[ch - '0'] else ch) }
    }
}

private fun persianWeekday(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "دوشنبه"
    2 -> "سه‌شنبه"
    3 -> "چهارشنبه"
    4 -> "پنجشنبه"
    5 -> "جمعه"
    6 -> "شنبه"
    else -> "یکشنبه"
}

private fun jalaliMonth(month: Int): String = when (month) {
    1 -> "فروردین"
    2 -> "اردیبهشت"
    3 -> "خرداد"
    4 -> "تیر"
    5 -> "مرداد"
    6 -> "شهریور"
    7 -> "مهر"
    8 -> "آبان"
    9 -> "آذر"
    10 -> "دی"
    11 -> "بهمن"
    else -> "اسفند"
}

private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var gy2 = gy - 1600
    val gm2 = gm - 1
    val gd2 = gd - 1
    var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
    for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
    val leap = (gy2 % 4 == 0 && gy2 % 100 != 0) || gy2 % 400 == 0
    if (gm2 > 1 && leap) gDayNo++
    gDayNo += gd2

    var jDayNo = gDayNo - 79
    val jNp = jDayNo / 12053
    jDayNo %= 12053
    var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
    jDayNo %= 1461
    if (jDayNo >= 366) {
        jy += (jDayNo - 1) / 365
        jDayNo = (jDayNo - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (jDayNo < 186) {
        jm = 1 + jDayNo / 31
        jd = 1 + jDayNo % 31
    } else {
        jm = 7 + (jDayNo - 186) / 30
        jd = 1 + (jDayNo - 186) % 30
    }
    return Triple(jy, jm, jd)
}
