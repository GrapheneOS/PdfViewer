package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Toolbar toggle by tap.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerToolbarToggleTest {

    private val robot = PdfViewerRobot()

    @Test
    fun tapWebView_hidesToolbar() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            robot.assertToolbarVisible(scenario)

            robot.tapWebView()
            PdfViewerTestUtils.waitForToolbarState(scenario, visible = false)
            robot.assertToolbarHidden(scenario)
        }
    }

    @Test
    fun tapWebView_whenHidden_showsToolbar() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            robot.tapWebView()
            PdfViewerTestUtils.waitForToolbarState(scenario, visible = false)

            robot.tapWebView()
            PdfViewerTestUtils.waitForToolbarState(scenario, visible = true)
            robot.assertToolbarVisible(scenario)
        }
    }
}