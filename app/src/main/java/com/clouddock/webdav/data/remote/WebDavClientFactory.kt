package com.clouddock.webdav.data.remote

import com.clouddock.webdav.data.model.WebDavAccount
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class WebDavClientFactory {
    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private data class CachedClient(val fingerprint: String, val client: WebDavClient)

    private val cache = ConcurrentHashMap<String, CachedClient>()

    fun get(account: WebDavAccount, password: String): WebDavClient {
        val fingerprint = listOf(
            account.baseUrl,
            account.username,
            password,
            account.allowUntrustedTls.toString()
        ).joinToString("|")
        val existing = cache[account.id]
        if (existing != null && existing.fingerprint == fingerprint) return existing.client

        val httpClient = if (account.allowUntrustedTls) {
            insecureClient()
        } else {
            sharedClient
        }
        val created = WebDavClient(account, password, httpClient)
        cache[account.id] = CachedClient(fingerprint, created)
        return created
    }

    fun evict(accountId: String) {
        cache.remove(accountId)
    }

    private fun insecureClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        return sharedClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
