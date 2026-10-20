package com.clouddock.webdav.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl

object WebDavUrl {
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "请输入 WebDAV 服务器地址" }
        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        val parsed = candidate.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("服务器地址格式不正确")
        require(parsed.scheme == "http" || parsed.scheme == "https") {
            "仅支持 HTTP 或 HTTPS 地址"
        }
        val builder = parsed.newBuilder()
            .query(null)
            .fragment(null)
        var path = parsed.encodedPath.ifEmpty { "/" }
        if (!path.endsWith("/")) path += "/"
        builder.encodedPath(path)
        return builder.build().toString()
    }

    fun parse(raw: String): HttpUrl {
        val normalized = normalizeBaseUrl(raw)
        return normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("服务器地址格式不正确")
    }

    fun urlFor(baseUrl: HttpUrl, relativePath: String, directory: Boolean): HttpUrl {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        require(segments.none { it == "." || it == ".." }) { "非法路径" }
        var builder = baseUrl.newBuilder()
        segments.forEach { builder = builder.addPathSegment(it) }
        var result = builder.build()
        if (directory && !result.encodedPath.endsWith("/")) {
            result = result.newBuilder().encodedPath(result.encodedPath + "/").build()
        }
        return result
    }

    fun basePathFor(baseUrl: HttpUrl): String = baseUrl.encodedPath
}
