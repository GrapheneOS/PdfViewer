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
import app.grapheneos.pdfviewer.search.DocumentSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
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
        private const val STATE_DOCUMENT_PROPERTIES = "documentProperties"
        private const val STATE_DOCUMENT_NAME = "documentName"
        private const val STATE_SEARCH_ACTIVE = "searchActive"
        private const val STATE_SEARCH_QUERY = "searchQuery"
        private const val SEARCH_DEBOUNCE_MS = 200L
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
    fun setWebViewCrashed(value: Boolean) {
        _webViewCrashed.value = value
        // Nothing can be highlighted or scrolled without a renderer, so do not leave an
        // auto-focusing find bar with a keyboard on top of the crash screen.
        if (value) closeSearch()
    }

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

    /**
     * Everything the find bar renders. [ordinal] is 1-based across the whole document and 0 when
     * nothing is selected; [scanning] means the corpus is still being extracted, so [total] is a
     * lower bound.
     */
    data class SearchResult(
        val total: Int = 0,
        val ordinal: Int = 0,
        val activePage: Int = 0,
        val activeIndex: Int = -1,
        val scannedPages: Int = 0,
        val scanning: Boolean = false,
        val truncated: Boolean = false,
        /** Changes with the query, so a repaint is triggered even when the active match does not. */
        val version: Int = 0
    )

    private val search = DocumentSearch()
    // The active match is read and written from the main thread (stepMatch), a background
    // dispatcher (runSearch) and a WebView binder thread (setPageText), so every read-modify-write
    // of the pair goes through this lock.
    private val searchLock = Any()
    private var activePage = 0
    private var activeIndex = -1
    private var searchJob: Job? = null

    /** Bumped per document so a sweep started for a previous one cannot write into the corpus. */
    @Volatile
    private var searchGeneration = 0
    val currentSearchGeneration: Int get() = searchGeneration

    val searchActive: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(STATE_SEARCH_ACTIVE, false)

    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(STATE_SEARCH_QUERY, "")
    fun setSearchQuery(value: String) { savedStateHandle[STATE_SEARCH_QUERY] = value }

    private val _searchResult = MutableStateFlow(SearchResult())
    val searchResult: StateFlow<SearchResult> = _searchResult.asStateFlow()

    fun openSearch() {
        setToolbarVisible(true)
        savedStateHandle[STATE_SEARCH_ACTIVE] = true
    }

    fun closeSearch() {
        savedStateHandle[STATE_SEARCH_ACTIVE] = false
        savedStateHandle[STATE_SEARCH_QUERY] = ""
        searchJob?.cancel()
        search.setQuery("")
        synchronized(searchLock) {
            activePage = 0
            activeIndex = -1
        }
        _searchResult.value = SearchResult()
    }

    /** True once every page has been extracted, or the index hit its cap. */
    fun extractionComplete(): Boolean {
        val stats = search.stats()
        return _numPages.value > 0 && (stats.scannedPages >= _numPages.value || stats.truncated)
    }

    fun tuplesFor(page: Int): String = search.tuplesFor(page)

    /**
     * Called on a WebView binder thread. Returns false to stop extraction.
     *
     * [generation] identifies the document the sweep was started for. `loadUrl` does not tear the
     * JS context down synchronously, so a sweep for the previous document can still land calls
     * here after [resetDocumentState] has cleared the corpus; without the token those pages would
     * be re-inserted and then never overwritten, and document A's text would be searched and
     * highlighted as if it were document B's.
     */
    fun setPageText(page: Int, itemsJson: String, generation: Int): Boolean {
        if (generation != searchGeneration) return false
        // Bounds-checked because this is reachable from the renderer, where untrusted PDF content
        // is parsed. Empty pages add no characters, so without this a loop over made-up page
        // numbers would grow the map without ever tripping the character cap.
        if (page < 1 || page > _numPages.value) return false
        val more = try {
            search.addPage(page, itemsJson)
        } catch (_: JSONException) {
            true
        }
        publishSearch()
        return more
    }

    fun runSearch(query: String, fromPage: Int) {
        // A configuration change re-runs the effect that calls this. Re-scanning would clear the
        // index and show 0/0 for the length of a full sweep, for no gain.
        if (search.isIndexed(query)) {
            publishSearch()
            return
        }
        searchJob?.cancel()
        if (query.isEmpty()) {
            search.setQuery("")
            synchronized(searchLock) {
                activePage = 0
                activeIndex = -1
            }
            publishSearch()
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            // The delay is the debounce: the next keystroke cancels this job before it elapses.
            delay(SEARCH_DEBOUNCE_MS)
            search.setQuery(query)
            synchronized(searchLock) {
                activePage = 0
                activeIndex = -1
            }
            publishSearch()
            // Driven by the pages actually held, not by the reported page count: pdf.js takes
            // that from the PDF's own /Count, so a hostile document can claim a hundred million
            // pages and turn this into an unbounded loop. Pages that arrive later are matched on
            // arrival by addPage.
            val numbers = search.pageNumbers()
            val start = numbers.indexOfFirst { it >= fromPage }.coerceAtLeast(0)
            for (i in numbers.indices) {
                ensureActive()
                search.matchPage(numbers[(start + i) % numbers.size])
                publishSearch()
            }
        }
    }

    fun stepMatch(forward: Boolean) {
        synchronized(searchLock) {
            val next = search.step(activePage, activeIndex, forward) ?: return
            activePage = next.first
            activeIndex = next.second
        }
        publishSearch()
    }

    /**
     * Publishes are serialised on [searchLock] as a whole, including the assignment. Without that,
     * a thread that read an early snapshot can be descheduled and then overwrite a later, correct
     * one, leaving the find bar showing a stale count with nothing to trigger another publish.
     */
    private fun publishSearch() = synchronized(searchLock) {
        if (activeIndex < 0) {
            search.firstPageFrom(page.value.coerceAtLeast(1))?.let {
                activePage = it
                activeIndex = 0
            }
        } else if (search.countOn(activePage) <= activeIndex) {
            // The page the selection was on no longer matches, e.g. the query changed.
            activePage = 0
            activeIndex = -1
        }
        val stats = search.stats()
        _searchResult.value = SearchResult(
            total = stats.total,
            ordinal = if (activeIndex < 0) 0 else {
                search.ordinalBefore(activePage) + activeIndex + 1
            },
            activePage = activePage,
            activeIndex = activeIndex,
            scannedPages = stats.scannedPages,
            scanning = searchQuery.value.isNotEmpty() && !extractionComplete(),
            truncated = stats.truncated,
            version = stats.version
        )
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
    /** Keyboard height. Under edge-to-edge the WebView is not resized, so scroll-to-match has to
     * subtract this itself or the active match lands behind the IME. */
    @Volatile var insetIme = 0f

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
        encryptedDocumentPassword = ""
        clearOutline()
        clearDocumentProperties()
        dismissPasswordPrompt()
        searchGeneration++
        search.clear()
        closeSearch()
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
