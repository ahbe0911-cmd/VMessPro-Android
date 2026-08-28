package com.vmesspro.android.ui

import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.data.local.NodeEntity
import com.vmesspro.android.data.local.SubscriptionEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferences
import kotlinx.coroutines.launch

private val Ink = Color(0xFF020610)
private val DeepNavy = Color(0xFF06101E)
private val Glass = Color(0xDE0A1628)
private val GlassStrong = Color(0xFF0D1C31)
private val Stroke = Color(0xFF1D3957)
private val StrokeBright = Color(0xFF2D6888)
private val Cyan = Color(0xFF39DAFF)
private val Purple = Color(0xFFA98CFF)
private val Mint = Color(0xFF4DE7B0)
private val Amber = Color(0xFFFFC86B)
private val Rose = Color(0xFFFF728A)
private val White = Color(0xFFF7FAFF)
private val Muted = Color(0xFFA9B8CE)
private val Dim = Color(0xFF71849F)

private enum class AppTab(val title: String, val icon: ImageVector) {
    Home("خانه", Icons.Rounded.Home),
    Servers("سرورها", Icons.Rounded.Dns),
    Subscriptions("اشتراک‌ها", Icons.Rounded.CloudSync),
    Settings("تنظیمات", Icons.Rounded.Settings),
}

private enum class OverlayRoute { Import, SplitTunnel }

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf<OverlayRoute?>(null) }

    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (VpnService.prepare(context) == null) {
            viewModel.connectSelected()
        } else {
            scope.launch { snackbar.showSnackbar("مجوز ساخت VPN صادر نشد") }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    fun powerAction() {
        when (connectionState) {
            is ConnectionState.Connected,
            ConnectionState.Preparing,
            ConnectionState.Connecting,
            ConnectionState.Verifying,
            ConnectionState.Reconnecting -> viewModel.disconnect()
            else -> {
                if (selectedNode == null) {
                    selectedTab = AppTab.Servers.ordinal
                    scope.launch { snackbar.showSnackbar("ابتدا یک سرور انتخاب کنید") }
                } else {
                    val permissionIntent = VpnService.prepare(context)
                    if (permissionIntent == null) viewModel.connectSelected()
                    else vpnPermissionLauncher.launch(permissionIntent)
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Ink, DeepNavy, Color(0xFF071426), Ink),
                        start = Offset.Zero,
                        end = Offset(1200f, 2200f),
                    )
                )
        ) {
            AmbientGlows(connectionState)
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (overlay == null) {
                        PremiumBottomBar(selectedTab) { selectedTab = it }
                    }
                },
            ) { innerPadding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                ) {
                    PremiumHeader(
                        state = connectionState,
                        overlay = overlay,
                        onBack = { overlay = null },
                    )
                    AnimatedContent(
                        targetState = overlay?.name ?: "tab-$selectedTab",
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 9 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -it / 10 })
                        },
                        label = "premium-screen",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (overlay) {
                            OverlayRoute.Import -> ImportScreen(
                                onImport = viewModel::importText,
                                onDone = {
                                    overlay = null
                                    selectedTab = AppTab.Servers.ordinal
                                },
                            )
                            OverlayRoute.SplitTunnel -> SplitTunnelScreen(
                                preferences = preferences,
                                apps = installedApps,
                                loading = appsLoading,
                                onLoad = viewModel::loadInstalledApps,
                                onModeChange = viewModel::setSplitMode,
                                onTogglePackage = viewModel::toggleSplitPackage,
                            )
                            null -> when (AppTab.entries[selectedTab]) {
                                AppTab.Home -> HomeScreen(
                                    state = connectionState,
                                    selectedNode = selectedNode,
                                    nodeCount = nodes.size,
                                    subscriptionCount = subscriptions.size,
                                    splitCount = selectedPackageCount(preferences),
                                    onPower = ::powerAction,
                                    onServer = { selectedTab = AppTab.Servers.ordinal },
                                    onImport = { overlay = OverlayRoute.Import },
                                    onSubscriptions = { selectedTab = AppTab.Subscriptions.ordinal },
                                    onSplit = { overlay = OverlayRoute.SplitTunnel },
                                    onProbe = selectedNode?.let { { viewModel.probeNode(it.stableId) } },
                                )
                                AppTab.Servers -> ServersScreen(
                                    nodes = nodes,
                                    selectedNodeId = selectedNode?.stableId,
                                    favorites = favorites,
                                    onSelect = viewModel::selectNode,
                                    onFavorite = viewModel::toggleFavorite,
                                    onDelete = viewModel::deleteNode,
                                    onProbe = viewModel::probeNode,
                                    onAdd = { overlay = OverlayRoute.Import },
                                )
                                AppTab.Subscriptions -> SubscriptionsScreen(
                                    subscriptions = subscriptions,
                                    onAdd = viewModel::addSubscription,
                                    onRefresh = viewModel::refreshSubscription,
                                    onDelete = viewModel::deleteSubscription,
                                )
                                AppTab.Settings -> SettingsScreen(
                                    preferences = preferences,
                                    onAutoReconnect = viewModel::setAutoReconnect,
                                    onSaveDns = viewModel::setDns,
                                    onSplit = { overlay = OverlayRoute.SplitTunnel },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientGlows(state: ConnectionState) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Reverse),
        label = "ambient-alpha",
    )
    val active = state is ConnectionState.Connected
    Box(
        Modifier
            .size(390.dp)
            .graphicsLayer(alpha = drift)
            .background(
                Brush.radialGradient(
                    listOf((if (active) Mint else Cyan).copy(alpha = 0.16f), Color.Transparent)
                ),
                CircleShape,
            )
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Purple.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(900f, 1550f),
                    radius = 850f,
                )
            )
    )
}

