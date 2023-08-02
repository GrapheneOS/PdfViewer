package app.grapheneos.pdfviewer.loader

import android.content.Context
import android.net.Uri
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader

class DocumentPropertiesAsyncTaskLoader(context: Context, private val mProperties: String, private val mNumPages: Int, private val mUri: Uri):
    AsyncTaskLoader<List<CharSequence>>(context) {

    companion object {
        const val TAG = "DocumentPropertiesLoader"
        const val ID = 1
    }

    //Workaround till PdfViewer is migrated to Kotlin
    fun asLoader() = this as Loader<List<CharSequence>>

    override fun onStartLoading() {
        super.onStartLoading()
        forceLoad()
    }

    override fun loadInBackground(): List<CharSequence> {
        val loader = DocumentPropertiesLoader(
            context,
            mProperties,
            mNumPages,
            mUri
        )
        return loader.loadAsList()
    }
}
