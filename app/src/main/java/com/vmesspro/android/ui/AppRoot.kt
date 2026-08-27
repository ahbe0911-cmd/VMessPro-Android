package com.vmesspro.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF050A14)
private val Panel = Color(0xE60A1324)
private val PanelStrong = Color(0xFF0E1A2D)
private val Border = Color(0xFF1C3653)
private val NeonBlue = Color(0xFF36D9FF)
private val NeonPurple = Color(0xFFA98BFF)
private val Mint = Color(0xFF4EE6B1)
private val TextPrimary = Color(0xFFF5F8FF)
private val TextSecondary = Color(0xFF9EADC5)
private val TextTertiary = Color(0xFF73849F)

private enum class AppTab(val title: String, val icon: ImageVector) {
    Home("خانه", Icons.Rounded.Home),
    Servers("سرورها", Icons.Rounded.Dns),
    Subscriptions("اشتراک‌ها", Icons.Rounded.CloudSync),
    Settings("تنظیمات", Icons.Rounded.Settings),
}

@Composable
fun AppRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Background, Color(0xFF071426), Background),
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
                bottomBar = {
                    PremiumBottomBar(selectedTab) { selectedTab = it }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                ) {
                    AppHeader()
                    when (AppTab.entries[selectedTab]) {
                        AppTab.Home -> HomeScreen()
                        AppTab.Servers -> EmptySection(
                            title = "سرورها",
                            subtitle = "VMess، VLESS و Reality را از این بخش مدیریت کنید.",
                            icon = Icons.Rounded.Dns,
                            actionText = "افزودن کانفیگ",
                        )
                        AppTab.Subscriptions -> EmptySection(
                            title = "اشتراک‌ها",
                            subtitle = "لینک‌های Subscription و به‌روزرسانی سرورها در این بخش قرار می‌گیرند.",
                            icon = Icons.Rounded.CloudSync,
                            actionText = "افزودن اشتراک",
                        )
                        AppTab.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DecorativeGlow() {
    Box(
        modifier = Modifier
            .size(300.dp)
            .background(
                Brush.radialGradient(listOf(Color(0x2436D9FF), Color.Transparent)),
                CircleShape,
            )
    )
}

@Composable
private fun PremiumBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = Color(0xF20A1221),
        tonalElevation = 0.dp,
    ) {
        AppTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onSelect(index) },
                icon = {
                    Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(22.dp))
                },
                label = {
                    Text(
                        tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
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
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xFF10283B),
                border = BorderStroke(1.dp, Color(0x6636D9FF)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("VMess Pro", color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("شبکه امن، سریع و شخصی", color = TextSecondary, fontSize = 10.sp)
            }
        }

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
                Box(Modifier.size(7.dp).background(TextTertiary, CircleShape))
                Text("آماده", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        ConnectionHero()
        Spacer(Modifier.height(14.dp))
        ServerSelector()

        Spacer(Modifier.height(18.dp))
        SectionTitle("دسترسی سریع", "مدیریت اتصال")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickActionCard("کانفیگ", "افزودن دستی", Icons.Rounded.Add, NeonBlue)
            QuickActionCard("اسکن QR", "ورود سریع", Icons.Rounded.QrCodeScanner, Mint)
            QuickActionCard("Split", "انتخاب برنامه", Icons.Rounded.Tune, NeonPurple)
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("وضعیت شبکه", "بعد از اتصال واقعی")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricCard("Ping", "—", "ms", Icons.Rounded.Speed, NeonBlue)
            MetricCard("ترافیک", "—", "MB", Icons.Rounded.CloudSync, Mint)
            MetricCard("سرعت", "—", "Mb/s", Icons.Rounded.Speed, NeonPurple)
        }

        Spacer(Modifier.height(18.dp))
        SecurityCard()
    }
}

@Composable
private fun ConnectionHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Color(0xAA183650)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x1F36D9FF), Color.Transparent, Color(0x18A98BFF))
                    )
                )
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("اتصال امن", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("یک سرور انتخاب کنید", color = TextSecondary, fontSize = 10.sp)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF101D30),
                    border = BorderStroke(1.dp, Border),
                ) {
                    Text("OFFLINE", color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(154.dp).border(1.dp, Color(0x3336D9FF), CircleShape))
                Box(Modifier.size(132.dp).border(1.dp, Color(0x5536D9FF), CircleShape))
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2CCCF4), Color(0xFF7474FF)))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        contentDescription = "اتصال",
                        tint = Color(0xFF03131D),
                        modifier = Modifier.size(46.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("متصل نیست", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("برای اتصال ابتدا سرور را انتخاب کنید", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ServerSelector() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PanelStrong),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF10283B),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Dns, contentDescription = null, tint = NeonBlue)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("سرور فعال", color = TextTertiary, fontSize = 9.sp)
                    Text(
                        "سروری انتخاب نشده",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("VMess / VLESS / Reality", color = TextSecondary, fontSize = 9.sp)
                }
            }
            Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = TextTertiary, fontSize = 9.sp)
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, icon: ImageVector, accent: Color) {
    Card(
        modifier = Modifier.width(108.dp),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0D192B)),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(13.dp), color = accent.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextTertiary, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, icon: ImageVector, accent: Color) {
    Card(
        modifier = Modifier.width(108.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xB80D192B)),
        border = BorderStroke(1.dp, Color(0xFF172A41)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, color = TextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = TextTertiary, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun SecurityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xC80C1828)),
        border = BorderStroke(1.dp, Color(0xFF1B354C)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = Mint.copy(alpha = 0.12f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = Mint)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.width(225.dp)) {
                    Text("حریم خصوصی", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("کلیدها و اطلاعات حساس فقط باید در فضای امن دستگاه نگهداری شوند.", color = TextSecondary, fontSize = 9.sp)
                }
            }
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptySection(title: String, subtitle: String, icon: ImageVector, actionText: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 38.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(modifier = Modifier.size(70.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFF10283B)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("هنوز چیزی اضافه نشده", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(50), color = NeonBlue.copy(alpha = 0.12f)) {
                    Text(actionText, color = NeonBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 20.dp)
    ) {
        Text("تنظیمات", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("کنترل شبکه، امنیت و رفتار اتصال", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))
        SettingRow("Split Tunneling", "انتخاب برنامه‌های داخل یا خارج VPN", Icons.Rounded.Tune, NeonPurple)
        Spacer(Modifier.height(10.dp))
        SettingRow("DNS امن", "تنظیمات DNS و حریم خصوصی", Icons.Rounded.Security, Mint)
        Spacer(Modifier.height(10.dp))
        SettingRow("اتصال خودکار", "اتصال مجدد پس از تغییر شبکه", Icons.Rounded.CloudSync, NeonBlue)
        Spacer(Modifier.height(10.dp))
        SettingRow("تست سرورها", "بررسی کیفیت سرورها با داده واقعی", Icons.Rounded.Speed, Mint)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: ImageVector, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PanelStrong),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.width(235.dp)) {
                    Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = TextTertiary, fontSize = 9.sp)
                }
            }
            Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = null, tint = TextTertiary)
        }
    }
}
