package app.grapheneos.pdfviewer

import app.grapheneos.pdfviewer.properties.DocumentProperty
import app.grapheneos.pdfviewer.viewModel.PdfViewModel

/**
 * Test-only accessors for internal states.
 *
 * All tests should read/write activity states through these extensions.
 */

var PdfViewer.currentPage: Int
    get() = viewModel.page.value
    set(value) {
        viewModel.setPage(value)
    }

var PdfViewer.totalPages: Int
    get() = viewModel.numPages.value
    set(value) {
        viewModel.setNumPages(value)
    }

var PdfViewer.crashed: Boolean
    get() = viewModel.webViewCrashed.value
    set(value) {
        viewModel.setWebViewCrashed(value)
    }

var PdfViewer.documentProperties: Map<DocumentProperty, String>?
    get() = viewModel.documentProperties.value
    set(value) {
        viewModel.setDocumentPropertiesForTest(value)
    }

var PdfViewer.documentName: String
    get() = viewModel.documentName.value
    set(value) {
        viewModel.setDocumentNameForTest(value)
    }

var PdfViewer.outlineStatus: PdfViewModel.OutlineStatus
    get() = viewModel.outline.value
    set(value) {
        viewModel.setOutlineForTest(value)
    }
