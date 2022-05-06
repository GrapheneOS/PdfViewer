package app.grapheneos.pdfviewer.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PasswordStatus : ViewModel() {

    enum class Status {
        MissingPassword,
        InvalidPassword,
        Validated
    }

    val status: MutableLiveData<Status> = MutableLiveData(Status.MissingPassword)

    fun passwordMissing() {
        status.postValue(Status.MissingPassword)
    }

    fun invalid() {
        status.postValue(Status.InvalidPassword)
    }

    fun validated() {
        status.postValue(Status.Validated)
    }

}