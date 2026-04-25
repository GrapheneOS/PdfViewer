package app.grapheneos.pdfviewer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.test.TestPdfProvider

/**
 * All [PdfViewer] launch configurations.
 */
object PdfViewerLauncher {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    fun launchDefault(): ActivityScenario<PdfViewer> =
        ActivityScenario.launch(PdfViewer::class.java)

    fun launchWithPdf(uri: Uri): ActivityScenario<PdfViewer> =
        launchWithViewAction(uri, "application/pdf")

    fun launchWithFakeUri(): ActivityScenario<PdfViewer> =
        launchWithPdf(Uri.parse("content://app.grapheneos.pdfviewer.test/fake.pdf"))

    fun launchWithMimeType(uri: Uri, mimeType: String): ActivityScenario<PdfViewer> =
        launchWithViewAction(uri, mimeType)

    fun launchWithNullMimeType(uri: Uri): ActivityScenario<PdfViewer> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            setClass(context, PdfViewer::class.java)
        }
        return ActivityScenario.launch(intent)
    }

    fun launchWithTestAsset(assetName: String): ActivityScenario<PdfViewer> {
        val uri = testAssetUri(assetName)
        return launchWithPdf(uri)
    }

    fun testAssetUri(assetName: String): Uri =
        Uri.parse("content://${TestPdfProvider.AUTHORITY}/$assetName")

    private fun launchWithViewAction(uri: Uri, mimeType: String): ActivityScenario<PdfViewer> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            setClass(context, PdfViewer::class.java)
        }
        return ActivityScenario.launch(intent)
    }
}
