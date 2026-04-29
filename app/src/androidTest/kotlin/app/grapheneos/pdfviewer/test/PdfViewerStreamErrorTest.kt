package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfViewerStreamErrorTest {

    private val robot = PdfViewerRobot()

    /**
     * FIXME: Currently, when the stream errors mid-read, the viewer silently
     * stays in a partial-load state with no feedback.
     */
    @Test
    fun loadPdf_streamFailsMidRead_currentlyFailsSilently() {
        PdfViewerLauncher.launchWithFailingMidRead(
            "test-multipage.pdf",
            bytesBeforeFailure = 1024
        ).use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            PdfViewerTestUtils.assertStableCondition(
                duration = 3_000,
                description = { "Document should not load" }
            ) {
                var notLoaded = false
                scenario.onActivity {
                    notLoaded = it.documentProperties == null &&
                            it.totalPages == 0
                }
                notLoaded
            }

            robot.assertWebViewVisible()
            robot.assertCrashUiHidden()
        }
    }
}