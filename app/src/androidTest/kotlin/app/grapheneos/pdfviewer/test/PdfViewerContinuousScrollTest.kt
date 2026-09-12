package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.pageFitMode
import app.grapheneos.pdfviewer.testrules.OrientationRules
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Page fitting mode and zoom integration tests.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerPageFitModeTest {

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

    @Test
    fun fitWidthMode_isDefaultForNewDocument() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                assertEquals(2, it.pageFitMode)
            }
        }
    }

    @Test
    fun fitPageMode_canvasFitsWithinViewport() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.clickFitPage()

            PdfViewerTestUtils.pollUntil(
                timeout = 10_000,
                description = { "Canvas should re-render in fit-page mode" }
            ) {
                val cssWidth = robot.getCanvasCssWidth(scenario)
                val cssHeight = robot.getCanvasCssHeight(scenario)
                val viewportWidth = robot.getViewportWidth(scenario)
                val viewportHeight = robot.getViewportHeight(scenario)
                cssWidth > 0 && cssHeight > 0 &&
                    cssWidth <= viewportWidth + 2 && cssHeight <= viewportHeight + 2
            }

            val cssWidth = robot.getCanvasCssWidth(scenario)
            val cssHeight = robot.getCanvasCssHeight(scenario)
            val viewportWidth = robot.getViewportWidth(scenario)
            val viewportHeight = robot.getViewportHeight(scenario)

            assertTrue(
                "Canvas width ($cssWidth) should fit viewport ($viewportWidth)",
                cssWidth <= viewportWidth + 2
            )
            assertTrue(
                "Canvas height ($cssHeight) should fit viewport ($viewportHeight)",
                cssHeight <= viewportHeight + 2
            )
        }
    }

    @Test
    fun fitWidthMode_canvasFillsViewportWidth() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val cssWidth = robot.getCanvasCssWidth(scenario)
            val viewportWidth = robot.getViewportWidth(scenario)

            assertTrue(
                "Canvas width ($cssWidth) should fill viewport width ($viewportWidth)",
                cssWidth >= viewportWidth - 2
            )
        }
    }

    @Test
    fun fitModeMenuItems_areVisibleAfterLoad() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            robot.assertMenuItemVisible(
                PdfViewerRobot.AppMenuItem.FitFree, expected = true
            )
            robot.assertMenuItemVisible(
                PdfViewerRobot.AppMenuItem.FitPage, expected = true
            )
            robot.assertMenuItemVisible(
                PdfViewerRobot.AppMenuItem.FitWidth, expected = true
            )
        }
    }

    @Test
    fun pageFitMode_survivesRecreation() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.pageFitMode = 1 // fit page
            }

            scenario.recreate()

            scenario.onActivity {
                assertEquals(1, it.pageFitMode)
            }
        }
    }

    @Test
    fun pinchZoom_switchesToFreeMode() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            assertEquals(
                "Should start in fit-width mode",
                2, robot.getPageFitMode(scenario)
            )

            robot.performPinchZoomIn(scenario)

            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "Should switch to free zoom mode after pinch" }
            ) {
                robot.getPageFitMode(scenario) == 0
            }

            assertEquals(
                "Pinch zoom should switch to free mode",
                0, robot.getPageFitMode(scenario)
            )
        }
    }
}