@Composable
private fun PremiumHeader(state: ConnectionState, overlay: OverlayRoute?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (overlay != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "بازگشت", tint = White)
                }
            }
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFF0D2A3D),
                border = BorderStroke(1.dp, Cyan.copy(alpha = 0.42f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Security, null, tint = Cyan, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    when (overlay) {
                        OverlayRoute.Import -> "ورود کانفیگ"
                        OverlayRoute.SplitTunnel -> "Split Tunneling"
                        null -> "VMess Pro"
                    },
                    color = White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    when (overlay) {
                        OverlayRoute.Import -> "VMess • VLESS • Reality • Trojan"
                        OverlayRoute.SplitTunnel -> "کنترل مسیر VPN برای هر برنامه"
                        null -> "sing-box core • اتصال واقعی Android VPN"
                    },
                    color = Muted,
                    fontSize = 9.sp,
                )
            }
        }
        if (overlay == null) ConnectionBadge(state)
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState) {
    val (label, color) = stateVisual(state)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PremiumBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color(0xF207101D),
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, tab.title, modifier = Modifier.size(21.dp)) },
                label = {
                    Text(
                        tab.title,
                        fontSize = 9.sp,
                        fontWeight = if (selected == index) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cyan,
                    selectedTextColor = Cyan,
                    indicatorColor = Color(0xFF0D2C42),
                    unselectedIconColor = Dim,
                    unselectedTextColor = Dim,
                ),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: ConnectionState,
    selectedNode: NodeEntity?,
    nodeCount: Int,
    subscriptionCount: Int,
    splitCount: Int,
    onPower: () -> Unit,
    onServer: () -> Unit,
    onImport: () -> Unit,
    onSubscriptions: () -> Unit,
    onSplit: () -> Unit,
    onProbe: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(bottom = 24.dp)
    ) {
        ConnectionHero(state, selectedNode, onPower)
        Spacer(Modifier.height(12.dp))
        ActiveServerCard(selectedNode, onServer, onProbe)
        Spacer(Modifier.height(17.dp))
        SectionHeader("دسترسی سریع", "عملکرد واقعی و ذخیره پایدار")
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { QuickCard("کانفیگ", "$nodeCount سرور", Icons.Rounded.Add, Cyan, onImport) }
            item { QuickCard("اشتراک", "$subscriptionCount لینک", Icons.Rounded.CloudSync, Mint, onSubscriptions) }
            item { QuickCard("Split", "$splitCount برنامه", Icons.Rounded.Tune, Purple, onSplit) }
        }
        Spacer(Modifier.height(17.dp))
        SectionHeader("وضعیت واقعی", "عدد نمایشی ساختگی نداریم")
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RealMetric(
                Modifier.weight(1f),
                "Ping TCP",
                selectedNode?.lastLatencyMs?.let { "$it ms" } ?: "—",
                Icons.Rounded.Speed,
                Cyan,
            )
            RealMetric(
                Modifier.weight(1f),
                "Core",
                "1.13.19",
                Icons.Rounded.Bolt,
                Purple,
            )
            RealMetric(
                Modifier.weight(1f),
                "Tunnel",
                if (state is ConnectionState.Connected) "فعال" else "—",
                Icons.Rounded.Lock,
                Mint,
            )
        }
        Spacer(Modifier.height(12.dp))
        PrivacyCard(onSplit)
    }
}

