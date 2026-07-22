package app.grapheneos.pdfviewer.test

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.RetryRules
import app.grapheneos.pdfviewer.continuousMode
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Continuous vs single-page view toggle.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerContinuousModeTest {

    @get:Rule
    val retryRules = RetryRules()

    private val robot = PdfViewerRobot()

    @Test
    fun toggle_hidesOtherPagesAndRestoresOnReEnable() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            // Default: continuous scrolling on, so all pages are laid out.
            scenario.onActivity { assertTrue(it.continuousMode) }
            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "all pages visible in continuous mode" }
            ) { displayedCount(scenario) == 4 }

            // Switch to single-page mode via the menu.
            scenario.onActivity { it.refreshMenuSync() }
            robot.clickContinuousScroll()

            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "single-page mode shows only the current page" }
            ) {
                var mode = true
                scenario.onActivity { mode = it.continuousMode }
                !mode && displayedCount(scenario) == 1
            }

            // Navigation in single-page mode keeps exactly one page shown.
            PdfViewerTestUtils.evaluateJs(scenario, "globalThis.scrollToPage(3)")
            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "page 3 visible alone in single-page mode" }
            ) {
                var page = 0
                scenario.onActivity { page = it.currentPage }
                page == 3 && displayedCount(scenario) == 1
            }

            // Re-enable continuous mode: all pages come back.
            scenario.onActivity { it.refreshMenuSync() }
            robot.clickContinuousScroll()
            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "all pages visible again after re-enabling" }
            ) {
                var mode = false
                scenario.onActivity { mode = it.continuousMode }
                mode && displayedCount(scenario) == 4
            }
        }
    }

    @Test
    fun singlePageMode_survivesRecreation() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            scenario.onActivity { it.continuousMode = false }
            PdfViewerTestUtils.evaluateJs(scenario, "setContinuousMode()")

            scenario.recreate()

            scenario.onActivity { assertFalse(it.continuousMode) }
        }
    }

    private fun displayedCount(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(
            scenario,
            "Array.from(document.querySelectorAll('.page-wrapper'))" +
                ".filter(w => getComputedStyle(w).display !== 'none').length"
        )
        return result.toIntOrNull() ?: 0
    }
}
