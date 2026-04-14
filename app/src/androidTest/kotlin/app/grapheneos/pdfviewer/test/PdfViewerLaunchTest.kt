package app.grapheneos.pdfviewer.test

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies bootstraps correctly under different intent configurations
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerLaunchTest {

    private val robot = PdfViewerRobot()

    @Test
    fun coldLaunch_noIntent_showsDefaultUiState() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            robot.assertWebViewVisible()
            robot.assertCrashUiHidden()

            scenario.onActivity {
                assertEquals(0, it.currentPage)
                assertEquals(0, it.totalPages)
            }
        }
    }

    @Test
    fun launch_withValidPdfIntent_initiatesLoading() {
        val uri = Uri.parse("content://localhost/test.pdf")
        PdfViewerLauncher.launchWithPdf(uri).use { scenario ->
            robot.assertWebViewVisible()
            robot.assertCrashUiHidden()

            scenario.onActivity {
                assertEquals(1, it.currentPage)
            }
        }
    }

    @Test
    fun launch_withInvalidMime_showsError() {
        val uri = Uri.parse("content://localhost/test.png")
        PdfViewerLauncher.launchWithMimeType(uri, "image/png").use { scenario ->
            robot.assertSnackbar(R.string.invalid_mime_type)

            scenario.onActivity {
                assertEquals(0, it.currentPage)
            }
        }
    }

    @Test
    fun launch_withFileUri_showsLegacyFileUriSnackbar() {
        val uri = Uri.parse("file:///sdcard/Documents/test.pdf")
        PdfViewerLauncher.launchWithPdf(uri).use {
            robot.assertSnackbar(R.string.legacy_file_uri)
        }
    }

    @Test
    fun launch_withNullMime_proceedsWithLoading() {
        val uri = Uri.parse("content://localhost/test.pdf")
        PdfViewerLauncher.launchWithNullMimeType(uri).use { scenario ->
            robot.assertWebViewVisible()

            scenario.onActivity {
                assertEquals(1, it.currentPage)
            }
        }
    }
}