@Composable
private fun ConnectionHero(state: ConnectionState, node: NodeEntity?, onPower: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.93f else 1f, tween(110), label = "power-press")
    val infinite = rememberInfiniteTransition(label = "power-ring")
    val pulse by infinite.animateFloat(
        0.96f,
        if (state is ConnectionState.Connected) 1.10f else 1.045f,
        infiniteRepeatable(tween(if (state is ConnectionState.Connected) 1300 else 2200), RepeatMode.Reverse),
        label = "power-pulse",
    )
    val (_, stateColor) = stateVisual(state)
    val ringColor by animateColorAsState(stateColor, tween(250), label = "ring-color")
    val busy = state == ConnectionState.Preparing || state == ConnectionState.Connecting || state == ConnectionState.Verifying

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = BorderStroke(1.dp, ringColor.copy(alpha = 0.30f)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(ringColor.copy(alpha = 0.13f), Color.Transparent, Purple.copy(alpha = 0.07f))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("تونل امن", color = White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        node?.let { "${it.protocol} • ${it.host}:${it.port}" } ?: "یک سرور انتخاب کنید",
                        color = Muted,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ConnectionBadge(state)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.height(162.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(158.dp).scale(pulse).border(1.dp, ringColor.copy(alpha = 0.24f), CircleShape))
                Box(Modifier.size(134.dp).border(1.dp, ringColor.copy(alpha = 0.48f), CircleShape))
                Box(
                    Modifier.size(108.dp).scale(pressScale).clip(CircleShape)
                        .background(
                            if (state is ConnectionState.Connected)
                                Brush.linearGradient(listOf(Mint, Cyan))
                            else Brush.linearGradient(listOf(Cyan, Purple))
                        )
                        .clickable(interactionSource = interaction, indication = null, onClick = onPower),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = Ink,
                            strokeWidth = 4.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            if (state is ConnectionState.Connected) "قطع اتصال" else "اتصال",
                            tint = Ink,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
            }
            Text(
                connectionTitle(state, node),
                color = White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(connectionSubtitle(state), color = Muted, fontSize = 9.sp)
            if (state is ConnectionState.Error) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Rose.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Rose.copy(alpha = 0.30f)),
                ) {
                    Text(
                        state.reason,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Rose,
                        fontSize = 8.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveServerCard(node: NodeEntity?, onClick: () -> Unit, onProbe: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = GlassStrong),
        border = BorderStroke(1.dp, Stroke),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(Modifier.size(45.dp), RoundedCornerShape(14.dp), color = Cyan.copy(alpha = 0.10f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Dns, null, tint = Cyan) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("سرور فعال", color = Dim, fontSize = 8.sp)
                    Text(
                        node?.name ?: "سروری انتخاب نشده",
                        color = White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        node?.let { "${it.protocol} • ${it.host}:${it.port}" } ?: "VMess / VLESS / Reality / Trojan",
                        color = Muted,
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
            if (node != null && onProbe != null) {
                TextButton(onClick = onProbe) { Text("تست", color = Cyan, fontSize = 9.sp) }
            }
            Icon(Icons.Rounded.KeyboardArrowLeft, null, tint = Muted)
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(100), label = "quick-scale")
    Card(
        modifier = Modifier.width(116.dp).scale(scale)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD20D192B)),
        border = BorderStroke(1.dp, Stroke),
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(39.dp), RoundedCornerShape(13.dp), color = accent.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(7.dp))
            Text(title, color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Dim, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun RealMetric(modifier: Modifier, title: String, value: String, icon: ImageVector, accent: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xC50B1728)),
        border = BorderStroke(1.dp, Color(0xFF162E48)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(7.dp))
            Text(title, color = Dim, fontSize = 7.sp)
            Text(value, color = White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun PrivacyCard(onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCA0B1728)),
        border = BorderStroke(1.dp, Stroke),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), RoundedCornerShape(14.dp), color = Mint.copy(alpha = 0.10f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, null, tint = Mint) }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("حریم خصوصی برنامه‌ها", color = White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Split Tunneling واقعی با Android VpnService", color = Muted, fontSize = 8.sp)
                }
            }
            Icon(Icons.Rounded.KeyboardArrowLeft, null, tint = Muted)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
        Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Dim, fontSize = 7.sp)
    }
}

