package app.grapheneos.pdfviewer.viewModel

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.grapheneos.pdfviewer.PreferenceHelper
import app.grapheneos.pdfviewer.outline.OutlineNode
import app.grapheneos.pdfviewer.preferences.PdfPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    var currentFingerprint: String? = null

    override fun onCleared() {
        super.onCleared()
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
     * Get saved page by fingerprint
     *
     * @return saved page number, or null if no fingerprint or no saved page
     */
    fun getPageByFingerprintBlocking(): Int? {
        return runBlocking {
            currentFingerprint?.let { repository.getPageForFile(it) }
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

        if (includeHashMapping && currentFingerprint != null) {
            repository.updatePagePosition(currentFingerprint!!, page)
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
    }

    private fun releaseUriPermissionIfHeld(uri: Uri) {
        try {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
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
            .any { permission ->
                permission.uri == uri && permission.isReadPermission
            }
    }
}
