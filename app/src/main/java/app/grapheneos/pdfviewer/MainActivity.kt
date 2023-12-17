package app.grapheneos.pdfviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.grapheneos.pdfviewer.ui.theme.PdfViewerTheme
import app.grapheneos.pdfviewer.viewModel.PdfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val isActionView = intent.action == Intent.ACTION_VIEW
            if (isActionView) {
                val pdfViewModel: PdfViewModel = viewModel()
                intent.data?.let {
                    pdfViewModel.clearUiState()
                    pdfViewModel.setUri(it)
                    pdfViewModel.setPage(1)
                    pdfViewModel.setEncryptedDocumentPassword("")
                }
            }

            PdfViewerTheme {
                PdfViewerApp(
                    modifier = Modifier,
                    isActionView = isActionView,
                )
            }
        }
    }
}
