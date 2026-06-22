package app.grapheneos.pdfviewer.util

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.webkit.WebView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.outlineStatus
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object PdfViewerTestUtils {

    private lateinit var composeRule: ComposeTestRule

    fun init(rule: ComposeTestRule) {
        composeRule = rule
    }

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
            val webView = activity.webView
                ?: throw AssertionError("WebView is null")
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
            composeRule.waitForIdle()
            try {
                if (condition()) return
            } catch (_: Throwable) {}
            Thread.sleep(interval)
        }
        throw AssertionError("${description()} not met within ${timeout}ms")
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
                    "parseInt(document.getElementById('content').style.width) > 0 " +
                            "&& parseInt(document.getElementById('content').style.height) > 0 " +
                            "&& parseFloat(document.getElementById('container').style.getPropertyValue('--scale-factor')) > 0 " +
                            "&& !document.getElementById('text').hidden"
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

    fun waitForCrashUiDismissed(
        scenario: ActivityScenario<PdfViewer>,
        timeout: Long = 10_000
    ) {
        pollUntil(
            timeout = timeout,
            description = { "Crash UI not dismissed within ${timeout}ms" }
        ) {
            var dismissed = false
            scenario.onActivity { activity ->
                dismissed = !activity.viewModel.webViewCrashed.value
            }
            dismissed
        }
    }

    fun waitForToolbarState(
        scenario: ActivityScenario<PdfViewer>,
        visible: Boolean,
        timeout: Long = 10_000
    ) {
        pollUntil(
            timeout = timeout,
            interval = 100,
            description = { "Toolbar visibility should be visible=$visible" }
        ) {
            var matches = false
            scenario.onActivity { matches = (it.viewModel.toolbarVisible.value == visible) }
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

    fun waitForCanvasCssDimensionsChanged(
        scenario: ActivityScenario<PdfViewer>,
        previousWidth: Int,
        previousHeight: Int,
        timeout: Long = 15_000
    ) {
        pollUntil(
            timeout = timeout,
            description = {
                "Canvas CSS dimensions did not change from ${previousWidth}x${previousHeight} " +
                        "within ${timeout}ms"
            }
        ) {
            val w = evaluateJs(
                scenario,
                "parseInt(document.getElementById('content').style.width) || 0"
            ).toIntOrNull()
            val h = evaluateJs(
                scenario,
                "parseInt(document.getElementById('content').style.height) || 0"
            ).toIntOrNull()
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
        pollUntil(
            timeout = timeout,
            description = {
                "Document orientation did not reach ${expected}°"
            }
        ) {
            val result = evaluateJs(scenario, "channel.getDocumentOrientationDegrees()")
            result.toIntOrNull() == expected
        }
    }

    fun waitForSnackbar(message: PdfViewerRobot.SnackbarMessage, timeout: Long = 10_000) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedText = context.getString(message.stringRes)
        composeRule.waitUntil(timeout) {
            composeRule.onAllNodes(hasText(expectedText))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun setDeviceOrientation(scenario: ActivityScenario<PdfViewer>, landscape: Boolean) {
        val targetOrientation = if (landscape)
            Configuration.ORIENTATION_LANDSCAPE
        else
            Configuration.ORIENTATION_PORTRAIT
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        if (landscape) {
            device.setOrientationLeft()
        } else {
            device.setOrientationNatural()
        }
        scenario.onActivity {
            it.requestedOrientation = if (landscape)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        instrumentation.waitForIdleSync()
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
        instrumentation.waitForIdleSync()
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
        duration: Long = 2_000
    ) {
        assertStableCondition(
            duration = duration,
            interval = 50,
            description = { "Toolbar visibility should stay visible=$expectedVisible" }
        ) {
            var actual = false
            scenario.onActivity { actual = it.viewModel.toolbarVisible.value }
            actual == expectedVisible
        }
    }
}
