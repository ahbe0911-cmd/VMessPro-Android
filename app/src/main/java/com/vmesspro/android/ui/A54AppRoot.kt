package com.vmesspro.android.ui

import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.util.Locale
import kotlinx.coroutines.launch

private val A54Deep = PremiumVpnColors.BackgroundDeep
private val A54Blue = PremiumVpnColors.Blue
private val A54BrightBlue = PremiumVpnColors.Blue
private val A54Cyan = PremiumVpnColors.Cyan
private val A54Mint = PremiumVpnColors.Emerald
private val A54Lime = PremiumVpnColors.Lime
private val A54Red = PremiumVpnColors.Red
private val A54Amber = PremiumVpnColors.Orange
private val A54Glass = PremiumVpnColors.SurfaceSoft
private val A54GlassStrong = PremiumVpnColors.SurfaceStrong
private val A54Border = PremiumVpnColors.Border
private val A54SoftText = PremiumVpnColors.TextSecondary

private enum class A54Tab(val title: String, val glyph: String) {
    Home("خانه", "⌂"),
    Locations("سرورها", "●"),
    Stats("آمار", "▥"),
    Settings("تنظیمات", "⚙"),
}

private enum class A54AppGroup(val title: String) {
    USER("نصب‌شده"),
    SYSTEM("سیستمی"),
}

@Composable
fun A54AppRoot(viewModel: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var splitOpen by rememberSaveable { mutableStateOf(false) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    var subscriptionOpen by rememberSaveable { mutableStateOf(false) }

    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val testing by viewModel.testingAllNodes.collectAsStateWithLifecycle()
    val progress by viewModel.testProgress.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (VpnService.prepare(context) == null) viewModel.connectSelected()
        else scope.launch { snackbar.showSnackbar("مجوز VPN صادر نشد") }
    }

    fun toggleConnection() {
        when (state) {
            is ConnectionState.Connected,
            ConnectionState.Preparing,
            ConnectionState.Connecting,
            ConnectionState.Verifying,
            ConnectionState.Reconnecting -> viewModel.disconnect()

            else -> {
                if (selectedNode == null) {
                    tab = A54Tab.Locations.ordinal
                    scope.launch { snackbar.showSnackbar("ابتدا یک سرور انتخاب کنید") }
                } else {
                    val permission = VpnService.prepare(context)
                    if (permission == null) viewModel.connectSelected() else vpnPermission.launch(permission)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = splitOpen) { splitOpen = false }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PremiumVpnColors.BackgroundDeep,
                            PremiumVpnColors.BackgroundMid,
                            PremiumVpnColors.BackgroundSoft,
                        )
                    )
                )
        ) {
            A54Background()
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!splitOpen) {
                        A54BottomBar(selected = tab, onSelect = { tab = it })
                    }
                },
            ) { padding ->
                if (splitOpen) {
                    A54SplitScreen(
                        modifier = Modifier.padding(padding),
                        preferences = prefs,
                        apps = apps,
                        loading = appsLoading,
                        onBack = { splitOpen = false },
                        onLoad = viewModel::loadInstalledApps,
                        onModeChange = viewModel::setSplitMode,
                        onToggle = viewModel::toggleSplitPackage,
                    )
                } else {
                    when (A54Tab.entries[tab]) {
                        A54Tab.Home -> PremiumHomeScreen(
                            modifier = Modifier.padding(padding),
                            state = state,
                            node = selectedNode,
                            telemetry = telemetry,
                            testing = testing,
                            onPower = ::toggleConnection,
                            onLocations = { tab = A54Tab.Locations.ordinal },
                            onSmart = viewModel::testAllAndSelectBest,
                            onSplit = {
                                splitOpen = true
                                viewModel.loadInstalledApps()
                            },
                            onImport = { importOpen = true },
                            onSettings = { tab = A54Tab.Settings.ordinal },
                        )

                        A54Tab.Locations -> A54Locations(
                            modifier = Modifier.padding(padding),
                            nodes = nodes,
                            subscriptions = subscriptions,
                            selectedId = selectedNode?.stableId,
                            testing = testing,
                            progress = progress,
                            onSelect = viewModel::selectNode,
                            onProbe = viewModel::probeNode,
                            onTestAll = viewModel::testAllAndSelectBest,
                            onImport = { importOpen = true },
                            onSubscription = { subscriptionOpen = true },
                            onRefreshSubscription = viewModel::refreshSubscription,
                            onDeleteSubscription = viewModel::deleteSubscription,
                        )

                        A54Tab.Stats -> A54Stats(
                            modifier = Modifier.padding(padding),
                            state = state,
                            node = selectedNode,
                            telemetry = telemetry,
                            nodeCount = nodes.size,
                            subscriptionCount = subscriptions.size,
                        )

                        A54Tab.Settings -> A54Settings(
                            modifier = Modifier.padding(padding),
                            prefs = prefs,
                            node = selectedNode,
                            onAutoReconnect = viewModel::setAutoReconnect,
                            onSplit = {
                                splitOpen = true
                                viewModel.loadInstalledApps()
                            },
                            onLocations = { tab = A54Tab.Locations.ordinal },
                            onTestAll = viewModel::testAllAndSelectBest,
                        )
                    }
                }
            }
        }

        if (importOpen) {
            A54ImportDialog(
                onDismiss = { importOpen = false },
                onImport = {
                    viewModel.importText(it)
                    importOpen = false
                    tab = A54Tab.Locations.ordinal
                },
            )
        }

        if (subscriptionOpen) {
            A54SubscriptionDialog(
                onDismiss = { subscriptionOpen = false },
                onAdd = { name, url ->
                    viewModel.addSubscription(name, url)
                    subscriptionOpen = false
                },
            )
        }
    }
}

