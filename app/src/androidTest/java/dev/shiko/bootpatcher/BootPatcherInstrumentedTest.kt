package dev.shiko.bootpatcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootPatcherInstrumentedTest {
    @Test
    fun fileContractsTargetAndroidDocumentsUi() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val openDocument = DocumentsUiOpenDocument().createIntent(context, arrayOf("*/*"))
        val openTree = DocumentsUiOpenTree().createIntent(context, null)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, openDocument.action)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, openTree.action)
        assertEquals(
            resolveDocumentsUiPackage(context, Intent.ACTION_OPEN_DOCUMENT),
            openDocument.`package`,
        )
        assertEquals(
            resolveDocumentsUiPackage(context, Intent.ACTION_OPEN_DOCUMENT_TREE),
            openTree.`package`,
        )
    }

    @Test
    fun replacesKernelAndRepacksBootImage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.cacheDir, "instrumented-patch-test").apply {
            deleteRecursively()
            check(mkdirs())
        }
        val input = File(testDir, "input-boot.img")
        val replacement = File(testDir, "Image")
        val output = File(testDir, "patched-boot.img")
        val expectedKernel = "boot-patcher replacement kernel".toByteArray()

        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("test-boot.img").use { source ->
                input.outputStream().use(source::copyTo)
            }
            replacement.writeBytes(expectedKernel)

            BootPatcher(context).patch(
                bootUri = Uri.fromFile(input),
                kernelUri = Uri.fromFile(replacement),
                kernelName = replacement.name,
                outputUriProvider = { Uri.fromFile(output) },
                onStage = {},
            )

            assertTrue(output.length() > 0L)
            val verifyDir = File(testDir, "verify").apply { check(mkdir()) }
            assertEquals(
                0,
                MagiskBoot.run(
                    verifyDir.absolutePath,
                    context.filesDir.absolutePath,
                    arrayOf("unpack", output.absolutePath),
                ),
            )
            assertArrayEquals(expectedKernel, File(verifyDir, "kernel").readBytes())
        } finally {
            testDir.deleteRecursively()
        }
    }
}
