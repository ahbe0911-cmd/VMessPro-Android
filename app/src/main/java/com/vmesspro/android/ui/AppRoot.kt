package com.vmesspro.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vmesspro.android.data.local.NodeEntity
import com.vmesspro.android.data.local.SubscriptionEntity
import com.vmesspro.android.data.preferences.SplitTunnelMode
import com.vmesspro.android.data.preferences.VpnPreferences
import kotlinx.coroutines.launch

private val Background = Color(0xFF040812)
private val BackgroundLift = Color(0xFF071426)
private val Panel = Color(0xE80A1425)
private val PanelStrong = Color(0xFF0D1A2D)
private val Border = Color(0xFF1A3653)
private val BorderBright = Color(0xFF275979)
private val NeonBlue = Color(0xFF39D7FF)
private val NeonPurple = Color(0xFFA88BFF)
private val Mint = Color(0xFF4BE7B0)
private val Danger = Color(0xFFFF6F88)
private val TextPrimary = Color(0xFFF6F9FF)
private val TextSecondary = Color(0xFFA7B5CA)
private val TextTertiary = Color(0xFF71829C)

private enum class AppTab(val title: String, val icon: ImageVector) {
    Home("خانه", Icons.Rounded.Home),
    Servers("سرورها", Icons.Rounded.Dns),
    Subscriptions("اشتراک‌ها", Icons.Rounded.CloudSync),
    Settings("تنظیمات", Icons.Rounded.Settings),
}

private enum class OverlayRoute {
    Import,
    SplitTunnel,
}

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var overlayRoute by rememberSaveable { mutableStateOf<OverlayRoute?>(null) }
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    BackHandler(enabled = overlayRoute != null) {
        overlayRoute = null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Background, BackgroundLift, Background),
                        start = Offset.Zero,
                        end = Offset(1100f, 1900f),
                    )
                )
        ) {
            DecorativeGlow()
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = TextPrimary,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (overlayRoute == null) {
                        PremiumBottomBar(selectedTab) {
                            selectedTab = it
                        }
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                ) {
                    AppHeader(
                        overlayRoute = overlayRoute,
                        onBack = { overlayRoute = null },
                    )
                    AnimatedContent(
                        targetState = overlayRoute?.name ?: "tab-$selectedTab",
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 })
                                .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 10 })
                        },
                        label = "screen-transition",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (overlayRoute) {
                            OverlayRoute.Import -> ImportScreen(
                                onImport = viewModel::importText,
                                onDone = {
                                    overlayRoute = null
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
                                    selectedNode = selectedNode,
                                    nodeCount = nodes.size,
                                    subscriptionCount = subscriptions.size,
                                    splitCount = selectedPackageCount(preferences),
                                    onServerClick = { selectedTab = AppTab.Servers.ordinal },
                                    onImportClick = { overlayRoute = OverlayRoute.Import },
                                    onSubscriptionClick = { selectedTab = AppTab.Subscriptions.ordinal },
                                    onSplitClick = { overlayRoute = OverlayRoute.SplitTunnel },
                                    onPowerClick = {
                                        if (selectedNode == null) {
                                            selectedTab = AppTab.Servers.ordinal
                                            scope.launch { snackbarHostState.showSnackbar("ابتدا یک سرور انتخاب کنید") }
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("سرور آماده است؛ اتصال واقعی Core در مرحله بعد فعال می‌شود")
                                            }
                                        }
                                    },
                                )
                                AppTab.Servers -> ServersScreen(
                                    nodes = nodes,
                                    selectedNodeId = selectedNode?.stableId,
                                    favoriteIds = favorites,
                                    onSelect = viewModel::selectNode,
                                    onFavorite = viewModel::toggleFavorite,
                                    onDelete = viewModel::deleteNode,
                                    onAdd = { overlayRoute = OverlayRoute.Import },
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
                                    onSplit = { overlayRoute = OverlayRoute.SplitTunnel },
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
private fun DecorativeGlow() {
    val transition = rememberInfiniteTransition(label = "glow")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "glow-alpha",
    )
    Box(
        modifier = Modifier
            .size(330.dp)
            .graphicsLayer(alpha = alpha)
            .background(
                Brush.radialGradient(listOf(Color(0x2636D9FF), Color.Transparent)),
                CircleShape,
            )
    )
}

@Composable
private fun PremiumBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color(0xF40A1221),
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(22.dp)) },
                label = {
                    Text(
                        tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (selectedTab == index) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonBlue,
                    selectedTextColor = NeonBlue,
                    indicatorColor = Color(0xFF102B40),
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                ),
            )
        }
    }
}

