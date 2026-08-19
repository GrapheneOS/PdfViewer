package app.grapheneos.pdfviewer.viewModel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.outline.OutlineNode
import app.grapheneos.pdfviewer.properties.DEFAULT_VALUE
import app.grapheneos.pdfviewer.properties.DocumentPropertiesRetriever
import app.grapheneos.pdfviewer.properties.DocumentProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class PdfViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val STATE_URI: String = "uri"
        private const val STATE_PAGE: String = "page"
        private const val STATE_DOCUMENT_ORIENTATION_DEGREES: String = "documentOrientationDegrees"
        private const val STATE_PAGE_FIT_MODE: String = "pageFitMode"
        private const val STATE_CONTINUOUS_MODE: String = "continuousMode"
        private const val STATE_DOCUMENT_PROPERTIES = "documentProperties"
        private const val STATE_DOCUMENT_NAME = "documentName"
    }

    val uri: StateFlow<Uri?> = savedStateHandle.getStateFlow(STATE_URI, null)
    fun setUri(value: Uri?) { savedStateHandle[STATE_URI] = value }

    val page: StateFlow<Int> = savedStateHandle.getStateFlow(STATE_PAGE, 0)
    fun setPage(value: Int) { savedStateHandle[STATE_PAGE] = value }

    val documentOrientationDegrees: StateFlow<Int> =
        savedStateHandle.getStateFlow(STATE_DOCUMENT_ORIENTATION_DEGREES, 0)
    fun setDocumentOrientationDegrees(value: Int) {
        savedStateHandle[STATE_DOCUMENT_ORIENTATION_DEGREES] = value
    }

    val pageFitMode: StateFlow<Int> = savedStateHandle.getStateFlow(STATE_PAGE_FIT_MODE, 1)
    fun setPageFitMode(value: Int) { savedStateHandle[STATE_PAGE_FIT_MODE] = value }

    val continuousMode: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(STATE_CONTINUOUS_MODE, true)
    fun setContinuousMode(value: Boolean) { savedStateHandle[STATE_CONTINUOUS_MODE] = value }

    val documentProperties: StateFlow<Map<DocumentProperty, String>?> =
        savedStateHandle.getStateFlow(STATE_DOCUMENT_PROPERTIES, null)

    val documentName: StateFlow<String> =
        savedStateHandle.getStateFlow(STATE_DOCUMENT_NAME, "")

    private val _numPages = MutableStateFlow(0)
    val numPages: StateFlow<Int> = _numPages.asStateFlow()
    fun setNumPages(value: Int) { _numPages.value = value }

    private val _documentLoaded = MutableStateFlow(false)
    val documentLoaded: StateFlow<Boolean> = _documentLoaded.asStateFlow()
    fun setDocumentLoaded(value: Boolean) { _documentLoaded.value = value }

    private val _webViewCrashed = MutableStateFlow(false)
    val webViewCrashed: StateFlow<Boolean> = _webViewCrashed.asStateFlow()
    fun setWebViewCrashed(value: Boolean) { _webViewCrashed.value = value }

    private val _toolbarVisible = MutableStateFlow(true)
    val toolbarVisible: StateFlow<Boolean> = _toolbarVisible.asStateFlow()
    fun setToolbarVisible(value: Boolean) { _toolbarVisible.value = value }

    private val _pageIndicator = MutableStateFlow(0)
    val pageIndicator: StateFlow<Int> = _pageIndicator.asStateFlow()

    fun showPageIndicator() {
        _pageIndicator.value++
    }

    enum class PasswordStatus {
        MissingPassword,
        InvalidPassword,
        Validated
    }

    private val _passwordStatus = MutableStateFlow(PasswordStatus.MissingPassword)
    val passwordStatus: StateFlow<PasswordStatus> = _passwordStatus.asStateFlow()

    private val _showPasswordDialog = MutableStateFlow(false)
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog.asStateFlow()

    private val _invalidPasswordEvent = Channel<Unit>(Channel.BUFFERED)
    val invalidPasswordEvent: Flow<Unit> = _invalidPasswordEvent.receiveAsFlow()

    fun requestPasswordPrompt() {
        _showPasswordDialog.value = true
        _passwordStatus.value = PasswordStatus.MissingPassword
    }

    fun dismissPasswordPrompt() {
        _showPasswordDialog.value = false
    }

    fun invalidPassword() {
        _passwordStatus.value = PasswordStatus.InvalidPassword
        _invalidPasswordEvent.trySend(Unit)
    }

    fun validated() {
        _passwordStatus.value = PasswordStatus.Validated
        dismissPasswordPrompt()
    }

    private val outlineScope = CoroutineScope(Dispatchers.IO)

    sealed class OutlineStatus {
        data object NotLoaded : OutlineStatus()
        data object NoOutline : OutlineStatus()
        data object Available : OutlineStatus()
        data object Requested : OutlineStatus()
        data object Loading : OutlineStatus()
        class Loaded(val outline: List<OutlineNode>) : OutlineStatus() {
            val lookup: Map<Int, OutlineNode> by lazy {
                buildMap {
                    fun collect(nodes: List<OutlineNode>) {
                        for (node in nodes) {
                            put(node.id, node)
                            collect(node.children)
                        }
                    }
                    collect(outline)
                }
            }
        }
    }

    // Outline status as StateFlow. The composable observes it to trigger evaluateJavascript calls.
    // Lazily loaded, and will be cached until a different PDF is loaded.
    private val _outline = MutableStateFlow<OutlineStatus>(OutlineStatus.NotLoaded)
    val outline: StateFlow<OutlineStatus> = _outline.asStateFlow()

    fun hasOutline(): Boolean {
        val status = _outline.value
        return status != OutlineStatus.NoOutline && status != OutlineStatus.NotLoaded
    }

    fun shouldAbortOutline(): Boolean {
        val status = _outline.value
        return status is OutlineStatus.Requested || status is OutlineStatus.Loading
    }

    fun requestOutlineIfNotAvailable() {
        if (_outline.value == OutlineStatus.Available) {
            _outline.value = OutlineStatus.Requested
        }
    }

    fun setLoadingOutline() {
        _outline.value = OutlineStatus.Loading
    }

    fun setHasOutline(hasOutline: Boolean) {
        if (_outline.value == OutlineStatus.NotLoaded) {
            _outline.value = if (hasOutline) OutlineStatus.Available else OutlineStatus.NoOutline
        }
    }

    fun clearOutline() {
        _outline.value = OutlineStatus.NotLoaded
        outlineScope.coroutineContext.cancelChildren()
    }

    fun parseOutlineString(outlineString: String?) {
        if (outlineString != null) {
            outlineScope.launch {
                _outline.value = OutlineStatus.Loaded(OutlineNode.parse(outlineString))
            }
        } else {
            _outline.value = OutlineStatus.Loaded(emptyList())
        }
    }

    private val _zoomRatio = MutableStateFlow(0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()
    fun setZoomRatio(value: Float) { _zoomRatio.value = value }

    @Volatile var encryptedDocumentPassword: String = ""
    @Volatile var zoomFocusX = 0f
    @Volatile var zoomFocusY = 0f
    @Volatile var insetLeft = 0f
    @Volatile var insetTop = 0f
    @Volatile var insetRight = 0f
    @Volatile var insetBottom = 0f

    val streamLock = Any()
    @Volatile var inputStream: InputStream? = null

    fun maybeCloseInputStream() {
        synchronized(streamLock) {
            val stream = inputStream ?: return
            inputStream = null
            try {
                stream.close()
            } catch (_: IOException) {}
        }
    }

    data class SnackbarEvent(val message: String, val long: Boolean = false)

    private val _snackbarEvent = Channel<SnackbarEvent>(Channel.BUFFERED)
    val snackbarEvent: Flow<SnackbarEvent> = _snackbarEvent.receiveAsFlow()

    fun postSnackbar(@StringRes messageResId: Int) {
        _snackbarEvent.trySend(
            SnackbarEvent(getApplication<Application>().getString(messageResId), long = true)
        )
    }

    fun postSnackbar(text: String) {
        _snackbarEvent.trySend(SnackbarEvent(text))
    }


    val documentPropertiesLoaded = AtomicBoolean(false)
    private var documentPropertiesJob: Job? = null

    fun retrieveDocumentProperties(properties: String, numPages: Int, uri: Uri) {
        documentPropertiesJob?.cancel()
        documentPropertiesJob = viewModelScope.launch(Dispatchers.IO) {
            val retriever = DocumentPropertiesRetriever(getApplication(), properties, numPages, uri)
            val result = retriever.retrieve()
            ensureActive()
            val name = resolveDocumentName(result)
            withContext(Dispatchers.Main) {
                savedStateHandle[STATE_DOCUMENT_PROPERTIES] = result
                savedStateHandle[STATE_DOCUMENT_NAME] = name
            }
        }
    }

    fun clearDocumentProperties() {
        documentPropertiesJob?.cancel()
        documentPropertiesJob = null
        savedStateHandle[STATE_DOCUMENT_PROPERTIES] = null
        savedStateHandle[STATE_DOCUMENT_NAME] = ""
    }

    private fun resolveDocumentName(properties: Map<DocumentProperty, String>): String {
        val fileName = properties[DocumentProperty.FileName].orEmpty()
        if (fileName.isNotEmpty() && fileName != DEFAULT_VALUE) return fileName
        val title = properties[DocumentProperty.Title].orEmpty()
        if (title.isNotEmpty() && title != DEFAULT_VALUE) return title
        return ""
    }

    fun saveDocumentAs(contentResolver: ContentResolver, source: Uri, destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(source)?.use { input ->
                    contentResolver.openOutputStream(destination)?.use { output ->
                        input.copyTo(output)
                    } ?: throw FileNotFoundException()
                } ?: throw FileNotFoundException()
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                when (e) {
                    is IOException, is IllegalArgumentException,
                    is IllegalStateException, is SecurityException -> {
                        postSnackbar(R.string.error_while_saving)
                    }
                    else -> throw e
                }
            }
        }
    }

    fun resetDocumentState() {
        setPage(1)
        _numPages.value = 0
        _zoomRatio.value = 0f
        setDocumentOrientationDegrees(0)
        setPageFitMode(1)
        setContinuousMode(true)
        encryptedDocumentPassword = ""
        clearOutline()
        clearDocumentProperties()
        dismissPasswordPrompt()
    }

    fun prepareForLoad() {
        documentPropertiesLoaded.set(false)
        _documentLoaded.value = false
        _zoomRatio.value = 0f
    }

    fun handleLoadError() {
        maybeCloseInputStream()
        viewModelScope.launch {
            resetDocumentState()
        }
        postSnackbar(R.string.error_while_opening)
    }

    override fun onCleared() {
        maybeCloseInputStream()
        outlineScope.cancel()
    }

    @VisibleForTesting
    fun setDocumentPropertiesForTest(value: Map<DocumentProperty, String>?) {
        savedStateHandle[STATE_DOCUMENT_PROPERTIES] = value
    }

    @VisibleForTesting
    fun setDocumentNameForTest(value: String) {
        savedStateHandle[STATE_DOCUMENT_NAME] = value
    }

    @VisibleForTesting
    fun setOutlineForTest(value: OutlineStatus) {
        _outline.value = value
    }
}
