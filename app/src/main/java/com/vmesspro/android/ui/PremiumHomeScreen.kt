package com.vmesspro.android.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.core.VpnTelemetry
import com.vmesspro.android.data.local.NodeEntity
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val LRM = "\u200E"

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun PremiumHomeScreen(
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
    onSettings: () -> Unit,
) {
    val connected = state is ConnectionState.Connected
    val busy = state == ConnectionState.Preparing || state == ConnectionState.Connecting ||
        state == ConnectionState.Verifying || state == ConnectionState.Reconnecting
    val error = state is ConnectionState.Error
    var publicIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connected) {
        publicIp = if (connected) premiumFetchPublicIp() else null
    }

    val activity = LocalContext.current as? Activity
    val windowClass = activity?.let { calculateWindowSizeClass(it) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        val widthCompact = windowClass?.widthSizeClass == WindowWidthSizeClass.Compact || maxWidth < 600.dp
        val heightCompact = windowClass?.heightSizeClass == WindowHeightSizeClass.Compact || maxHeight < 560.dp
        val short = maxHeight < 640.dp
        val narrow = maxWidth < 360.dp

        val horizontalPadding = when {
            narrow -> 10.dp
            widthCompact -> 14.dp
            else -> 20.dp
        }
        val gap = when {
            heightCompact -> 4.dp
            short -> 6.dp
            else -> 8.dp
        }
        val headerHeight = if (short) 50.dp else 58.dp
        val serverHeight = if (short) 62.dp else 68.dp
        val statsHeight = if (short) 60.dp else 68.dp
        val actionsHeight = if (short) 54.dp else 60.dp
        val orbMax = when {
            heightCompact -> 124.dp
            short -> 142.dp
            else -> 160.dp
        }
        val orbMin = if (heightCompact) 112.dp else 124.dp
        val orbSize = minOf(maxWidth * 0.40f, maxHeight * 0.25f).coerceIn(orbMin, orbMax)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = if (short) 3.dp else 5.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            PremiumHeader(
                modifier = Modifier.height(headerHeight),
                state = state,
                onSettings = onSettings,
            )

            PremiumConnectSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                orbSize = orbSize,
                state = state,
                connected = connected,
                busy = busy,
                error = error,
                enabled = !testing,
                onPower = onPower,
            )

            PremiumServerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(serverHeight),
                node = node,
                connected = connected,
                state = state,
                onClick = onLocations,
            )

            PremiumStatsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statsHeight),
                publicIp = publicIp,
                connected = connected,
                node = node,
                telemetry = telemetry,
                compact = narrow,
            )

            PremiumQuickActions(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actionsHeight),
                testing = testing,
                onImport = onImport,
                onSmart = onSmart,
                onSplit = onSplit,
                onLocations = onLocations,
            )
        }
    }
}