@Composable
private fun ServersScreen(
    nodes: List<NodeEntity>,
    selectedNodeId: String?,
    favorites: Set<String>,
    onSelect: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
    onProbe: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(nodes, query) {
        if (query.isBlank()) nodes else nodes.filter {
            it.name.contains(query, true) || it.host.contains(query, true) || it.protocol.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenTitle("سرورها", "${nodes.size} کانفیگ واقعی", onAdd)
        Spacer(Modifier.height(10.dp))
        SearchField(query, { query = it }, "جستجو در سرورها")
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            EmptyState(
                if (nodes.isEmpty()) "هنوز سروری ندارید" else "نتیجه‌ای پیدا نشد",
                if (nodes.isEmpty()) "کانفیگ VMess یا VLESS را اضافه کنید." else "عبارت جستجو را تغییر دهید.",
                Icons.Rounded.Dns,
                if (nodes.isEmpty()) onAdd else null,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
                items(filtered, key = { it.stableId }) { node ->
                    ServerCard(
                        node,
                        node.stableId == selectedNodeId,
                        node.stableId in favorites,
                        { onSelect(node.stableId) },
                        { onFavorite(node.stableId) },
                        { onDelete(node.stableId) },
                        { onProbe(node.stableId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    node: NodeEntity,
    selected: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onProbe: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).animateContentSize(),
        RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xE511263A) else GlassStrong),
        border = BorderStroke(1.dp, if (selected) Cyan.copy(alpha = 0.70f) else Stroke),
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(43.dp), RoundedCornerShape(14.dp), color = Cyan.copy(alpha = 0.10f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Dns, null, tint = if (selected) Mint else Cyan)
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.name, color = White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${node.protocol} • ${node.host}:${node.port}", color = Muted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        node.lastLatencyMs?.let { "$it ms" } ?: if (selected) "سرور انتخاب‌شده" else "تست نشده",
                        color = node.lastLatencyMs?.let { Mint } ?: Dim,
                        fontSize = 7.sp,
                    )
                }
            }
            TextButton(onClick = onProbe, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("تست", fontSize = 8.sp) }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "علاقه‌مندی", tint = if (favorite) Purple else Dim)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "حذف", tint = Rose) }
        }
    }
}

@Composable
private fun ImportScreen(
    onImport: (String) -> com.vmesspro.android.domain.config.ImportPreview,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        Text("ورود هوشمند کانفیگ", color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("چند لینک را هم‌زمان وارد کنید؛ موارد معتبر ذخیره می‌شوند.", color = Muted, fontSize = 8.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; summary = null },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            placeholder = { Text("vless://...\nvmess://...\nhttps://subscription...") },
            shape = RoundedCornerShape(20.dp),
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                clipboard.getText()?.text?.let { text = it }
                summary = null
            }) {
                Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("چسباندن")
            }
            TextButton(onClick = { text = ""; summary = null }) { Text("پاک کردن") }
        }
        Spacer(Modifier.height(13.dp))
        Button(
            onClick = {
                if (text.isBlank()) summary = "متنی برای پردازش وارد نشده است."
                else {
                    val preview = onImport(text)
                    summary = "${preview.validServerCount} معتبر • ${preview.duplicateCount} تکراری • ${preview.invalidCount} نامعتبر • ${preview.subscriptionUrls.size} اشتراک"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink),
        ) { Text("بررسی و ذخیره", fontWeight = FontWeight.ExtraBold) }
        summary?.let {
            Spacer(Modifier.height(11.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0C2133), border = BorderStroke(1.dp, StrokeBright)) {
                Text(it, Modifier.padding(12.dp), color = White, fontSize = 9.sp)
            }
            if (!it.startsWith("0 معتبر")) {
                Spacer(Modifier.height(9.dp))
                FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("مشاهده سرورها") }
            }
        }
    }
}

