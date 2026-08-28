package com.vmesspro.android.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

    val activity = LocalActivity.current
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
            else -> 9.dp
        }
        val headerHeight = if (short) 52.dp else 62.dp
        val serverHeight = if (short) 66.dp else 78.dp
        val statsHeight = if (short) 68.dp else 84.dp
        val actionsHeight = if (short) 58.dp else 74.dp
        val orbMax = when {
            heightCompact -> 130.dp
            short -> 154.dp
            else -> 184.dp
        }
        val orbMin = if (heightCompact) 116.dp else 132.dp
        val orbSize = minOf(maxWidth * 0.47f, maxHeight * 0.29f).coerceIn(orbMin, orbMax)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = if (short) 3.dp else 6.dp),
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
                    fontSize = 23.sp,
                )
                Spacer(Modifier.size(9.dp))
                Surface(
                    modifier = Modifier.shadow(
                        elevation = 7.dp,
                        shape = CircleShape,
                        ambientColor = PremiumVpnColors.Purple.copy(alpha = .28f),
                        spotColor = PremiumVpnColors.Purple.copy(alpha = .34f),
                    ),
                    shape = CircleShape,
                    color = PremiumVpnColors.Purple.copy(alpha = .20f),
                    border = BorderStroke(1.dp, PremiumVpnColors.Purple.copy(alpha = .75f)),
                ) {
                    Text(
                        "PRO",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        color = PremiumVpnColors.Purple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date,
                    color = PremiumVpnColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.size(7.dp))
                Text("•", color = statusColor, fontSize = 11.sp)
                Spacer(Modifier.size(7.dp))
                Text(
                    time,
                    color = PremiumVpnColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(45.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(15.dp),
                    ambientColor = PremiumVpnColors.Cyan.copy(alpha = .12f),
                    spotColor = PremiumVpnColors.Cyan.copy(alpha = .16f),
                ),
            shape = RoundedCornerShape(15.dp),
            color = PremiumVpnColors.SurfaceSoft,
            border = BorderStroke(1.dp, PremiumVpnColors.Border),
            onClick = onSettings,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("⚙", color = PremiumVpnColors.TextPrimary, fontSize = 20.sp)
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
        val innerOrbSize = (orbSize - 23.dp).coerceAtLeast(96.dp)
        Box(
            modifier = Modifier
                .size(orbSize)
                .graphicsLayer {
                    scaleX = pulse * settledScale
                    scaleY = pulse * settledScale
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val outerRadius = size.minDimension / 2f
                drawCircle(
                    color = accent.copy(alpha = .30f),
                    radius = outerRadius - 1.dp.toPx(),
                    style = Stroke(width = 1.1.dp.toPx()),
                )
                drawCircle(
                    color = accent.copy(alpha = .16f),
                    radius = outerRadius * .88f,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = .20f), Color.Transparent),
                        center = center,
                        radius = outerRadius,
                    ),
                    radius = outerRadius,
                )
            }

            Box(
                modifier = Modifier
                    .size(innerOrbSize)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = accent.copy(alpha = .34f),
                        spotColor = accent.copy(alpha = .40f),
                    )
                    .border(1.2.dp, accent.copy(alpha = .70f), CircleShape)
                    .padding(5.dp)
                    .border(2.dp, Color.White.copy(alpha = .86f), CircleShape)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(baseA, baseB)))
                    .clickable(enabled = enabled, onClick = onPower),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension * .22f
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    drawCircle(
                        color = Color.White.copy(alpha = .07f),
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
        }

        Spacer(Modifier.height(8.dp))
        Crossfade(targetState = label, animationSpec = tween(220), label = "connectLabel") { text ->
            Text(
                text,
                color = PremiumVpnColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (orbSize < 140.dp) 19.sp else 23.sp,
            )
        }
        Text(
            premiumStatus(state),
            color = accent,
            fontSize = if (orbSize < 140.dp) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
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
        shape = RoundedCornerShape(25.dp),
        color = PremiumVpnColors.Surface,
        border = BorderStroke(1.dp, PremiumVpnColors.Border.copy(alpha = .85f)),
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            PremiumVpnColors.SurfaceStrong.copy(alpha = .78f),
                            PremiumVpnColors.Blue.copy(alpha = .10f),
                            PremiumVpnColors.Surface.copy(alpha = .94f),
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = PremiumVpnColors.SurfaceSoft,
                    border = BorderStroke(1.dp, PremiumVpnColors.Border),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("‹", color = PremiumVpnColors.TextPrimary, fontSize = 20.sp)
                    }
                }
                Surface(
                    shape = PremiumVpnShapes.Pill,
                    color = statusColor.copy(alpha = .13f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = .38f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusLabel, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(5.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
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
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$LRM$protocol  •  $ping",
                        color = PremiumVpnColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }

                PremiumGlobeIcon(
                    modifier = Modifier.size(50.dp),
                    countryCode = node?.countryCode,
                )
            }
        }
    }
}