@Composable
private fun AppHeader(overlayRoute: OverlayRoute?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (overlayRoute != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "بازگشت", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
            }
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF10283B),
                border = BorderStroke(1.dp, Color(0x6636D9FF)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    when (overlayRoute) {
                        OverlayRoute.Import -> "ورود کانفیگ"
                        OverlayRoute.SplitTunnel -> "Split Tunneling"
                        null -> "VMess Pro"
                    },
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    when (overlayRoute) {
                        OverlayRoute.Import -> "VMess / VLESS / Reality / Trojan"
                        OverlayRoute.SplitTunnel -> "انتخاب دقیق برنامه‌های عبوری از VPN"
                        null -> "شبکه امن، سریع و شخصی"
                    },
                    color = TextSecondary,
                    fontSize = 9.sp,
                )
            }
        }
        if (overlayRoute == null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF0F2032),
                border = BorderStroke(1.dp, Border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).background(Mint, CircleShape))
                    Text("READY", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    selectedNode: NodeEntity?,
    nodeCount: Int,
    subscriptionCount: Int,
    splitCount: Int,
    onServerClick: () -> Unit,
    onImportClick: () -> Unit,
    onSubscriptionClick: () -> Unit,
    onSplitClick: () -> Unit,
    onPowerClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(3.dp))
        ConnectionHero(selectedNode, onPowerClick)
        Spacer(Modifier.height(13.dp))
        ServerSelector(selectedNode, onServerClick)
        Spacer(Modifier.height(17.dp))
        SectionTitle("دسترسی سریع", "همه گزینه‌ها فعال هستند")
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { QuickActionCard("کانفیگ", "$nodeCount سرور", Icons.Rounded.Add, NeonBlue, onImportClick) }
            item { QuickActionCard("اشتراک", "$subscriptionCount لینک", Icons.Rounded.CloudSync, Mint, onSubscriptionClick) }
            item { QuickActionCard("Split", "$splitCount برنامه", Icons.Rounded.Tune, NeonPurple, onSplitClick) }
        }
        Spacer(Modifier.height(17.dp))
        SectionTitle("وضعیت شبکه", "اطلاعات ساختگی نمایش داده نمی‌شود")
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { MetricCard("Ping", "—", "ms", Icons.Rounded.Speed, NeonBlue) }
            item { MetricCard("ترافیک", "—", "MB", Icons.Rounded.CloudSync, Mint) }
            item { MetricCard("سرعت", "—", "Mb/s", Icons.Rounded.Speed, NeonPurple) }
        }
        Spacer(Modifier.height(17.dp))
        SecurityCard(onSplitClick)
    }
}