@Composable
private fun SubscriptionsScreen(
    subscriptions: List<SubscriptionEntity>,
    onAdd: (String, String) -> Unit,
    onRefresh: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenTitle("اشتراک‌ها", "دریافت و Refresh واقعی", { showAdd = true })
        Spacer(Modifier.height(10.dp))
        if (subscriptions.isEmpty()) {
            EmptyState("اشتراکی اضافه نشده", "لینک Subscription را اضافه کنید.", Icons.Rounded.CloudSync, { showAdd = true })
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
                items(subscriptions, key = { it.id }) { sub ->
                    Card(
                        Modifier.fillMaxWidth(), RoundedCornerShape(19.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassStrong), border = BorderStroke(1.dp, Stroke)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(42.dp), RoundedCornerShape(13.dp), color = Mint.copy(alpha = 0.10f)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CloudSync, null, tint = Mint) }
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sub.name, color = White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        sub.lastRefreshError?.let { "خطا: $it" }
                                            ?: if (sub.lastRefreshAt == null) "هنوز بروزرسانی نشده" else "بروزرسانی موفق",
                                        color = if (sub.lastRefreshError == null) Muted else Rose,
                                        fontSize = 7.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { onRefresh(sub.id) }) { Icon(Icons.Rounded.Refresh, "بروزرسانی", tint = Cyan) }
                            IconButton(onClick = { onDelete(sub.id) }) { Icon(Icons.Rounded.Delete, "حذف", tint = Rose) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddSubscriptionDialog({ showAdd = false }) { name, url ->
        onAdd(name, url)
        showAdd = false
    }
}

@Composable
private fun AddSubscriptionDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن اشتراک") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("لینک Subscription") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onAdd(name, url) }, enabled = url.isNotBlank()) { Text("دریافت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun SettingsScreen(
    preferences: VpnPreferences,
    onAutoReconnect: (Boolean) -> Unit,
    onSaveDns: (String?) -> Unit,
    onSplit: () -> Unit,
) {
    var dns by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(preferences.customDns) { dns = preferences.customDns.orEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        Text("تنظیمات", color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("تنظیمات اتصال در DataStore ذخیره و در Core اعمال می‌شوند.", color = Dim, fontSize = 8.sp)
        Spacer(Modifier.height(12.dp))
        SettingToggle(
            "اتصال مجدد خودکار",
            "زیرساخت حالت اتصال برای Reconnect آماده است",
            preferences.autoReconnect,
            onAutoReconnect,
        )
        Spacer(Modifier.height(9.dp))
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = GlassStrong), border = BorderStroke(1.dp, Stroke)) {
            Column(Modifier.padding(13.dp)) {
                Text("DNS سفارشی", color = White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Text("IPv4 مثل 1.1.1.1 یا DoH مثل https://.../dns-query", color = Muted, fontSize = 7.sp)
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(dns, { dns = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("خالی = DNS سیستم") }, shape = RoundedCornerShape(15.dp))
                Spacer(Modifier.height(7.dp))
                FilledTonalButton(onClick = { onSaveDns(dns.ifBlank { null }) }) { Text("ذخیره DNS") }
            }
        }
        Spacer(Modifier.height(9.dp))
        Card(Modifier.fillMaxWidth().clickable(onClick = onSplit), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = GlassStrong), border = BorderStroke(1.dp, Stroke)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = Purple)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Split Tunneling", color = White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${selectedPackageCount(preferences)} برنامه انتخاب‌شده", color = Muted, fontSize = 7.sp)
                    }
                }
                Icon(Icons.Rounded.KeyboardArrowLeft, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(9.dp))
        Surface(shape = RoundedCornerShape(17.dp), color = Cyan.copy(alpha = 0.07f), border = BorderStroke(1.dp, Cyan.copy(alpha = 0.22f))) {
            Column(Modifier.padding(12.dp)) {
                Text("Core Runtime", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                Text("sing-box/libbox v1.13.19 • Android VpnService • isolated :vpn process", color = Muted, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = GlassStrong), border = BorderStroke(1.dp, Stroke)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Muted, fontSize = 7.sp)
            }
            Switch(checked, onChange)
        }
    }
}