@Composable
private fun A54Locations(
    modifier: Modifier,
    nodes: List<NodeEntity>,
    subscriptions: List<SubscriptionEntity>,
    selectedId: String?,
    testing: Boolean,
    progress: NodeTestProgress,
    onSelect: (String) -> Unit,
    onProbe: (String) -> Unit,
    onTestAll: () -> Unit,
    onImport: () -> Unit,
    onSubscription: () -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { A54Header("مکان‌ها و سرورها", "تست واقعی Xray و انتخاب بهترین") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTestAll, enabled = !testing, modifier = Modifier.weight(1f)) {
                    Text(if (testing) "${progress.completed}/${progress.total}" else "تست همه")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("افزودن کانفیگ", color = Color.White) }
            }
        }
        if (subscriptions.isNotEmpty()) {
            item { Text("اشتراک‌ها", color = Color.White, fontWeight = FontWeight.Black) }
            items(subscriptions, key = { it.id }) { sub ->
                A54GlassCard {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(sub.lastRefreshError ?: "آماده", color = if (sub.lastRefreshError == null) A54Mint else A54Red, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        TextButton(onClick = { onRefreshSubscription(sub.id) }) { Text("↻", color = Color.White) }
                        TextButton(onClick = { onDeleteSubscription(sub.id) }) { Text("حذف", color = Color(0xFFFFC1C1)) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onSubscription, modifier = Modifier.fillMaxWidth()) {
                Text("+ افزودن Subscription", color = Color.White)
            }
        }
        item { Text("پروفایل‌ها", color = Color.White, fontWeight = FontWeight.Black) }
        if (nodes.isEmpty()) {
            item { A54GlassCard { Text("هنوز کانفیگی اضافه نشده است.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = Color.White, textAlign = TextAlign.Center) } }
        } else {
            items(nodes, key = { it.stableId }) { node ->
                A54GlassCard(strong = node.stableId == selectedId, onClick = { onSelect(node.stableId) }) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(a54CountryFlag(node.countryCode), style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(node.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${node.protocol.uppercase()} • ${node.lastLatencyMs?.let { "$it ms" } ?: "تست نشده"}",
                                color = if (node.lastProbeSucceeded == true) A54Mint else A54SoftText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (node.stableId == selectedId) Text("✓", color = A54Lime, fontWeight = FontWeight.Black)
                        TextButton(onClick = { onProbe(node.stableId) }) { Text("تست", color = A54Cyan) }
                    }
                }
            }
        }
    }
}

@Composable
private fun A54SplitScreen(
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
    var group by rememberSaveable { mutableStateOf(A54AppGroup.USER) }
    LaunchedEffect(Unit) { onLoad() }

    val userApps = remember(apps) { apps.filterNot { it.isSystem } }
    val systemApps = remember(apps) { apps.filter { it.isSystem } }
    val source = if (group == A54AppGroup.USER) userApps else systemApps
    val filtered = remember(source, query) {
        val q = query.trim()
        if (q.isBlank()) source else source.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }
    val selected = if (preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED) {
        preferences.includedPackages
    } else {
        preferences.excludedPackages
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(onClick = onBack, shape = CircleShape, color = A54GlassStrong, border = androidx.compose.foundation.BorderStroke(1.dp, A54Border)) {
                    Text("›", modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.Black)
                }
                A54Header("تونل تفکیکی", "انتخاب دقیق برنامه‌های داخل یا خارج VPN")
            }
        }
        item {
            A54GlassCard(strong = true) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حالت تونل", color = Color.White, fontWeight = FontWeight.Black)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        A54ModeChip(
                            "فقط انتخاب‌شده‌ها",
                            preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED,
                            Modifier.weight(1f),
                        ) { onModeChange(SplitTunnelMode.ONLY_SELECTED) }
                        A54ModeChip(
                            "به‌جز انتخاب‌شده‌ها",
                            preferences.splitTunnelMode == SplitTunnelMode.EXCLUDE_SELECTED,
                            Modifier.weight(1f),
                        ) { onModeChange(SplitTunnelMode.EXCLUDE_SELECTED) }
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
                placeholder = { Text("نام برنامه یا package") },
                leadingIcon = { Text("⌕", color = A54Cyan, style = MaterialTheme.typography.titleLarge) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                A54GroupTab("نصب‌شده", userApps.size, group == A54AppGroup.USER, Modifier.weight(1f)) { group = A54AppGroup.USER }
                A54GroupTab("سیستمی", systemApps.size, group == A54AppGroup.SYSTEM, Modifier.weight(1f)) { group = A54AppGroup.SYSTEM }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${a54Digits(filtered.size.toString())} برنامه", color = A54SoftText)
                Text("انتخاب‌شده: ${a54Digits(filtered.count { it.packageName in selected }.toString())}", color = A54Mint, fontWeight = FontWeight.Bold)
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
        } else if (filtered.isEmpty()) {
            item { A54GlassCard { Text("برنامه‌ای پیدا نشد.", modifier = Modifier.fillMaxWidth().padding(18.dp), color = Color.White, textAlign = TextAlign.Center) } }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                A54AppRow(app, checked) { onToggle(app.packageName, it) }
            }
        }
    }
}

@Composable
private fun A54ModeChip(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) A54Mint.copy(alpha = .22f) else Color.White.copy(alpha = .08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) A54Mint else A54Border),
    ) {
        Text(title, modifier = Modifier.padding(10.dp), color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun A54GroupTab(title: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) Color.White.copy(alpha = .95f) else A54Glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.White else A54Border),
    ) {
        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (selected) A54Blue else Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(a54Digits(count.toString()), color = if (selected) A54Blue else A54SoftText, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun A54AppRow(app: InstalledAppInfo, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    A54GlassCard(strong = checked, onClick = { onToggle(!checked) }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .95f), modifier = Modifier.size(42.dp)) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(5.dp))
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (app.isSystem) "⚙" else "A", color = A54Blue, fontWeight = FontWeight.Black) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = A54SoftText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (app.isSystem) "برنامه سیستمی" else "برنامه نصب‌شده", color = if (app.isSystem) A54Amber else A54Mint, style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun A54Stats(
    modifier: Modifier,
    state: ConnectionState,
    node: NodeEntity?,
    telemetry: VpnTelemetry,
    nodeCount: Int,
    subscriptionCount: Int,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { A54Header("آمار اتصال", "اطلاعات زنده از Core برنامه") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                A54BigStat("دانلود", a54Rate(telemetry.downloadBytesPerSecond), "↓", Modifier.weight(1f))
                A54BigStat("آپلود", a54Rate(telemetry.uploadBytesPerSecond), "↑", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                A54BigStat("دانلود کل", a54Bytes(telemetry.downloadedBytesTotal), "⇩", Modifier.weight(1f))
                A54BigStat("آپلود کل", a54Bytes(telemetry.uploadedBytesTotal), "⇧", Modifier.weight(1f))
            }
        }
        item {
            A54GlassCard {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    A54InfoRow("وضعیت", a54Status(state))
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54InfoRow("سرور", node?.name ?: "انتخاب نشده")
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54InfoRow("پینگ", node?.lastLatencyMs?.let { "$it ms" } ?: "—")
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54InfoRow("پروفایل / اشتراک", "$nodeCount / $subscriptionCount")
                }
            }
        }
    }
}

