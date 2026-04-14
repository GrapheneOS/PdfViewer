package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.currentPage
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

            val initialWidth = robot.getCanvasWidth(scenario)
            val initialHeight = robot.getCanvasHeight(scenario)

            robot.performPinchZoomIn()

            PdfViewerTestUtils.waitForCanvasDimensionsChanged(
                scenario, initialWidth, initialHeight
            )

            val zoomedWidth = robot.getCanvasWidth(scenario)
            val zoomedHeight = robot.getCanvasHeight(scenario)
            assertTrue(
                "Canvas width should increase after zoom in " +
                        "($initialWidth → $zoomedWidth)",
                zoomedWidth > initialWidth
            )
            assertTrue(
                "Canvas height should increase after zoom in " +
                        "($initialHeight → $zoomedHeight)",
                zoomedHeight > initialHeight
            )

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }
}