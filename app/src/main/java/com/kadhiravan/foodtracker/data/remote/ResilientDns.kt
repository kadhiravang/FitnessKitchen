package com.kadhiravan.foodtracker.data.remote

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Falls back to DNS-over-HTTPS resolvers (Cloudflare, then Google) when the system
 * resolver fails or hangs, covers both a brief handoff-window DNS hiccup and longer,
 * more stubborn resolver outages that would otherwise hang for minutes with no bound,
 * since [Dns.lookup] is a blocking call with no timeout of its own. Each layer gets a
 * short, hard timeout so a bad resolver fails fast instead of stalling the whole request.
 * Shared by every chat API client, the failure mode isn't provider-specific.
 */
internal class ResilientDns(bootstrapClient: OkHttpClient) : Dns {
    private val cloudflare = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://1.1.1.1/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1))
        )
        .build()

    private val google = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://8.8.8.8/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)),
            InetAddress.getByAddress(byteArrayOf(8, 8, 4, 4))
        )
        .build()

    private val executor = Executors.newCachedThreadPool()

    override fun lookup(hostname: String): List<InetAddress> {
        tryResolve("system", hostname) { Dns.SYSTEM.lookup(hostname) }?.let { return it }
        tryResolve("cloudflare-doh", hostname) { cloudflare.lookup(hostname) }?.let { return it }
        tryResolve("google-doh", hostname) { google.lookup(hostname) }?.let { return it }
        throw UnknownHostException(hostname)
    }

    private fun tryResolve(label: String, hostname: String, resolve: () -> List<InetAddress>): List<InetAddress>? {
        return try {
            executor.submit(Callable { resolve() }).get(4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.d("ResilientDns", "$label failed for $hostname: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
