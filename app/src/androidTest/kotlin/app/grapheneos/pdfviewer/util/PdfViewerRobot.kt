package app.grapheneos.pdfviewer.util

import android.graphics.Rect
import android.view.View
import android.widget.NumberPicker
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.fragment.PasswordPromptFragment
import app.grapheneos.pdfviewer.isMenuItemEnabled
import app.grapheneos.pdfviewer.isMenuItemVisible
import app.grapheneos.pdfviewer.outlineStatus
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasToString
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt

/**
 * Encapsulates all Espresso view assertions and actions.
 */
class PdfViewerRobot {

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

    enum class AppMenuItem(
        @IdRes internal val id: Int,
        @StringRes internal val titleRes: Int
    ) {
        Open(R.id.action_open, R.string.action_open),
        Previous(R.id.action_previous, R.string.action_previous),
        Next(R.id.action_next, R.string.action_next),
        First(R.id.action_first, R.string.action_first),
        Last(R.id.action_last, R.string.action_last),
        JumpToPage(R.id.action_jump_to_page, R.string.action_jump_to_page),
        RotateClockwise(
            R.id.action_rotate_clockwise, R.string.action_rotate_clockwise
        ),
        RotateCounterclockwise(
            R.id.action_rotate_counterclockwise,
            R.string.action_rotate_counterclockwise
        ),
        Share(R.id.action_share, R.string.action_share),
        SaveAs(R.id.action_save_as, R.string.action_save_as),
        Outline(R.id.action_outline, R.string.action_outline),
        ViewDocumentProperties(
            R.id.action_view_document_properties,
            R.string.action_view_document_properties
        )
    }

    enum class SnackbarMessage(@StringRes internal val stringRes: Int) {
        InvalidMime(R.string.invalid_mime_type),
        LegacyFileUri(R.string.legacy_file_uri),
        FileOpenError(R.string.error_while_opening),
        SaveError(R.string.error_while_saving)
    }

    // WebView

    fun assertWebViewVisible() {
        onView(withId(R.id.webview)).check(matches(isDisplayed()))
    }

    fun assertCrashUiHidden() {
        onView(withId(R.id.webview_alert_layout)).check(matches(not(isDisplayed())))
    }

    /**
     * Asserts the full crash UI state
     */
    fun assertCrashUiVisible() {
        onView(withId(R.id.webview_alert_layout)).check(matches(isDisplayed()))
        onView(withId(R.id.webview_alert_title))
            .check(matches(withText(R.string.webview_crash_title)))
        onView(withId(R.id.webview_alert_message))
            .check(matches(withText(R.string.webview_crash_message)))
        onView(withId(R.id.webview_alert_reload)).check(matches(isDisplayed()))
        onView(withId(R.id.webview)).check(matches(not(isDisplayed())))
    }

    // Toolbar

    fun assertToolbarTitle(scenario: ActivityScenario<PdfViewer>, expected: String) {
        scenario.onActivity {
            assertEquals(expected, it.supportActionBar?.title?.toString())
        }
    }

