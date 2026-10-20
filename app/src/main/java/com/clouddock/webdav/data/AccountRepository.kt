package com.clouddock.webdav.data

import android.content.Context
import android.content.SharedPreferences
import com.clouddock.webdav.data.local.SafPermissions
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.data.security.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AccountRepository(
    private val context: Context,
    private val cipher: SecretCipher
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<WebDavAccount>> = _accounts.asStateFlow()

    fun find(id: String): WebDavAccount? = _accounts.value.firstOrNull { it.id == id }

    fun passwordFor(account: WebDavAccount): String = cipher.decrypt(account.encryptedPassword)

    fun encryptPassword(password: String): String = cipher.encrypt(password)

    fun upsert(account: WebDavAccount) {
        synchronized(lock) {
            val list = _accounts.value.toMutableList()
            val index = list.indexOfFirst { it.id == account.id }
            if (index >= 0) list[index] = account else list.add(account)
            persist(list)
        }
    }

    fun updateSyncInfo(id: String, timestamp: Long, message: String) {
        synchronized(lock) {
            val list = _accounts.value.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(lastSyncAt = timestamp, lastSyncMessage = message)
                persist(list)
            }
        }
    }

    fun delete(id: String, releaseCacheGrantIfUnused: Boolean = true): WebDavAccount? {
        synchronized(lock) {
            val list = _accounts.value.toMutableList()
            val removed = list.firstOrNull { it.id == id } ?: return null
            list.removeAll { it.id == id }
            persist(list)
            val uri = removed.cacheTreeUri
            if (releaseCacheGrantIfUnused && uri != null &&
                list.none { it.cacheTreeUri == uri }
            ) {
                SafPermissions.release(context, uri)
            }
            return removed
        }
    }

    private fun persist(list: List<WebDavAccount>) {
        val array = JSONArray()
        list.forEach { account -> array.put(account.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
        _accounts.value = list
    }

    private fun loadAccounts(): List<WebDavAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    runCatching { item.toAccount() }.getOrNull()?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun WebDavAccount.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("username", username)
        put("encryptedPassword", encryptedPassword)
        put("cacheTreeUri", cacheTreeUri ?: JSONObject.NULL)
        put("cacheLocationName", cacheLocationName ?: JSONObject.NULL)
        put("allowUntrustedTls", allowUntrustedTls)
        put("syncOnOpen", syncOnOpen)
        put("syncIntervalMinutes", syncIntervalMinutes)
        put("lastSyncAt", lastSyncAt)
        put("lastSyncMessage", lastSyncMessage ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("accentIndex", accentIndex)
    }

    private fun JSONObject.toAccount(): WebDavAccount {
        val id = optString("id").ifBlank { UUID.randomUUID().toString() }
        return WebDavAccount(
            id = id,
            name = optString("name", "WebDAV"),
            baseUrl = optString("baseUrl"),
            username = optString("username"),
            encryptedPassword = optString("encryptedPassword"),
            cacheTreeUri = if (isNull("cacheTreeUri")) null else optString("cacheTreeUri").ifBlank { null },
            cacheLocationName = if (isNull("cacheLocationName")) null else optString("cacheLocationName").ifBlank { null },
            allowUntrustedTls = optBoolean("allowUntrustedTls", false),
            syncOnOpen = optBoolean("syncOnOpen", true),
            syncIntervalMinutes = optInt("syncIntervalMinutes", 30),
            lastSyncAt = optLong("lastSyncAt", 0L),
            lastSyncMessage = if (isNull("lastSyncMessage")) null else optString("lastSyncMessage").ifBlank { null },
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            accentIndex = optInt("accentIndex", 0)
        )
    }

    private companion object {
        const val PREFS_NAME = "clouddock_accounts"
        const val KEY_ACCOUNTS = "accounts_json_v1"
    }
}
