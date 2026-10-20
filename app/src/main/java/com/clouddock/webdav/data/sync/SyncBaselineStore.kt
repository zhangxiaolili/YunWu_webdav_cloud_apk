package com.clouddock.webdav.data.sync

import android.content.Context
import com.clouddock.webdav.data.model.SyncBaseline
import org.json.JSONObject
import java.io.File

class SyncBaselineStore(context: Context) {
    private val directory = File(context.filesDir, "sync_state").apply { mkdirs() }
    private val lock = Any()

    fun load(accountId: String): MutableMap<String, SyncBaseline> = synchronized(lock) {
        val file = fileFor(accountId)
        if (!file.isFile) return mutableMapOf()
        return runCatching {
            val root = JSONObject(file.readText())
            val entries = root.optJSONObject("entries") ?: return mutableMapOf()
            buildMap {
                val keys = entries.keys()
                while (keys.hasNext()) {
                    val path = keys.next()
                    val value = entries.optJSONObject(path) ?: continue
                    put(
                        path,
                        SyncBaseline(
                            remoteEtag = if (value.isNull("remoteEtag")) null else value.optString("remoteEtag"),
                            remoteModified = value.optLong("remoteModified", 0L),
                            remoteSize = value.optLong("remoteSize", -1L),
                            localModified = value.optLong("localModified", 0L),
                            localSize = value.optLong("localSize", -1L)
                        )
                    )
                }
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun save(accountId: String, baselines: Map<String, SyncBaseline>) = synchronized(lock) {
        val file = fileFor(accountId)
        val entries = JSONObject()
        baselines.forEach { (path, baseline) ->
            entries.put(
                path,
                JSONObject().apply {
                    put("remoteEtag", baseline.remoteEtag ?: JSONObject.NULL)
                    put("remoteModified", baseline.remoteModified)
                    put("remoteSize", baseline.remoteSize)
                    put("localModified", baseline.localModified)
                    put("localSize", baseline.localSize)
                }
            )
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("entries", entries)
        }
        val temp = File(directory, "${file.name}.tmp")
        temp.writeText(root.toString())
        if (file.exists()) file.delete()
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    fun clear(accountId: String) = synchronized(lock) {
        fileFor(accountId).delete()
        Unit
    }

    private fun fileFor(accountId: String): File =
        File(directory, accountId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")
}
