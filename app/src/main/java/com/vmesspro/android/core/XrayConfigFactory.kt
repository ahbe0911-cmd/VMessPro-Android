package com.vmesspro.android.core

import android.content.Context
import go.Seq
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import org.amnezia.vpn.protocol.xray.libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

internal data class PreparedXrayConfig(
    val configFile: File,
    val socksPort: Int,
    val socksUser: String,
    val socksPass: String,
    val endpointAddress: InetAddress,
) {
    val socksProxyUrl: String
        get() = "socks5://$socksUser:$socksPass@127.0.0.1:$socksPort"
}

/**
 * Converts the original VMess/VLESS/Trojan share link directly with Amnezia libXray.
 * We intentionally do not translate an Xray profile into sing-box JSON here: preserving
 * Xray/Reality semantics is the reason this engine exists.
 */
internal object XrayConfigFactory {
    fun prepare(
        context: Context,
        rawConfig: String,
        endpointHost: String,
        filePrefix: String,
    ): PreparedXrayConfig {
        require(rawConfig.isNotBlank()) { "کانفیگ خالی است" }
        Seq.setContext(context.applicationContext)

        val workDir = File(context.cacheDir, "xray").apply { mkdirs() }
        val token = "${filePrefix}-${System.nanoTime()}"
        val shareFile = File(workDir, "$token.share")
        val convertedFile = File(workDir, "$token.converted.json")
        val runnableFile = File(workDir, "$token.config.json")

        shareFile.writeText(rawConfig.trim())
        val conversionError = LibXray.convertShareTextToXrayJson(
            shareFile.absolutePath,
            convertedFile.absolutePath,
        )
        check(conversionError.isNullOrBlank()) {
            "Xray نتوانست کانفیگ را تبدیل کند: $conversionError"
        }
        check(convertedFile.isFile && convertedFile.length() > 0L) {
            "خروجی تبدیل Xray ساخته نشد"
        }

        val convertedRoot = JSONObject(convertedFile.readText())
        val convertedOutbounds = convertedRoot.optJSONArray("outbounds")
            ?: error("کانفیگ Xray هیچ outbound ندارد")
        check(convertedOutbounds.length() > 0) { "کانفیگ Xray هیچ سرور قابل استفاده ندارد" }

        // One database node represents one share link, so only the first converted outbound is used.
        val proxyOutbound = JSONObject(convertedOutbounds.getJSONObject(0).toString()).apply {
            remove("name") // helper field used by libXray subscription conversion, not Xray core config
            put("tag", "proxy")
        }

        val endpointAddress = resolveEndpoint(endpointHost)
        rewriteEndpointAddress(proxyOutbound, endpointAddress.hostAddress ?: endpointHost)

        val socksPort = acquireFreeLocalPort()
        val socksUser = randomToken(16)
        val socksPass = randomToken(28)

        val socksInbound = JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "password")
                    .put("udp", true)
                    .put(
                        "accounts",
                        JSONArray().put(
                            JSONObject()
                                .put("user", socksUser)
                                .put("pass", socksPass)
                        )
                    )
            )

        val directOutbound = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject())

        val blockOutbound = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
            .put("settings", JSONObject())

        val runnableRoot = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(socksInbound))
            .put("outbounds", JSONArray().put(proxyOutbound).put(directOutbound).put(blockOutbound))
            .put(
                "routing",
                JSONObject()
                    .put("domainStrategy", "AsIs")
                    .put("rules", JSONArray())
            )

        runnableFile.writeText(runnableRoot.toString())

        runCatching { shareFile.delete() }
        runCatching { convertedFile.delete() }

        return PreparedXrayConfig(
            configFile = runnableFile,
            socksPort = socksPort,
            socksUser = socksUser,
            socksPass = socksPass,
            endpointAddress = endpointAddress,
        )
    }

    fun initializeXrayAssets(context: Context): Pair<String, String> {
        Seq.setContext(context.applicationContext)
        val assetsPath = context.getDir("assets", Context.MODE_PRIVATE).absolutePath
        LibXray.initXray(assetsPath)
        val geoDir = File(assetsPath, "geo").absolutePath
        return assetsPath to geoDir
    }

    private fun resolveEndpoint(host: String): InetAddress {
        return InetAddress.getAllByName(host.trim()).firstOrNull()
            ?: error("آدرس سرور قابل Resolve نیست: $host")
    }

    private fun rewriteEndpointAddress(outbound: JSONObject, resolvedAddress: String) {
        val protocol = outbound.optString("protocol").lowercase()
        val settings = outbound.optJSONObject("settings") ?: return
        when (protocol) {
            "vmess", "vless" -> {
                val vnext = settings.optJSONArray("vnext") ?: return
                if (vnext.length() > 0) vnext.getJSONObject(0).put("address", resolvedAddress)
            }

            "trojan", "shadowsocks", "socks" -> {
                val servers = settings.optJSONArray("servers") ?: return
                if (servers.length() > 0) servers.getJSONObject(0).put("address", resolvedAddress)
            }
        }
    }

    private fun acquireFreeLocalPort(): Int {
        return ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }

    private fun randomToken(length: Int): String = UUID.randomUUID()
        .toString()
        .replace("-", "")
        .let { base -> if (base.length >= length) base.take(length) else base.padEnd(length, 'x') }
}
