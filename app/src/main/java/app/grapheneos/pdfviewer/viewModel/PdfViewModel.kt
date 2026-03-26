package app.grapheneos.pdfviewer.viewModel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.grapheneos.pdfviewer.loader.DocumentPropertiesLoader
import app.grapheneos.pdfviewer.loader.DocumentProperty
import app.grapheneos.pdfviewer.outline.OutlineNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

class PdfViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        private const val STATE_URI: String = "uri"
        private const val STATE_PAGE: String = "page"
        private const val STATE_ZOOM_RATIO: String = "zoomRatio"
        private const val STATE_DOCUMENT_ORIENTATION_DEGREES: String = "documentOrientationDegrees"
    }

    @Volatile
    var uri: Uri? = savedStateHandle[STATE_URI]
        set(value) {
            field = value
            savedStateHandle[STATE_URI] = value
        }

    @Volatile
    var page: Int = savedStateHandle[STATE_PAGE] ?: 1
        set(value) {
            field = value
            savedStateHandle[STATE_PAGE] = value
        }

    @Volatile
    var zoomRatio: Float = savedStateHandle[STATE_ZOOM_RATIO] ?: 0f
        set(value) {
            field = value
            savedStateHandle[STATE_ZOOM_RATIO] = value
        }

    @Volatile
    var documentOrientationDegrees: Int = savedStateHandle[STATE_DOCUMENT_ORIENTATION_DEGREES] ?: 0
        set(value) {
            field = value
            savedStateHandle[STATE_DOCUMENT_ORIENTATION_DEGREES] = value
        }

    @Volatile
    var numPages: Int = 0

    @Volatile
    var encryptedDocumentPassword: String = ""

    var webViewCrashed: Boolean = false

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

    private val _saveError = MutableLiveData<Boolean>()
    val saveError: LiveData<Boolean> get() = _saveError
    private val _documentProperties = MutableLiveData<Map<DocumentProperty, String>?>()
    val documentProperties: LiveData<Map<DocumentProperty, String>?> get() = _documentProperties
    private var documentPropertiesLoading = false

    private val scope = CoroutineScope(Dispatchers.IO)

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

    fun clearSaveError() {
        _saveError.value = false
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
                        withContext(Dispatchers.Main) {
                            _saveError.value = true
                        }
                    }
                    else -> throw e
                }
            }
        }
    }

    fun loadDocumentProperties(properties: String, numPages: Int, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val loader = DocumentPropertiesLoader(getApplication(), properties, numPages, uri)
            val result = loader.load()
            withContext(Dispatchers.Main) {
                if (documentPropertiesLoading) {
                    _documentProperties.value = result
                }
            }
        }
        documentPropertiesLoading = true
    }

    fun clearDocumentProperties() {
        _documentProperties.value = null
        documentPropertiesLoading = false
    }
}
