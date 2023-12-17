package app.grapheneos.pdfviewer.loader

import android.content.Context
import android.net.Uri
import androidx.loader.content.AsyncTaskLoader

class DocumentPropertiesAsyncTaskLoader(
    context: Context?,
    private val mProperties: String,
    private val mNumPages: Int,
    private val mUri: Uri
) :
    AsyncTaskLoader<List<CharSequence>?>(context!!) {
    override fun onStartLoading() {
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

    companion object {
        const val TAG: String = "DocumentPropertiesLoader"

        const val ID: Int = 1
    }
}