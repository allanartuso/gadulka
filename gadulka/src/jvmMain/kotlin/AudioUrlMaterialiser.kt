/*
 * Copyright 2026 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

private val cache = ConcurrentHashMap<String, String>()

internal fun materialiseToLocalUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    when (uri.scheme?.lowercase()) {
        "file", "http", "https" -> return url
    }
    return cache.computeIfAbsent(url) { extractToTempFile(url) }
}

private fun extractToTempFile(url: String): String = runCatching {
    val ext = url.substringAfterLast('.', "")
        .takeWhile { it.isLetterOrDigit() }
        .ifEmpty { "bin" }
    val dir = File(System.getProperty("java.io.tmpdir"), "gadulka-cache").apply { mkdirs() }
    val target = File(dir, "${url.hashCode().toUInt().toString(16)}.$ext")
    if (!target.exists() || target.length() == 0L) {
        URI(url).toURL().openStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        target.deleteOnExit()
    }
    target.toURI().toString()
}.getOrElse { url }
