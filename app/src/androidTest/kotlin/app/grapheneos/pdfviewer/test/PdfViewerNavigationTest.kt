package app.grapheneos.pdfviewer.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.documentName
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Page navigation and JumpToPage dialog.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerNavigationTest {

    private val robot = PdfViewerRobot()

    private fun setupNavigableState(
        scenario: ActivityScenario<PdfViewer>,
        page: Int
    ) {
        scenario.onActivity {
            it.currentPage = page
            it.refreshMenuSync()
        }
    }

    // Next / Previous clicks

    @Test
    fun tapNext_increasesPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 2)

            robot.clickNext()
            scenario.onActivity {
                assertEquals(3, it.currentPage)
            }
        }
    }

    @Test
    fun tapPrevious_decreasesPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 3)

            robot.clickPrevious()
            scenario.onActivity {
                assertEquals(2, it.currentPage)
            }
        }
    }

    @Test
    fun tapNext_updatesMenuState() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = false, nextEnabled = true)
            robot.clickNext()
            scenario.onActivity { it.refreshMenuSync() }
            robot.assertNavigationState(previousEnabled = true, nextEnabled = true)
        }
    }

    // First / Last buttons

    @Test
    fun tapFirst_goesToFirstPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 4)

            robot.click(PdfViewerRobot.AppMenuItem.First)
            scenario.onActivity {
                assertEquals(1, it.currentPage)
            }
        }
    }

    @Test
    fun tapLast_goesToLastPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 2)

            robot.click(PdfViewerRobot.AppMenuItem.Last)
            scenario.onActivity {
                assertEquals(4, it.currentPage)
            }
        }
    }

    // JumpToPageFragment

    @Test
    fun jumpToPageDialog_opensWithCorrectValues() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 3)

            robot.click(PdfViewerRobot.AppMenuItem.JumpToPage)
            robot.assertNumberPickerStateInDialog(minValue = 1, maxValue = 4, currentValue = 3)
        }
    }

    @Test
    fun jumpToPageDialog_okNavigatesToSelectedPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 1)

            robot.click(PdfViewerRobot.AppMenuItem.JumpToPage)
            robot.setNumberPickerValue(3)
            robot.clickDialogOk()

            scenario.onActivity {
                assertEquals(3, it.currentPage)
            }
        }
    }

    @Test
    fun jumpToPageDialog_cancelDoesNotNavigate() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 3)

            robot.click(PdfViewerRobot.AppMenuItem.JumpToPage)
            robot.setNumberPickerValue(4)
            robot.clickDialogCancel()

            scenario.onActivity {
                assertEquals(3, it.currentPage)
            }
        }
    }

    @Test
    fun openSecondDocument_resetsAllStates() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity { it.onJumpToPageInDocument(3) }
            robot.clickRotateClockwise()
            PdfViewerTestUtils.waitForDocumentRotation(scenario, expected = 90)

            robot.performPinchZoomIn()
            val zoomedRatio = robot.getZoomRatio(scenario)
            assertTrue(
                "Zoom should have increased (was $zoomedRatio)",
                zoomedRatio > 1.0f
            )

            Intents.init()
            try {
                val secondUri = PdfViewerLauncher.testAssetUri("test-simple.pdf")
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                    .respondWith(
                        Instrumentation.ActivityResult(
                            Activity.RESULT_OK,
                            Intent().apply { data = secondUri }
                        )
                    )

                robot.click(PdfViewerRobot.AppMenuItem.Open)
            } finally {
                Intents.release()
            }

            PdfViewerTestUtils.waitForDocumentChanged(scenario, expectedPages = 1)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity {
                assertEquals("Page should reset to 1", 1, it.currentPage)
                assertEquals("New document should have 1 page", 1, it.totalPages)
                assertEquals(
                    "Document name should be fro new document",
                    "test-simple.pdf", it.documentName
                )
            }

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")

            assertEquals(
                "Document rotation should reset on new document",
                0, robot.getDocumentRotationDegrees(scenario)
            )

            val newZoom = robot.getZoomRatio(scenario)
            assertTrue(
                "Zoom should reset on new document (was $zoomedRatio, now $newZoom)",
                newZoom < zoomedRatio
            )
        }
    }

    @Test
    fun openDocumentWithoutOutline_afterDocumentWithOutline_hidesOutlineMenuItem() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)
            scenario.onActivity { it.refreshMenuSync() }
            robot.assertMenuItemVisible(scenario, PdfViewerRobot.AppMenuItem.Outline, expected = true)

            Intents.init()
            try {
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                    .respondWith(
                        Instrumentation.ActivityResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                data = PdfViewerLauncher.testAssetUri("test-simple.pdf")
                            }
                        )
                    )
                robot.click(PdfViewerRobot.AppMenuItem.Open)
            } finally {
                Intents.release()
            }

            PdfViewerTestUtils.waitForDocumentChanged(scenario, expectedPages = 1)
            scenario.onActivity { it.refreshMenuSync() }
            robot.assertMenuItemVisible(scenario, PdfViewerRobot.AppMenuItem.Outline, expected = false)
        }
    }

    @Test
    fun openDocumentWithOutline_afterDocumentWithoutOutline_showsOutlineMenuItem() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            scenario.onActivity { it.refreshMenuSync() }
            robot.assertMenuItemVisible(scenario, PdfViewerRobot.AppMenuItem.Outline, expected = false)

            Intents.init()
            try {
                intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                    .respondWith(
                        Instrumentation.ActivityResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                data = PdfViewerLauncher.testAssetUri("test-multipage.pdf")
                            }
                        )
                    )
                robot.click(PdfViewerRobot.AppMenuItem.Open)
            } finally {
                Intents.release()
            }

            PdfViewerTestUtils.waitForDocumentChanged(scenario, expectedPages = 4)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)
            scenario.onActivity { it.refreshMenuSync() }
            robot.assertMenuItemVisible(scenario, PdfViewerRobot.AppMenuItem.Outline, expected = true)
        }
    }
}
