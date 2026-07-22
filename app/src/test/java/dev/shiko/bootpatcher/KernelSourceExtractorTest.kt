package dev.shiko.bootpatcher

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KernelSourceExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun copiesRawImage() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val output = temporaryFolder.newFile()

        KernelSourceExtractor.extract(ByteArrayInputStream(expected), "Image", output)

        assertArrayEquals(expected, output.readBytes())
    }

    @Test
    fun extractsNestedImageFromAnyKernelZip() {
        val expected = "kernel data".toByteArray()
        val output = temporaryFolder.newFile()
        val zip = createZip("AnyKernel3/Image" to expected)

        KernelSourceExtractor.extract(ByteArrayInputStream(zip), "kernel.zip", output)

        assertArrayEquals(expected, output.readBytes())
    }

    @Test
    fun detectsZipBySignature() {
        val expected = "kernel data".toByteArray()
        val output = temporaryFolder.newFile()
        val zip = createZip("Image" to expected)

        KernelSourceExtractor.extract(ByteArrayInputStream(zip), "download.bin", output)

        assertArrayEquals(expected, output.readBytes())
    }

    @Test
    fun rejectsZipWithoutImage() {
        val output = temporaryFolder.newFile()
        val zip = createZip("README.md" to "text".toByteArray())

        assertThrows(IllegalStateException::class.java) {
            KernelSourceExtractor.extract(ByteArrayInputStream(zip), "kernel.zip", output)
        }
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
