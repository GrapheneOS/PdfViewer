package app.grapheneos.pdfviewer.test

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.TestTags
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain

/**
 * Find in document, end to end: the JS text pump, the ICU index in the ViewModel and the
 * Custom Highlight API paint path.
 *
 * test-multipage.pdf is four pages of "N Chapter <name>" / "Page <name> Content", so "Content"
 * matches exactly once per page.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerSearchTest {

    private val composeRule = RetryableComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryRules()).around(composeRule)

    private fun string(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before
    fun setup() {
        PdfViewerTestUtils.init(composeRule)
    }

    private fun openSearch() =
        composeRule.onNodeWithContentDescription(string(R.string.action_search)).performClick()

    private fun typeQuery(text: String) {
        composeRule.onNodeWithTag(TestTags.SEARCH_FIELD).performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun countIs(expected: String) {
        PdfViewerTestUtils.pollUntil(description = { "match count should be $expected" }) {
            composeRule.onNodeWithTag(TestTags.SEARCH_COUNT).assertTextEquals(expected)
            true
        }
    }

    private fun clickNext() =
        composeRule.onNodeWithContentDescription(string(R.string.search_next)).performClick()

    private fun clickPrevious() =
        composeRule.onNodeWithContentDescription(string(R.string.search_previous)).performClick()

    @Test
    fun findsEveryMatchAndNavigatesAcrossPages() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            openSearch()
            composeRule.onNodeWithTag(TestTags.SEARCH_FIELD).assertIsDisplayed()

            typeQuery("Content")
            countIs("1/4")
            scenario.onActivity { assertEquals(1, it.currentPage) }

            clickNext()
            countIs("2/4")
            PdfViewerTestUtils.pollUntil(description = { "should jump to page 2" }) {
                var page = 0
                scenario.onActivity { page = it.currentPage }
                page == 2
            }

            clickNext()
            countIs("3/4")
            clickNext()
            countIs("4/4")

            // Wraps off the end of the document, and back off the front.
            clickNext()
            countIs("1/4")
            clickPrevious()
            countIs("4/4")
            PdfViewerTestUtils.pollUntil(description = { "should wrap back to page 4" }) {
                var page = 0
                scenario.onActivity { page = it.currentPage }
                page == 4
            }
        }
    }

    @Test
    fun paintsHighlightsOnTheTextLayer() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            openSearch()
            typeQuery("Chapter")
            countIs("1/4")

            // The riskiest assumption in the design: that the Custom Highlight API accepts
            // Ranges over pdf.js's transformed, transparent text layer spans.
            PdfViewerTestUtils.pollUntil(description = { "highlights should be registered" }) {
                PdfViewerTestUtils.evaluateJs(scenario, "CSS.highlights.get('pdf-find').size") != "0"
            }
            assertEquals(
                "active match should be highlighted separately",
                "1",
                PdfViewerTestUtils.evaluateJs(scenario, "CSS.highlights.get('pdf-find-active').size")
            )

            // The range must actually cover the matched word, not an arbitrary span.
            val text = PdfViewerTestUtils.evaluateJs(
                scenario,
                "Array.from(CSS.highlights.get('pdf-find'))[0].toString()"
            )
            assertTrue("highlighted text was $text", text.contains("Chapter", ignoreCase = true))
        }
    }

    @Test
    fun survivesZoomAndRotation() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            openSearch()
            typeQuery("Chapter")
            countIs("1/4")

            // Re-rendering rebuilds the text layer; highlights have to be re-established from
            // the stored offsets rather than preserved.
            scenario.onActivity { it.viewModel.setDocumentOrientationDegrees(90) }
            PdfViewerTestUtils.evaluateJs(scenario, "onRenderPage(0)")
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.pollUntil(description = { "highlights should survive rotation" }) {
                PdfViewerTestUtils.evaluateJs(scenario, "CSS.highlights.get('pdf-find').size") != "0"
            }

            scenario.onActivity { it.viewModel.setZoomRatio(2f) }
            PdfViewerTestUtils.evaluateJs(scenario, "onRenderPage(1)")
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.pollUntil(description = { "highlights should survive zoom" }) {
                PdfViewerTestUtils.evaluateJs(scenario, "CSS.highlights.get('pdf-find').size") != "0"
            }
        }
    }

    @Test
    fun reportsNoMatchesAndDisablesNavigation() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            openSearch()
            typeQuery("nothingmatchesthis")
            countIs("0/0")
            composeRule.onNodeWithContentDescription(string(R.string.search_next))
                .assertIsNotEnabled()
            PdfViewerTestUtils.pollUntil(description = { "no highlights for a miss" }) {
                PdfViewerTestUtils.evaluateJs(scenario, "CSS.highlights.get('pdf-find').size") == "0"
            }
        }
    }

    @Test
    fun closingSearchClearsHighlights() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            openSearch()
            typeQuery("Chapter")
            countIs("1/4")

            composeRule.onNodeWithContentDescription(string(R.string.action_close)).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(TestTags.SEARCH_FIELD).assertDoesNotExist()
            PdfViewerTestUtils.pollUntil(description = { "highlights should be cleared" }) {
                PdfViewerTestUtils.evaluateJs(scenario, "CSS.highlights.get('pdf-find').size") == "0"
            }
            scenario.onActivity { assertEquals("", it.viewModel.searchQuery.value) }
        }
    }
}
