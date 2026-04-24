package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests encrypted PDF interactions.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerEncryptedPdfTest {

    private val robot = PdfViewerRobot()

    @Test
    fun encryptedPdf_wrongThenCorrectPassword() {
        PdfViewerLauncher.launchWithTestAsset("test-encrypted.pdf").use { scenario ->
            robot.waitForPasswordDialog()

            robot.typePassword("somepass")
            robot.assertPasswordPositiveButtonEnabled(enabled = true)
            robot.clickPasswordPositiveButton()

            robot.waitForPasswordError("invalid password")
            robot.assertPasswordPositiveButtonEnabled(enabled = false)

            robot.typePassword("testpass")
            robot.assertPasswordPositiveButtonEnabled(enabled = true)
            robot.clickPasswordPositiveButton()

            robot.waitForPasswordDialogDismissed()
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.assertCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Password-Protected Content")

            scenario.onActivity {
                assertEquals(
                    "Encrypted PDF should have 1 page", 1, it.totalPages
                )
                assertNotNull(
                    "Document properties should be populated after unlock",
                    it.documentProperties
                )
            }

            robot.assertBridgePage(scenario, 1)
        }
    }

    @Test
    fun encryptedPdf_correctPassword_loadsAndRenders() {
        PdfViewerLauncher.launchWithTestAsset("test-encrypted.pdf").use { scenario ->
            robot.waitForPasswordDialog()

            robot.typePassword("testpass")
            robot.clickPasswordPositiveButton()

            robot.waitForPasswordDialogDismissed()
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.assertCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Password-Protected Content")
            robot.assertTextLayerAligned(scenario)

            scenario.onActivity {
                assertEquals(1, it.totalPages)
                assertNotNull(it.documentProperties)

                val props = it.documentProperties!!.map { p -> p.toString() }
                assertTrue(
                    "Properties should contain the PDF title",
                    props.any { p -> p.contains("Encrypted Document") }
                )
            }
        }
    }
}
