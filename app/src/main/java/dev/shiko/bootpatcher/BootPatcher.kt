package dev.shiko.bootpatcher

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

enum class PatchStage {
    COPYING_BOOT,
    READING_KERNEL,
    UNPACKING,
    REPLACING_KERNEL,
    REPACKING,
    SAVING,
}

class BootPatcher(private val context: Context) {
    fun patch(
        bootUri: Uri,
        kernelUri: Uri,
        kernelName: String,
        outputUriProvider: () -> Uri,
        onStage: (PatchStage) -> Unit,
    ) {
        val workDir = File(context.cacheDir, "patch-jobs/${UUID.randomUUID()}")
        check(workDir.mkdirs()) { "Unable to create a working directory." }

        try {
            val bootImage = File(workDir, "boot.img")
            val replacement = File(workDir, "replacement-kernel")

            onStage(PatchStage.COPYING_BOOT)
            context.contentResolver.openInputStream(bootUri)?.use { input ->
                FileOutputStream(bootImage).use(input::copyTo)
            } ?: error("Unable to read the selected boot image.")

            onStage(PatchStage.READING_KERNEL)
            context.contentResolver.openInputStream(kernelUri)?.use { input ->
                KernelSourceExtractor.extract(input, kernelName, replacement)
            } ?: error("Unable to read the selected kernel image.")

            onStage(PatchStage.UNPACKING)
            runMagiskboot(workDir, "unpack", bootImage.name)
            val unpackedKernel = File(workDir, "kernel")
            check(unpackedKernel.isFile) {
                "The boot image contains no replaceable kernel."
            }

            onStage(PatchStage.REPLACING_KERNEL)
            replacement.copyTo(unpackedKernel, overwrite = true)

            onStage(PatchStage.REPACKING)
            runMagiskboot(workDir, "repack", bootImage.name)
            val patchedImage = File(workDir, "new-boot.img")
            check(patchedImage.isFile && patchedImage.length() > 0L) {
                "magiskboot did not produce new-boot.img."
            }

            onStage(PatchStage.SAVING)
            val outputUri = outputUriProvider()
            context.contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                patchedImage.inputStream().use { it.copyTo(output) }
            } ?: error("Unable to write the output image.")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun runMagiskboot(workDir: File, vararg arguments: String) {
        val exitCode = MagiskBoot.run(
            workDir.absolutePath,
            context.filesDir.absolutePath,
            arguments.toList().toTypedArray(),
        )
        check(exitCode == 0) {
            "magiskboot exited with code $exitCode."
        }
    }
}

internal object MagiskBoot {
    init {
        System.loadLibrary("magiskboot")
    }

    external fun run(workDir: String, restoreDir: String, arguments: Array<String>): Int
}

internal object KernelSourceExtractor {
    private const val MAX_KERNEL_SIZE = 512L * 1024L * 1024L

    fun extract(input: InputStream, sourceName: String, destination: File) {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val signature = ByteArray(4)
        val bytesRead = buffered.read(signature)
        buffered.reset()
        val isZip = sourceName.endsWith(".zip", ignoreCase = true) ||
            (bytesRead == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte())

        if (isZip) {
            extractImageFromZip(buffered, destination)
        } else {
            FileOutputStream(destination).use { output ->
                buffered.copyToWithLimit(output, MAX_KERNEL_SIZE)
            }
        }
        check(destination.length() > 0L) { "The selected kernel image is empty." }
    }

    private fun extractImageFromZip(input: InputStream, destination: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val isImage = !entry.isDirectory && entry.name.substringAfterLast('/') == "Image"
                if (isImage) {
                    FileOutputStream(destination).use { output ->
                        zip.copyToWithLimit(output, MAX_KERNEL_SIZE)
                    }
                    return
                }
                zip.closeEntry()
            }
        }
        error("The selected ZIP contains no Image file.")
    }

    private fun InputStream.copyToWithLimit(output: FileOutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "The kernel image exceeds the 512 MiB limit." }
            output.write(buffer, 0, count)
        }
    }
}
