package com.vmesspro.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppRoot() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF050914), Color(0xFF071427), Color(0xFF050914))
                )
            )
            .padding(horizontal = 20.dp, vertical = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("VMess Pro", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "کلاینت شبکه امن",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xB20B1324)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(80.dp))
                            .padding(34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = "اتصال",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("قطع", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "هیچ اتصال VPN فعالی وجود ندارد",
                        color = Color(0xFF9EACC5),
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard("سرور", "انتخاب نشده", Modifier.weight(1f))
                StatusCard("Ping", "—", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "وضعیت اتصال فقط از VPN Service و Core واقعی خوانده خواهد شد؛ داده نمایشی استفاده نمی‌شود.",
                color = Color(0xFF7E8CA6),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x99111D33)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF8FA0BC), fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}
