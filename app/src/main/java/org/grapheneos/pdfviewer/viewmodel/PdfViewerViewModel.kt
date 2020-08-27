package org.grapheneos.pdfviewer.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "PdfViewerViewModel"

private const val STATE_URI = "uri"
private const val STATE_PAGE = "page"
private const val STATE_ZOOM_RATIO = "zoomRatio"
private const val STATE_ORIENTATION_DEGREES = "orientationDegrees"

class PdfViewerViewModel(private val state: SavedStateHandle) : ViewModel() {
    private val documentProperties: MutableLiveData<List<CharSequence>> by lazy {
        MutableLiveData<List<CharSequence>>(ArrayList())
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
            zoomRatio = get(STATE_ZOOM_RATIO) ?: 1f
            documentOrientationDegrees = get(STATE_ORIENTATION_DEGREES) ?: 0
        }
    }

    fun saveState() {
        state.run {
            set(STATE_URI, uri)
            set(STATE_PAGE, page)
            set(STATE_ZOOM_RATIO, zoomRatio)
            set(STATE_ORIENTATION_DEGREES, documentOrientationDegrees)
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
            val propertiesList = parsePropertiesString(propertiesString, context, numPages, uri)
            if (propertiesList == null) {
                clearDocumentProperties()
            } else {
                documentProperties.postValue(
                    (documentProperties.value as ArrayList<CharSequence>).apply {
                        clear()
                        addAll(propertiesList)
                    }
                )
            }
        }
    }
}
