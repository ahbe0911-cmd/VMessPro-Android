package com.vmesspro.android.ui.runtime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmesspro.android.core.ConnectionState
import com.vmesspro.android.ui.AppViewModel
import java.util.Locale

/**
 * Compact real-time operations panel.
 *
 * Values shown here come from sing-box/libbox CommandStatus; no synthetic throughput is used.
 * Bulk probes use the app's bounded-concurrency TCP probe implementation and persist results.
 */
@Composable
fun RuntimeStatusOverlay(viewModel: AppViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val testingAll by viewModel.testingAllNodes.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        SmallFloatingActionButton(
            onClick = { open = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 82.dp),
            containerColor = Color(0xFF10253A),
            contentColor = Color(0xFF48DFFF),
        ) {
            Icon(Icons.Rounded.Speed, contentDescription = "آمار زنده و تست سرورها")
        }
    }

    if (!open) return

    AlertDialog(
        onDismissRequest = { open = false },
        icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
        title = { Text("وضعیت زنده VPN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                selectedNode?.let { node ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0C1B2C),
                        border = BorderStroke(1.dp, Color(0xFF21425E)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                node.name,
                                color = Color(0xFFF5F8FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${node.protocol} • ${node.host}:${node.port}",
                                color = Color(0xFF9EB1C7),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveMetric(
                        modifier = Modifier.weight(1f),
                        title = "دانلود",
                        value = if (telemetry.trafficAvailable) {
                            "${formatBytes(telemetry.downloadBytesPerSecond)}/s"
                        } else "—",
                        accent = Color(0xFF4DE7B0),
                    )
                    LiveMetric(
                        modifier = Modifier.weight(1f),
                        title = "آپلود",
                        value = if (telemetry.trafficAvailable) {
                            "${formatBytes(telemetry.uploadBytesPerSecond)}/s"
                        } else "—",
                        accent = Color(0xFF39DAFF),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiveMetric(
                        modifier = Modifier.weight(1f),
                        title = "دریافت کل",
                        value = formatBytes(telemetry.downloadedBytesTotal),
                        accent = Color(0xFF4DE7B0),
                    )
                    LiveMetric(
                        modifier = Modifier.weight(1f),
                        title = "ارسال کل",
                        value = formatBytes(telemetry.uploadedBytesTotal),
                        accent = Color(0xFFA98CFF),
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF081625),
                    border = BorderStroke(1.dp, Color(0xFF1B3852)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x1439DAFF), Color.Transparent, Color(0x14A98CFF))
                                )
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("اتصال‌های Core", color = Color(0xFF93A8C0), fontSize = 9.sp)
                            Text(
                                "ورودی ${telemetry.activeConnectionsIn} • خروجی ${telemetry.activeConnectionsOut}",
                                color = Color(0xFFF5F8FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            connectionLabel(state),
                            color = connectionColor(state),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                Text(
                    "تست گروهی با حداکثر ۸ اتصال هم‌زمان انجام می‌شود؛ نتیجه واقعی هر Node در Room ذخیره می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = viewModel::testAllNodes,
                        enabled = nodes.isNotEmpty() && !testingAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (testingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("تست همه")
                    }
                    Button(
                        onClick = viewModel::testAllAndSelectBest,
                        enabled = nodes.isNotEmpty() && !testingAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("بهترین سرور")
                    }
                }

                Text(
                    "${nodes.size} سرور • آمار ترافیک مستقیماً از CommandStatus هسته sing-box دریافت می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { open = false }) { Text("بستن") }
        },
    )
}

@Composable
private fun LiveMetric(
    modifier: Modifier,
    title: String,
    value: String,
    accent: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = Color(0xFF0A1828),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(title, color = Color(0xFF8FA4BC), fontSize = 8.sp)
            Text(
                value,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "متصل"
    ConnectionState.Preparing -> "آماده‌سازی"
    ConnectionState.Connecting -> "در حال اتصال"
    ConnectionState.Verifying -> "تأیید مسیر"
    ConnectionState.Reconnecting -> "Failover"
    is ConnectionState.Error -> "خطا"
    ConnectionState.Disconnected -> "قطع"
}

private fun connectionColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> Color(0xFF4DE7B0)
    ConnectionState.Reconnecting,
    ConnectionState.Preparing,
    ConnectionState.Connecting,
    ConnectionState.Verifying -> Color(0xFFFFC86B)
    is ConnectionState.Error -> Color(0xFFFF728A)
    ConnectionState.Disconnected -> Color(0xFF8FA4BC)
}

private fun formatBytes(value: Long): String {
    val safe = value.coerceAtLeast(0L).toDouble()
    if (safe < 1024.0) return "${safe.toLong()} B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var amount = safe / 1024.0
    var index = 0
    while (amount >= 1024.0 && index < units.lastIndex) {
        amount /= 1024.0
        index++
    }
    return if (amount >= 100.0) {
        String.format(Locale.US, "%.0f %s", amount, units[index])
    } else {
        String.format(Locale.US, "%.1f %s", amount, units[index])
    }
}
