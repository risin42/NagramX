package tw.nekomimi.nekogram.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.telegram.messenger.FileLog
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.Cache
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.SetResponse
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type
import tw.nekomimi.nekogram.NekoConfig
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

object DnsFactory {

    private const val USE_IPV4_ONLY: Byte = 0
    private const val USE_IPV6_ONLY: Byte = 1
    private const val USE_IPV4_IPV6_RANDOM: Byte = 2

    private val httpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    @Volatile
    private var ipStrategy: Byte = -1

    @Volatile
    private var hasIPv4: Boolean = false

    @Volatile
    private var hasStrangeIPv4: Boolean = false

    @Volatile
    private var hasIPv6: Boolean = false

    private var forceIPv6 = ConnectionsManager.getInstance(UserConfig.selectedAccount).forceTryIPv6

    private val cache = Cache()

    private val DEFAULT_DOH_PROVIDERS = arrayOf(
        "https://1.1.1.1/dns-query",
        "https://1.0.0.1/dns-query",
        "https://8.8.8.8/dns-query",
        "https://8.8.4.4/dns-query",
        "https://[2606:4700:4700::1001]/dns-query", // Cloudflare IPv6
        "https://[2001:4860:4860::8844]/dns-query", // Google IPv6
    )

    private fun providers(): Array<String> {
        if (NekoConfig.dnsType.Int() != NekoConfig.DNS_TYPE_CUSTOM_DOH) {
            return DEFAULT_DOH_PROVIDERS
        }

        val validCustomProviders = NekoConfig.customDoH.String()
            .split(",")
            .map { it.trim() }
            .filter { url ->
                url.toHttpUrlOrNull()?.isHttps == true
            }

        return if (validCustomProviders.isNotEmpty()) {
            validCustomProviders.toTypedArray()
        } else {
            DEFAULT_DOH_PROVIDERS
        }
    }

    class CustomException(message: String) : Exception(message)

