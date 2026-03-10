package app.grapheneos.pdfviewer


import android.content.Context

object PreferenceHelper {

    const val PREF_NAME = "app_prefs"
    const val KEY_RESUME_LAST_DOCUMENT = "resume_last_document"

    fun isResumeLastDocumentEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_RESUME_LAST_DOCUMENT, true)
    }
}