@Composable
private fun A54Settings(
    modifier: Modifier,
    prefs: VpnPreferences,
    node: NodeEntity?,
    onAutoReconnect: (Boolean) -> Unit,
    onSplit: () -> Unit,
    onLocations: () -> Unit,
    onTestAll: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { A54Header("تنظیمات", "مدیریت برنامه و اتصال") }
        item {
            A54GlassCard(strong = true) {
                Column {
                    A54SwitchRow("اتصال خودکار", "تلاش مجدد در صورت قطع", prefs.autoReconnect, onAutoReconnect)
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54ActionRow("پروتکل اتصال", node?.protocol?.uppercase() ?: "Xray", onLocations)
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54ActionRow("تونل تفکیکی", if (prefs.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED) "فقط انتخاب‌شده‌ها" else "به‌جز انتخاب‌شده‌ها", onSplit)
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54ActionRow("تست همه سرورها", "Xray HTTP واقعی", onTestAll)
                }
            }
        }
        item {
            A54GlassCard {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    A54InfoRow("موتور VPN", "Amnezia libXray")
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54InfoRow("اعتبار اتصال", "HTTPS واقعی قبل از Connected")
                    HorizontalDivider(color = Color.White.copy(alpha = .13f))
                    A54InfoRow("حفاظت Loop", "VpnService.protect(fd)")
                }
            }
        }
    }
}

