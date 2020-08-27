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
import org.grapheneos.pdfviewer.R
import org.grapheneos.pdfviewer.Utils
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException

private const val TAG = "DocumentPropertiesParser"
private const val MISSING_STRING = "-"

internal fun parsePropertiesString(
    propertiesString: String,
    context: Context,
    numPages: Int,
    uri: Uri?
): ArrayList<CharSequence>? {
    val properties = ArrayList<CharSequence>()
    val names = context.resources.getStringArray(R.array.property_names);
    val cursor: Cursor? = context.contentResolver.query(uri!!, null, null, null, null);
    cursor?.let {
        it.moveToFirst();

        val indexOfName = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (indexOfName >= 0) {
            properties.add(
                getProperty(null, names[0], it.getString(indexOfName), context)
            )
        }

        val indexOfSize = it.getColumnIndex(OpenableColumns.SIZE)
        if (indexOfSize >= 0) {
            val fileSize = it.getString(indexOfSize).toLong()
            properties.add(
                getProperty(null, names[1], Utils.parseFileSize(fileSize), context)
            )
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
        return properties
    } catch (e: JSONException) {
        Log.e(TAG, "Failed to parse properties: ${e.message}", e)
    }
    return null
}

private fun getProperty(
    json: JSONObject?,
    name: String,
    specName: String,
    context: Context
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
