package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.MIN_ZOOM_RATIO
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Document load and rendering verification.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerRenderTest {

    private val robot = PdfViewerRobot()

    @Test
    fun documentLoad_setsCorrectActivityStates() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                assertEquals(1, it.totalPages)
                assertNotNull(it.documentProperties)

                val props = it.documentProperties!!.map { p -> p.toString() }
                assertTrue(
                    "Properties should contain correct title",
                    props.any { p -> p.contains("Test Document") }
                )
                assertTrue(
                    "Properties should contain correct author",
                    props.any { p -> p.contains("Test Author") }
                )
            }
            robot.assertToolbarTitle(scenario, "test-simple.pdf")
        }
    }

    @Test
    fun documentLoad_rendersCanvasAndTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.assertCanvasRendered(scenario)

            val width = robot.getCanvasWidth(scenario)
            val height = robot.getCanvasHeight(scenario)
            assertTrue("Canvas width ($width) should be > 0", width > 0)
            assertTrue("Canvas height ($height) should be > 0", height > 0)

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }

    @Test
    fun documentLoad_bridgeValuesAreConsistent() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity {
                assertEquals(1, it.currentPage)
            }

            robot.assertBridgePage(scenario, 1)
        }
    }

    @Test
    fun rotation_changesCanvasOrientationAndPreservesTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")

            val originalWidth = robot.getCanvasWidth(scenario)
            val originalHeight = robot.getCanvasHeight(scenario)
            assertTrue(
                "Initial page should be portrait (w=$originalWidth < h=$originalHeight)",
                originalWidth < originalHeight
            )

            robot.clickRotateClockwise()
            PdfViewerTestUtils.waitForCanvasDimensionsChanged(
                scenario, originalWidth, originalHeight
            )

            val rotatedWidth = robot.getCanvasWidth(scenario)
            val rotatedHeight = robot.getCanvasHeight(scenario)
            assertTrue(
                "After 90° clockwise, page should be landscape (w=$rotatedWidth > h=$rotatedHeight)",
                rotatedWidth > rotatedHeight
            )
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)

            robot.clickRotateCounterclockwise()
            PdfViewerTestUtils.waitForCanvasDimensionsChanged(
                scenario, rotatedWidth, rotatedHeight
            )

            assertEquals(
                "Width should return to original",
                originalWidth, robot.getCanvasWidth(scenario)
            )
            assertEquals(
                "Height should return to original",
                originalHeight, robot.getCanvasHeight(scenario)
            )
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }

    @Test
    fun zoomIn_increasesDimensionsAndPreservesTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")

            val initialWidth = robot.getCanvasCssWidth(scenario)
            val initialHeight = robot.getCanvasCssHeight(scenario)

            robot.performPinchZoomIn(scenario)

            PdfViewerTestUtils.waitForCanvasCssDimensionsChanged(
                scenario, initialWidth, initialHeight
            )

            val zoomedWidth = robot.getCanvasCssWidth(scenario)
            val zoomedHeight = robot.getCanvasCssHeight(scenario)
            assertTrue(
                "Canvas CSS width should increase after zoom in " +
                        "($initialWidth → $zoomedWidth)",
                zoomedWidth > initialWidth
            )
            assertTrue(
                "Canvas CSS height should increase after zoom in " +
                        "($initialHeight → $zoomedHeight)",
                zoomedHeight > initialHeight
            )

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }

    @Test
    fun zoomOut_decreasesDimensionsAndPreservesTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val defaultWidth = robot.getCanvasCssWidth(scenario)
            val defaultHeight = robot.getCanvasCssHeight(scenario)

            robot.performPinchZoomIn(scenario)
            PdfViewerTestUtils.waitForCanvasCssDimensionsChanged(
                scenario, defaultWidth, defaultHeight
            )
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)

            val initialWidth = robot.getCanvasCssWidth(scenario)
            val initialHeight = robot.getCanvasCssHeight(scenario)
            val initialZoomRatio = robot.getZoomRatio(scenario)

            robot.performPinchZoomOut(scenario)
            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = {
                    "Zoom ratio should decrease after zoom out " +
                            "(initial=$initialZoomRatio, current=${robot.getZoomRatio(scenario)})"
                }
            ) {
                robot.getZoomRatio(scenario) < initialZoomRatio
            }

            PdfViewerTestUtils.waitForCanvasCssDimensionsChanged(
                scenario, initialWidth, initialHeight
            )

            val zoomedWidth = robot.getCanvasCssWidth(scenario)
            val zoomedHeight = robot.getCanvasCssHeight(scenario)
            assertTrue(
                "Canvas CSS width should decrease after zoom out " +
                        "($initialWidth → $zoomedWidth)",
                zoomedWidth < initialWidth
            )
            assertTrue(
                "Canvas CSS height should decrease after zoom out " +
                        "($initialHeight → $zoomedHeight)",
                zoomedHeight < initialHeight
            )

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }

    @Test
    fun zoomOut_clampsToMinimumZoomRatio() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            repeat(5) { robot.performPinchZoomOut(scenario, speed = 1500) }

            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = {
                    "Zoom ratio did not clamp to MIN_ZOOM_RATIO " +
                            "(was ${robot.getZoomRatio(scenario)})"
                }
            ) {
                abs(robot.getZoomRatio(scenario) - MIN_ZOOM_RATIO) < 0.001f
            }
        }
    }

    @Test
    fun setZoomRatio_clampsToMinimumZoomRatio() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val clampedZoom = PdfViewerTestUtils.evaluateJs(
                scenario,
                """
                    (function() {
                        channel.setZoomRatio(-1);
                        return channel.getZoomRatio();
                    })()
                """.trimIndent()
            ).toFloat()

            assertTrue(
                "Zoom ratio did not clamp to MIN_ZOOM_RATIO " +
                        "(was $clampedZoom)",
                abs(clampedZoom - MIN_ZOOM_RATIO) < 0.001f
            )
        }
    }

    @Test
    fun documentPropertiesDialog_showsExpectedRows() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            scenario.onActivity { it.refreshMenuSync() }

            robot.click(PdfViewerRobot.AppMenuItem.ViewDocumentProperties)

            robot.assertDocumentPropertyVisible("Test Document")
            robot.assertDocumentPropertyVisible("Test Author")
        }
    }
}
