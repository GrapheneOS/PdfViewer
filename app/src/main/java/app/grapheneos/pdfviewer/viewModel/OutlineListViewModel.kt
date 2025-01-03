package app.grapheneos.pdfviewer.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.grapheneos.pdfviewer.outline.OutlineNode
import kotlin.math.abs

class OutlineListViewModel : ViewModel() {
    private val outlineStack = mutableListOf<OutlineNode>()
    private val scrollPositionStack = mutableListOf<Int>()
    var lastRemovedPosition = 0
        private set

    private var isSetup = false

    val currentChild = MutableLiveData<OutlineNode?>(null)

    fun getSubtitleString() = outlineStack.lastOrNull()?.title?.trim()

    fun setupDocument(topLevel: List<OutlineNode>, currentPage: Int) {
        if (isSetup) return
        isSetup = true

        currentChild.postValue(null)
        outlineStack.clear()
        scrollPositionStack.clear()

        // if no exact match, binarySearchBy returns the position where it would be found
        if (currentPage > 0) {
            val position = topLevel.binarySearchBy(currentPage) { it.pageNumber }
            lastRemovedPosition = abs(position - 2).coerceAtLeast(0)
        }
    }

    fun setCurrent(current: OutlineNode, position: Int) {
        outlineStack.add(current)
        scrollPositionStack.add(position)
        currentChild.postValue(current)
    }

    fun goBack() {
        outlineStack.removeLastOrNull()
        lastRemovedPosition = scrollPositionStack.removeLastOrNull() ?: 0
        currentChild.postValue(outlineStack.lastOrNull())
    }

    fun hasPrevious(): Boolean = outlineStack.isNotEmpty()
}