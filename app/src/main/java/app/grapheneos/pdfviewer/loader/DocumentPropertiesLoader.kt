package app.grapheneos.pdfviewer.loader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import androidx.core.database.getLongOrNull
import app.grapheneos.pdfviewer.R
import org.json.JSONException

class DocumentPropertiesLoader(
    private val context: Context,
    private val properties: String,
    private val numPages: Int,
    private val uri: Uri
) {

    companion object {
        const val TAG = "DocumentPropertiesLoader"
    }

    fun load(): Map<DocumentProperty, String> {
        val result = mutableMapOf<DocumentProperty, String>()
        result.addFileProperties()
        result.addPageSizeProperty()
        result.addPDFJsProperties()
        return result
    }

    private fun MutableMap<DocumentProperty, String>.addPageSizeProperty() {
        this[DocumentProperty.Pages] = java.lang.String.valueOf(numPages)
    }

    private fun MutableMap<DocumentProperty, String>.addFileProperties() {
        putAll(getFileProperties())
    }

    private fun MutableMap<DocumentProperty, String>.addPDFJsProperties() {
        putAll(getPDFJsProperties())
    }

    private fun getPDFJsProperties(): Map<DocumentProperty, String> {
        return try {
            PDFJsPropertiesToDocumentPropertyConverter(
                properties,
                context.getString(R.string.document_properties_invalid_date),
                parseExceptionListener = { parseException, value ->
                    Log.w(
                        TAG,
                        "${parseException.message} for $value at offset: ${parseException.errorOffset}"
                    )
                }
            ).convert()
        } catch (_: JSONException) {
            Log.w(TAG, "invalid properties")
            emptyMap()
        }
    }

    private fun getFileProperties(): Map<DocumentProperty, String> {
        val collections = mutableMapOf<DocumentProperty, String>()
        val proj = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )

        context.contentResolver.query(
            uri,
            proj,
            null,
            null
        )?.use { cursor ->
            cursor.moveToFirst()
            val indexName: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (indexName >= 0) {
                collections[DocumentProperty.FileName] = cursor.getString(indexName)
            }

            val indexSize: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (indexSize >= 0) {
                val fileSize = cursor.getLongOrNull(indexSize)
                collections[DocumentProperty.FileSize] =
                    fileSize?.let { Formatter.formatFileSize(context, it) } ?: context.getString(R.string.unknown_file_size)
            }
        }
        return collections
    }
}
