package org.grapheneos.pdfviewer.viewmodel

import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grapheneos.pdfviewer.R
import org.grapheneos.pdfviewer.Utils
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException

private const val TAG = "PdfViewerViewModel"

private const val MISSING_STRING = "-"

private const val STATE_URI = "uri"
private const val STATE_PAGE = "page"
private const val STATE_ZOOMRATIO = "zoomRatio"
private const val STATE_ORIENTATIONDEGREES = "orientationDegrees"
private const val STATE_DOCUMENT_PROPERTIES = "documentProperties"

class PdfViewerViewModel(private val state: SavedStateHandle) : ViewModel() {
    private val documentProperties: MutableLiveData<List<CharSequence>> by lazy {
        MutableLiveData<List<CharSequence>>(
                state.get(STATE_DOCUMENT_PROPERTIES) ?: ArrayList())
    }
    var uri: Uri? = null
    var page: Int = 0
    var numPages: Int = 0
    var zoomRatio: Float = 1f
    var documentOrientationDegrees: Int = 0

    fun restoreState() {
        state.run {
            uri = get(STATE_URI)
            page = get(STATE_PAGE) ?: 0
            zoomRatio = get(STATE_ZOOMRATIO) ?: 1f
            documentOrientationDegrees = get(STATE_ORIENTATIONDEGREES) ?: 0
        }
    }

    fun saveState() {
        state.run {
            set(STATE_URI, uri)
            set(STATE_PAGE, page)
            set(STATE_ZOOMRATIO, zoomRatio)
            set(STATE_ORIENTATIONDEGREES, documentOrientationDegrees)
            set(STATE_DOCUMENT_PROPERTIES, documentProperties.value as ArrayList<CharSequence>)
        }
    }

    @NonNull
    fun getDocumentProperties(): LiveData<List<CharSequence>> = documentProperties

    fun clearDocumentProperties() {
        val list = documentProperties.value as? ArrayList<CharSequence> ?: ArrayList()
        list.clear()
        documentProperties.postValue(list)
        Log.d(TAG, "clearDocumentProperties")
    }

    fun loadProperties(propertiesString: String, context: Context) {
        if (uri == null) {
            Log.w(TAG, "Failed to parse properties: Uri is null")
            clearDocumentProperties()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val names = context.resources.getStringArray(R.array.property_names);
            val properties = ArrayList<CharSequence>()

            val cursor: Cursor? = context.contentResolver.query(uri!!, null, null, null, null);
            cursor?.let {
                it.moveToFirst();

                val indexOfName = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indexOfName >= 0) {
                    properties.add(getProperty(null, names[0], it.getString(indexOfName),
                            context))
                }

                val indexOfSize = it.getColumnIndex(OpenableColumns.SIZE)
                if (indexOfSize >= 0) {
                    val fileSize = it.getString(indexOfSize).toLong()
                    properties.add(getProperty(null, names[1], Utils.parseFileSize(fileSize),
                            context))
                }
            }
            cursor?.close()

            val specNames = arrayOf("Title", "Author", "Subject", "Keywords", "CreationDate",
                    "ModDate", "Producer", "Creator", "PDFFormatVersion", numPages.toString())
            try {
                val json = JSONObject(propertiesString)
                for (i in 2 until names.size) {
                    properties.add(getProperty(json, names[i], specNames[i - 2], context))
                }

                Log.d(TAG, "Successfully parsed properties")
                val list = documentProperties.value as ArrayList<CharSequence>
                list.run {
                    clear()
                    addAll(properties)
                    documentProperties.postValue(this)
                }
            } catch (e: JSONException) {
                clearDocumentProperties()
                Log.e(TAG, "Failed to parse properties: ${e.message}", e)
            }
        }
    }

    private fun getProperty(
            json: JSONObject?, name: String, specName: String, context: Context
    ): CharSequence {
        val property = SpannableStringBuilder(name).append(":\n")
        val value = json?.optString(specName, MISSING_STRING) ?: specName

        val valueToAppend = if (specName.endsWith("Date") && value != MISSING_STRING) {
            try {
                Utils.parseDate(value)
            } catch (e: ParseException) {
                Log.w(TAG, "${e.message} for $value at offset: ${e.errorOffset}")
                context.getString(R.string.document_properties_invalid_date)
            }
        } else {
            value
        }
        property.append(valueToAppend)
        property.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return property
    }
}
