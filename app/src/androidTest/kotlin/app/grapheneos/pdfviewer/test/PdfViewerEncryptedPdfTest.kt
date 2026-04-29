package app.grapheneos.pdfviewer.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
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

    @Test
    fun openEncryptedPdf_afterNonEncrypted_promptsForPassword() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            Intents.init()
            try {
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                    .respondWith(
                        Instrumentation.ActivityResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                data = PdfViewerLauncher.testAssetUri("test-encrypted.pdf")
                            }
                        )
                    )
                robot.click(PdfViewerRobot.AppMenuItem.Open)
            } finally {
                Intents.release()
            }

            robot.waitForPasswordDialog()
            robot.typePassword("testpass")
            robot.clickPasswordPositiveButton()

            robot.waitForPasswordDialogDismissed()
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Password-Protected Content")

            scenario.onActivity {
                assertEquals(1, it.totalPages)
            }
        }
    }
}
