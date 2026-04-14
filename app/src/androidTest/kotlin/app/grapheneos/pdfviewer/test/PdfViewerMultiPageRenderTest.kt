package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.outlineStatus
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests multi-page PDF with navigation rendering, page count, and outline.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerMultiPageRenderTest {

    private val robot = PdfViewerRobot()

    // Activity state

    @Test
    fun documentLoad_setsCorrectPageCount() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                assertEquals(4, it.totalPages)
            }
        }
    }

    // Navigation rendering

    @Test
    fun navigateToNextPage_rendersNewContent() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page One Content")

            robot.clickNext()

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page Two Content")
            robot.assertBridgePage(scenario, 2)
        }
    }

    @Test
    fun navigateBackToPreviousPage_rendersOriginalContent() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page One Content")

            robot.clickNext()
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page Two Content")

            robot.clickPrevious()
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page One Content")
            robot.assertBridgePage(scenario, 1)
        }
    }

    @Test
    fun navigateFirstAndLast_rendersCorrectContent() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page One Content")

            robot.clickMenuItem(R.id.action_last, R.string.action_last)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page Four Content")
            robot.assertBridgePage(scenario, 4)

            robot.clickMenuItem(R.id.action_first, R.string.action_first)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page One Content")
            robot.assertBridgePage(scenario, 1)
        }
    }

    @Test
    fun navigationButtonStates_updateAfterRenderedNavigation() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity { it.refreshMenuSync() }
            robot.assertNavigationState(previousEnabled = false, nextEnabled = true)

            robot.clickMenuItem(R.id.action_last, R.string.action_last)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page Four Content")

            scenario.onActivity { it.refreshMenuSync() }
            robot.assertNavigationState(previousEnabled = true, nextEnabled = false)
        }
    }

    // Outline

    @Test
    fun documentWithOutline_outlineIsAvailable() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)

            scenario.onActivity {
                assertTrue("hasOutline should be true", it.viewModel.hasOutline())
                assertEquals(
                    PdfViewModel.OutlineStatus.Available,
                    it.outlineStatus
                )
            }

            robot.assertMenuItemVisible(scenario, R.id.action_outline, expected = true)
        }
    }

    @Test
    fun outlineRequest_producesLoadedEntries() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)

            robot.requestOutline(scenario)
            PdfViewerTestUtils.waitForOutlineLoaded(scenario)

            val outlineSize = robot.getLoadedOutlineSize(scenario)
            assertEquals(
                "Outline should have 4 entries (one per section)",
                4, outlineSize
            )
        }
    }

    @Test
    fun outlineNavigation_tapEntry_navigatesAndRenders() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)

            scenario.onActivity { it.refreshMenuSync() }

            robot.openOutlineFragment()
            robot.waitForOutlineEntries()
            robot.clickOutlineEntry(1)

            PdfViewerTestUtils.assertTextLayerContent(scenario, "Page Two Content")
            robot.assertBridgePage(scenario, 2)

            scenario.onActivity {
                assertEquals(2, it.currentPage)
            }
        }
    }
}