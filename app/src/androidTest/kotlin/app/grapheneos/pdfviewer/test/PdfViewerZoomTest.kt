package app.grapheneos.pdfviewer.test

import androidx.test.core.app.ActivityScenario
import app.grapheneos.pdfviewer.PdfJsChannel.Companion.MIN_ZOOM_RATIO
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.testrules.OrientationRules
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Shared zoom behavior across supported input methods.
 */
@RunWith(Parameterized::class)
class PdfViewerZoomTest(private val zoomInput: ZoomInput) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): List<Array<Any>> = ZoomInput.entries.map { arrayOf(it) }
    }

    enum class ZoomInput(private val displayName: String) {
        TOUCH("touch") {
            override fun zoomIn(robot: PdfViewerRobot, scenario: ActivityScenario<PdfViewer>) {
                robot.performPinchZoomIn(scenario)
            }

            override fun zoomOut(robot: PdfViewerRobot, scenario: ActivityScenario<PdfViewer>) {
                robot.performPinchZoomOut(scenario)
            }

            override fun zoomOutUntilClamp(
                robot: PdfViewerRobot,
                scenario: ActivityScenario<PdfViewer>
            ) {
                repeat(5) {
                    robot.performPinchZoomOut(scenario, speed = 1500)
                }
            }
        },

        CTRL_MOUSE_WHEEL("ctrl_mouse_wheel") {
            override fun zoomIn(robot: PdfViewerRobot, scenario: ActivityScenario<PdfViewer>) {
                robot.performCtrlMouseWheelZoom(scenario, zoomIn = true)
            }

            override fun zoomOut(robot: PdfViewerRobot, scenario: ActivityScenario<PdfViewer>) {
                robot.performCtrlMouseWheelZoom(scenario, zoomIn = false)
            }
        };

        abstract fun zoomIn(robot: PdfViewerRobot, scenario: ActivityScenario<PdfViewer>)

        abstract fun zoomOut(robot: PdfViewerRobot, scenario: ActivityScenario<PdfViewer>)

        open fun zoomOutUntilClamp(
            robot: PdfViewerRobot,
            scenario: ActivityScenario<PdfViewer>
        ) {
            repeat(16) {
                zoomOut(robot, scenario)
            }
        }

        override fun toString(): String = displayName
    }

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
    fun zoomIn_increasesDimensionsAndPreservesTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")

            val initialWidth = robot.getCanvasCssWidth(scenario)
            val initialHeight = robot.getCanvasCssHeight(scenario)
            val initialZoomRatio = robot.getZoomRatio(scenario)

            zoomInput.zoomIn(robot, scenario)

            waitForZoomRatioAbove(scenario, initialZoomRatio, "$zoomInput zoom in")
            PdfViewerTestUtils.waitForCanvasCssDimensionsChanged(
                scenario,
                initialWidth,
                initialHeight
            )

            assertCanvasCssDimensionsIncreased(
                scenario,
                initialWidth,
                initialHeight,
                "$zoomInput zoom in"
            )
            assertTextLayerStillRendered(scenario)
        }
    }

    @Test
    fun zoomOut_decreasesDimensionsAndPreservesTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val defaultWidth = robot.getCanvasCssWidth(scenario)
            val defaultHeight = robot.getCanvasCssHeight(scenario)
            val defaultZoomRatio = robot.getZoomRatio(scenario)

            zoomInput.zoomIn(robot, scenario)
            waitForZoomRatioAbove(scenario, defaultZoomRatio, "$zoomInput setup zoom in")
            PdfViewerTestUtils.waitForCanvasCssDimensionsChanged(
                scenario,
                defaultWidth,
                defaultHeight
            )
            assertTextLayerStillRendered(scenario)

            val initialWidth = robot.getCanvasCssWidth(scenario)
            val initialHeight = robot.getCanvasCssHeight(scenario)
            val initialZoomRatio = robot.getZoomRatio(scenario)

            zoomInput.zoomOut(robot, scenario)

            waitForZoomRatioBelow(scenario, initialZoomRatio, "$zoomInput zoom out")
            PdfViewerTestUtils.waitForCanvasCssDimensionsChanged(
                scenario,
                initialWidth,
                initialHeight
            )

            assertCanvasCssDimensionsDecreased(
                scenario,
                initialWidth,
                initialHeight,
                "$zoomInput zoom out"
            )
            assertTextLayerStillRendered(scenario)
        }
    }

    @Test
    fun zoomOut_clampsToMinimumZoomRatio() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            zoomInput.zoomOutUntilClamp(robot, scenario)

            PdfViewerTestUtils.pollUntil(
                timeout = 15_000,
                description = {
                    "$zoomInput zoom out did not clamp to MIN_ZOOM_RATIO " +
                            "(was ${robot.getZoomRatio(scenario)})"
                }
            ) {
                abs(robot.getZoomRatio(scenario) - MIN_ZOOM_RATIO) < 0.001f
            }
        }
    }

    private fun waitForZoomRatioAbove(
        scenario: ActivityScenario<PdfViewer>,
        initialZoomRatio: Float,
        action: String
    ) {
        PdfViewerTestUtils.pollUntil(
            timeout = 15_000,
            description = {
                "Zoom ratio should increase after $action " +
                        "(initial=$initialZoomRatio, current=${robot.getZoomRatio(scenario)})"
            }
        ) {
            robot.getZoomRatio(scenario) > initialZoomRatio
        }
    }

    private fun waitForZoomRatioBelow(
        scenario: ActivityScenario<PdfViewer>,
        initialZoomRatio: Float,
        action: String
    ) {
        PdfViewerTestUtils.pollUntil(
            timeout = 15_000,
            description = {
                "Zoom ratio should decrease after $action " +
                        "(initial=$initialZoomRatio, current=${robot.getZoomRatio(scenario)})"
            }
        ) {
            robot.getZoomRatio(scenario) < initialZoomRatio
        }
    }

    private fun assertCanvasCssDimensionsIncreased(
        scenario: ActivityScenario<PdfViewer>,
        initialWidth: Int,
        initialHeight: Int,
        action: String
    ) {
        val zoomedWidth = robot.getCanvasCssWidth(scenario)
        val zoomedHeight = robot.getCanvasCssHeight(scenario)
        assertTrue(
            "Canvas CSS width should increase after $action ($initialWidth -> $zoomedWidth)",
            zoomedWidth > initialWidth
        )
        assertTrue(
            "Canvas CSS height should increase after $action ($initialHeight -> $zoomedHeight)",
            zoomedHeight > initialHeight
        )
    }

    private fun assertCanvasCssDimensionsDecreased(
        scenario: ActivityScenario<PdfViewer>,
        initialWidth: Int,
        initialHeight: Int,
        action: String
    ) {
        val zoomedWidth = robot.getCanvasCssWidth(scenario)
        val zoomedHeight = robot.getCanvasCssHeight(scenario)
        assertTrue(
            "Canvas CSS width should decrease after $action ($initialWidth -> $zoomedWidth)",
            zoomedWidth < initialWidth
        )
        assertTrue(
            "Canvas CSS height should decrease after $action ($initialHeight -> $zoomedHeight)",
            zoomedHeight < initialHeight
        )
    }

    private fun assertTextLayerStillRendered(scenario: ActivityScenario<PdfViewer>) {
        PdfViewerTestUtils.waitForCanvasRendered(scenario)
        PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
        robot.assertTextLayerAligned(scenario)
    }
}