@Composable
private fun A54BigStat(title: String, value: String, glyph: String, modifier: Modifier) {
    A54GlassCard(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(glyph, color = A54Cyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(title, color = A54SoftText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun A54InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Text(value, color = A54SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun A54SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black)
            Text(subtitle, color = A54SoftText, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun A54ActionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black)
            Text(value, color = A54Mint, style = MaterialTheme.typography.labelSmall)
        }
        Text("‹", color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun A54Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(subtitle, color = A54SoftText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun A54BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val accents = listOf(
        PremiumVpnColors.Purple,
        PremiumVpnColors.Cyan,
        PremiumVpnColors.Emerald,
        PremiumVpnColors.Pink,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(23.dp),
            color = PremiumVpnColors.NavSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, PremiumVpnColors.Border),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                A54Tab.entries.forEachIndexed { index, item ->
                    val active = selected == index
                    val accent = accents[index]
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onClick = { onSelect(index) },
                        shape = RoundedCornerShape(17.dp),
                        color = if (active) accent.copy(alpha = .16f) else Color.Transparent,
                        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .30f)) else null,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                item.glyph,
                                color = if (active) accent else PremiumVpnColors.TextMuted,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                item.title,
                                color = if (active) PremiumVpnColors.TextPrimary else PremiumVpnColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun A54GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = PremiumVpnShapes.Medium,
            color = if (strong) A54GlassStrong else A54Glass,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (strong) A54Cyan.copy(alpha = .48f) else A54Border),
            shadowElevation = if (strong) 3.dp else 1.dp,
        ) { content() }
    } else {
        Surface(
            modifier = cardModifier,
            shape = PremiumVpnShapes.Medium,
            color = if (strong) A54GlassStrong else A54Glass,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (strong) A54Cyan.copy(alpha = .48f) else A54Border),
            shadowElevation = if (strong) 3.dp else 1.dp,
        ) { content() }
    }
}

@Composable
private fun A54Background() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(PremiumVpnColors.Purple.copy(alpha = .14f), radius = w * .48f, center = Offset(w * .10f, h * .20f))
        drawCircle(PremiumVpnColors.Cyan.copy(alpha = .11f), radius = w * .52f, center = Offset(w * .92f, h * .42f))
        drawCircle(PremiumVpnColors.Emerald.copy(alpha = .08f), radius = w * .38f, center = Offset(w * .18f, h * .80f))
        drawCircle(PremiumVpnColors.Pink.copy(alpha = .06f), radius = w * .34f, center = Offset(w * .92f, h * .78f))
        for (i in 0..7) {
            val y = h * (.18f + i * .075f)
            drawArc(
                PremiumVpnColors.Cyan.copy(alpha = .018f + i * .003f),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(-w * .5f, y - h * .18f),
                size = androidx.compose.ui.geometry.Size(w * 2f, h * .40f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun A54ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن کانفیگ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                placeholder = { Text("VMess / VLESS / Reality / Trojan یا Subscription") },
            )
        },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onImport(text) }) { Text("وارد کردن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun A54SubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
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

private fun a54Status(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "قطع • آماده برای اتصال"
    ConnectionState.Preparing -> "در حال آماده‌سازی"
    ConnectionState.Connecting -> "در حال اتصال Xray"
    ConnectionState.Verifying -> "در حال تأیید عبور دیتا"
    is ConnectionState.Connected -> "متصل و تأییدشده"
    ConnectionState.Reconnecting -> "در حال اتصال مجدد"
    is ConnectionState.Error -> "خطا • اتصال برقرار نشد"
}

private fun a54CountryFlag(code: String?): String {
    val c = code?.trim()?.uppercase().orEmpty()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    return buildString { c.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
}

private fun a54Rate(bytesPerSecond: Long): String {
    val value = bytesPerSecond.coerceAtLeast(0L).toDouble()
    return when {
        value >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", value / (1024 * 1024))
        value >= 1024 -> String.format(Locale.US, "%.1f KB/s", value / 1024)
        else -> "${value.toLong()} B/s"
    }
}

private fun a54Bytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    return when {
        value >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", value / (1024 * 1024 * 1024))
        value >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", value / (1024 * 1024))
        value >= 1024 -> String.format(Locale.US, "%.1f KB", value / 1024)
        else -> "${value.toLong()} B"
    }
}

private fun a54Digits(value: String): String = buildString(value.length) {
    value.forEach { ch -> append(if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch) }
}
