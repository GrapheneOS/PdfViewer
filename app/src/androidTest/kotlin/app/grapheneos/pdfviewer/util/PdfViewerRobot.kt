package app.grapheneos.pdfviewer.util

import android.graphics.Rect
import android.view.View
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.TestTags
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt

/**
 * Encapsulates all Espresso view assertions and actions.
 */
class PdfViewerRobot(private val composeRule: ComposeTestRule) {

    private data class GestureMargins(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private companion object {
        const val UIAUTOMATOR_DEFAULT_GESTURE_MARGIN_PERCENT = 0.1f
        const val GESTURE_EDGE_MARGIN_DP = 32f
    }

    private fun getTargetContext() =
        InstrumentationRegistry.getInstrumentation().targetContext

    enum class AppMenuItem(
        @StringRes internal val titleRes: Int
    ) {
        Open(R.string.action_open),
        Previous(R.string.action_previous),
        Next(R.string.action_next),
        First(R.string.action_first),
        Last(R.string.action_last),
        JumpToPage(R.string.action_jump_to_page),
        RotateClockwise(R.string.action_rotate_clockwise),
        RotateCounterclockwise(R.string.action_rotate_counterclockwise),
        Share(R.string.action_share),
        SaveAs(R.string.action_save_as),
        Outline(R.string.action_outline),
        ViewDocumentProperties(R.string.action_view_document_properties)
    }

    enum class SnackbarMessage(@StringRes internal val stringRes: Int) {
        InvalidMime(R.string.invalid_mime_type),
        LegacyFileUri(R.string.legacy_file_uri),
        FileOpenError(R.string.error_while_opening),
        SaveError(R.string.error_while_saving)
    }

    // WebView

    fun assertWebViewVisible() {
        onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))
    }

    fun assertCrashUiHidden() {
        composeRule.onNodeWithTag(TestTags.WEBVIEW_ALERT).assertDoesNotExist()
    }

    /**
     * Asserts the full crash UI state
     */
    fun assertCrashUiVisible() {
        composeRule.onNodeWithTag(TestTags.WEBVIEW_ALERT).assertIsDisplayed()
        composeRule.onNodeWithText(getTargetContext().getString(R.string.webview_crash_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(getTargetContext().getString(R.string.webview_crash_message))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.RELOAD_BUTTON).assertIsDisplayed()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
    }

    // Toolbar

    fun assertToolbarTitle(scenario: ActivityScenario<PdfViewer>, expected: String) {
        scenario.onActivity {
            assertEquals(expected, it.viewModel.documentName.value.ifEmpty {
                it.getString(R.string.app_name)
            })
        }
    }

    fun assertToolbarTitleIsAppName(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            assertTrue(
                "Document name should be empty when showing app name",
                it.viewModel.documentName.value.isEmpty()
            )
        }
    }

    fun assertToolbarVisible(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            assertEquals("Toolbar should be visible", true, it.viewModel.toolbarVisible.value)
        }
    }

    fun assertToolbarHidden(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            assertEquals("Toolbar should be hidden", false, it.viewModel.toolbarVisible.value)
        }
    }

    // Menu

    fun click(item: AppMenuItem) {
        val title = getTargetContext().getString(item.titleRes)
        try {
            composeRule.onNodeWithContentDescription(title).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(title).performClick()
        } catch (_: AssertionError) {
            composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(title).performScrollTo().performClick()
        }
    }

    fun assertNavigationNotShown() {
        val prevDesc = getTargetContext().getString(R.string.action_previous)
        val nextDesc = getTargetContext().getString(R.string.action_next)
        composeRule.onNodeWithContentDescription(prevDesc).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(nextDesc).assertDoesNotExist()
    }

    /**
     * Asserts the enabled state of the previous and next buttons.
     */
    fun assertNavigationState(previousEnabled: Boolean, nextEnabled: Boolean) {
        val prevDesc = getTargetContext().getString(R.string.action_previous)
        val nextDesc = getTargetContext().getString(R.string.action_next)
        val prevNode = composeRule.onNodeWithContentDescription(prevDesc)
        val nextNode = composeRule.onNodeWithContentDescription(nextDesc)
        if (previousEnabled) prevNode.assertIsEnabled() else prevNode.assertIsNotEnabled()
        if (nextEnabled) nextNode.assertIsEnabled() else nextNode.assertIsNotEnabled()
    }

    fun assertMenuItemEnabled(
        item: AppMenuItem,
        expected: Boolean
    ) {
        val title = getTargetContext().getString(item.titleRes)
        try {
            val node = composeRule.onNodeWithContentDescription(title)
            if (expected) node.assertIsEnabled() else node.assertIsNotEnabled()
        } catch (_: AssertionError) {
            composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
            composeRule.waitForIdle()
            val node = composeRule.onNodeWithText(title).performScrollTo()
            if (expected) node.assertIsEnabled() else node.assertIsNotEnabled()
            composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
        }
    }

    fun assertMenuItemVisible(
        item: AppMenuItem,
        expected: Boolean
    ) {
        val title = getTargetContext().getString(item.titleRes)
        if (expected) {
            try {
                composeRule.onNodeWithContentDescription(title).assertIsDisplayed()
            } catch (_: AssertionError) {
                composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
            }
        } else {
            composeRule.onNodeWithContentDescription(title).assertDoesNotExist()
            try {
                composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithText(title).assertDoesNotExist()
                composeRule.onNodeWithContentDescription(TestTags.OVERFLOW_MENU).performClick()
            } catch (_: AssertionError) {}
        }
    }

    // Menu item actions

    fun clickNext() = click(AppMenuItem.Next)
    fun clickPrevious() = click(AppMenuItem.Previous)
    fun clickReload() {
        composeRule.onNodeWithTag(TestTags.RELOAD_BUTTON).performClick()
    }
    fun tapWebView() {
        onView(isAssignableFrom(WebView::class.java)).perform(click())
    }

    fun clickRotateClockwise() = click(AppMenuItem.RotateClockwise)
    fun clickRotateCounterclockwise() = click(AppMenuItem.RotateCounterclockwise)

    // JumpToPage

    fun assertJumpToPageDialogState(currentValue: Int, maxValue: Int) {
        composeRule.onNodeWithTag(TestTags.JUMP_TO_PAGE_FIELD)
            .assertTextContains(currentValue.toString())
        composeRule.onNodeWithText("Page (1-$maxValue)").assertIsDisplayed()
    }

    fun setJumpToPageValue(value: Int) {
        composeRule.onNodeWithTag(TestTags.JUMP_TO_PAGE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(TestTags.JUMP_TO_PAGE_FIELD).performTextInput(value.toString())
    }

    fun typeJumpToPageWithoutClearing(value: Int) {
        composeRule.onNodeWithTag(TestTags.JUMP_TO_PAGE_FIELD).performTextInput(value.toString())
    }

    fun clickDialogOk() {
        val text = getTargetContext().getString(android.R.string.ok)
        composeRule.onNodeWithText(text).performClick()
    }

    fun clickDialogCancel() {
        val text = getTargetContext().getString(android.R.string.cancel)
        composeRule.onNodeWithText(text).performClick()
    }

    // Password

    fun showPasswordDialog(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            it.viewModel.requestPasswordPrompt()
        }
    }

    fun typePassword(text: String) {
        composeRule.onNodeWithTag(TestTags.PASSWORD_FIELD).performTextInput(text)
    }

    fun clearPasswordField() {
        composeRule.onNodeWithTag(TestTags.PASSWORD_FIELD).performTextClearance()
    }

    fun assertPasswordPositiveButtonEnabled(enabled: Boolean) {
        val buttonText = getTargetContext().getString(R.string.open)
        val node = composeRule.onNodeWithText(buttonText)
        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
    }

    fun assertPasswordError() {
        val expectedError = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.invalid_password)
        composeRule.onNodeWithText(expectedError).assertIsDisplayed()
    }

    fun assertPasswordDialogShown() {
        composeRule.onNodeWithTag(TestTags.PASSWORD_FIELD).assertIsDisplayed()
    }

    fun waitForPasswordDialog(timeout: Long = 15_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithTag(TestTags.PASSWORD_FIELD)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun waitForPasswordError(timeout: Long = 15_000) {
        val expectedError = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.invalid_password)
        composeRule.waitUntil(timeout) {
            composeRule
                .onAllNodes(hasText(expectedError))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun waitForPasswordDialogDismissed(timeout: Long = 15_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithTag(TestTags.PASSWORD_FIELD)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    fun clickPasswordPositiveButton() {
        val buttonText = getTargetContext().getString(R.string.open)
        composeRule.onNodeWithText(buttonText).performClick()
    }

    // Helpers

    // WebView JS

    fun assertCanvasRendered(scenario: ActivityScenario<PdfViewer>) {
        val result = PdfViewerTestUtils.evaluateJs(scenario,
            "parseInt(document.getElementById('content').style.width) > 0 " +
                    "&& parseInt(document.getElementById('content').style.height) > 0"
        )
        assertTrue("Canvas should have non-zero CSS dimensions after rendering", result == "true")
    }

    fun assertBridgePage(scenario: ActivityScenario<PdfViewer>, expectedPage: Int) {
        val result = PdfViewerTestUtils.evaluateJs(scenario, "channel.getPage()")
        assertEquals(
            "channel.getPage() should match mPage",
            expectedPage.toString(),
            result
        )
    }

    fun getCanvasWidth(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(scenario,
            "document.getElementById('content').width"
        )
        return result.toInt()
    }

    fun getCanvasHeight(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(scenario,
            "document.getElementById('content').height"
        )
        return result.toInt()
    }

    fun getCanvasCssWidth(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(scenario,
            "parseInt(document.getElementById('content').style.width) || 0"
        )
        return result.toInt()
    }

    fun getCanvasCssHeight(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(scenario,
            "parseInt(document.getElementById('content').style.height) || 0"
        )
        return result.toInt()
    }

    fun getViewportWidth(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(scenario, "document.body.clientWidth")
        return result.toInt()
    }

    fun getViewportHeight(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(scenario,
            "document.body.clientHeight"
        )
        return result.toInt()
    }

    fun getDocumentRotationDegrees(scenario: ActivityScenario<PdfViewer>): Int {
        val result = PdfViewerTestUtils.evaluateJs(
            scenario, "channel.getDocumentOrientationDegrees()"
        )
        return result.toInt()
    }

    // Outline

    fun requestOutline(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            it.viewModel.requestOutlineIfNotAvailable()
        }
    }

    fun getLoadedOutlineSize(scenario: ActivityScenario<PdfViewer>): Int {
        var size = -1
        scenario.onActivity {
            val status = it.viewModel.outline.value
            if (status is PdfViewModel.OutlineStatus.Loaded) {
                size = status.outline.size
            }
        }
        return size
    }

    fun openOutlineFragment() = click(AppMenuItem.Outline)

    fun waitForOutlineEntries(timeout: Long = 15_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithTag(TestTags.OUTLINE_ITEM)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun assertDocumentPropertyVisible(expected: String) {
        composeRule.onNodeWithText(expected, substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    fun clickOutlineEntry(position: Int) {
        composeRule.onAllNodesWithTag(TestTags.OUTLINE_ITEM)[position]
            .performScrollTo()
            .performClick()
    }

    fun waitForOutlineFragmentDismissed(timeout: Long = 5_000) {
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodesWithTag(TestTags.OUTLINE_ITEM)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    // Zoom

    fun performPinchZoomIn(
        scenario: ActivityScenario<PdfViewer>,
        percent: Float = 0.75f,
        speed: Int = 500,
    ) {
        val webView = findWebViewObject()
        applyContentGestureMargins(webView, scenario)
        webView.pinchOpen(percent, speed)
    }

    fun performPinchZoomOut(
        scenario: ActivityScenario<PdfViewer>,
        percent: Float = 0.75f,
        speed: Int = 500,
    ) {
        val webView = findWebViewObject()
        applyContentGestureMargins(webView, scenario)
        webView.pinchClose(percent, speed)
    }

    private fun findWebViewObject(): UiObject2 {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Using class name because UiAutomator cannot find WebView reliably because
        // android:importantForAccessibility is "no"
        return device.wait(
            Until.findObject(By.clazz("android.webkit.WebView")),
            10_000
        ) ?: throw AssertionError(
            "Could not find WebView by class `android.webkit.WebView` within 10s"
        )
    }

    private fun applyContentGestureMargins(
        webViewObject: UiObject2,
        scenario: ActivityScenario<PdfViewer>?
    ) {
        if (scenario == null) {
            return
        }

        // UiObject2.pinchClose starts at the outer corners. Edge-to-edge keeps the
        // WebView bounds behind the app bar, so constrain pinches to real content.
        val margins = getContentGestureMargins(scenario)
        webViewObject.setGestureMargins(
            margins.left,
            margins.top,
            margins.right,
            margins.bottom
        )
    }

    private fun getContentGestureMargins(
        scenario: ActivityScenario<PdfViewer>
    ): GestureMargins {
        var margins: GestureMargins? = null
        scenario.onActivity { activity ->
            val webView = activity.webView ?: return@onActivity
            val webViewBounds = getBoundsInScreen(webView)

            val insetTypes = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            val insets = ViewCompat.getRootWindowInsets(webView)?.getInsets(insetTypes)

            val density = webView.resources.displayMetrics.density
            val edgeMargin = (GESTURE_EDGE_MARGIN_DP * density).roundToInt()
            val defaultHorizontal =
                (webViewBounds.width() * UIAUTOMATOR_DEFAULT_GESTURE_MARGIN_PERCENT).roundToInt()
            val defaultVertical =
                (webViewBounds.height() * UIAUTOMATOR_DEFAULT_GESTURE_MARGIN_PERCENT).roundToInt()

            val toolbarBottom = if (activity.viewModel.toolbarVisible.value) {
                activity.viewModel.insetTop.toInt()
            } else {
                0
            }
            val topObstruction = maxOf(
                insets?.top ?: 0,
                toolbarBottom
            ).coerceAtLeast(0)

            val left = maxOf(defaultHorizontal, (insets?.left ?: 0) + edgeMargin)
            val top = maxOf(defaultVertical, topObstruction + edgeMargin)
            val right = maxOf(defaultHorizontal, (insets?.right ?: 0) + edgeMargin)
            val bottom = maxOf(defaultVertical, (insets?.bottom ?: 0) + edgeMargin)
            val fittedHorizontal = fitMargins(left, right, webViewBounds.width())
            val fittedVertical = fitMargins(top, bottom, webViewBounds.height())

            margins = GestureMargins(
                fittedHorizontal.first,
                fittedVertical.first,
                fittedHorizontal.second,
                fittedVertical.second
            )
        }
        return margins ?: GestureMargins(0, 0, 0, 0)
    }

    private fun getBoundsInScreen(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
    }

    private fun fitMargins(start: Int, end: Int, size: Int): Pair<Int, Int> {
        if (size <= 2) {
            return 0 to 0
        }
        val maxTotal = size - 2
        if (start + end <= maxTotal) {
            return start to end
        }

        val scale = maxTotal.toFloat() / (start + end).toFloat()
        val fittedStart = (start * scale).roundToInt().coerceIn(0, maxTotal)
        return fittedStart to maxTotal - fittedStart
    }

    fun getZoomRatio(scenario: ActivityScenario<PdfViewer>): Float {
        val result = PdfViewerTestUtils.evaluateJs(scenario, "channel.getZoomRatio()")
        return result.toFloat()
    }

    // Text layer alignment

    fun assertTextLayerAligned(scenario: ActivityScenario<PdfViewer>) {
        val result = PdfViewerTestUtils.evaluateJs(scenario, """
            (function() {
                var text = document.getElementById('text');
                var container = document.getElementById('container');
                var canvas = document.getElementById('content');
                if (!text || !container || !canvas) return 'missing_elements';
                if (text.hidden) return 'text_hidden';
                var scaleFactor = container.style.getPropertyValue('--scale-factor');
                if (!scaleFactor || scaleFactor === '' || parseFloat(scaleFactor) <= 0) return 'no_scale_factor';
                var zoomRatio = channel.getZoomRatio();
                if (Math.abs(parseFloat(scaleFactor) - zoomRatio) > 0.01) {
                    return 'scale_mismatch:' + scaleFactor + '_vs_' + zoomRatio;
                }
                var spans = text.querySelectorAll('span');
                if (spans.length === 0) return 'no_spans';
                var canvasRect = canvas.getBoundingClientRect();
                var spanRect = spans[0].getBoundingClientRect();
                if (spanRect.width <= 0 || spanRect.height <= 0) return 'span_zero_size';
                if (spanRect.right < canvasRect.left || spanRect.left > canvasRect.right ||
                    spanRect.bottom < canvasRect.top || spanRect.top > canvasRect.bottom) {
                    return 'span_outside_canvas:' +
                        'span(' + spanRect.left.toFixed(1) + ',' + spanRect.top.toFixed(1) + ',' +
                        spanRect.right.toFixed(1) + ',' + spanRect.bottom.toFixed(1) + ')' +
                        '_canvas(' + canvasRect.left.toFixed(1) + ',' + canvasRect.top.toFixed(1) + ',' +
                        canvasRect.right.toFixed(1) + ',' + canvasRect.bottom.toFixed(1) + ')';
                }
                return 'aligned';
            })()
        """.trimIndent())
        assertEquals(
            "Text layer should be aligned with canvas",
            "\"aligned\"", result
        )
    }
}
