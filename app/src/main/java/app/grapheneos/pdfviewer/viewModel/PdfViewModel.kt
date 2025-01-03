package app.grapheneos.pdfviewer.viewModel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.grapheneos.pdfviewer.outline.OutlineNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfViewModel : ViewModel() {

    enum class Status {
        MissingPassword,
        InvalidPassword,
        Validated
    }

    val status: MutableLiveData<Status> = MutableLiveData(Status.MissingPassword)

    val outline: MutableLiveData<List<OutlineNode>?> = MutableLiveData(null)

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }

    fun passwordMissing() {
        status.postValue(Status.MissingPassword)
    }

    fun invalid() {
        status.postValue(Status.InvalidPassword)
    }

    fun validated() {
        status.postValue(Status.Validated)
    }

    fun clearOutline() {
        outline.postValue(null)
    }

    fun parseOutlineString(outlineString: String?) {
        if (outlineString != null) {
            scope.launch { outline.postValue(OutlineNode.parse(outlineString)) }
        } else {
            outline.postValue(emptyList())
        }
    }
}
