package com.vmesspro.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vpnSettingsDataStore by preferencesDataStore(name = "vpn_settings")

enum class SplitTunnelMode {
    ONLY_SELECTED,
    EXCLUDE_SELECTED,
}

data class VpnPreferences(
    val selectedNodeId: String? = null,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.EXCLUDE_SELECTED,
    val includedPackages: Set<String> = emptySet(),
    val excludedPackages: Set<String> = emptySet(),
    val bankingPackages: Set<String> = emptySet(),
    val autoReconnect: Boolean = true,
    val customDns: String? = null,
    val themeMode: String = "dark",
)

class VpnPreferencesRepository(private val context: Context) {
    private object Keys {
        val selectedNodeId = stringPreferencesKey("selected_node_id")
        val splitMode = stringPreferencesKey("split_mode")
        val includedPackages = stringSetPreferencesKey("included_packages")
        val excludedPackages = stringSetPreferencesKey("excluded_packages")
        val bankingPackages = stringSetPreferencesKey("banking_packages")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val customDns = stringPreferencesKey("custom_dns")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    val preferences: Flow<VpnPreferences> = context.vpnSettingsDataStore.data.map(::mapPreferences)

    suspend fun setSelectedNode(id: String?) {
        context.vpnSettingsDataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(Keys.selectedNodeId) else prefs[Keys.selectedNodeId] = id
        }
    }

    suspend fun setSplitTunnel(
        mode: SplitTunnelMode,
        includedPackages: Set<String>,
        excludedPackages: Set<String>,
        bankingPackages: Set<String>,
    ) {
        context.vpnSettingsDataStore.edit { prefs ->
            prefs[Keys.splitMode] = mode.name
            prefs[Keys.includedPackages] = includedPackages
            prefs[Keys.excludedPackages] = excludedPackages
            prefs[Keys.bankingPackages] = bankingPackages
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { it[Keys.autoReconnect] = enabled }
    }

    suspend fun setCustomDns(value: String?) {
        context.vpnSettingsDataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(Keys.customDns) else prefs[Keys.customDns] = value.trim()
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.vpnSettingsDataStore.edit { it[Keys.themeMode] = mode }
    }

    private fun mapPreferences(prefs: Preferences): VpnPreferences {
        val mode = runCatching {
            SplitTunnelMode.valueOf(prefs[Keys.splitMode] ?: SplitTunnelMode.EXCLUDE_SELECTED.name)
        }.getOrDefault(SplitTunnelMode.EXCLUDE_SELECTED)

        return VpnPreferences(
            selectedNodeId = prefs[Keys.selectedNodeId],
            splitTunnelMode = mode,
            includedPackages = prefs[Keys.includedPackages].orEmpty(),
            excludedPackages = prefs[Keys.excludedPackages].orEmpty(),
            bankingPackages = prefs[Keys.bankingPackages].orEmpty(),
            autoReconnect = prefs[Keys.autoReconnect] ?: true,
            customDns = prefs[Keys.customDns],
            themeMode = prefs[Keys.themeMode] ?: "dark",
        )
    }
}
