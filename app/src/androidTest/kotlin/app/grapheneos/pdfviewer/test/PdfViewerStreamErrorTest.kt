package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfViewerStreamErrorTest {

    private val robot = PdfViewerRobot()

    @Test
    fun loadPdf_streamFailsMidRead_showsError() {
        PdfViewerLauncher.launchWithFailingMidRead(
            "test-multipage.pdf",
            bytesBeforeFailure = 1024
        ).use { scenario ->
            PdfViewerTestUtils.waitForSnackbar(PdfViewerRobot.SnackbarMessage.FileOpenError)

            scenario.onActivity {
                assertNull(it.documentProperties)
                assertEquals(0, it.totalPages)
            }

            robot.assertWebViewVisible()
            robot.assertCrashUiHidden()
        }
    }
}