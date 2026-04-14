package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.crashed
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whole WebView crash recovery flow.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerCrashRecoveryTest {

    private val robot = PdfViewerRobot()

    @Test
    fun reloadButton_dismissesCrashUiAndRecovers() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            scenario.onActivity { it.crashed = true }
            scenario.recreate()

            robot.assertCrashUiVisible()
            robot.clickReload()

            PdfViewerTestUtils.waitForCrashUiDismissed()

            robot.assertCrashUiHidden()
            robot.assertWebViewVisible()
        }
    }
}