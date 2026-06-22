package app.grapheneos.pdfviewer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import app.grapheneos.pdfviewer.ui.PdfViewerTheme
import app.grapheneos.pdfviewer.viewModel.PdfViewModel

class PdfViewer : ComponentActivity() {

    companion object {
        private const val TAG = "PdfViewer"
        const val PDF_MIME = "application/pdf"
    }

    val viewModel: PdfViewModel by viewModels()

    @VisibleForTesting
    internal var webView: WebView? = null

    @VisibleForTesting
    fun onJumpToPageInDocument(selectedPage: Int) {
        jumpToPage(viewModel, webView, selectedPage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        var initialMimeError = false
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) {
            val type = intent.type
            if (type != null && type != PDF_MIME) {
                initialMimeError = true
            } else {
                if (type == null) {
                    Log.w(TAG, "MIME type is null, but we'll try to load it anyway")
                }
                viewModel.setUri(intent.data)
                viewModel.resetDocumentState()
            }
        }

        setContent {
            PdfViewerTheme {
                PdfViewerScreen(
                    viewModel = viewModel,
                    initialMimeError = initialMimeError,
                    onRequestRecreate = { recreate() },
                    onWebViewCreated = { webView = it },
                    onWebViewDestroyed = { webView = null }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.maybeCloseInputStream()
    }
}
