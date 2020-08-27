package org.grapheneos.pdfviewer.viewmodel

import android.app.Application
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grapheneos.pdfviewer.R
import org.grapheneos.pdfviewer.Utils
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException

private const val TAG = "PdfViewerViewModel"

private const val MISSING_STRING = "-"

class PdfViewerViewModel(application: Application) : AndroidViewModel(application) {
    var uri: Uri? = null
    var page: Int = 0
    var numPages: Int = 0
    var zoomRatio: Float = 1f
    var documentOrientationDegrees: Int = 0

    private val documentProperties: MutableLiveData<List<CharSequence>> by lazy {
        MutableLiveData<List<CharSequence>>()
    }

    @NonNull
    fun getDocumentProperties(): LiveData<List<CharSequence>> = documentProperties

    fun clearDocumentProperties() {
        val list = documentProperties.value as? ArrayList<CharSequence> ?: ArrayList()
        list.clear()
        documentProperties.postValue(list)
    }

    fun loadProperties(propertiesString: String) {
        if (uri == null) {
            Log.w(TAG, "Failed to parse properties: Uri is null")
            clearDocumentProperties()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val context = getApplication<Application>()
            val names = context.resources.getStringArray(R.array.property_names);
            val properties = ArrayList<CharSequence>()

            val cursor: Cursor? = context.contentResolver.query(uri!!, null, null, null, null);
            cursor?.let {
                it.moveToFirst();

                val indexOfName = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (indexOfName >= 0) {
                    properties.add(getProperty(null, names[0], it.getString(indexOfName)))
                }

                val indexOfSize = it.getColumnIndex(OpenableColumns.SIZE)
                if (indexOfSize >= 0) {
                    val fileSize = it.getString(indexOfSize).toLong()
                    properties.add(getProperty(null, names[1], Utils.parseFileSize(fileSize)))
                }
            }
            cursor?.close()

            val specNames = arrayOf("Title", "Author", "Subject", "Keywords", "CreationDate",
                    "ModDate", "Producer", "Creator", "PDFFormatVersion", numPages.toString())
            try {
                val json = JSONObject(propertiesString)
                for (i in 2 until names.size) {
                    properties.add(getProperty(json, names[i], specNames[i - 2]))
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

    private fun getProperty(json: JSONObject?, name: String, specName: String): CharSequence {
        val property = SpannableStringBuilder(name).append(":\n")
        val value = json?.optString(specName, MISSING_STRING) ?: specName

        val valueToAppend = if (specName.endsWith("Date") && value != MISSING_STRING) {
            try {
                Utils.parseDate(value)
            } catch (e: ParseException) {
                Log.w(TAG, "${e.message} for $value at offset: ${e.errorOffset}")
                val context = getApplication<Application>()
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
