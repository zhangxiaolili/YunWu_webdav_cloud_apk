package com.clouddock.webdav

import com.clouddock.webdav.ui.util.decodeText
import com.clouddock.webdav.ui.util.formatBytes
import com.clouddock.webdav.ui.util.joinPath
import com.clouddock.webdav.ui.util.normalizeRelativePath
import com.clouddock.webdav.ui.util.parentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.Charset

class PathAndEncodingTest {

    @Test
    fun pathsAreNormalizedWithoutLosingSpaces() {
        assertEquals("a/b c/d.txt", normalizeRelativePath("//a//b c/d.txt/"))
        assertEquals("a/b", parentPath("a/b/c.txt"))
        assertEquals("", parentPath("file.txt"))
        assertEquals("a/新 文件.txt", joinPath("a", "新 文件.txt"))
    }

    @Test
    fun traversalAndInvalidNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRelativePath("a/../secret")
        }
        assertThrows(IllegalArgumentException::class.java) {
            joinPath("a", "../secret")
        }
    }

    @Test
    fun utf8TextIsDetected() {
        val payload = "你好，CloudDock\n第二行".toByteArray(Charsets.UTF_8)
        val decoded = decodeText(payload)
        assertEquals("UTF-8", decoded.charsetName)
        assertEquals("你好，CloudDock\n第二行", decoded.text)
    }

    @Test
    fun gb18030ChineseTextIsDetected() {
        val charset = Charset.forName("GB18030")
        val text = "中文编码测试，云坞文件管理器。"
        val decoded = decodeText(text.toByteArray(charset))
        assertEquals("GB18030", decoded.charsetName)
        assertEquals(text, decoded.text)
    }

    @Test
    fun byteFormattingIsReadable() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1024L * 1024 + 512 * 1024))
    }
}
