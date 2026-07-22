package dev.shiko.bootpatcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

const val DOCUMENTS_UI_PACKAGE = "com.android.documentsui"
const val GOOGLE_DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"

val DOCUMENTS_UI_PACKAGES = listOf(DOCUMENTS_UI_PACKAGE, GOOGLE_DOCUMENTS_UI_PACKAGE)

class DocumentsUiOpenDocument : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val mimeTypes = input.distinct().toTypedArray()
        val baseType = mimeTypes.singleOrNull() ?: "*/*"
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setPackage(
                resolveDocumentsUiPackage(context, Intent.ACTION_OPEN_DOCUMENT)
                    ?: DOCUMENTS_UI_PACKAGE,
            )
            .setType(baseType)
            .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

class DocumentsUiOpenTree : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .setPackage(
                resolveDocumentsUiPackage(context, Intent.ACTION_OPEN_DOCUMENT_TREE)
                    ?: DOCUMENTS_UI_PACKAGE,
            )
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            .apply {
                input?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

fun isDocumentsUiAvailable(context: Context, action: String): Boolean =
    resolveDocumentsUiPackage(context, action) != null

fun resolveDocumentsUiPackage(context: Context, action: String): String? =
    DOCUMENTS_UI_PACKAGES.firstOrNull { packageName ->
        Intent(action)
            .apply {
                if (action == Intent.ACTION_OPEN_DOCUMENT) {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            }
            .setPackage(packageName)
            .resolveActivity(context.packageManager) != null
    }
