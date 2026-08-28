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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
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

private val A54Deep = Color(0xFF041A62)
private val A54Blue = Color(0xFF0751C9)
private val A54BrightBlue = Color(0xFF0879EE)
private val A54Cyan = Color(0xFF10E3FF)
private val A54Mint = Color(0xFF49F0AE)
private val A54Lime = Color(0xFF78F456)
private val A54Red = Color(0xFFFF4F65)
private val A54Amber = Color(0xFFFFC74A)
private val A54Glass = Color(0x2AFFFFFF)
private val A54GlassStrong = Color(0x44FFFFFF)
private val A54Border = Color(0x66FFFFFF)
private val A54SoftText = Color(0xFFC7DDFF)

private enum class A54Tab(val title: String, val glyph: String) {
    Home("خانه", "⌂"),
    Locations("مکان‌ها", "●"),
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
                        listOf(A54Deep, Color(0xFF043A9D), A54Blue, Color(0xFF075CD6))
                    )
                )
        ) {
            A54Background()
            Scaffold(
                containerColor = Color.Transparent,
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
                        A54Tab.Home -> A54Home(
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
private fun A54Home(
    modifier: Modifier,
    state: ConnectionState,
    node: NodeEntity?,
    telemetry: VpnTelemetry,
    testing: Boolean,
    onPower: () -> Unit,
    onLocations: () -> Unit,
    onSmart: () -> Unit,
    onSplit: () -> Unit,
    onImport: () -> Unit,
) {
    val connected = state is ConnectionState.Connected
    val busy = state == ConnectionState.Preparing || state == ConnectionState.Connecting ||
        state == ConnectionState.Verifying || state == ConnectionState.Reconnecting
    val failed = state == ConnectionState.Disconnected || state is ConnectionState.Error
    var ip by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connected) {
        ip = if (connected) a54FetchPublicIp() else null
    }

    BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding()) {
        val compact = maxWidth <= 380.dp
        val sidePadding = if (compact) 12.dp else 16.dp
        val gap = if (compact) 8.dp else 10.dp
        val powerSize = (maxWidth * 0.56f).coerceIn(192.dp, 224.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePadding, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            item { A54DateTime() }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    A54LogoTile()
                    Column(
                        Modifier.weight(1f).padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "اتصال امن",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "سریع، واقعی و بدون اتصال نمایشی",
                            color = A54SoftText,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    A54CrownTile()
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    A54PowerButton(
                        size = powerSize,
                        connected = connected,
                        busy = busy,
                        failed = failed,
                        enabled = !testing,
                        onClick = onPower,
                    )
                }
            }
            item {
                val statusColor = when {
                    connected -> A54Lime
                    busy -> A54Amber
                    else -> A54Red
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.size(7.dp))
                    Text(
                        a54Status(state),
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            item { A54ServerCard(node, onLocations) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    A54Metric(
                        title = "آی‌پی",
                        value = ip?.let(::a54ShortIp) ?: if (connected) "…" else "—",
                        subtitle = if (connected) "واقعی" else "قطع",
                        glyph = "●",
                        accent = if (connected) A54Mint else A54Red,
                        modifier = Modifier.weight(1f),
                    )
                    A54Metric(
                        title = "پینگ",
                        value = node?.lastLatencyMs?.toString() ?: "—",
                        subtitle = "ms",
                        glyph = "⌁",
                        accent = A54Mint,
                        modifier = Modifier.weight(1f),
                    )
                    A54Metric(
                        title = "سرعت",
                        value = a54Mbps(telemetry.downloadBytesPerSecond),
                        subtitle = "Mbps",
                        glyph = "◴",
                        accent = A54Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    A54Metric(
                        title = "امنیت",
                        value = node?.protocol?.uppercase() ?: "Xray",
                        subtitle = if (connected) "فعال" else "آماده",
                        glyph = "◆",
                        accent = Color(0xFFC77BFF),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    A54Action("افزودن", "+", onImport, Modifier.weight(1f))
                    A54Action(if (testing) "در حال تست" else "تست همه", "◉", onSmart, Modifier.weight(1f))
                    A54Action("Split", "↗", onSplit, Modifier.weight(1f))
                    A54Action("Xray", "⬡", onLocations, Modifier.weight(1f))
                    A54Action("هوشمند", "↔", onSmart, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun A54DateTime() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalDateTime.now()
            delay(1_000L)
        }
    }
    val j = a54GregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    val date = "${a54Weekday(now.dayOfWeek.value)} ${a54Digits(j.third.toString())} ${a54Month(j.second)} ${a54Digits(j.first.toString())}"
    val time = a54Digits(now.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, color = Color.White.copy(alpha = .94f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = A54Glass,
            border = androidx.compose.foundation.BorderStroke(1.dp, A54Border),
        ) {
            Text(time, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun A54PowerButton(
    size: Dp,
    connected: Boolean,
    busy: Boolean,
    failed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "a54Power").animateFloat(
        initialValue = .975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(1_350), RepeatMode.Reverse),
        label = "a54Pulse",
    )
    val accent = when {
        connected -> A54Cyan
        busy -> A54Amber
        failed -> A54Red
        else -> A54Red
    }
    val brush = when {
        connected -> Brush.radialGradient(listOf(Color(0xFF5EEA9C), Color(0xFF0FB2DC), Color(0xFF0750D2)))
        busy -> Brush.radialGradient(listOf(Color(0xFFFFD760), Color(0xFF1AA8D0), Color(0xFF0750D2)))
        else -> Brush.radialGradient(listOf(Color(0xFFFF7481), Color(0xFFEC405C), Color(0xFF8B1D58)))
    }

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .shadow(24.dp, CircleShape, ambientColor = accent.copy(alpha = .70f), spotColor = accent.copy(alpha = .85f))
            .border(3.dp, Color.White.copy(alpha = .92f), CircleShape)
            .padding(7.dp)
            .border(4.dp, accent.copy(alpha = .90f), CircleShape)
            .padding(10.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .13f), size.minDimension * .44f, style = Stroke(1.6.dp.toPx()))
            drawCircle(accent.copy(alpha = .22f), size.minDimension * .34f)
            drawArc(
                color = Color.White,
                startAngle = -52f,
                sweepAngle = 284f,
                useCenter = false,
                topLeft = Offset(size.width * .34f, size.height * .28f),
                size = androidx.compose.ui.geometry.Size(size.width * .32f, size.height * .32f),
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(
                Color.White,
                Offset(size.width * .50f, size.height * .23f),
                Offset(size.width * .50f, size.height * .43f),
                8.dp.toPx(),
                StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(size * .28f))
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
                Spacer(Modifier.height(6.dp))
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun A54ServerCard(node: NodeEntity?, onClick: () -> Unit) {
    A54GlassCard(onClick = onClick, strong = true) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = .96f)) {
                Text(a54CountryFlag(node?.countryCode), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text("موقعیت فعلی", color = A54SoftText, style = MaterialTheme.typography.labelSmall)
                Text(
                    node?.name ?: "انتخاب سرور",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    node?.let { "${it.protocol.uppercase()} • ${it.lastLatencyMs?.let { ms -> "$ms ms" } ?: "تست نشده"}" }
                        ?: "برای اتصال یک پروفایل را انتخاب کنید",
                    color = if (node?.lastProbeSucceeded == true) A54Mint else A54SoftText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Surface(shape = CircleShape, color = A54GlassStrong) {
                Text("‹", modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun A54Metric(title: String, value: String, subtitle: String, glyph: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = A54Glass),
        border = androidx.compose.foundation.BorderStroke(1.dp, A54Border),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(glyph, color = accent, fontWeight = FontWeight.Black)
                Text(title, color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun A54Action(title: String, glyph: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = A54Glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, A54Border),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxSize().padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(glyph, color = A54Cyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(title, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xED031D68),
        border = androidx.compose.foundation.BorderStroke(1.dp, A54Border),
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.SpaceAround) {
            A54Tab.entries.forEachIndexed { index, item ->
                val active = selected == index
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) A54BrightBlue.copy(alpha = .48f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(item.glyph, color = if (active) Color.White else A54SoftText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                    Text(item.title, color = if (active) Color.White else A54SoftText, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Black else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun A54LogoTile() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = A54BrightBlue.copy(alpha = .72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, A54Cyan.copy(alpha = .7f)),
        shadowElevation = 8.dp,
        modifier = Modifier.size(58.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("◇", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun A54CrownTile() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = A54Glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, A54Border),
        modifier = Modifier.size(58.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("♛", color = Color(0xFFFFD542), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
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
            shape = RoundedCornerShape(22.dp),
            color = if (strong) A54GlassStrong else A54Glass,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (strong) A54Cyan.copy(alpha = .70f) else A54Border),
            shadowElevation = if (strong) 5.dp else 1.dp,
        ) { content() }
    } else {
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(22.dp),
            color = if (strong) A54GlassStrong else A54Glass,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (strong) A54Cyan.copy(alpha = .70f) else A54Border),
            shadowElevation = if (strong) 5.dp else 1.dp,
        ) { content() }
    }
}

@Composable
private fun A54Background() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(A54Cyan.copy(alpha = .20f), radius = w * .55f, center = Offset(w * .02f, h * .45f))
        drawCircle(A54BrightBlue.copy(alpha = .22f), radius = w * .52f, center = Offset(w * .98f, h * .35f))
        for (i in 0..8) {
            val y = h * (.18f + i * .065f)
            drawArc(
                A54Cyan.copy(alpha = .035f + i * .004f),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(-w * .5f, y - h * .18f),
                size = androidx.compose.ui.geometry.Size(w * 2f, h * .40f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        for (i in 0..20) {
            val x = ((i * 71) % 100) / 100f * w
            val y = ((i * 43) % 100) / 100f * h
            drawCircle(Color.White.copy(alpha = .10f), radius = (1 + i % 3).dp.toPx(), center = Offset(x, y))
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

private fun a54ShortIp(ip: String): String = if (ip.length <= 11) ip else ip.take(8) + "…"

private fun a54Mbps(bytesPerSecond: Long): String =
    String.format(Locale.US, "%.1f", bytesPerSecond.coerceAtLeast(0L) * 8.0 / 1_000_000.0)

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

private suspend fun a54FetchPublicIp(): String? = withContext(Dispatchers.IO) {
    for (endpoint in listOf("https://api.ipify.org", "https://checkip.amazonaws.com")) {
        val result = runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 4_000
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

private fun a54Digits(value: String): String = buildString(value.length) {
    value.forEach { ch -> append(if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch) }
}

private fun a54Weekday(javaDay: Int): String = when (javaDay) {
    1 -> "دوشنبه"
    2 -> "سه‌شنبه"
    3 -> "چهارشنبه"
    4 -> "پنجشنبه"
    5 -> "جمعه"
    6 -> "شنبه"
    else -> "یکشنبه"
}

private fun a54Month(month: Int): String = listOf(
    "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
).getOrElse(month) { "" }

private fun a54GregorianToJalali(gyInput: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gDm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    var gy = gyInput
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gDm[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }
    return Triple(jy, jm, jd)
}
