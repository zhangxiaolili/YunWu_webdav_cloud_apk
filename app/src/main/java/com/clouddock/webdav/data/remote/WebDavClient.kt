package com.clouddock.webdav.data.remote

import com.clouddock.webdav.data.model.DavEntry
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.ui.util.normalizeRelativePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * A deliberately small RFC 4918 client covering the operations a file manager needs:
 * PROPFIND, GET, PUT, MKCOL, DELETE and MOVE. It supports Basic authentication, redirects,
 * streaming transfers and user-approved self-signed TLS.
 */
class WebDavClient(
    private val account: WebDavAccount,
    private val password: String,
    private val httpClient: OkHttpClient
) {
    private val baseUrl: HttpUrl = WebDavUrl.parse(account.baseUrl)

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val items = propfind("", 1, includeSelf = false)
        "连接成功 · 根目录 ${items.size} 个项目"
    }

    suspend fun list(relativePath: String): List<DavEntry> = withContext(Dispatchers.IO) {
        val normalized = normalizeRelativePath(relativePath)
        propfind(normalized, depth = 1, includeSelf = false)
            .filter { it.path != normalized }
            .sortedWith(compareBy<DavEntry> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
    }

    suspend fun stat(relativePath: String, directory: Boolean = false): DavEntry? =
        withContext(Dispatchers.IO) {
            val normalized = normalizeRelativePath(relativePath)
            try {
                val entries = propfind(normalized, depth = 0, includeSelf = true, directory = directory)
                entries.firstOrNull { it.path == normalized } ?: entries.firstOrNull()
            } catch (e: WebDavHttpException) {
                if (e.statusCode == 404) null else throw e
            }
        }

    suspend fun download(
        relativePath: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val normalized = normalizeRelativePath(relativePath)
        require(normalized.isNotEmpty())
        val request = newRequest(WebDavUrl.urlFor(baseUrl, normalized, directory = false))
            .get()
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) throw statusException(response.code)
            val body = response.body ?: throw WebDavException("服务器返回了空响应")
            target.parentFile?.mkdirs()
            val total = body.contentLength()
            onProgress(0L, total)
            FileOutputStream(target, false).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }
        }
    }

    suspend fun upload(
        relativePath: String,
        input: InputStream,
        length: Long,
        mimeType: String,
        onProgress: (uploaded: Long, total: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val normalized = normalizeRelativePath(relativePath)
        require(normalized.isNotEmpty())
        val effectiveLength = if (length >= 0) length else -1L
        val body = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()
            override fun contentLength() = effectiveLength
            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(128 * 1024)
                var uploaded = 0L
                onProgress(0L, effectiveLength)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    uploaded += read
                    onProgress(uploaded, effectiveLength)
                }
                sink.flush()
            }
        }
        val request = newRequest(WebDavUrl.urlFor(baseUrl, normalized, directory = false))
            .put(body)
            .build()
        try {
            execute(request).use { response ->
                if (!response.isSuccessful) throw statusException(response.code)
            }
        } finally {
            runCatching { input.close() }
        }
    }

    suspend fun uploadBytes(relativePath: String, bytes: ByteArray, mimeType: String) {
        upload(relativePath, bytes.inputStream(), bytes.size.toLong(), mimeType)
    }

    suspend fun makeDirectory(relativePath: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeRelativePath(relativePath)
        require(normalized.isNotEmpty()) { "根目录已存在" }
        val request = newRequest(WebDavUrl.urlFor(baseUrl, normalized, directory = true))
            .method("MKCOL", null)
            .build()
        execute(request).use { response ->
            // 405 is returned by several servers when the collection already exists.
            if (response.code != 405 && !response.isSuccessful) throw statusException(response.code)
        }
    }

    suspend fun delete(relativePath: String, directory: Boolean) = withContext(Dispatchers.IO) {
        val normalized = normalizeRelativePath(relativePath)
        require(normalized.isNotEmpty()) { "不能删除挂载根目录" }
        val request = newRequest(WebDavUrl.urlFor(baseUrl, normalized, directory))
            .delete()
            .build()
        execute(request).use { response ->
            if (response.code != 404 && !response.isSuccessful) throw statusException(response.code)
        }
    }

    suspend fun move(
        relativePath: String,
        destinationPath: String,
        directory: Boolean
    ) = withContext(Dispatchers.IO) {
        val normalized = normalizeRelativePath(relativePath)
        val destination = normalizeRelativePath(destinationPath)
        require(normalized.isNotEmpty() && destination.isNotEmpty())
        val destinationUrl = WebDavUrl.urlFor(baseUrl, destination, directory)
        val request = newRequest(WebDavUrl.urlFor(baseUrl, normalized, directory))
            .method("MOVE", null)
            .header("Destination", destinationUrl.toString())
            .header("Overwrite", "T")
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) throw statusException(response.code)
        }
    }

    private suspend fun propfind(
        path: String,
        depth: Int,
        includeSelf: Boolean,
        directory: Boolean = depth > 0
    ): List<DavEntry> {
        val request = newRequest(WebDavUrl.urlFor(baseUrl, path, directory))
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth.toString())
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) throw statusException(response.code)
            val xml = response.body?.string()
                ?: throw WebDavException("服务器返回了空的 PROPFIND 响应")
            return parseMultiStatus(xml, parentPath = path, includeSelf = includeSelf)
        }
    }

    private fun newRequest(url: HttpUrl): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "CloudDock/1.0 (Android WebDAV Client)")
            .header("Accept", "*/*")
        if (account.username.isNotEmpty()) {
            builder.header("Authorization", Credentials.basic(account.username, password, Charsets.UTF_8))
        }
        return builder
    }

    private fun execute(request: Request) = httpClient.newCall(request).execute()

    private fun statusException(code: Int): WebDavHttpException =
        WebDavHttpException(code, friendlyMessageForStatus(code))

    private fun parseMultiStatus(
        xml: String,
        parentPath: String,
        includeSelf: Boolean
    ): List<DavEntry> {
        val parser: XmlPullParser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newPullParser()
        parser.setInput(StringReader(xml))

        val result = mutableListOf<DavEntry>()
        var current: ResponseDraft? = null
        var currentLeaf: String? = null
        var text = StringBuilder()
        var inResponse = false
        var event = parser.eventType

        fun commitLeaf() {
            val draft = current ?: return
            when (currentLeaf) {
                "href" -> draft.href = text.toString().trim()
                "displayname" -> draft.displayName = text.toString().trim()
                "getcontentlength" -> draft.length = text.toString().trim().toLongOrNull() ?: 0L
                "getlastmodified" -> draft.modified = parseHttpDate(text.toString().trim())
                "getetag" -> draft.etag = text.toString().trim().trim('"').removePrefix("W/")
                "getcontenttype" -> draft.contentType = text.toString().trim()
            }
            currentLeaf = null
            text = StringBuilder()
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == "response") {
                        current = ResponseDraft()
                        inResponse = true
                        currentLeaf = null
                        text = StringBuilder()
                    } else if (inResponse) {
                        when (tag) {
                            "collection" -> current?.isCollection = true
                            "href", "displayname", "getcontentlength", "getlastmodified",
                            "getetag", "getcontenttype" -> {
                                currentLeaf = tag
                                text = StringBuilder()
                            }
                        }
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inResponse && currentLeaf != null) text.append(parser.text)
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == currentLeaf) commitLeaf()
                    if (tag == "response") {
                        val draft = current
                        current = null
                        inResponse = false
                        currentLeaf = null
                        if (draft != null) {
                            val entry = draft.toEntry(baseUrl, parentPath)
                            if (entry != null) {
                                val normalizedParent = normalizeRelativePath(parentPath)
                                if (includeSelf || entry.path != normalizedParent) {
                                    result += entry
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseHttpDate(value: String): Long {
        if (value.isBlank()) return 0L
        return runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.recoverCatching {
            java.time.OffsetDateTime.parse(
                value,
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            ).toInstant().toEpochMilli()
        }.recoverCatching {
            java.time.LocalDateTime.parse(
                value,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
            ).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    private data class ResponseDraft(
        var href: String? = null,
        var displayName: String? = null,
        var length: Long = 0L,
        var modified: Long = 0L,
        var etag: String? = null,
        var contentType: String? = null,
        var isCollection: Boolean = false
    ) {
        fun toEntry(baseUrl: HttpUrl, parentPath: String): DavEntry? {
            val hrefValue = href ?: return null
            val relative = relativePathFromHref(hrefValue, baseUrl)
                ?: fallbackRelativePath(hrefValue, displayName, parentPath)
                ?: return null
            val normalized = runCatching { normalizeRelativePath(relative) }.getOrNull() ?: return null
            val nameFromHref = hrefValue
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .substringAfterLast('/')
                .let { decodePercent(it) }
                .takeIf { it.isNotBlank() }
            val name = displayName?.takeIf { it.isNotBlank() }
                ?: nameFromHref
                ?: normalized.substringAfterLast('/')
            return DavEntry(
                path = normalized,
                name = name,
                isDirectory = isCollection,
                size = if (isCollection) 0L else length.coerceAtLeast(0L),
                lastModified = modified,
                etag = etag?.takeIf { it.isNotBlank() },
                mimeType = contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
            )
        }
    }

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getetag/>
    <d:getcontenttype/>
  </d:prop>
</d:propfind>"""

        internal fun relativePathFromHref(href: String, baseUrl: HttpUrl): String? {
            val resolved = baseUrl.resolve(href) ?: href.toHttpUrlOrNull() ?: return null
            val baseSegments = baseUrl.pathSegments.dropLastWhile { it.isEmpty() }
            val hrefSegments = resolved.pathSegments.dropLastWhile { it.isEmpty() }
            if (hrefSegments.size < baseSegments.size) return null
            if (hrefSegments.subList(0, baseSegments.size) != baseSegments) return null
            return hrefSegments.drop(baseSegments.size)
                .filter { it.isNotEmpty() }
                .joinToString("/")
        }

        private fun fallbackRelativePath(href: String, displayName: String?, parentPath: String): String? {
            val rawName = displayName?.takeIf { it.isNotBlank() }
                ?: href.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
            val name = decodePercent(rawName)
            if (name.isBlank()) return null
            val parent = normalizeRelativePath(parentPath)
            return if (parent.isEmpty()) name else "$parent/$name"
        }

        private fun decodePercent(value: String): String =
            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