    fun assertToolbarTitleIsAppName(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            val appName = it.getString(R.string.app_name)
            assertEquals(appName, it.supportActionBar?.title?.toString())
        }
    }

    fun assertToolbarVisible(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            assertEquals("Toolbar should be visible", true, it.supportActionBar?.isShowing)
        }
    }

    fun assertToolbarHidden(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            assertEquals("Toolbar should be hidden", false, it.supportActionBar?.isShowing)
        }
    }

    // Menu

    fun click(item: AppMenuItem) {
        clickMenuItem(item.id, item.titleRes)
    }

    fun assertNavigationNotShown() {
        assertViewNotVisible(AppMenuItem.Previous.id)
        assertViewNotVisible(AppMenuItem.Next.id)
    }

    /**
     * Asserts the enabled state of the previous and next buttons.
     */
    fun assertNavigationState(previousEnabled: Boolean, nextEnabled: Boolean) {
        val prevMatcher = if (previousEnabled) isEnabled() else not(isEnabled())
        val nextMatcher = if (nextEnabled) isEnabled() else not(isEnabled())
        onView(withId(AppMenuItem.Previous.id)).check(matches(prevMatcher))
        onView(withId(AppMenuItem.Next.id)).check(matches(nextMatcher))
    }

    fun assertMenuItemEnabled(
        scenario: ActivityScenario<PdfViewer>,
        item: AppMenuItem,
        expected: Boolean
    ) {
        scenario.onActivity { activity ->
            val name = activity.resources.getResourceEntryName(item.id)
            assertEquals(
                "Expected '$name' enabled=$expected",
                expected,
                activity.isMenuItemEnabled(item.id)
            )
        }
    }

    fun assertMenuItemVisible(
        scenario: ActivityScenario<PdfViewer>,
        item: AppMenuItem,
        expected: Boolean
    ) {
        scenario.onActivity { activity ->
            val name = activity.resources.getResourceEntryName(item.id)
            assertEquals(
                "Expected '$name' visible=$expected",
                expected,
                activity.isMenuItemVisible(item.id)
            )
        }
    }


    // Menu item actions

    fun clickNext() = click(AppMenuItem.Next)
    fun clickPrevious() = click(AppMenuItem.Previous)
    fun clickReload() {
        onView(withId(R.id.webview_alert_reload)).perform(click())
    }
    fun tapWebView() {
        onView(withId(R.id.webview)).perform(click())
    }

    fun clickRotateClockwise() = click(AppMenuItem.RotateClockwise)
    fun clickRotateCounterclockwise() = click(AppMenuItem.RotateCounterclockwise)

    /**
     * The broad catch is intentional to catch: 1. The view doesn't exist;
     * 2. the view isn't displayed enough.
     */
    private fun clickMenuItem(@IdRes id: Int, @StringRes titleRes: Int) {
        try {
            onView(withId(id)).perform(click())
        } catch (_: Exception) {
            Espresso.openActionBarOverflowOrOptionsMenu(
                ApplicationProvider.getApplicationContext()
            )
            onView(withText(titleRes)).perform(click())
        }
    }

    // JumpToPage

    fun assertNumberPickerStateInDialog(
        minValue: Int, maxValue: Int, currentValue: Int
    ) {
        onView(isAssignableFrom(NumberPicker::class.java))
            .inRoot(isDialog())
            .check(matches(hasNumberPickerMin(minValue)))
        onView(isAssignableFrom(NumberPicker::class.java))
            .inRoot(isDialog())
            .check(matches(hasNumberPickerMax(maxValue)))
        onView(isAssignableFrom(NumberPicker::class.java))
            .inRoot(isDialog())
            .check(matches(hasNumberPickerValue(currentValue)))
    }

    fun setNumberPickerValue(value: Int) {
        onView(isAssignableFrom(NumberPicker::class.java))
            .perform(setNumberPickerValueAction(value))
    }

    fun clickDialogOk() {
        onView(withText(android.R.string.ok)).perform(click())
    }

    fun clickDialogCancel() {
        onView(withText(android.R.string.cancel)).perform(click())
    }

    // Password

    fun showPasswordDialog(scenario: ActivityScenario<PdfViewer>) {
        scenario.onActivity {
            PasswordPromptFragment().show(
                it.supportFragmentManager,
                PasswordPromptFragment::class.java.name
            )
            it.supportFragmentManager.executePendingTransactions()
        }
    }

    fun typePassword(text: String) {
        onView(withId(R.id.pdf_password_edit_text))
            .inRoot(isDialog())
            .perform(typeText(text), closeSoftKeyboard())
    }

    fun clearPasswordField() {
        onView(withId(R.id.pdf_password_edit_text))
            .inRoot(isDialog())
            .perform(clearText(), closeSoftKeyboard())
    }

    fun assertPasswordPositiveButtonEnabled(enabled: Boolean) {
        val matcher = if (enabled) isEnabled() else not(isEnabled())
        onView(withId(android.R.id.button1))
            .inRoot(isDialog())
            .check(matches(matcher))
    }

    fun assertPasswordError(expectedError: String) {
        onView(withId(R.id.pdf_password_text_input_layout))
            .inRoot(isDialog())
            .check(matches(hasTextInputLayoutError(expectedError)))
    }

    fun assertPasswordDialogShown() {
        onView(withId(R.id.pdf_password_edit_text))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    // Helpers

    private fun assertViewNotVisible(@IdRes viewId: Int) {
        try {
            onView(withId(viewId)).check(doesNotExist())
        } catch (_: AssertionError) {
            onView(withId(viewId)).check(matches(not(isDisplayed())))
        }
    }

    // NumberPicker

    private fun hasNumberPickerMin(expected: Int): Matcher<View> =
        object : BoundedMatcher<View, NumberPicker>(NumberPicker::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("NumberPicker with minValue=$expected")
            }
            override fun matchesSafely(picker: NumberPicker) = picker.minValue == expected
        }

    private fun hasNumberPickerMax(expected: Int): Matcher<View> =
        object : BoundedMatcher<View, NumberPicker>(NumberPicker::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("NumberPicker with maxValue=$expected")
            }
            override fun matchesSafely(picker: NumberPicker) = picker.maxValue == expected
        }

    private fun hasNumberPickerValue(expected: Int): Matcher<View> =
        object : BoundedMatcher<View, NumberPicker>(NumberPicker::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("NumberPicker with value=$expected")
            }
            override fun matchesSafely(picker: NumberPicker) = picker.value == expected
        }

    private fun setNumberPickerValueAction(value: Int): ViewAction =
        object : ViewAction {
            override fun getConstraints() = isAssignableFrom(NumberPicker::class.java)
            override fun getDescription() = "Set NumberPicker value to $value"
            override fun perform(uiController: UiController, view: View) {
                (view as NumberPicker).value = value
                uiController.loopMainThreadUntilIdle()
            }
        }

    // TextInputLayout matcher

    private fun hasTextInputLayoutError(expected: String): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("TextInputLayout with error='$expected'")
            }
            override fun matchesSafely(view: View): Boolean {
                if (view !is TextInputLayout) return false
                return view.error?.toString() == expected
            }
        }

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
            val status = it.outlineStatus
            if (status is PdfViewModel.OutlineStatus.Loaded) {
                size = status.outline.size
            }
        }
        return size
    }

    fun openOutlineFragment() = click(AppMenuItem.Outline)

    fun waitForOutlineEntries(timeout: Long = 15_000) {
        PdfViewerTestUtils.pollUntilAssertion(timeout) {
            onView(withId(R.id.list)).check(matches(isDisplayed()))
            onView(withId(R.id.list)).check(matches(hasMinimumChildCount(1)))
        }
    }

    fun assertDocumentPropertyVisible(expected: String) {
        onData(
            allOf(
                instanceOf(CharSequence::class.java),
                hasToString(containsString(expected))
            )
        )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    fun clickOutlineEntry(position: Int) {
        onView(withId(R.id.list))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                position, click()
            ))
    }

    fun waitForOutlineFragmentDismissed(timeout: Long = 5_000) {
        PdfViewerTestUtils.pollUntilAssertion(timeout) {
            onView(withId(R.id.list)).check(doesNotExist())
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
            val webView = activity.findViewById<View>(R.id.webview)
            val webViewBounds = getBoundsInScreen(webView)
            val appBarBounds = Rect()
            val appBar = activity.findViewById<View>(R.id.app_bar_layout)
            val appBarBottom = if (appBar.getGlobalVisibleRect(appBarBounds)) {
                appBarBounds.bottom
            } else {
                webViewBounds.top
            }
            val insetTypes = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            val insets = ViewCompat.getRootWindowInsets(webView)?.getInsets(insetTypes)

            val density = webView.resources.displayMetrics.density
            val edgeMargin = (GESTURE_EDGE_MARGIN_DP * density).roundToInt()
            val defaultHorizontal =
                (webViewBounds.width() * UIAUTOMATOR_DEFAULT_GESTURE_MARGIN_PERCENT).roundToInt()
            val defaultVertical =
                (webViewBounds.height() * UIAUTOMATOR_DEFAULT_GESTURE_MARGIN_PERCENT).roundToInt()
            val topObstruction = maxOf(
                insets?.top ?: 0,
                appBarBottom - webViewBounds.top
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

    fun waitForPasswordDialog(timeout: Long = 15_000) {
        PdfViewerTestUtils.pollUntilAssertion(timeout) {
            onView(withId(R.id.pdf_password_edit_text))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }

    fun clickPasswordPositiveButton() {
        onView(withId(android.R.id.button1))
            .inRoot(isDialog())
            .perform(click())
    }

    fun waitForPasswordError(expectedError: String, timeout: Long = 15_000) {
        PdfViewerTestUtils.pollUntilAssertion(timeout) {
            onView(withId(R.id.pdf_password_text_input_layout))
                .inRoot(isDialog())
                .check(matches(hasTextInputLayoutError(expectedError)))
        }
    }

    fun waitForPasswordDialogDismissed(timeout: Long = 15_000) {
        PdfViewerTestUtils.pollUntilAssertion(timeout) {
            onView(withId(R.id.pdf_password_edit_text)).check(doesNotExist())
        }
    }
}
