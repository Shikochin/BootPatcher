package dev.shiko.bootpatcher

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

const val DEFAULT_OUTPUT_FILE_NAME = "new-boot.img"

fun normalizeOutputFileName(value: String): String {
    val safeName = value.trim()
        .replace('/', '_')
        .replace('\\', '_')
        .filterNot(Char::isISOControl)
        .ifBlank { DEFAULT_OUTPUT_FILE_NAME }
    return if (safeName.endsWith(".img", ignoreCase = true)) safeName else "$safeName.img"
}

internal object OutputDocumentResolver {
    fun getOrCreate(context: Context, treeUri: Uri, fileName: String): Uri {
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            treeDocumentId,
        )

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            )
            val nameColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val typeColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == fileName) {
                    check(cursor.getString(typeColumn) != DocumentsContract.Document.MIME_TYPE_DIR) {
                        "$fileName is a directory in the selected location."
                    }
                    return DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(idColumn),
                    )
                }
            }
        }

        return checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId),
                "application/octet-stream",
                fileName,
            ),
        ) { "Unable to create $fileName in the selected location." }
    }

    fun queryDirectoryName(context: Context, treeUri: Uri): String {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (column >= 0) return cursor.getString(column)
            }
        }
        return treeUri.lastPathSegment?.substringAfterLast(':') ?: treeUri.authority.orEmpty()
    }

    fun isSameDocument(context: Context, first: Uri, second: Uri): Boolean {
        if (first == second) return true
        if (first.authority != second.authority) return false
        return runCatching {
            DocumentsContract.isDocumentUri(context, first) &&
                DocumentsContract.isDocumentUri(context, second) &&
                DocumentsContract.getDocumentId(first) == DocumentsContract.getDocumentId(second)
        }.getOrDefault(false)
    }
}
