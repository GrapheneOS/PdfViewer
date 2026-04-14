package app.grapheneos.pdfviewer.util

import android.util.Log
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
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

    /**
     * Blocks until the WebView's viewer page has finished loading and the
     * document state has become STATE_LOADED.
     */
    fun waitForDocumentLoaded(timeout: Long = 10_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            try {
                onView(withId(R.id.action_previous)).check(matches(isDisplayed()))
                return
            } catch (_: Throwable) {
                Thread.sleep(200)
            }
        }
        onView(withId(R.id.action_previous)).check(matches(isDisplayed()))
    }

    /**
     * Blocks until the full document load pipeline completes from JS.
     */
    fun waitForDocumentFullyLoaded(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        Log.d("WAIT", "waitForDocumentFullyLoaded: start")
        while (System.currentTimeMillis() - start < timeout) {
            var loaded = false
            var props = false
            var pages = 0
            scenario.onActivity { activity ->
                props = activity.documentProperties != null
                pages = activity.totalPages
                loaded = props && pages > 0
            }
            Log.d("WAIT", "  props=$props, pages=$pages, elapsed=${System.currentTimeMillis() - start}ms")
            if (loaded) {
                Log.d("WAIT", "waitForDocumentFullyLoaded: done in ${System.currentTimeMillis() - start}ms")
                return
            }
            Thread.sleep(200)
        }
        scenario.onActivity { activity ->
            assertEquals(
                "Document Properties should be non-null",
                true,
                activity.documentProperties != null
            )
        }
    }

    fun waitForCanvasRendered(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        Log.d("WAIT", "waitForCanvasRendered: start")
        while (System.currentTimeMillis() - start < timeout) {
            try {
                val result = evaluateJs(scenario,
                    "document.getElementById('content').width > 0 " +
                            "&& document.getElementById('content').height > 0"
                )
                Log.d("WAIT", "  canvas check result=$result, elapsed=${System.currentTimeMillis() - start}ms")
                if (result == "true") {
                    Log.d("WAIT", "waitForCanvasRendered: done in ${System.currentTimeMillis() - start}ms")
                    return
                }
            } catch (t: Throwable) {
                Log.w("WAIT", "  canvas check exception at ${System.currentTimeMillis() - start}ms: ${t.message}")
            }
            Thread.sleep(200)
        }
        throw AssertionError("Canvas not rendered within ${timeout}ms")
    }

    fun assertTextLayerContent(
        scenario: ActivityScenario<PdfViewer>,
        expected: String,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        Log.d("WAIT", "assertTextLayerContent($expected): start")
        while (System.currentTimeMillis() - start < timeout) {
            try {
                val result = evaluateJs(scenario,
                    "document.getElementById('text').textContent"
                )
                Log.d("WAIT", "  text layer result=${result.take(80)}, elapsed=${System.currentTimeMillis() - start}ms")
                if (result.contains(expected)) {
                    Log.d("WAIT", "assertTextLayerContent: done in ${System.currentTimeMillis() - start}ms")
                    return
                }
            } catch (t: Throwable) {
                Log.w("WAIT", "  text layer exception at ${System.currentTimeMillis() - start}ms: ${t.message}")
            }
            Thread.sleep(200)
        }
        val actual = try {
            evaluateJs(scenario, "document.getElementById('text').textContent")
        } catch (e: Throwable) {
            "JS evaluation failed: ${e.message}"
        }
        throw AssertionError(
            "Text layer did not contain '$expected' within ${timeout}ms. Actual: $actual"
        )
    }

    fun waitForOutlineAvailable(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            var available = false
            scenario.onActivity {
                available = it.viewModel.hasOutline()
            }
            if (available) return
            Thread.sleep(200)
        }
        throw AssertionError("Outline not available within ${timeout}ms")
    }

    fun waitForOutlineLoaded(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            var loaded = false
            scenario.onActivity {
                loaded = it.outlineStatus is PdfViewModel.OutlineStatus.Loaded
            }
            if (loaded) return
            Thread.sleep(250)
        }
        throw AssertionError("Outline not loaded within ${timeout}ms")
    }

    fun waitForCrashUiDismissed(timeout: Long = 10_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            try {
                onView(withId(R.id.webview_alert_layout))
                    .check(matches(not(isDisplayed())))
                return
            } catch (_: Throwable) {
                Thread.sleep(200)
            }
        }
        onView(withId(R.id.webview_alert_layout))
            .check(matches(not(isDisplayed())))
    }

    fun waitForToolbarState(
        scenario: ActivityScenario<PdfViewer>,
        visible: Boolean,
        timeout: Long = 5_000
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            var matches = false
            scenario.onActivity {
                matches = (it.supportActionBar?.isShowing == visible)
            }
            if (matches) return
            Thread.sleep(100)
        }
        scenario.onActivity {
            assertEquals(
                "Toolbar visibility should be visible=$visible",
                visible,
                it.supportActionBar?.isShowing
            )
        }
    }

    fun waitForCanvasDimensionsChanged(
        scenario: ActivityScenario<PdfViewer>,
        previousWidth: Int,
        previousHeight: Int,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            try {
                val w = evaluateJs(scenario,
                    "document.getElementById('content').width").toIntOrNull()
                val h = evaluateJs(scenario,
                    "document.getElementById('content').height").toIntOrNull()
                if (w != null && h != null &&
                    (w != previousWidth || h != previousHeight)) return
            } catch (_: Throwable) {}
            Thread.sleep(200)
        }
        throw AssertionError(
            "Canvas dimensions did not change from ${previousWidth}x${previousHeight} " +
                    "within ${timeout}ms"
        )
    }

    fun waitForDocumentChanged(
        scenario: ActivityScenario<PdfViewer>,
        expectedPages: Int,
        timeout: Long = 15_000
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            var matches = false
            scenario.onActivity { activity ->
                matches = activity.totalPages == expectedPages &&
                        activity.documentProperties != null
            }
            if (matches) return
            Thread.sleep(200)
        }
        throw AssertionError(
            "Document did not change to expected state " +
                    "(expectedPages=$expectedPages) within ${timeout}ms"
        )
    }
}