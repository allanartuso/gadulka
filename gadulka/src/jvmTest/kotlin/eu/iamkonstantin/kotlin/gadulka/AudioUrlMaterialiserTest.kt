/*
 * Copyright 2026 Konstantin <hi@iamkonstantin.eu>.
 *  Use of this source code is governed by the BSD 3-Clause License that can be found in LICENSE file.
 */

package eu.iamkonstantin.kotlin.gadulka

import java.io.File
import java.net.URI
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioUrlMaterialiserTest {

    private val payload = byteArrayOf(0x4D, 0x34, 0x61, 0x75, 0x64, 0x69, 0x6F)

    private fun buildJarWithEntry(entryPath: String, content: ByteArray): File {
        val jar = File.createTempFile("gadulka-test", ".jar").also { it.deleteOnExit() }
        JarOutputStream(jar.outputStream()).use { jos ->
            jos.putNextEntry(JarEntry(entryPath))
            jos.write(content)
            jos.closeEntry()
        }
        return jar
    }

    @Test
    fun `jar URL is extracted to a file URL with matching content`() {
        val jar = buildJarWithEntry("files/test.m4a", payload)
        val jarUrl = "jar:${jar.toURI()}!/files/test.m4a"

        val result = materialiseToLocalUrl(jarUrl)

        assertTrue(result.startsWith("file:"), "Expected file: URI but got: $result")
        val resolved = File(URI(result))
        assertTrue(resolved.exists(), "Extracted file does not exist: $resolved")
        assertTrue(resolved.readBytes().contentEquals(payload), "Extracted content does not match payload")
    }

    @Test
    fun `file URL is returned unchanged`() {
        val file = File.createTempFile("gadulka-passthrough", ".m4a").also { it.deleteOnExit() }
        val fileUrl = file.toURI().toString()

        val result = materialiseToLocalUrl(fileUrl)

        assertEquals(fileUrl, result)
    }
}
