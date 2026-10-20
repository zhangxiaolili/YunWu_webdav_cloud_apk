package com.clouddock.webdav.ui.util

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class DecodedText(
    val text: String,
    val charsetName: String
)

/**
 * Detects common text encodings. UTF-8 is preferred, then GB18030 (a superset of GBK/GB2312),
 * with a UTF-16 heuristic for files that omit a BOM.
 */
fun decodeText(bytes: ByteArray): DecodedText {
    if (bytes.isEmpty()) return DecodedText("", "UTF-8")

    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return DecodedText(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return DecodedText(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE")
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return DecodedText(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE")
    }

    if (looksLikeUtf16(bytes)) {
        val littleEndian = isLikelyUtf16LittleEndian(bytes)
        return DecodedText(
            String(bytes, if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE),
            if (littleEndian) "UTF-16LE" else "UTF-16BE"
        )
    }

    if (isValidUtf8(bytes)) {
        return DecodedText(String(bytes, Charsets.UTF_8), "UTF-8")
    }

    val gb18030 = runCatching { Charset.forName("GB18030") }.getOrNull()
    if (gb18030 != null && isValid(gb18030, bytes)) {
        return DecodedText(String(bytes, gb18030), "GB18030")
    }

    return DecodedText(String(bytes, Charsets.ISO_8859_1), "ISO-8859-1")
}

fun encodeText(text: String, charsetName: String): ByteArray {
    val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
    return text.toByteArray(charset)
}

private fun isValidUtf8(bytes: ByteArray): Boolean = isValid(Charsets.UTF_8, bytes)

private fun isValid(charset: Charset, bytes: ByteArray): Boolean {
    return try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    } catch (_: Exception) {
        false
    }
}

private fun looksLikeUtf16(bytes: ByteArray): Boolean {
    if (bytes.size < 8 || bytes.size % 2 != 0) return false
    val sample = minOf(bytes.size, 512)
    var zeroEven = 0
    var zeroOdd = 0
    for (index in 0 until sample) {
        if (bytes[index] == 0.toByte()) {
            if (index % 2 == 0) zeroEven++ else zeroOdd++
        }
    }
    val half = sample / 2
    return zeroEven > half / 3 || zeroOdd > half / 3
}

private fun isLikelyUtf16LittleEndian(bytes: ByteArray): Boolean {
    var zeroOdd = 0
    for (index in 1 until minOf(bytes.size, 512) step 2) {
        if (bytes[index] == 0.toByte()) zeroOdd++
    }
    return zeroOdd > bytes.size.coerceAtMost(512) / 8
}
