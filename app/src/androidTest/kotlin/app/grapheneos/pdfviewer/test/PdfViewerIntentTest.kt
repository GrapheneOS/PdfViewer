package app.grapheneos.pdfviewer.test

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.pdfviewer.documentName
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerRobot.AppMenuItem
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Outbound intent verification and save-as file I/O.
 *
 * Intents are causing scenario.close() to hang for ~45 second.
 * By releasing Intents before the scenario closes, we avoid this.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerIntentTest {

    private val robot = PdfViewerRobot()

    private fun stubAllExternalIntents() {
        intending(not(isInternal()))
            .respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
    }

    /**
     * action_open fires ACTION_OPEN_DOCUMENT with type application/pdf.
     */
    @Test
    fun actionOpen_firesCorrectIntent() {
        PdfViewerLauncher.launchDefault().use {
            Intents.init()
            try {
                stubAllExternalIntents()

                robot.click(AppMenuItem.Open)

                intended(allOf(
                    hasAction(Intent.ACTION_OPEN_DOCUMENT),
                    hasType("application/pdf")
                ))
            } finally {
                Intents.release()
            }
        }
    }

    /**
     * action_share fires ACTION_CHOOSER with EXTRA_INTENT.
     */
    @Test
    fun actionShare_firesChooserWithCorrectPayload() {
        val uri = PdfViewerLauncher.testAssetUri("test-simple.pdf")
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            Intents.init()
            try {
                stubAllExternalIntents()

                robot.click(AppMenuItem.Share)

                intended(
                    allOf(
                        hasAction(Intent.ACTION_CHOOSER),
                        hasExtra(
                            `is`(Intent.EXTRA_INTENT),
                            allOf(
                                hasAction(Intent.ACTION_SEND),
                                hasType("application/pdf"),
                                hasExtra(Intent.EXTRA_STREAM, uri)
                            )
                        )
                    )
                )
            } finally {
                Intents.release()
            }
        }
    }

    /**
     * action_save_as fires ACTION_CREATE_DOCUMENT with type application/pdf
     * and EXTRA_TITLE set to the document name.
     */
    @Test
    fun actionSaveAs_firesCorrectIntent() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.documentName = "test_document.pdf"
                it.refreshMenuSync()
            }

            Intents.init()
            try {
                stubAllExternalIntents()

                robot.click(AppMenuItem.SaveAs)

                intended(allOf(
                    hasAction(Intent.ACTION_CREATE_DOCUMENT),
                    hasType("application/pdf"),
                    hasExtra(Intent.EXTRA_TITLE, "test_document.pdf")
                ))
            } finally {
                Intents.release()
            }
        }
    }

    @Test
    fun saveAs_writesFileSuccessfully() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val outputFile = File(appContext.cacheDir, "test_save_output.pdf")
        if (outputFile.exists()) outputFile.delete()

        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity { it.refreshMenuSync() }

            Intents.init()
            try {
                intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
                    .respondWith(
                        ActivityResult(
                            Activity.RESULT_OK,
                            Intent().apply { data = Uri.fromFile(outputFile) }
                        )
                    )

                robot.click(AppMenuItem.SaveAs)
            } finally {
                Intents.release()
            }

            assertTrue("Output file should exist", outputFile.exists())
            assertTrue("Output file should not be empty", outputFile.length() > 0)

            val testContext = InstrumentationRegistry.getInstrumentation().context
            val originalBytes = testContext.assets.open("test-simple.pdf")
                .use { it.readBytes() }
            val savedBytes = outputFile.readBytes()
            assertArrayEquals(
                "Saved file should match original",
                originalBytes, savedBytes
            )
        }

        if (outputFile.exists()) outputFile.delete()
    }

    @Test
    fun saveAs_showsErrorWhenWriteFails() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.refreshMenuSync()
            }


            Intents.init()
            try {
                intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
                    .respondWith(
                        ActivityResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                data = Uri.parse("content://invalid/nonexistent.pdf")
                            }
                        )
                    )

                robot.click(AppMenuItem.SaveAs)
            } finally {
                Intents.release()
            }

            PdfViewerTestUtils.waitForSnackbar(PdfViewerRobot.SnackbarMessage.SaveError)
        }
    }
}
