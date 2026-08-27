package com.vmesspro.android.core

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal object AndroidLocalDnsTransport : LocalDNSTransport {
    private const val RCODE_NXDOMAIN = 3
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "vmesspro-dns").apply { isDaemon = true }
    }

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Raw DNS requires Android 10+" }
        val network = PhysicalNetworkMonitor.currentNetwork ?: error("missing physical network")
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val signal = CancellationSignal()

        DnsResolver.getInstance().rawQuery(
            network,
            message,
            DnsResolver.FLAG_NO_RETRY,
            executor,
            signal,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                    latch.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    failure.set(error)
                    latch.countDown()
                }
            },
        )

        if (!latch.await(12, TimeUnit.SECONDS)) {
            signal.cancel()
            throw IOException("DNS query timed out")
        }
        failure.get()?.let { throw it }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        val active = PhysicalNetworkMonitor.currentNetwork ?: error("missing physical network")
        val addresses = try {
            active.getAllByName(domain)
        } catch (_: UnknownHostException) {
            ctx.errorCode(RCODE_NXDOMAIN)
            return
        }

        val filtered = when {
            network.endsWith("4") -> addresses.filterIsInstance<Inet4Address>()
            network.endsWith("6") -> addresses.filterIsInstance<Inet6Address>()
            else -> addresses.toList()
        }
        if (filtered.isEmpty()) {
            ctx.errorCode(RCODE_NXDOMAIN)
        } else {
            ctx.success(filtered.mapNotNull { it.hostAddress }.joinToString("\n"))
        }
    }
}