@Composable
private fun PremiumGlobeIcon(
    modifier: Modifier,
    countryCode: String?,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = PremiumVpnColors.Cyan.copy(alpha = .24f),
                spotColor = PremiumVpnColors.Blue.copy(alpha = .30f),
            )
            .clip(CircleShape)
            .background(PremiumVpnColors.Blue.copy(alpha = .10f))
            .border(1.dp, PremiumVpnColors.Blue.copy(alpha = .36f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val stroke = 2.2.dp.toPx()
            drawCircle(
                color = PremiumVpnColors.Cyan,
                radius = size.minDimension / 2f - stroke,
                style = Stroke(width = stroke),
            )
            drawOval(
                color = PremiumVpnColors.Cyan,
                topLeft = Offset(size.width * .27f, stroke),
                size = Size(size.width * .46f, size.height - stroke * 2f),
                style = Stroke(width = stroke),
            )
            drawLine(
                color = PremiumVpnColors.Cyan,
                start = Offset(stroke, size.height / 2f),
                end = Offset(size.width - stroke, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = PremiumVpnColors.Cyan,
                startAngle = 18f,
                sweepAngle = 144f,
                useCenter = false,
                topLeft = Offset(stroke, size.height * .17f),
                size = Size(size.width - stroke * 2f, size.height * .66f),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = PremiumVpnColors.Cyan,
                startAngle = 198f,
                sweepAngle = 144f,
                useCenter = false,
                topLeft = Offset(stroke, size.height * .17f),
                size = Size(size.width - stroke * 2f, size.height * .66f),
                style = Stroke(width = stroke),
            )
        }
        val flag = premiumCountryFlag(countryCode)
        if (flag != "🌐") {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(17.dp),
                shape = CircleShape,
                color = PremiumVpnColors.NavSurface,
                border = BorderStroke(1.dp, PremiumVpnColors.Border),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(flag, fontSize = 9.sp)
                }
            }
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
        PremiumStat("آی پی", publicIp?.let(::premiumCompactIp) ?: if (connected) "…" else "—", "⌖", PremiumVpnColors.Purple),
        PremiumStat("پینگ", node?.lastLatencyMs?.let { "$it" } ?: "—", "⌁", PremiumVpnColors.Lime),
        PremiumStat("سرعت", premiumMbps(telemetry.downloadBytesPerSecond), "◔", PremiumVpnColors.Cyan),
        PremiumStat("پروتکل", node?.protocol?.uppercase() ?: "Xray", "◈", PremiumVpnColors.Pink),
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 19.dp else 24.dp),
        color = PremiumVpnColors.SurfaceSoft,
        border = BorderStroke(1.dp, PremiumVpnColors.Border.copy(alpha = .80f)),
        shadowElevation = 4.dp,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            stats.forEachIndexed { index, stat ->
                PremiumStatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    stat = stat,
                    compact = compact,
                )
                if (index < stats.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                            .width(1.dp)
                            .background(PremiumVpnColors.Border.copy(alpha = .65f))
                    )
                }
            }
        }
    }
}

private data class PremiumStat(
    val title: String,
    val value: String,
    val glyph: String,
    val accent: Color,
)

@Composable
private fun PremiumStatCard(
    modifier: Modifier,
    stat: PremiumStat,
    compact: Boolean,
) {
    Column(
        modifier = modifier.padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stat.glyph,
            color = stat.accent,
            fontSize = if (compact) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            stat.title,
            color = stat.accent,
            fontSize = if (compact) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            stat.value,
            color = PremiumVpnColors.TextPrimary,
            fontSize = when {
                stat.title == "آی پی" && compact -> 8.sp
                stat.title == "آی پی" -> 10.sp
                compact -> 12.sp
                else -> 15.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
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
            .shadow(
                elevation = if (enabled) 5.dp else 1.dp,
                shape = RoundedCornerShape(19.dp),
                ambientColor = accent.copy(alpha = .18f),
                spotColor = accent.copy(alpha = .22f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = PremiumVpnColors.SurfaceSoft.copy(alpha = .92f),
        border = BorderStroke(1.2.dp, accent.copy(alpha = if (enabled) .68f else .15f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                glyph,
                color = if (enabled) accent else PremiumVpnColors.TextMuted,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                color = if (enabled) PremiumVpnColors.TextPrimary else PremiumVpnColors.TextMuted,
                fontSize = 10.sp,
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