@Composable
private fun ConnectionHero(selectedNode: NodeEntity?, onPowerClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "power-press")
    val pulse = rememberInfiniteTransition(label = "power-pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "ring-scale",
    )
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Color(0xAA183650)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0x2236D9FF), Color.Transparent, Color(0x1AA98BFF))))
                .padding(horizontal = 21.dp, vertical = 21.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("اتصال امن", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        selectedNode?.let { "${it.protocol} • ${it.host}:${it.port}" } ?: "یک سرور انتخاب کنید",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF101D30), border = BorderStroke(1.dp, Border)) {
                    Text(
                        if (selectedNode == null) "NO SERVER" else "READY",
                        color = if (selectedNode == null) TextTertiary else Mint,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(158.dp)) {
                Box(Modifier.size(154.dp).scale(ringScale).border(1.dp, Color(0x2F36D9FF), CircleShape))
                Box(Modifier.size(130.dp).border(1.dp, Color(0x5A36D9FF), CircleShape))
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(pressScale)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF2DCCF3), Color(0xFF7776FF))))
                        .clickable(interactionSource = interaction, indication = null, onClick = onPowerClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "اتصال", tint = Color(0xFF03131D), modifier = Modifier.size(46.dp))
                }
            }
            Text(
                if (selectedNode == null) "آماده انتخاب سرور" else selectedNode.name,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (selectedNode == null) "با لمس دکمه به فهرست سرورها می‌روید" else "سرور انتخاب شده و برای Core آماده است",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ServerSelector(selectedNode: NodeEntity?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PanelStrong),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(15.dp), color = Color(0xFF10283B)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Dns, contentDescription = null, tint = NeonBlue) }
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("سرور فعال", color = TextTertiary, fontSize = 8.sp)
                    Text(
                        selectedNode?.name ?: "سروری انتخاب نشده",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        selectedNode?.let { "${it.protocol} • ${it.host}:${it.port}" } ?: "VMess / VLESS / Reality / Trojan",
                        color = TextSecondary,
                        fontSize = 8.sp,
                    )
                }
            }
            Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = TextTertiary, fontSize = 8.sp)
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(110), label = "quick-press")
    Card(
        modifier = Modifier.width(116.dp).scale(scale).clickable(interactionSource = source, indication = null, onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD20D192B)),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(13.dp), color = accent.copy(alpha = 0.13f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(7.dp))
            Text(title, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextTertiary, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, icon: ImageVector, accent: Color) {
    Card(
        modifier = Modifier.width(116.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBF0D192B)),
        border = BorderStroke(1.dp, Color(0xFF172B43)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Text(title, color = TextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = TextTertiary, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun SecurityCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xC70B1727)),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = Mint.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, contentDescription = null, tint = Mint) }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("حریم خصوصی برنامه‌ها", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Split Tunneling و استثنا کردن همراه‌بانک", color = TextSecondary, fontSize = 8.sp)
                }
            }
            Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun ServersScreen(
    nodes: List<NodeEntity>,
    selectedNodeId: String?,
    favoriteIds: Set<String>,
    onSelect: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(nodes, query) {
        if (query.isBlank()) nodes else nodes.filter {
            it.name.contains(query, true) || it.host.contains(query, true) || it.protocol.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("سرورها", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("${nodes.size} کانفیگ ذخیره‌شده", color = TextTertiary, fontSize = 9.sp)
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("افزودن")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("جستجو در نام، آدرس یا پروتکل") },
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            EmptyState(
                title = if (nodes.isEmpty()) "هنوز سروری ندارید" else "نتیجه‌ای پیدا نشد",
                subtitle = if (nodes.isEmpty()) "کانفیگ VMess یا VLESS را اضافه کنید." else "عبارت جستجو را تغییر دهید.",
                icon = Icons.Rounded.Dns,
                action = if (nodes.isEmpty()) onAdd else null,
                actionText = "افزودن کانفیگ",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(filtered, key = { it.stableId }) { node ->
                    ServerCard(
                        node = node,
                        selected = node.stableId == selectedNodeId,
                        favorite = node.stableId in favoriteIds,
                        onSelect = { onSelect(node.stableId) },
                        onFavorite = { onFavorite(node.stableId) },
                        onDelete = { onDelete(node.stableId) },
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
) {
    val borderColor = if (selected) NeonBlue.copy(alpha = 0.75f) else Border
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).animateContentSize(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xE6112437) else PanelStrong),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(43.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFF10283B)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Dns, contentDescription = null, tint = if (selected) Mint else NeonBlue)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.width(180.dp)) {
                    Text(node.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${node.protocol} • ${node.host}:${node.port}", color = TextSecondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (selected) "سرور فعال" else "برای انتخاب لمس کنید", color = if (selected) Mint else TextTertiary, fontSize = 8.sp)
                }
            }
            Row {
                IconButton(onClick = onFavorite) {
                    Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = "علاقه‌مندی", tint = if (favorite) NeonPurple else TextTertiary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = Danger)
                }
            }
        }
    }
}

@Composable
private fun ImportScreen(
    onImport: (String) -> com.vmesspro.android.domain.config.ImportPreview,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var value by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)
    ) {
        Text("ورود هوشمند کانفیگ", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("یک یا چند لینک VMess / VLESS / Trojan یا لینک Subscription را وارد کنید.", color = TextSecondary, fontSize = 9.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; summary = null },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            placeholder = { Text("vless://...\nvmess://...\nhttps://subscription...") },
            shape = RoundedCornerShape(20.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilledTonalButton(
                onClick = {
                    clipboard.getText()?.text?.let { value = it }
                    summary = null
                }
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("چسباندن")
            }
            TextButton(onClick = { value = ""; summary = null }) { Text("پاک کردن") }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (value.isBlank()) {
                    summary = "متنی برای پردازش وارد نشده است."
                } else {
                    val preview = onImport(value)
                    summary = "${preview.validServerCount} سرور معتبر • ${preview.duplicateCount} تکراری • ${preview.invalidCount} نامعتبر • ${preview.subscriptionUrls.size} اشتراک"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color(0xFF03131D)),
        ) {
            Text("بررسی و ذخیره", fontWeight = FontWeight.ExtraBold)
        }
        summary?.let {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0E2132), border = BorderStroke(1.dp, BorderBright)) {
                Text(it, modifier = Modifier.padding(13.dp), color = TextPrimary, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (it.startsWith("0 ").not()) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("اشتراک‌ها", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("دریافت و بروزرسانی واقعی Subscription", color = TextTertiary, fontSize = 9.sp)
            }
            FilledTonalButton(onClick = { showAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("افزودن")
            }
        }
        Spacer(Modifier.height(10.dp))
        if (subscriptions.isEmpty()) {
            EmptyState("اشتراکی اضافه نشده", "لینک Subscription را اضافه کنید تا سرورها دریافت شوند.", Icons.Rounded.CloudSync, { showAdd = true }, "افزودن اشتراک")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
                items(subscriptions, key = { it.id }) { subscription ->
                    SubscriptionCard(subscription, { onRefresh(subscription.id) }, { onDelete(subscription.id) })
                }
            }
        }
    }
    if (showAdd) {
        AddSubscriptionDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                onAdd(name, url)
                showAdd = false
            },
        )
    }
}

