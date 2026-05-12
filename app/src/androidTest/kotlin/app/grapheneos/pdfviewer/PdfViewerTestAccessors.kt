package app.grapheneos.pdfviewer

import androidx.annotation.IdRes
import app.grapheneos.pdfviewer.properties.DocumentProperty
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.appbar.MaterialToolbar

/**
 * Test-only accessors for internal states.
 *
 * All tests should read/write activity states through these extensions.
 */

const val MIN_ZOOM_RATIO: Float = 0.2f

var PdfViewer.currentPage: Int
    get() = viewModel.page
    set(value) {
        viewModel.page = value
    }

var PdfViewer.totalPages: Int
    get() = viewModel.numPages
    set(value) {
        viewModel.numPages = value
    }

var PdfViewer.crashed: Boolean
    get() = viewModel.webViewCrashed
    set(value) {
        viewModel.webViewCrashed = value
    }

var PdfViewer.documentProperties: Map<DocumentProperty, String>?
    get() = viewModel.documentProperties.value
    set(value) {
        viewModel.setDocumentPropertiesForTest(value)
    }

var PdfViewer.documentName: String
    get() = viewModel.documentName.value ?: ""
    set(value) {
        viewModel.setDocumentNameForTest(value)
    }

var PdfViewer.outlineStatus: PdfViewModel.OutlineStatus
    get() = viewModel.outline.value ?: PdfViewModel.OutlineStatus.NotLoaded
    set(value) {
        viewModel.outline.value = value
    }

val PdfViewer.toolbar: MaterialToolbar
    get() = findViewById(R.id.toolbar)

/**
 * Synchronously refreshes menu item states by calling [onPrepareOptionsMenu]
 * with live [Menu]. [invalidateOptionsMenu] schedules update using Choreographer,
 * which Espresso does not wait for.
 */
fun PdfViewer.refreshMenuSync() {
    onPrepareOptionsMenu(toolbar.menu)
}

fun PdfViewer.isMenuItemEnabled(@IdRes id: Int): Boolean {
    return toolbar.menu.findItem(id)?.isEnabled ?: false
}

fun PdfViewer.isMenuItemVisible(@IdRes id: Int): Boolean {
    return toolbar.menu.findItem(id)?.isVisible ?: false
}
