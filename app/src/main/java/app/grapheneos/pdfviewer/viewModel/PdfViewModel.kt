package app.grapheneos.pdfviewer.viewModel

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.grapheneos.pdfviewer.PreferenceHelper
import app.grapheneos.pdfviewer.hashing.FileHash
import app.grapheneos.pdfviewer.outline.OutlineNode
import app.grapheneos.pdfviewer.preferences.PdfPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PdfViewModel"
    }

    enum class PasswordStatus {
        MissingPassword,
        InvalidPassword,
        Validated
    }

    val passwordStatus: MutableLiveData<PasswordStatus> = MutableLiveData(PasswordStatus.MissingPassword)

    sealed class OutlineStatus {
        data object NotLoaded : OutlineStatus()
        data object NoOutline : OutlineStatus()
        data object Available : OutlineStatus()
        data object Requested : OutlineStatus()
        data object Loading : OutlineStatus()
        class Loaded(val outline: List<OutlineNode>) : OutlineStatus()
    }

    // Outline state as LiveData, since we require the Activity to observe so it can use the
    // WebView to get outline. Lazily loaded, and will be cached until a different PDF is loaded.
    val outline: MutableLiveData<OutlineStatus> = MutableLiveData(OutlineStatus.NotLoaded)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val repository = PdfPreferencesRepository(application)
    private val contentResolver: ContentResolver = application.contentResolver

    private var hashCalculationDeferred: Deferred<Unit>? = null
    private var currentFileHash: String? = null

    override fun onCleared() {
        super.onCleared()
        hashCalculationDeferred?.cancel()
        scope.cancel()
    }

    fun hasOutline(): Boolean {
        return outline.value != OutlineStatus.NoOutline &&
                outline.value != OutlineStatus.NotLoaded
    }

    fun shouldAbortOutline(): Boolean {
        return outline.value is OutlineStatus.Requested || outline.value is OutlineStatus.Loading
    }

    fun requestOutlineIfNotAvailable() {
        if (outline.value == OutlineStatus.Available) {
            outline.value = OutlineStatus.Requested
        }
    }

    fun setLoadingOutline() {
        outline.value = OutlineStatus.Loading
    }

    fun passwordMissing() {
        passwordStatus.postValue(PasswordStatus.MissingPassword)
    }

    fun invalid() {
        passwordStatus.postValue(PasswordStatus.InvalidPassword)
    }

    fun validated() {
        passwordStatus.postValue(PasswordStatus.Validated)
    }

    fun clearOutline() {
        outline.postValue(OutlineStatus.NotLoaded)
        scope.coroutineContext.cancelChildren()
    }

    fun parseOutlineString(outlineString: String?) {
        if (outlineString != null) {
            scope.launch {
                outline.postValue(OutlineStatus.Loaded(OutlineNode.parse(outlineString)))
            }
        } else {
            outline.postValue(OutlineStatus.Loaded(emptyList()))
        }
    }

    fun setHasOutline(hasOutline: Boolean) {
        if (outline.value == OutlineStatus.NotLoaded) {
            outline.postValue(if (hasOutline) OutlineStatus.Available else OutlineStatus.NoOutline)
        }
    }

    /**
     * Load initial PDF state if preference is enabled
     */
    fun maybeLoadPdfStateBlocking(): PdfPreferencesRepository.PdfState? {
        return runBlocking {
            if (PreferenceHelper.isResumeLastDocumentEnabled(getApplication())) {
                repository.pdfStateFlow.first()
            } else {
                null
            }
        }
    }

    /**
     * Calculate file hash
     *
     * @param uri Document URI
     */
    fun calculateHash(uri: Uri) {
        hashCalculationDeferred?.cancel()
        currentFileHash = null

        hashCalculationDeferred = viewModelScope.async {
            val hash = FileHash.calculateFileHash(uri, contentResolver)
            currentFileHash = hash
        }
    }

    /**
     * Wait for hash calculation to complete and get saved page
     *
     * @return saved page number, or null if no hash or no saved page
     */
    fun getPageByHashBlocking(): Int? {
        return runBlocking {
            hashCalculationDeferred?.await()

            if (currentFileHash != null) {
                repository.getPageForFile(currentFileHash!!)
            } else {
                null
            }
        }
    }

    /**
     * Save PDF state
     *
     * @param uriString Document URI string
     * @param page Current page
     * @param includeHashMapping Whether to also save hash->page mapping
     */
    fun savePdfState(uriString: String, page: Int, includeHashMapping: Boolean) {
        viewModelScope.launch {
            savePdfStateCommon(uriString, page, includeHashMapping)
        }
    }

    /**
     * Save PDF state (blocking)
     * Use in onStop to ensure completion before process death
     *
     * @param uriString Document URI string
     * @param page Current page
     * @param includeHashMapping Whether to also save hash->page mapping
     */
    fun savePdfStateBlocking(uriString: String, page: Int, includeHashMapping: Boolean) {
        runBlocking {
            savePdfStateCommon(uriString, page, includeHashMapping)
        }
    }

    private suspend fun savePdfStateCommon(uriString: String, page: Int, includeHashMapping: Boolean) {
        val oldState = repository.pdfStateFlow.first()
        val oldUri = oldState.lastOpenedUri

        repository.saveLastOpened(uriString, page)

        // Release old permission if it's different from new URI
        if (oldUri != null && oldUri != uriString) {
            releaseUriPermissionIfHeld(oldUri.toUri())
        }

        if (includeHashMapping && currentFileHash != null) {
            repository.updatePagePosition(currentFileHash!!, page)
        }
    }

    fun clearLastOpened() {
        viewModelScope.launch {
            val state = repository.pdfStateFlow.first()
            val currentUri = state.lastOpenedUri

            repository.clearLastOpened()

            if (currentUri != null) {
                releaseUriPermissionIfHeld(currentUri.toUri())
            }
        }
    }

    fun prepareNewPdf(uri: Uri, page: Int) {
        if (takePersistableUriPermission(uri)) {
            savePdfState(uri.toString(), page, false)
        }
        calculateHash(uri)
    }

    private fun releaseUriPermissionIfHeld(uri: Uri) {
        try {
            // Find if we have persisted permission for this URI
            val persistedPermissions = contentResolver.persistedUriPermissions
            val permission = persistedPermissions.find { it.uri == uri } ?: return

            // Build flags for permissions we actually hold
            var flags = 0
            if (permission.isReadPermission) {
                flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            // We only requested READ, but to be safe check WRITE too
            if (permission.isWritePermission) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

            // Release if we have any permissions
            if (flags != 0) {
                contentResolver.releasePersistableUriPermission(uri, flags)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission release failed with SecurityException", e)
        } catch (e: Exception) {
            Log.w(TAG, "Permission release failed", e)
        }
    }

    private fun takePersistableUriPermission(uri: Uri): Boolean {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    fun hasUriPermission(uri: Uri?): Boolean {
        return contentResolver.persistedUriPermissions
            .stream()
            .anyMatch { permission: UriPermission? -> permission!!.uri == uri && permission.isReadPermission }
    }
}
