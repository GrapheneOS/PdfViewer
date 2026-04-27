package app.grapheneos.pdfviewer.util

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.outlineStatus
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object PdfViewerTestUtils {

    /**
     * Evaluates JavaScript in the WebView by calling
     * [WebView.evaluateJavascript] directly from the Android side.
     */
    fun evaluateJs(
        scenario: ActivityScenario<PdfViewer>,
        script: String
    ): String {
        val latch = CountDownLatch(1)
        var result = "null"

        scenario.onActivity { activity ->
            val webView = activity.findViewById<WebView>(R.id.webview)
            webView.evaluateJavascript(script) { value ->
                result = value ?: "null"
                latch.countDown()
            }
        }

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw AssertionError("evaluateJavascript did not return within 10s")
        }
        return result
    }

    fun pollUntil(
        timeout: Long = 10_000,
        interval: Long = 200,
        description: () -> String = { "condition" },
        condition: () -> Boolean
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            try {
                if (condition()) return
            } catch (_: Throwable) { /* retry */ }
            Thread.sleep(interval)
        }
        throw AssertionError("${description()} not met within ${timeout}ms")
    }

    fun pollUntilAssertion(
        timeout: Long = 10_000,
        interval: Long = 200,
        assertion: () -> Unit
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            try {
                assertion()
                return
            } catch (_: Throwable) { Thread.sleep(interval) }
        }
        assertion()
    }

    fun assertStableCondition(
        duration: Long = 3_000,
        interval: Long = 200,
        description: () -> String = { "condition" },
        condition: () -> Boolean
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < duration) {
            if (!condition()) {
                throw AssertionError(
                    "${description()} became false during ${duration}ms window"
                )
            }
            Thread.sleep(interval)
        }
    }

    /**
     * Blocks until the WebView's viewer page has finished loading and the
     * document state has become STATE_LOADED.
     */
    fun waitForDocumentLoaded(timeout: Long = 10_000) {
        pollUntilAssertion(timeout) {
            onView(withId(R.id.action_previous)).check(matches(isDisplayed()))
        }
    }

    /**
     * Blocks until the full document load pipeline completes from JS.
     */
    fun waitForDocumentFullyLoaded(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        Log.d("WAIT", "waitForDocumentFullyLoaded: start")
        val start = System.currentTimeMillis()
        pollUntil(
            timeout = timeout,
            description = { "Document not fully loaded within ${timeout}ms" }
        ) {
            var loaded = false
            var props = false
            var pages = 0
            scenario.onActivity { activity ->
                props = activity.documentProperties != null
                pages = activity.totalPages
                loaded = props && pages > 0
            }
            Log.d("WAIT", "  props=$props, pages=$pages, elapsed=${System.currentTimeMillis() - start}ms")
            loaded
        }
        Log.d("WAIT", "waitForDocumentFullyLoaded: done in ${System.currentTimeMillis() - start}ms")
    }

    fun waitForCanvasRendered(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        Log.d("WAIT", "waitForCanvasRendered: start")
        val start = System.currentTimeMillis()
        pollUntil(
            timeout = timeout,
            description = { "Canvas not rendered within ${timeout}ms" }
        ) {
            try {
                val result = evaluateJs(scenario,
                    "document.getElementById('content').width > 0 " +
                            "&& document.getElementById('content').height > 0"
                )
                Log.d("WAIT", "  canvas check result=$result, elapsed=${System.currentTimeMillis() - start}ms")
                result == "true"
            } catch (t: Throwable) {
                Log.w("WAIT", "  canvas check exception at ${System.currentTimeMillis() - start}ms: ${t.message}")
                false
            }
        }
        Log.d("WAIT", "waitForCanvasRendered: done in ${System.currentTimeMillis() - start}ms")
    }

    fun assertTextLayerContent(
        scenario: ActivityScenario<PdfViewer>,
        expected: String,
        timeout: Long = 15_000
    ) {
        Log.d("WAIT", "assertTextLayerContent($expected): start")
        val start = System.currentTimeMillis()
        try {
            pollUntil(
                timeout = timeout,
                description = { "Text layer did not contain '$expected' within ${timeout}ms" }
            ) {
                try {
                    val result = evaluateJs(scenario,
                        "document.getElementById('text').textContent"
                    )
                    Log.d("WAIT", "  text layer result=${result.take(80)}, elapsed=${System.currentTimeMillis() - start}ms")
                    result.contains(expected)
                } catch (t: Throwable) {
                    Log.w("WAIT", "  text layer exception at ${System.currentTimeMillis() - start}ms: ${t.message}")
                    false
                }
            }
            Log.d("WAIT", "assertTextLayerContent: done in ${System.currentTimeMillis() - start}ms")
        } catch (_: AssertionError) {
            val actual = try {
                evaluateJs(scenario, "document.getElementById('text').textContent")
            } catch (e: Throwable) {
                "JS evaluation failed: ${e.message}"
            }
            throw AssertionError(
                "Text layer did not contain '$expected' within ${timeout}ms. Actual: $actual"
            )
        }
    }

    fun waitForOutlineAvailable(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        pollUntil(
            timeout = timeout,
            description = { "Outline not available within ${timeout}ms" }
        ) {
            var available = false
            scenario.onActivity { available = it.viewModel.hasOutline() }
            available
        }
    }

    fun waitForOutlineLoaded(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        pollUntil(
            timeout = timeout,
            interval = 250,
            description = { "Outline not loaded within ${timeout}ms" }
        ) {
            var loaded = false
            scenario.onActivity {
                loaded = it.outlineStatus is PdfViewModel.OutlineStatus.Loaded
            }
            loaded
        }
    }

    fun waitForCrashUiDismissed(timeout: Long = 10_000) {
        pollUntilAssertion(timeout) {
            onView(withId(R.id.webview_alert_layout))
                .check(matches(not(isDisplayed())))
        }
    }

    fun waitForToolbarState(
        scenario: ActivityScenario<PdfViewer>,
        visible: Boolean,
        timeout: Long = 5_000
    ) {
        pollUntil(
            timeout = timeout,
            interval = 100,
            description = { "Toolbar visibility should be visible=$visible" }
        ) {
            var matches = false
            scenario.onActivity { matches = (it.supportActionBar?.isShowing == visible) }
            matches
        }
    }

    fun waitForCanvasDimensionsChanged(
        scenario: ActivityScenario<PdfViewer>,
        previousWidth: Int,
        previousHeight: Int,
        timeout: Long = 15_000
    ) {
        pollUntil(
            timeout = timeout,
            description = {
                "Canvas dimensions did not change from ${previousWidth}x${previousHeight} " +
                        "within ${timeout}ms"
            }
        ) {
            val w = evaluateJs(scenario, "document.getElementById('content').width").toIntOrNull()
            val h = evaluateJs(scenario, "document.getElementById('content').height").toIntOrNull()
            w != null && h != null && (w != previousWidth || h != previousHeight)
        }
    }

    fun waitForDocumentChanged(
        scenario: ActivityScenario<PdfViewer>,
        expectedPages: Int,
        timeout: Long = 15_000
    ) {
        pollUntil(
            timeout = timeout,
            description = {
                "Document did not change to expected state (expectedPages=$expectedPages) " +
                        "within ${timeout}ms"
            }
        ) {
            var matches = false
            scenario.onActivity { activity ->
                matches = activity.totalPages == expectedPages &&
                        activity.documentProperties != null
            }
            matches
        }
    }

    fun waitForDocumentRotation(
        scenario: ActivityScenario<PdfViewer>,
        expected: Int,
        timeout: Long = 10_000
    ) {
        val robot = PdfViewerRobot()
        pollUntil(
            timeout = timeout,
            description = {
                "Document orientation did not reach ${expected}° " +
                        "(was ${robot.getDocumentRotationDegrees(scenario)}°)"
            }
        ) {
            robot.getDocumentRotationDegrees(scenario) == expected
        }
    }

    fun waitForSnackbar(message: PdfViewerRobot.SnackbarMessage, timeout: Long = 10_000) {
        pollUntilAssertion(timeout) {
            onView(withId(com.google.android.material.R.id.snackbar_text))
                .check(matches(withText(message.stringRes)))
        }
    }

    fun setDeviceOrientation(scenario: ActivityScenario<PdfViewer>, landscape: Boolean) {
        val targetOrientation = if (landscape)
            Configuration.ORIENTATION_LANDSCAPE
        else
            Configuration.ORIENTATION_PORTRAIT
        scenario.onActivity {
            it.requestedOrientation = if (landscape)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        pollUntil(
            timeout = 10_000,
            description = { "Activity did not reach orientation=landscape($landscape) within 10s" }
        ) {
            var matches = false
            scenario.onActivity {
                matches = it.resources.configuration.orientation == targetOrientation
            }
            matches
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    fun selectAllText(scenario: ActivityScenario<PdfViewer>) {
        evaluateJs(
            scenario, """
        (function() {
                var range = document.createRange();
                range.selectNodeContents(document.getElementById('text'));
                var sel = window.getSelection();
                sel.removeAllRanges();
                sel.addRange(range);
                return window.getSelection().toString().length > 0;
            })()
    """.trimIndent()
        )
    }

    fun assertToolbarStableVisibility(
        scenario: ActivityScenario<PdfViewer>,
        expectedVisible: Boolean,
        duration: Long = 500
    ) {
        assertStableCondition(
            duration = duration,
            interval = 50,
            description = { "Toolbar visibility should stay visible=$expectedVisible" }
        ) {
            var actual = false
            scenario.onActivity { actual = it.supportActionBar?.isShowing == true }
            actual == expectedVisible
        }
    }
}
