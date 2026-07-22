package app.grapheneos.pdfviewer.test

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.totalPages
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
 * In-depth exercise of the continuous-scroll pipeline against a generated
 * 73-page PDF. The tests cover lazy rendering, document order, late-page jumps,
 * pinch focal behavior, rotation, continuous/single toggle, and free-zoom reset.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerBigDocTest {

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
    fun loadsAndLaysOutAllPagesInOrder() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            val total = scenario.onActivityAndReturn { it.totalPages }
            assertTrue("big doc should have many pages", total > 20)

            // buildPages appends wrappers asynchronously after onLoaded; wait until
            // all of them exist before asserting order.
            PdfViewerTestUtils.pollUntil(
                timeout = 20_000,
                description = { "wrappers not all created (expected $total) breakdown=" + wrapperBreakdown(scenario) }
            ) { wrapperCount(scenario) == total }

            // Wrappers must appear in document order regardless of async resolution.
            // evaluateJavascript returns strings JSON-quoted, so strip the quotes.
            val order = eval(scenario,
                "Array.from(document.querySelectorAll('.page-wrapper')).map(w => w.dataset.page).join(',')").trim('"')
            val nums = order.split(",").mapNotNull { it.trim().toIntOrNull() }
            assertEquals("expected $total wrappers", total, nums.size)
            assertEquals("wrappers not in document order",
                (1..total).toList(), nums)
        }
    }

    @Test
    fun continuousScrollAdvancesCurrentPage() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            assertEquals("starts on page 1", 1, scenario.onActivityAndReturn { it.currentPage })

            // Scroll down several viewports; the reported page must advance.
            var lastSeen = 1
            repeat(8) {
                robot.scrollDown(scenario)
                PdfViewerTestUtils.pollUntil(
                    timeout = 10_000,
                    description = { "page did not advance after scroll (last=$lastSeen)" }
                ) {
                    val p = scenario.onActivityAndReturn { it.currentPage }
                    p >= lastSeen + 1
                }
                lastSeen = scenario.onActivityAndReturn { it.currentPage }
            }
            assertTrue("should have scrolled well into the document", lastSeen > 4)
        }
    }

    @Test
    fun jumpToLatePageRendersInOrder() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            val total = scenario.onActivityAndReturn { it.totalPages }
            val target = total - 2

            robot.scrollToPageJs(scenario, target)
            PdfViewerTestUtils.waitForScrollToPage(scenario, target)

            // The target page must actually render (canvas sized) after the jump.
            PdfViewerTestUtils.pollUntil(
                timeout = 15_000,
                description = { "late page $target did not render after jump" }
            ) {
                robot.getCanvasCssHeight(scenario) > 0
            }
            assertEquals(target, scenario.onActivityAndReturn { it.currentPage })
        }
    }

    @Test
    fun pinchZoomSwitchesToFreeAndEnlarges() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            val before = robot.getCanvasCssHeight(scenario)
            assertTrue("baseline canvas height > 0", before > 0)

            robot.performPinchZoomIn(scenario)

            PdfViewerTestUtils.pollUntil(
                timeout = 10_000,
                description = { "pinch did not switch to free zoom" }
            ) { robot.getPageFitMode(scenario) == 0 }

            PdfViewerTestUtils.pollUntil(
                timeout = 10_000,
                description = { "canvas did not enlarge after pinch" }
            ) { robot.getCanvasCssHeight(scenario) > before + 10 }
        }
    }

    @Test
    fun rotationAppliesAndReRenders() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            val before = robot.getCanvasCssHeight(scenario)

            robot.clickRotateClockwise()
            PdfViewerTestUtils.pollUntil(
                timeout = 10_000,
                description = { "document did not rotate to 90" }
            ) { robot.getDocumentRotationDegrees(scenario) == 90 }

            // Page must re-render at the new orientation (height should change).
            PdfViewerTestUtils.pollUntil(
                timeout = 10_000,
                description = { "page did not re-render after rotation" }
            ) {
                val h = robot.getCanvasCssHeight(scenario)
                h > 0 && h != before
            }
        }
    }

    @Test
    fun continuousToggleHidesAndRestoresPages() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            val total = scenario.onActivityAndReturn { it.totalPages }

            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "all pages visible initially" }
            ) { displayedCount(scenario) == total }

            robot.clickContinuousScroll()
            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "single-page mode should show one page" }
            ) { displayedCount(scenario) == 1 }

            robot.clickContinuousScroll()
            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = { "all pages should reappear" }
            ) { displayedCount(scenario) == total }
        }
    }

    @Test
    fun freeZoomReDerivesAfterFitModeCycle() {
        PdfViewerLauncher.launchWithTestAsset("test-large.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            // pinch -> free zoom
            robot.performPinchZoomIn(scenario)
            PdfViewerTestUtils.pollUntil(timeout = 8_000, description = { "not free" }) {
                robot.getPageFitMode(scenario) == 0
            }

            // fit width -> free again (review P2: must not reuse stale ratio / jump to MIN)
            robot.clickFitWidth()
            PdfViewerTestUtils.pollUntil(timeout = 8_000, description = { "not fit-width" }) {
                robot.getPageFitMode(scenario) == 2
            }
            robot.clickFitFree()
            PdfViewerTestUtils.pollUntil(timeout = 8_000, description = { "not free again" }) {
                robot.getPageFitMode(scenario) == 0
            }

            // Re-derived free zoom must be well above MIN_ZOOM_RATIO (0.2), i.e. not a
            // jump-to-min caused by a stale/zero ratio disagreement.
            val zoom = eval(scenario, "channel.getZoomRatio()").toFloatOrNull() ?: 0f
            assertTrue("free zoom $zoom should re-derive above MIN (0.2)", zoom > 0.2f)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
        }
    }

    private fun displayedCount(scenario: ActivityScenario<PdfViewer>): Int {
        val r = eval(scenario,
            "Array.from(document.querySelectorAll('.page-wrapper'))" +
                ".filter(w => getComputedStyle(w).display !== 'none').length")
        return r.toIntOrNull() ?: 0
    }

    private fun wrapperCount(scenario: ActivityScenario<PdfViewer>): Int {
        val r = eval(scenario, "document.querySelectorAll('.page-wrapper').length")
        return r.toIntOrNull() ?: 0
    }

    private fun wrapperBreakdown(scenario: ActivityScenario<PdfViewer>): String =
        eval(scenario, "JSON.stringify({" +
            "all:document.querySelectorAll('.page-wrapper').length," +
            "pagesChildren:document.getElementById('pages').children.length," +
            "containerChildren:document.getElementById('container').children.length})")

    private fun eval(scenario: ActivityScenario<PdfViewer>, js: String): String =
        PdfViewerTestUtils.evaluateJs(scenario, js)

    private fun <T> ActivityScenario<PdfViewer>.onActivityAndReturn(block: (PdfViewer) -> T): T {
        var v: T? = null
        onActivity { v = block(it) }
        @Suppress("UNCHECKED_CAST")
        return v as T
    }
}
