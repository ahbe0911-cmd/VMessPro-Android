package com.vmesspro.android.core

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.vpn.protocol.xray.libXray.LibXray

/**
 * Tests the real Xray protocol handshake and an HTTP request through the profile.
 * This is intentionally not a TCP port probe.
 */
internal class XrayProfileTester(context: Context) {
    private val appContext = context.applicationContext

    suspend fun test(rawConfig: String, endpointHost: String): ProbeResult = withContext(Dispatchers.IO) {
        val prepared = runCatching {
            XrayConfigFactory.prepare(
                context = appContext,
                rawConfig = rawConfig,
                endpointHost = endpointHost,
                filePrefix = "probe",
            )
        }.getOrElse { error ->
            return@withContext ProbeResult(
                tcpLatencyMs = null,
                httpRttMs = null,
                success = false,
                error = error.message ?: "تبدیل کانفیگ Xray ناموفق بود",
            )
        }

        try {
            val (_, geoDir) = XrayConfigFactory.initializeXrayAssets(appContext)
            val rawResult = LibXray.ping(
                geoDir,
                prepared.configFile.absolutePath,
                PROFILE_TEST_TIMEOUT_SECONDS,
                PROFILE_TEST_URL,
                prepared.socksProxyUrl,
            ).orEmpty()

            val separator = rawResult.indexOf(':')
            val delayText = if (separator >= 0) rawResult.substring(0, separator) else rawResult
            val detail = if (separator >= 0) rawResult.substring(separator + 1).trim() else ""
            val delayMs = delayText.toLongOrNull()

            if (delayMs != null && delayMs in 1 until XRAY_ERROR_DELAY_FLOOR) {
                ProbeResult(
                    tcpLatencyMs = delayMs,
                    httpRttMs = delayMs,
                    success = true,
                    error = null,
                )
            } else {
                ProbeResult(
                    tcpLatencyMs = null,
                    httpRttMs = null,
                    success = false,
                    error = detail.ifBlank { "Xray نتوانست ترافیک HTTP واقعی عبور دهد" },
                )
            }
        } catch (error: Throwable) {
            ProbeResult(
                tcpLatencyMs = null,
                httpRttMs = null,
                success = false,
                error = error.message ?: "تست Xray ناموفق بود",
            )
        } finally {
            runCatching { File(prepared.configFile.absolutePath).delete() }
        }
    }

    private companion object {
        const val PROFILE_TEST_TIMEOUT_SECONDS = 7L
        const val XRAY_ERROR_DELAY_FLOOR = 10_000L
        const val PROFILE_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}
