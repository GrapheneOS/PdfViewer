package app.grapheneos.pdfviewer

import androidx.annotation.IdRes
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.appbar.MaterialToolbar

/**
 * Test-only accessors for internal states.
 *
 * All tests should read/write activity states through these extensions.
 */

var PdfViewer.currentPage: Int
    get() = mPage
    set(value) {
        mPage = value
    }

var PdfViewer.totalPages: Int
    get() = mNumPages
    set(value) {
        mNumPages = value
    }

var PdfViewer.crashed: Boolean
    get() = webViewCrashed
    set(value) {
        webViewCrashed = value
    }

var PdfViewer.documentProperties: List<CharSequence>?
    get() = mDocumentProperties
    set(value) {
        mDocumentProperties = value
    }

var PdfViewer.outlineStatus: PdfViewModel.OutlineStatus
    get() = viewModel.outline.value ?: PdfViewModel.OutlineStatus.NotLoaded
    set(value) {
        viewModel.outline.value = value
    }

/**
 * Synchronously refreshes menu item states by calling [onPrepareOptionsMenu]
 * with live [Menu]. [invalidateOptionsMenu] schedules update using Choreographer,
 * which Espresso does not wait for.
 */
fun PdfViewer.refreshMenuSync() {
    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
    onPrepareOptionsMenu(toolbar.menu)
}

fun PdfViewer.isMenuItemEnabled(@IdRes id: Int): Boolean {
    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
    return toolbar.menu.findItem(id)?.isEnabled ?: false
}

fun PdfViewer.isMenuItemVisible(@IdRes id: Int): Boolean {
    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
    return toolbar.menu.findItem(id)?.isVisible ?: false
}
