package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfViewerLandscapeTest {

    private val robot = PdfViewerRobot()

    @Test
    fun rotationToLandscape_canvasRefitsNewViewport() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val cssWidth = robot.getCanvasCssWidth(scenario)
            val cssHeight = robot.getCanvasCssHeight(scenario)
            val viewportWidth = robot.getViewportWidth(scenario)
            val viewportHeight = robot.getViewportHeight(scenario)

            assertTrue("Canvas CSS width ($cssWidth) should be > 0", cssWidth > 0)
            assertTrue("Canvas CSS height ($cssHeight) should be > 0", cssHeight > 0)
            assertTrue(
                "Canvas CSS width ($cssWidth) should fit viewport ($viewportWidth)",
                cssWidth <= viewportWidth + 2
            )
            assertTrue(
                "Canvas CSS height ($cssHeight) should fit viewport ($viewportHeight)",
                cssHeight <= viewportHeight + 2
            )
        }
    }

    @Test
    fun rotationToLandscape_preservesTextLayerAlignment() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            robot.assertTextLayerAligned(scenario)

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }

    @Test
    fun pageNumber_survivesOrientationChange() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity { it.currentPage = 3 }

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity { assertEquals(3, it.currentPage) }
            robot.assertBridgePage(scenario, 3)
        }
    }

    @Test
    fun documentRotation_survivesDeviceOrientationChange() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            robot.clickRotateClockwise()
            PdfViewerTestUtils.waitForDocumentRotation(scenario, 90)

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            assertEquals(
                "Document rotation should persist across config change",
                90, robot.getDocumentRotationDegrees(scenario)
            )
        }
    }

    @Test
    fun jumpToPageDialog_preservesPickerValueAcrossRotation() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.currentPage = 3
                it.refreshMenuSync()
            }

            robot.click(PdfViewerRobot.AppMenuItem.JumpToPage)
            robot.setNumberPickerValue(4)

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)

            robot.assertNumberPickerStateInDialog(
                minValue = 1, maxValue = 4, currentValue = 4
            )
        }
    }

    @Test
    fun passwordDialog_survivesRotationWithInitialState() {
        PdfViewerLauncher.launchWithTestAsset("test-encrypted.pdf").use { scenario ->
            robot.waitForPasswordDialog()

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)
            robot.waitForPasswordDialog()

            robot.assertPasswordDialogShown()
            robot.assertPasswordPositiveButtonEnabled(enabled = false)
        }
    }

    @Test
    fun documentPropertiesDialog_survivesRotation() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            scenario.onActivity { it.refreshMenuSync() }

            robot.click(PdfViewerRobot.AppMenuItem.ViewDocumentProperties)
            robot.assertDocumentPropertyVisible("Test Document")

            PdfViewerTestUtils.setDeviceOrientation(scenario, landscape = true)

            robot.assertDocumentPropertyVisible("Test Document")
            robot.assertDocumentPropertyVisible("Test Author")
        }
    }
}