    @JvmStatic
    @JvmOverloads
    fun lookup(domain: String, fallback: Boolean = false): List<InetAddress> {
        if (NekoConfig.dnsType.Int() != NekoConfig.DNS_TYPE_SYSTEM) {
            FileLog.d("Lookup for '$domain' requested (fallback=$fallback)")

            val type = getDnsQueryType(fallback)
            val dc = DClass.IN
            val name = Name(domain, Name.root)

            val sr = cache.lookupRecords(name, type, dc)
            val cachedResult = srToAddresses(sr, domain, isCache = true, fallback = fallback)
            if (cachedResult != null) {
                FileLog.d("Cache hit for '$domain'. Returning cached result: ${cachedResult.map { it.hostAddress }}")
                return cachedResult
            }
            FileLog.d("Cache miss for '$domain' (type: ${Type.string(type)}). Performing DoH query.")

            val message = Message.newQuery(Record.newRecord(name, type, dc)).toWire()
            val usedProviders = providers()
            FileLog.d("Querying ${usedProviders.size} DoH providers: ${usedProviders.joinToString()}")

            val ret: List<InetAddress>? = runBlocking {
                val result = withTimeoutOrNull(5000L) {
                    suspendCancellableCoroutine { continuation ->
                        val callTag = "dns-lookup-$domain-${System.nanoTime()}"
                        val counterAll = AtomicInteger(0)
                        val counterGood = AtomicInteger(0)

                        continuation.invokeOnCancellation {
                            FileLog.d("['${domain}'] Coroutine cancelled, cleaning up OkHttp calls.")
                            for (call in httpClient.dispatcher.runningCalls()) {
                                if (call.request().tag() == callTag) call.cancel()
                            }
                            for (call in httpClient.dispatcher.queuedCalls()) {
                                if (call.request().tag() == callTag) call.cancel()
                            }
                        }

                        for (provider in usedProviders) {
                            launch(Dispatchers.IO) {
                                if (!continuation.isActive) return@launch

                                try {
                                    FileLog.d("['${domain}'] Querying provider: $provider")
                                    val request = Request.Builder().url(provider).tag(callTag)
                                        .header("accept", "application/dns-message")
                                        .post(message.toRequestBody("application/dns-message".toMediaTypeOrNull()))
                                        .build()
                                    val response = httpClient.newCall(request).execute()
                                    if (!response.isSuccessful) {
                                        throw CustomException("HTTP ${response.code} from $provider")
                                    }

                                    val responseBody = response.body.bytes()
                                    val resultMessage = Message(responseBody)
                                    val rcode = resultMessage.header.rcode
                                    if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN && rcode != Rcode.NXRRSET) {
                                        throw CustomException("DNS error from $provider: ${Rcode.string(rcode)}")
                                    }

                                    val addresses = srToAddresses(
                                        cache.addMessage(resultMessage),
                                        domain,
                                        isCache = false,
                                        fallback = fallback
                                    )

                                    if (addresses != null && continuation.isActive && counterGood.incrementAndGet() == 1) {
                                        FileLog.d("['${domain}'] First successful response from $provider. Result: ${addresses.map { it.hostAddress }}")
                                        continuation.resume(addresses)
                                    } else if (addresses == null) {
                                        FileLog.d("['${domain}'] Provider $provider returned a non-conclusive answer (e.g., CNAME, NXRRSET).")
                                    }
                                } catch (e: Exception) {
                                    if (continuation.isActive) {
                                        FileLog.e("['${domain}'] DoH query to $provider failed: ${e.message ?: e.javaClass.simpleName}")
                                    }
                                } finally {
                                    if (counterAll.incrementAndGet() == usedProviders.size && counterGood.get() == 0) {
                                        if (continuation.isActive) {
                                            continuation.resume(null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (result == null) {
                    FileLog.d("['${domain}'] DoH query timed out after 5 seconds.")
                }
                result
            }

            if (ret != null) {
                FileLog.d("DoH lookup for '$domain' succeeded.")
                return ret
            } else {
                FileLog.w("All DoH providers failed or timed out for '$domain'.")
            }
        }

        FileLog.d("Using system DNS for '$domain'.")
        try {
            val addresses = InetAddress.getAllByName(domain).toList()
            FileLog.d("System DNS resolved '$domain' to: ${addresses.map { it.hostAddress }}")
            return addresses
        } catch (e: Exception) {
            FileLog.e("System DNS lookup for '$domain' failed: ${e.message ?: e.javaClass.simpleName}")
        }

        FileLog.e("All DNS resolution methods failed for '$domain'.")
        return listOf()
    }

    @JvmStatic
    fun getTxtRecords(domain: String): List<String> {
        FileLog.d("TXT lookup for '$domain' requested")

        val type = Type.TXT
        val dc = DClass.IN
        val name = Name(domain, Name.root)

        fun srToTxtRecords(sr: SetResponse?): List<String>? {
            if (sr != null && sr.isSuccessful) {
                return sr.answers()
                    .flatMap { it.rrs(true) }
                    .filterIsInstance<TXTRecord>()
                    .flatMap { it.strings }
            }
            return null
        }

        val sr = cache.lookupRecords(name, type, dc)
        val cachedResult = srToTxtRecords(sr)
        if (cachedResult != null) {
            FileLog.d("Cache hit for '$domain' (TXT). Returning cached result: $cachedResult")
            return cachedResult
        }
        FileLog.d("Cache miss for '$domain' (TXT). Performing DoH query.")

        val message = Message.newQuery(Record.newRecord(name, type, dc)).toWire()
        val usedProviders = providers()
        FileLog.d("Querying ${usedProviders.size} DoH providers for TXT records: ${usedProviders.joinToString()}")

        val ret: List<String>? = runBlocking {
            val result = withTimeoutOrNull(5000L) {
                suspendCancellableCoroutine { continuation ->
                    val callTag = "dns-txt-lookup-$domain-${System.nanoTime()}"
                    val counterAll = AtomicInteger(0)
                    val counterGood = AtomicInteger(0)

                    continuation.invokeOnCancellation {
                        FileLog.d("['${domain}'] TXT Coroutine cancelled, cleaning up OkHttp calls.")
                        for (call in httpClient.dispatcher.runningCalls()) {
                            if (call.request().tag() == callTag) call.cancel()
                        }
                        for (call in httpClient.dispatcher.queuedCalls()) {
                            if (call.request().tag() == callTag) call.cancel()
                        }
                    }

                    for (provider in usedProviders) {
                        launch(Dispatchers.IO) {
                            if (!continuation.isActive) return@launch

                            try {
                                FileLog.d("['${domain}'] Querying provider for TXT: $provider")
                                val request = Request.Builder().url(provider).tag(callTag)
                                    .header("accept", "application/dns-message")
                                    .post(message.toRequestBody("application/dns-message".toMediaTypeOrNull()))
                                    .build()
                                val response = httpClient.newCall(request).execute()
                                if (!response.isSuccessful) {
                                    throw CustomException("HTTP ${response.code} from $provider")
                                }

                                val responseBody = response.body.bytes()
                                val resultMessage = Message(responseBody)
                                val rcode = resultMessage.header.rcode
                                if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN && rcode != Rcode.NXRRSET) {
                                    throw CustomException("DNS error from $provider: ${Rcode.string(rcode)}")
                                }

                                val txtRecords = srToTxtRecords(cache.addMessage(resultMessage))
                                if (txtRecords != null && continuation.isActive && counterGood.incrementAndGet() == 1) {
                                    FileLog.d("['${domain}'] First successful TXT response from $provider. Result: $txtRecords")
                                    continuation.resume(txtRecords)
                                } else if (txtRecords == null) {
                                    FileLog.d("['${domain}'] Provider $provider returned a non-conclusive answer for TXT.")
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    FileLog.e("['${domain}'] DoH query to $provider failed: ${e.message ?: e.javaClass.simpleName}")
                                }
                            } finally {
                                if (counterAll.incrementAndGet() == usedProviders.size && counterGood.get() == 0) {
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (result == null) {
                FileLog.d("['${domain}'] DoH TXT query timed out after 5 seconds.")
            }
            result
        }

        if (ret != null) {
            FileLog.d("DoH TXT lookup for '$domain' succeeded.")
            return ret
        } else {
            FileLog.w("All DoH providers failed or timed out for '$domain' (TXT).")
            return listOf()
        }
    }

    private fun srToAddresses(
        sr: SetResponse?, domain: String, isCache: Boolean, fallback: Boolean
    ): List<InetAddress>? {
        if (sr == null) return null

        val source = if (isCache) "Cache" else "DoH Response"

        if (sr.isSuccessful) {
            val records = sr.answers().flatMap { it.rrs(true) }
            val addresses = records.mapNotNull {
                (it as? ARecord)?.address ?: (it as? AAAARecord)?.address
            }
            if (addresses.isNotEmpty()) {
                FileLog.d("[$source] Successfully found ${addresses.size} records for '$domain'.")
                return addresses
            }
        }

        if (sr.isCNAME) {
            val cname = sr.cname.target.toString(true)
            FileLog.d("[$source] '$domain' is a CNAME pointing to '$cname'. Following redirection.")
            return lookup(cname, false) // Start a new lookup for the CNAME target
        }

        val noFallbackAvailable = !hasIPv4 || !hasIPv6
        if (sr.isNXRRSET && !noFallbackAvailable && !fallback) {
            FileLog.d("[$source] for '$domain' resulted in NXRRSET. Trying fallback IP version.")
            return lookup(domain, true) // Retry with the other IP version
        }

        return null
    }

    private fun getIpStrategy(): Byte {
        if (ipStrategy.toInt() != -1) return ipStrategy

        hasIPv4 = false
        hasStrangeIPv4 = false
        hasIPv6 = false

        FileLog.d("Determining IP strategy...")

        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }
                for (address in networkInterface.interfaceAddresses) {
                    val inetAddress = address.address
                    if (inetAddress.isLinkLocalAddress || inetAddress.isLoopbackAddress || inetAddress.isMulticastAddress) {
                        continue
                    }
                    when (inetAddress) {
                        is Inet6Address -> hasIPv6 = true
                        is Inet4Address -> {
                            if (inetAddress.hostAddress?.startsWith("192.0.0.") == false) {
                                hasIPv4 = true
                            } else {
                                hasStrangeIPv4 = true
                            }
                        }
                    }
                }
            }

            val determinedStrategy = when {
                hasIPv6 -> when {
                    !hasIPv4 -> USE_IPV6_ONLY
                    forceIPv6 -> USE_IPV6_ONLY
                    hasStrangeIPv4 -> USE_IPV4_IPV6_RANDOM
                    NekoConfig.useIPv6.Bool() -> USE_IPV4_IPV6_RANDOM
                    else -> USE_IPV4_ONLY
                }

                else -> USE_IPV4_ONLY
            }
            ipStrategy = determinedStrategy

            val strategyName = when (determinedStrategy) {
                USE_IPV4_ONLY -> "IPv4 Only"
                USE_IPV6_ONLY -> "IPv6 Only"
                USE_IPV4_IPV6_RANDOM -> "IPv4 & IPv6 Random"
                else -> "Unknown"
            }
            FileLog.d("IP strategy set to: $strategyName. (hasIPv4=$hasIPv4, hasIPv6=$hasIPv6, forceIPv6=$forceIPv6)")
        } catch (e: Throwable) {
            FileLog.e("Failed to determine IP strategy. Defaulting to IPv4 only.", e)
            ipStrategy = USE_IPV4_ONLY
        }

        return ipStrategy
    }

    private fun getDnsQueryType(fallback: Boolean): Int {
        val strategy = getIpStrategy()
        val noFallback = !hasIPv4 || !hasIPv6

        return if (noFallback) {
            if (hasIPv4) Type.A else Type.AAAA
        } else {
            when (strategy) {
                USE_IPV6_ONLY -> Type.AAAA
                USE_IPV4_ONLY -> Type.A
                USE_IPV4_IPV6_RANDOM -> if (NekoConfig.useIPv6.Bool() xor !fallback) Type.A else Type.AAAA
                else -> Type.A
            }
        }
    }
}
