package app.grapheneos.pdfviewer.loader

import app.grapheneos.pdfviewer.Utils
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import kotlin.jvm.Throws

class PDFJsPropertiesToDocumentPropertyConverter(
    private val properties: String,
    private val propertyInvalidDate: String,
    private val parseExceptionListener: (e: ParseException, value: String) -> Unit
) {

    @Throws(JSONException::class)
    fun convert(): Map<DocumentProperty, String> {
        val result = mutableMapOf<DocumentProperty, String>()

        val json = JSONObject(properties)
        addJsonProperties(json, result)
        return result
    }

    private fun addJsonProperties(
        json: JSONObject,
        collections: MutableMap<DocumentProperty, String>
    ) {
        for (documentProperty in DocumentProperty.values()) {
            val key = documentProperty.key
            if (key.isEmpty()) continue
            val value = json.optString(key, DEFAULT_VALUE)
            collections[documentProperty] = prettify(documentProperty, value)
        }
    }

    private fun prettify(property: DocumentProperty, value: String): String {
        if (value != DEFAULT_VALUE && property.isDate) {
            return try {
                Utils.parseDate(value)
            } catch (parseException: ParseException) {
                parseExceptionListener.invoke(parseException, value)
                propertyInvalidDate
            }
        }
        return value
    }
}