@Composable
private fun SubscriptionCard(subscription: SubscriptionEntity, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = PanelStrong),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(43.dp), shape = RoundedCornerShape(14.dp), color = Mint.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = Mint) }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.width(190.dp)) {
                    Text(subscription.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subscription.lastRefreshError?.let { "خطا: $it" } ?: if (subscription.lastRefreshAt == null) "هنوز بروزرسانی نشده" else "آخرین بروزرسانی انجام شده",
                        color = if (subscription.lastRefreshError == null) TextSecondary else Danger,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row {
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "بروزرسانی", tint = NeonBlue) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "حذف", tint = Danger) }
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("لینک Subscription") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, url) }, enabled = url.isNotBlank()) { Text("دریافت") }
        },
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
    var dnsInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(preferences.customDns) { dnsInput = preferences.customDns.orEmpty() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)
    ) {
        Text("تنظیمات", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("تنظیمات فعلی واقعاً در DataStore ذخیره می‌شوند.", color = TextTertiary, fontSize = 9.sp)
        Spacer(Modifier.height(12.dp))
        SettingsToggleCard(
            title = "اتصال مجدد خودکار",
            subtitle = "پس از قطع شبکه، آماده تلاش مجدد باشد",
            checked = preferences.autoReconnect,
            onCheckedChange = onAutoReconnect,
        )
        Spacer(Modifier.height(9.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(19.dp),
            colors = CardDefaults.cardColors(containerColor = PanelStrong),
            border = BorderStroke(1.dp, Border),
        ) {
            Column(Modifier.padding(13.dp)) {
                Text("DNS سفارشی", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("مثال: 1.1.1.1 یا آدرس DoH در مرحله Core", color = TextSecondary, fontSize = 8.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = { dnsInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("خالی = پیش‌فرض") },
                    shape = RoundedCornerShape(15.dp),
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { onSaveDns(dnsInput.ifBlank { null }) }) { Text("ذخیره DNS") }
            }
        }
        Spacer(Modifier.height(9.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSplit),
            shape = RoundedCornerShape(19.dp),
            colors = CardDefaults.cardColors(containerColor = PanelStrong),
            border = BorderStroke(1.dp, Border),
        ) {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = NeonPurple)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Split Tunneling", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${selectedPackageCount(preferences)} برنامه انتخاب شده", color = TextSecondary, fontSize = 8.sp)
                    }
                }
                Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun SettingsToggleCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = PanelStrong),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.width(250.dp)) {
                Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextSecondary, fontSize = 8.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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
        Text("انتخاب برنامه‌ها", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text("انتخاب‌ها در DataStore ذخیره می‌شوند و برای VpnService آماده‌اند.", color = TextTertiary, fontSize = 9.sp)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("جستجوی برنامه") },
            shape = RoundedCornerShape(17.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NeonBlue) }
        } else if (filtered.isEmpty()) {
            EmptyState("برنامه‌ای پیدا نشد", "اگر فهرست خالی است، دسترسی Package Visibility را بررسی کنید.", Icons.Rounded.Tune, null, null)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    AppToggleRow(app, app.packageName in selected) { enabled -> onTogglePackage(app.packageName, enabled) }
                }
            }
        }
    }
}

@Composable
private fun AppToggleRow(app: InstalledAppInfo, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xE6112437) else Color(0xC90C1829)),
        border = BorderStroke(1.dp, if (selected) NeonPurple.copy(alpha = 0.55f) else Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(39.dp), shape = RoundedCornerShape(12.dp), color = NeonPurple.copy(alpha = 0.1f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Security, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(19.dp)) }
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.width(245.dp)) {
                    Text(app.label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = TextTertiary, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(checked = selected, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector,
    action: (() -> Unit)?,
    actionText: String?,
) {
    Box(Modifier.fillMaxSize().padding(top = 30.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(70.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFF10283B)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(32.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TextSecondary, fontSize = 9.sp)
            if (action != null && actionText != null) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = action) { Text(actionText) }
            }
        }
    }
}

private fun selectedPackageCount(preferences: VpnPreferences): Int = when (preferences.splitTunnelMode) {
    SplitTunnelMode.ONLY_SELECTED -> preferences.includedPackages.size
    SplitTunnelMode.EXCLUDE_SELECTED -> preferences.excludedPackages.size
}