@Composable
private fun SplitTunnelScreen(
    preferences: VpnPreferences,
    apps: List<InstalledAppInfo>,
    loading: Boolean,
    onLoad: () -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onLoad() }
    val selected = when (preferences.splitTunnelMode) {
        SplitTunnelMode.ONLY_SELECTED -> preferences.includedPackages
        SplitTunnelMode.EXCLUDE_SELECTED -> preferences.excludedPackages
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("انتخاب برنامه‌ها", color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("این انتخاب‌ها مستقیماً به allowlist / denylist خود VpnService می‌روند.", color = Dim, fontSize = 8.sp)
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item {
                FilterChip(
                    selected = preferences.splitTunnelMode == SplitTunnelMode.EXCLUDE_SELECTED,
                    onClick = { onModeChange(SplitTunnelMode.EXCLUDE_SELECTED) },
                    label = { Text("همه به‌جز انتخاب‌شده‌ها") },
                )
            }
            item {
                FilterChip(
                    selected = preferences.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED,
                    onClick = { onModeChange(SplitTunnelMode.ONLY_SELECTED) },
                    label = { Text("فقط انتخاب‌شده‌ها") },
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        SearchField(query, { query = it }, "جستجوی برنامه")
        Spacer(Modifier.height(7.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
            filtered.isEmpty() -> EmptyState("برنامه‌ای پیدا نشد", "فهرست برنامه‌های قابل مشاهده خالی است.", Icons.Rounded.Tune, null)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val enabled = app.packageName in selected
                    Card(
                        Modifier.fillMaxWidth(), RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xE5112539) else Color(0xC90B1728)),
                        border = BorderStroke(1.dp, if (enabled) Purple.copy(alpha = 0.55f) else Stroke),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), color = Purple.copy(alpha = 0.10f)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(if (enabled) Icons.Rounded.Check else Icons.Rounded.Security, null, tint = Purple, modifier = Modifier.size(18.dp)) }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, color = Dim, fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Switch(enabled, { onTogglePackage(app.packageName, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Dim, fontSize = 8.sp)
        }
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text("افزودن")
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        placeholder = { Text(hint) },
        shape = RoundedCornerShape(17.dp),
    )
}

@Composable
private fun EmptyState(title: String, subtitle: String, icon: ImageVector, action: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(68.dp), RoundedCornerShape(23.dp), color = Color(0xFF0D2A3D)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Cyan, modifier = Modifier.size(31.dp)) }
            }
            Spacer(Modifier.height(11.dp))
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 8.sp)
            if (action != null) {
                Spacer(Modifier.height(11.dp))
                FilledTonalButton(onClick = action) { Text("افزودن") }
            }
        }
    }
}

private fun stateVisual(state: ConnectionState): Pair<String, Color> = when (state) {
    ConnectionState.Disconnected -> "قطع" to Dim
    ConnectionState.Preparing -> "آماده‌سازی" to Amber
    ConnectionState.Connecting -> "اتصال" to Cyan
    ConnectionState.Verifying -> "بررسی" to Purple
    is ConnectionState.Connected -> "متصل" to Mint
    ConnectionState.Reconnecting -> "اتصال مجدد" to Amber
    is ConnectionState.Error -> "خطا" to Rose
}

private fun connectionTitle(state: ConnectionState, node: NodeEntity?): String = when (state) {
    is ConnectionState.Connected -> node?.name ?: "متصل"
    ConnectionState.Preparing -> "در حال آماده‌سازی"
    ConnectionState.Connecting -> "در حال ساخت تونل"
    ConnectionState.Verifying -> "در حال بررسی مسیر VPN"
    ConnectionState.Reconnecting -> "در حال اتصال مجدد"
    is ConnectionState.Error -> "اتصال ناموفق"
    ConnectionState.Disconnected -> node?.name ?: "آماده اتصال"
}

private fun connectionSubtitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "ترافیک از تونل Android VPN عبور می‌کند"
    ConnectionState.Preparing -> "مجوزها و تنظیمات Core در حال آماده‌سازی است"
    ConnectionState.Connecting -> "libbox در حال ایجاد TUN و مسیرهای شبکه است"
    ConnectionState.Verifying -> "دسترسی اینترنت از خود تونل در حال تست است"
    ConnectionState.Reconnecting -> "در حال بازیابی اتصال"
    is ConnectionState.Error -> "برای تلاش دوباره دکمه اتصال را لمس کنید"
    ConnectionState.Disconnected -> "برای اتصال دکمه مرکزی را لمس کنید"
}

private fun selectedPackageCount(preferences: VpnPreferences): Int = when (preferences.splitTunnelMode) {
    SplitTunnelMode.ONLY_SELECTED -> preferences.includedPackages.size
    SplitTunnelMode.EXCLUDE_SELECTED -> preferences.excludedPackages.size
}
