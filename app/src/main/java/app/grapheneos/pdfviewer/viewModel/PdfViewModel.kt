package app.grapheneos.pdfviewer.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.grapheneos.pdfviewer.outline.OutlineNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PdfViewModel : ViewModel() {

    enum class PasswordStatus {
        MissingPassword,
        InvalidPassword,
        Validated
    }

    val passwordStatus: MutableLiveData<PasswordStatus> = MutableLiveData(PasswordStatus.MissingPassword)

    sealed class OutlineStatus {
        data object NotLoaded : OutlineStatus()
        data object Requested : OutlineStatus()
        data object Loading : OutlineStatus()
        class Loaded(val outline: List<OutlineNode>) : OutlineStatus()
    }

    // Outline state as LiveData, since we require the Activity to observe so it can use the
    // WebView to get outline
    val outline: MutableLiveData<OutlineStatus> = MutableLiveData(OutlineStatus.NotLoaded)

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun requestOutlineIfNotAvailable() {
        if (outline.value == OutlineStatus.NotLoaded) {
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
}
