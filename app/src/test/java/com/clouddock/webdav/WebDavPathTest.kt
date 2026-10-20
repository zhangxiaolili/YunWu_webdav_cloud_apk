package com.clouddock.webdav

import com.clouddock.webdav.data.remote.WebDavClient
import com.clouddock.webdav.data.remote.WebDavUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavPathTest {

    @Test
    fun baseUrlNormalizationAddsTrailingSlashAndStripsQuery() {
        assertEquals(
            "https://example.com/remote.php/dav/files/user/",
            WebDavUrl.normalizeBaseUrl("https://example.com/remote.php/dav/files/user?token=1#frag")
        )
        assertEquals(
            "https://dav.example.com/",
            WebDavUrl.normalizeBaseUrl("https://dav.example.com")
        )
    }

    @Test
    fun fileAndDirectoryUrlsAreBuiltRelativeToTheBasePath() {
        val base = WebDavUrl.parse("https://example.com/remote.php/dav/files/user")
        assertEquals(
            "https://example.com/remote.php/dav/files/user/%E9%A1%B9%E7%9B%AE/file%20name.txt",
            WebDavUrl.urlFor(base, "项目/file name.txt", directory = false).toString()
        )
        assertEquals(
            "https://example.com/remote.php/dav/files/user/folder/",
            WebDavUrl.urlFor(base, "folder", directory = true).toString()
        )
    }

    @Test
    fun hrefsAreResolvedBackToRelativeWebDavPaths() {
        val base = WebDavUrl.parse("https://example.com/remote.php/dav/files/user/")
        assertEquals(
            "folder/file name.txt",
            WebDavClient.relativePathFromHref(
                "/remote.php/dav/files/user/folder/file%20name.txt",
                base
            )
        )
        assertEquals(
            "folder",
            WebDavClient.relativePathFromHref(
                "https://example.com/remote.php/dav/files/user/folder/",
                base
            )
        )
        assertNull(WebDavClient.relativePathFromHref("/other/place/file.txt", base))
    }
}