@Composable
private fun PremiumHeader(
    modifier: Modifier,
    state: ConnectionState,
    onSettings: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalDateTime.now()
            delay(1_000L)
        }
    }

    val jalali = premiumGregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    val date = "${premiumWeekday(now.dayOfWeek.value)} ${premiumDigits(jalali.third.toString())} ${premiumMonth(jalali.second)}"
    val time = premiumDigits(now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)))
    val statusColor by animateColorAsState(premiumStatusColor(state), label = "headerStatus")

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VMess Pro",
                    color = PremiumVpnColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.size(7.dp))
                Surface(
                    shape = PremiumVpnShapes.Pill,
                    color = PremiumVpnColors.Purple.copy(alpha = .18f),
                    border = BorderStroke(1.dp, PremiumVpnColors.Purple.copy(alpha = .45f)),
                ) {
                    Text(
                        "PRO",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        color = PremiumVpnColors.Purple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.size(5.dp))
                Text(
                    "$date  •  $time",
                    color = PremiumVpnColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }

        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = PremiumVpnColors.SurfaceSoft,
            border = BorderStroke(1.dp, PremiumVpnColors.Border),
            onClick = onSettings,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("⚙", color = PremiumVpnColors.TextPrimary, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun PremiumConnectSection(
    modifier: Modifier,
    orbSize: Dp,
    state: ConnectionState,
    connected: Boolean,
    busy: Boolean,
    error: Boolean,
    enabled: Boolean,
    onPower: () -> Unit,
) {
    val label = when {
        connected -> "قطع اتصال"
        busy -> "در حال اتصال…"
        error -> "تلاش مجدد"
        else -> "اتصال"
    }
    val accent by animateColorAsState(
        when {
            connected -> PremiumVpnColors.Emerald
            busy -> PremiumVpnColors.Cyan
            error -> PremiumVpnColors.Red
            else -> PremiumVpnColors.Purple
        },
        animationSpec = tween(350),
        label = "orbAccent",
    )
    val baseA by animateColorAsState(
        when {
            connected -> PremiumVpnColors.Emerald
            busy -> PremiumVpnColors.Cyan
            error -> PremiumVpnColors.Red
            else -> PremiumVpnColors.Blue
        },
        animationSpec = tween(350),
        label = "orbA",
    )
    val baseB by animateColorAsState(
        when {
            connected -> PremiumVpnColors.Cyan
            busy -> PremiumVpnColors.Purple
            error -> Color(0xFFB72D6A)
            else -> PremiumVpnColors.Purple
        },
        animationSpec = tween(350),
        label = "orbB",
    )
    val settledScale by animateFloatAsState(
        targetValue = if (connected) 1.015f else 1f,
        animationSpec = tween(300),
        label = "orbSettle",
    )
    val pulse = if (busy) {
        val infinite = rememberInfiniteTransition(label = "connectPulse")
        val value by infinite.animateFloat(
            initialValue = .97f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulseScale",
        )
        value
    } else {
        1f
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(orbSize)
                .graphicsLayer {
                    scaleX = pulse * settledScale
                    scaleY = pulse * settledScale
                }
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = accent.copy(alpha = .22f),
                    spotColor = accent.copy(alpha = .28f),
                )
                .border(1.5.dp, accent.copy(alpha = .48f), CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(baseA, baseB)))
                .clickable(enabled = enabled, onClick = onPower),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * .23f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                drawCircle(
                    color = Color.White.copy(alpha = .08f),
                    radius = size.minDimension * .43f,
                    style = Stroke(width = 1.dp.toPx()),
                )
                if (busy) {
                    drawCircle(
                        color = Color.White.copy(alpha = .18f),
                        radius = size.minDimension * .38f,
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                }
                drawArc(
                    color = Color.White,
                    startAngle = -44f,
                    sweepAngle = 268f,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 4.6.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(
                    color = Color.White,
                    start = Offset(centerX, centerY - radius * 1.22f),
                    end = Offset(centerX, centerY - radius * .12f),
                    strokeWidth = 4.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Crossfade(targetState = label, animationSpec = tween(220), label = "connectLabel") { text ->
            Text(
                text,
                color = PremiumVpnColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            premiumStatus(state),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun PremiumServerCard(
    modifier: Modifier,
    node: NodeEntity?,
    connected: Boolean,
    state: ConnectionState,
    onClick: () -> Unit,
) {
    val ping = node?.lastLatencyMs?.let { "$LRM$it ms" } ?: "تست نشده"
    val protocol = node?.protocol?.uppercase() ?: "Xray"
    val statusColor = when {
        connected -> PremiumVpnColors.Emerald
        state is ConnectionState.Error -> PremiumVpnColors.Red
        else -> PremiumVpnColors.TextMuted
    }
    val statusLabel = when {
        connected -> "Connected"
        state is ConnectionState.Error -> "Error"
        else -> "Ready"
    }

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = PremiumVpnShapes.Medium,
        color = PremiumVpnColors.Surface,
        border = BorderStroke(1.dp, PremiumVpnColors.Border),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = Color.White.copy(alpha = .96f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(premiumCountryFlag(node?.countryCode), fontSize = 22.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    node?.name ?: "انتخاب سرور",
                    color = PremiumVpnColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$LRM$protocol • $ping",
                    color = PremiumVpnColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }

            Surface(
                shape = PremiumVpnShapes.Pill,
                color = statusColor.copy(alpha = .13f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = .32f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.size(4.dp))
                    Text(statusLabel, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("‹", color = PremiumVpnColors.TextSecondary, fontSize = 20.sp)
        }
    }
}

@Composable
private fun PremiumStatsRow(
    modifier: Modifier,
    publicIp: String?,
    connected: Boolean,
    node: NodeEntity?,
    telemetry: VpnTelemetry,
    compact: Boolean,
) {
    val stats = listOf(
        PremiumStat("IP", publicIp?.let(::premiumCompactIp) ?: if (connected) "…" else "—", "●", PremiumVpnColors.Purple),
        PremiumStat("Ping", node?.lastLatencyMs?.let { "$it" } ?: "—", "ms", PremiumVpnColors.Lime),
        PremiumStat("Speed", premiumMbps(telemetry.downloadBytesPerSecond), "Mbps", PremiumVpnColors.Cyan),
        PremiumStat("Protocol", node?.protocol?.uppercase() ?: "Xray", "◆", PremiumVpnColors.Pink),
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        stats.forEach { stat ->
            PremiumStatCard(
                modifier = Modifier.weight(1f).fillMaxSize(),
                stat = stat,
                compact = compact,
            )
        }
    }
}

private data class PremiumStat(
    val title: String,
    val value: String,
    val unitOrGlyph: String,
    val accent: Color,
)

@Composable
private fun PremiumStatCard(
    modifier: Modifier,
    stat: PremiumStat,
    compact: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 13.dp else 16.dp),
        color = PremiumVpnColors.SurfaceSoft,
        border = BorderStroke(1.dp, PremiumVpnColors.Border),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stat.title,
                color = stat.accent,
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                stat.value,
                color = PremiumVpnColors.TextPrimary,
                fontSize = when {
                    stat.title == "IP" && compact -> 9.sp
                    stat.title == "IP" -> 10.sp
                    compact -> 11.sp
                    else -> 13.sp
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
            Text(
                stat.unitOrGlyph,
                color = PremiumVpnColors.TextMuted,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PremiumQuickActions(
    modifier: Modifier,
    testing: Boolean,
    onImport: () -> Unit,
    onSmart: () -> Unit,
    onSplit: () -> Unit,
    onLocations: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PremiumQuickAction("افزودن", "+", PremiumVpnColors.Purple, true, onImport, Modifier.weight(1f))
        PremiumQuickAction(if (testing) "تست…" else "تست", "⚡", PremiumVpnColors.Lime, !testing, onSmart, Modifier.weight(1f))
        PremiumQuickAction("Split", "↗", PremiumVpnColors.Emerald, true, onSplit, Modifier.weight(1f))
        PremiumQuickAction("Xray", "⬡", PremiumVpnColors.Cyan, true, onLocations, Modifier.weight(1f))
        PremiumQuickAction("Auto", "✦", PremiumVpnColors.Pink, !testing, onSmart, Modifier.weight(1f))
    }
}

@Composable
private fun PremiumQuickAction(
    title: String,
    glyph: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = PremiumVpnColors.SurfaceSoft,
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) .28f else .12f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                glyph,
                color = if (enabled) accent else PremiumVpnColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                color = if (enabled) PremiumVpnColors.TextPrimary else PremiumVpnColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun premiumStatus(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "آماده برای اتصال"
    ConnectionState.Preparing -> "آماده‌سازی تونل"
    ConnectionState.Connecting -> "اتصال به Xray"
    ConnectionState.Verifying -> "تأیید عبور واقعی دیتا"
    is ConnectionState.Connected -> "متصل و تأییدشده"
    ConnectionState.Reconnecting -> "اتصال مجدد"
    is ConnectionState.Error -> "اتصال برقرار نشد"
}

private fun premiumStatusColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> PremiumVpnColors.Emerald
    ConnectionState.Preparing,
    ConnectionState.Connecting,
    ConnectionState.Verifying,
    ConnectionState.Reconnecting -> PremiumVpnColors.Warning
    is ConnectionState.Error -> PremiumVpnColors.Red
    ConnectionState.Disconnected -> PremiumVpnColors.TextMuted
}

private fun premiumCountryFlag(code: String?): String {
    val value = code?.trim()?.uppercase().orEmpty()
    if (value.length != 2 || value.any { it !in 'A'..'Z' }) return "🌐"
    return buildString { value.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
}

private fun premiumCompactIp(value: String): String {
    val ip = value.trim()
    if (ip.length <= 15) return ip
    return if (ip.contains(':')) {
        val first = ip.substringBefore(':').take(4)
        val last = ip.substringAfterLast(':').takeLast(4)
        "$LRM$first…$last"
    } else {
        ip.take(15)
    }
}

private fun premiumMbps(bytesPerSecond: Long): String =
    String.format(Locale.US, "%.1f", bytesPerSecond.coerceAtLeast(0L) * 8.0 / 1_000_000.0)

private suspend fun premiumFetchPublicIp(): String? = withContext(Dispatchers.IO) {
    for (endpoint in listOf("https://api.ipify.org", "https://checkip.amazonaws.com")) {
        val result = runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 3_000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("Connection", "close")
            }
            try {
                if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        if (!result.isNullOrBlank()) return@withContext result
    }
    null
}

private fun premiumDigits(value: String): String = buildString(value.length) {
    value.forEach { ch -> append(if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch) }
}

private fun premiumWeekday(javaDay: Int): String = when (javaDay) {
    1 -> "دوشنبه"
    2 -> "سه‌شنبه"
    3 -> "چهارشنبه"
    4 -> "پنجشنبه"
    5 -> "جمعه"
    6 -> "شنبه"
    else -> "یکشنبه"
}

private fun premiumMonth(month: Int): String = listOf(
    "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
).getOrElse(month) { "" }

private fun premiumGregorianToJalali(gyInput: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
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
