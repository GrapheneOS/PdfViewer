package app.grapheneos.pdfviewer.test

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.PdfJsChannel.Companion.MAX_ZOOM_RATIO
import app.grapheneos.pdfviewer.PdfJsChannel.Companion.MIN_ZOOM_RATIO
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.TestTags
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.documentName
import app.grapheneos.pdfviewer.testrules.OrientationRules
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import app.grapheneos.pdfviewer.zoomRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Page navigation, JumpToPage dialog and CustomZoomDialog.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerNavigationTest {

    private val composeRule = RetryableComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryRules())
        .around(OrientationRules())
        .around(composeRule)

    private val robot = PdfViewerRobot(composeRule)

    @Before
    fun setup() {
        PdfViewerTestUtils.init(composeRule)
    }

    private fun setupNavigableState(
        scenario: ActivityScenario<PdfViewer>,
        page: Int
    ) {
        scenario.onActivity {
            it.currentPage = page
        }
        composeRule.waitForIdle()
    }

    // Next / Previous clicks

    @Test
    fun tapNext_increasesPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
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
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
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

            robot.assertNavigationState(previousEnabled = false, nextEnabled = true)
            robot.clickNext()

            PdfViewerTestUtils.pollUntil(
                description = { "Page did not change to 2" }
            ) {
                var page = 0
                scenario.onActivity {
                    page = it.currentPage
                }
                page == 2
            }

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
            robot.assertJumpToPageDialogState(currentValue = 3, maxValue = 4)
        }
    }

    @Test
    fun jumpToPageDialog_okNavigatesToSelectedPage() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 1)

            robot.click(PdfViewerRobot.AppMenuItem.JumpToPage)
            robot.setJumpToPageValue(3)
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
            robot.setJumpToPageValue(4)
            robot.clickDialogCancel()

            scenario.onActivity {
                assertEquals(3, it.currentPage)
            }
        }
    }

    @Test
    fun jumpToPageDialog_prefilledCurrentPageIsSelected_typingReplaces() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            setupNavigableState(scenario, page = 3)

            robot.click(PdfViewerRobot.AppMenuItem.JumpToPage)

            robot.typeJumpToPageWithoutClearing(2)

            composeRule.onNodeWithTag(TestTags.JUMP_TO_PAGE_FIELD)
                .assertTextEquals("2")

            robot.clickDialogOk()
            scenario.onActivity {
                assertEquals(2, it.currentPage)
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

            robot.performPinchZoomIn(scenario)
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
            robot.assertMenuItemVisible(PdfViewerRobot.AppMenuItem.Outline, expected = true)

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
            robot.assertMenuItemVisible(PdfViewerRobot.AppMenuItem.Outline, expected = false)
        }
    }

    @Test
    fun openDocumentWithOutline_afterDocumentWithoutOutline_showsOutlineMenuItem() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            robot.assertMenuItemVisible(PdfViewerRobot.AppMenuItem.Outline, expected = false)

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
            robot.assertMenuItemVisible(PdfViewerRobot.AppMenuItem.Outline, expected = true)
        }
    }

    @Test
    fun zoomButtons_menuStaysOpenAfterClick() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.clickMenuZoomIn()
            robot.assertZoomRowVisible()
        }
    }

    @Test
    fun zoomInButton_disabledAtMaxZoom() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity {
                it.zoomRatio = MAX_ZOOM_RATIO
            }
            composeRule.waitForIdle()

            robot.assertZoomInEnabled(false)
            robot.assertZoomOutEnabled(true)
        }
    }

    @Test
    fun zoomOutButton_disabledAtMinZoom() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity {
                it.zoomRatio = MIN_ZOOM_RATIO
            }
            composeRule.waitForIdle()

            robot.assertZoomOutEnabled(false)
            robot.assertZoomInEnabled(true)
        }
    }

    @Test
    fun customZoomDialog_opensWithCurrentZoom() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val currentZoom = robot.getZoomRatio(scenario)
            val expectedPercent = (currentZoom * 100).roundToInt()

            robot.clickZoomPercentage()
            robot.assertCustomZoomDialogText(expectedPercent)
        }
    }

    @Test
    fun customZoomDialog_okAppliesZoom() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.clickZoomPercentage()
            robot.setCustomZoomValue(200)
            robot.clickDialogOk()

            PdfViewerTestUtils.pollUntil(
                description = {
                    "Zoom ratio should be 2.0 after custom zoom " +
                            "(was ${robot.getZoomRatio(scenario)})"
                }
            ) {
                abs(robot.getZoomRatio(scenario) - 2.0f) < 0.01f
            }
        }
    }

    @Test
    fun customZoomDialog_cancelDoesNotChangeZoom() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val initialZoom = robot.getZoomRatio(scenario)

            robot.clickZoomPercentage()
            robot.setCustomZoomValue(200)
            robot.clickDialogCancel()
            composeRule.waitForIdle()

            val afterZoom = robot.getZoomRatio(scenario)
            assertEquals(
                "Zoom should not change after dialog cancel",
                initialZoom, afterZoom, 0.001f
            )
        }
    }

    @Test
    fun customZoomDialog_prefilledValueIsSelected_typingReplaces() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.clickZoomPercentage()
            robot.typeCustomZoom(200)

            composeRule.onNodeWithTag(TestTags.CUSTOM_ZOOM_FIELD)
                .assertTextContains("200")

            robot.clickDialogOk()

            PdfViewerTestUtils.pollUntil(
                description = {
                    "Zoom ratio should be 2.0 after typing replacement " +
                            "(was ${robot.getZoomRatio(scenario)})"
                }
            ) {
                abs(robot.getZoomRatio(scenario) - 2.0f) < 0.01f
            }
        }
    }

    @Test
    fun customZoomDialog_rejectsInvalidInput() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.clickZoomPercentage()
            robot.setCustomZoomValue(0)
            robot.assertDialogOkEnabled(false)
        }
    }
}
