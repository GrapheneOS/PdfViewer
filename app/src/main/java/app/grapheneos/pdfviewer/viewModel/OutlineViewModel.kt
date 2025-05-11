package app.grapheneos.pdfviewer.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.grapheneos.pdfviewer.outline.OutlineNode

class OutlineViewModel : ViewModel() {
    private val outlineStack = mutableListOf<OutlineNode>()

    private var isSetup = false

    sealed class Action {
        data object Idle : Action()
        data object Back : Action()
        data object Close : Action()
        data class ViewChildren(val parent: OutlineNode, val firstOpen: Boolean = false) : Action()
        data class OpenPage(val page: Int) : Action()
    }

    val currentAction = MutableLiveData<Action>(Action.Idle)

    fun submitAction(action: Action) {
        var finalAction = action

        when (action) {
            Action.Idle, Action.Close, is Action.OpenPage -> {}
            Action.Back -> {
                goBack()
                if (!hasPrevious()) {
                    finalAction = Action.Close
                }
            }
            is Action.ViewChildren -> {
                setCurrent(action.parent)
            }
        }

        if (currentAction.value == Action.Idle && finalAction == Action.Idle) {
            return
        }
        
        currentAction.value = finalAction
    }

    val currentChild = MutableLiveData<OutlineNode?>(null)

    fun findNodeOrNull(id: Int): OutlineNode? = outlineStack.find { it.id == id }

    fun getSubtitleString() = outlineStack.lastOrNull()?.title?.trim() ?: docTitle

    private var docTitle = ""

    fun setupDocument(topLevel: List<OutlineNode>, currentPage: Int, title: String) {
        if (isSetup) {
            return
        }
        isSetup = true
        docTitle = title

        outlineStack.clear()

        val fakeOutline = OutlineNode(-1, title, 1, topLevel)
        submitAction(Action.ViewChildren(fakeOutline, firstOpen = true))
    }

    private fun setCurrent(current: OutlineNode) {
        outlineStack.add(current)
        currentChild.postValue(current)
    }

    private fun goBack() {
        outlineStack.removeLastOrNull()
        currentChild.postValue(outlineStack.lastOrNull())
    }

    fun hasPrevious(): Boolean = outlineStack.size >= 1
}
