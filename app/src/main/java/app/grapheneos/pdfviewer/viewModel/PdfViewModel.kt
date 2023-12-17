package app.grapheneos.pdfviewer.viewModel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.webkit.WebView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.core.database.getLongOrNull
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.loader.DocumentPropertiesAsyncTaskLoader
import app.grapheneos.pdfviewer.loader.DocumentProperty
import app.grapheneos.pdfviewer.loader.PDFJsPropertiesToDocumentPropertyConverter
import app.grapheneos.pdfviewer.ui.MAX_ZOOM_RATIO
import app.grapheneos.pdfviewer.ui.MIN_ZOOM_RATIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.io.InputStream

class PdfViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    @OptIn(SavedStateHandleSaveableApi::class)
    class PdfUiState(savedStateHandle: SavedStateHandle) {
        /** WebView */
        val webView: MutableState<WebView?> = mutableStateOf(null)
        /** uri of file */
        var uri: Uri by savedStateHandle.saveable {
            mutableStateOf(Uri.EMPTY)
        }
        /** InputStream of file */
        val inputStream: MutableState<InputStream?> = mutableStateOf(null)
        /** Document state */
        val documentState: MutableState<Int> = mutableIntStateOf(0)
        /** Encrypted document password */
        var encryptedDocumentPassword: String by savedStateHandle.saveable {
            mutableStateOf("")
        }
        /** current page */
        var page: Int by savedStateHandle.saveable {
            mutableIntStateOf(0)
        }
        /** Zoom ratio */
        var zoomRatio: Float by savedStateHandle.saveable {
            mutableFloatStateOf(1f)
        }
        /** Document orientation degrees */
        var documentOrientationDegrees: Int by savedStateHandle.saveable {
            mutableIntStateOf(0)
        }
        /** Number of pages */
        var numPages: Int by mutableIntStateOf(0)
        /** Document properties */
        val documentProperties: MutableMap<DocumentProperty, String> = mutableStateMapOf()
        /** Document name */
        var documentName: String by mutableStateOf("")
    }

    private val _uiState = MutableStateFlow(PdfUiState(savedStateHandle))
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    fun setUri(uri: Uri) {
        _uiState.value.uri = uri
    }

    fun openInputStream(contentResolver: ContentResolver) {
        _uiState.value.inputStream.value = contentResolver.openInputStream(uiState.value.uri)
    }

    fun maybeCloseInputStream() {
        if (uiState.value.inputStream.value != null) {
            _uiState.value.inputStream.value = null
            try {
                _uiState.value.inputStream.value?.close()
            } catch (ignored: IOException) {}
        }
    }

    fun setDocumentState(state: Int) {
        _uiState.value.documentState.value = state
    }

    fun loadPdf(context: Context, snackbarHostState: SnackbarHostState) {
        try {
            if (uiState.value.inputStream.value != null) {
                _uiState.value.inputStream.value?.close()
            }
            openInputStream(context.contentResolver)
        } catch (e: IOException) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_while_opening))
            }
            return
        }

        _uiState.value.documentState.value = 0
        _uiState.value.webView.value?.loadUrl("https://localhost/viewer/index.html")
    }

    fun renderPage(zoom: Int) {
        _uiState.value.webView.value?.evaluateJavascript("onRenderPage($zoom)", null)
    }

    fun setZoomRatio(ratio: Float) {
        _uiState.value.zoomRatio = ratio.coerceAtMost(MAX_ZOOM_RATIO).coerceAtLeast(MIN_ZOOM_RATIO)
    }

    fun zoomIn(value: Float, end: Boolean) {
        if (uiState.value.zoomRatio < MAX_ZOOM_RATIO) {
            _uiState.value.zoomRatio = (_uiState.value.zoomRatio + value).coerceAtMost(MAX_ZOOM_RATIO)

            renderPage(if (end) 1 else 2)
        }
    }

    fun zoomOut(value: Float, end: Boolean) {
        if (uiState.value.zoomRatio > MIN_ZOOM_RATIO) {
            _uiState.value.zoomRatio = (uiState.value.zoomRatio - value).coerceAtLeast(MIN_ZOOM_RATIO)

            renderPage(if (end) 1 else 2)
        }
    }

    fun zoomEnd() {
        renderPage(1)
    }

    fun setPage(page: Int) {
        _uiState.value.page = page
    }

    fun jumpToPageInDocument(selectedPage: Int) {
        if (selectedPage >= 1 && selectedPage <= uiState.value.numPages && uiState.value.page != selectedPage) {
            _uiState.value.page = selectedPage
            renderPage(0)
        }
    }

    fun setNumPages(numPages: Int) {
        _uiState.value.numPages = numPages
    }

    fun documentOrientationChanged(orientationDegreesOffset: Int) {
        _uiState.value.documentOrientationDegrees = (uiState.value.documentOrientationDegrees + orientationDegreesOffset) % 360
        if (uiState.value.documentOrientationDegrees < 0) {
            _uiState.value.documentOrientationDegrees += 360
        }
        renderPage(0)
    }

    fun setDocumentProperties(properties: String, propertyInvalidDate: String, context: Context) {
        if (uiState.value.documentProperties.isNotEmpty()) {
            throw SecurityException("documentProperties not empty")
        }

        _uiState.value.documentProperties.putAll(getFileProperties(context))
        _uiState.value.documentProperties[DocumentProperty.Pages] = uiState.value.numPages.toString()
        try {
            _uiState.value.documentProperties.putAll(PDFJsPropertiesToDocumentPropertyConverter(
                properties,
                propertyInvalidDate,
                parseExceptionListener = { parseException, value ->
                    Log.w(
                        DocumentPropertiesAsyncTaskLoader.TAG,
                        "${parseException.message} for $value at offset: ${parseException.errorOffset}"
                    )
                }
            ).convert())
        } catch (e: JSONException) {
            Log.w(
                DocumentPropertiesAsyncTaskLoader.TAG,
                "invalid properties"
            )
            _uiState.value.documentProperties.clear()
        }

        setDocumentName()
    }

    fun clearDocumentProperties() {
        _uiState.value.documentProperties.clear()
    }

    private fun getFileProperties(context: Context): Map<DocumentProperty, String> {
        val collections = mutableMapOf<DocumentProperty, String>()
        val proj = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
        )

        context.contentResolver.query(
            uiState.value.uri,
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

    fun setDocumentName() {
        if (uiState.value.documentProperties.isEmpty()) {
            _uiState.value.documentName = ""
        } else {
            var fileName = ""
            var title = ""
            for (property in uiState.value.documentProperties.entries) {
                if (property.key == DocumentProperty.FileName) {
                    fileName = property.value
                }
                if (property.key == DocumentProperty.Title) {
                    title = property.value
                }
            }

            _uiState.value.documentName = if (fileName.length > 2) fileName else title
        }
    }

    fun setEncryptedDocumentPassword(password: String) {
        _uiState.value.encryptedDocumentPassword = password
    }

    fun loadPdfWithPassword() {
        _uiState.value.webView.value?.evaluateJavascript("loadDocument()", null)
    }

    fun setWebView(webView: WebView) {
        _uiState.value.webView.value = webView
    }

    fun getWebViewRelease(): Int? {
        val webViewPackage = WebView.getCurrentWebViewPackage()
        val webViewVersionName = webViewPackage?.versionName
        return if (webViewVersionName != null) {
            Integer.parseInt(webViewVersionName.substring(0, webViewVersionName.indexOf(".")))
        } else {
            null
        }
    }

    /** Set uiState to default values */
    fun clearUiState() {
        _uiState.value = PdfUiState(savedStateHandle)
    }

    override fun onCleared() {
        super.onCleared()
        clearUiState()
    }
}
