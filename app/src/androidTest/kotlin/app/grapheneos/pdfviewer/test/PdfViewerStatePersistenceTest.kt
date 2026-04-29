package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.crashed
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Configuration change and state persistence
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerStatePersistenceTest {

    private val robot = PdfViewerRobot()

    @Test
    fun pageNumber_survivesRecreation() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.currentPage = 7
            }

            scenario.recreate()
            scenario.onActivity {
                assertEquals(7, it.currentPage)
            }
        }
    }

    @Test
    fun crashState_survivesRecreation_showsCrashUi() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            scenario.onActivity {
                it.crashed = true
            }

            scenario.recreate()
            robot.assertCrashUiVisible()
        }
    }

    @Test
    fun documentReloads_afterRecreation() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                assertEquals(1, it.currentPage)
            }

            scenario.recreate()
            PdfViewerTestUtils.waitForDocumentLoaded()
            scenario.onActivity {
                assertEquals(1, it.currentPage)
            }
        }
    }
}
