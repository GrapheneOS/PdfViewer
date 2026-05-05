package app.grapheneos.pdfviewer.test

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Edge-to-edge rendering and inset geometry checks.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerEdgeToEdgeTest {

    private val robot = PdfViewerRobot()

    private data class EdgeInsets(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    @Test
    fun edgeToEdgeBridge_reportsAndroidInsets() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = {
                    "JS bridge insets should match Android layout/window insets " +
                            "(expected=${getExpectedInsets(scenario)}, " +
                            "actual=${getChannelInsets(scenario)})"
                }
            ) {
                insetsMatch(getExpectedInsets(scenario), getChannelInsets(scenario))
            }

            val expected = getExpectedInsets(scenario)
            val actual = getChannelInsets(scenario)
            assertTrue("Expected visible app bar top inset > 0", expected.top > 0f)
            assertInsetsMatch("JS bridge insets", expected, actual)
        }
    }

    @Test
    fun edgeToEdgeCanvasPadding_matchesBridgeInsets() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)

            PdfViewerTestUtils.pollUntil(
                timeout = 5_000,
                description = {
                    "Canvas padding should match JS bridge insets " +
                            "(expected=${getChannelInsets(scenario)}, " +
                            "actual=${getCanvasPaddingInDevicePixels(scenario)})"
                }
            ) {
                insetsMatch(
                    getChannelInsets(scenario),
                    getCanvasPaddingInDevicePixels(scenario)
                )
            }

            assertInsetsMatch(
                "Canvas padding",
                getChannelInsets(scenario),
                getCanvasPaddingInDevicePixels(scenario)
            )
        }
    }

    @Test
    fun toolbarToggle_preservesTextLayerAlignment() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForCanvasRendered(scenario)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)

            robot.tapWebView()
            PdfViewerTestUtils.waitForToolbarState(scenario, visible = false)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)

            robot.tapWebView()
            PdfViewerTestUtils.waitForToolbarState(scenario, visible = true)
            PdfViewerTestUtils.assertTextLayerContent(scenario, "Test Text")
            robot.assertTextLayerAligned(scenario)
        }
    }

    private fun getExpectedInsets(
        scenario: ActivityScenario<PdfViewer>
    ): EdgeInsets {
        var result: EdgeInsets? = null
        scenario.onActivity { activity ->
            val webView = activity.findViewById<View>(R.id.webview)
            val appBar = activity.findViewById<View>(R.id.app_bar_layout)
            val insetTypes = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            val insets = ViewCompat.getRootWindowInsets(webView)?.getInsets(insetTypes)

            result = EdgeInsets(
                left = (insets?.left ?: 0).toFloat(),
                top = appBar.height.toFloat(),
                right = (insets?.right ?: 0).toFloat(),
                bottom = (insets?.bottom ?: 0).toFloat()
            )
        }
        return result ?: EdgeInsets(0f, 0f, 0f, 0f)
    }

    private fun getChannelInsets(
        scenario: ActivityScenario<PdfViewer>
    ) = EdgeInsets(
        left = PdfViewerTestUtils.evaluateJs(
            scenario, "channel.getInsetLeft()"
        ).toFloat(),
        top = PdfViewerTestUtils.evaluateJs(
            scenario, "channel.getInsetTop()"
        ).toFloat(),
        right = PdfViewerTestUtils.evaluateJs(
            scenario, "channel.getInsetRight()"
        ).toFloat(),
        bottom = PdfViewerTestUtils.evaluateJs(
            scenario, "channel.getInsetBottom()"
        ).toFloat()
    )

    private fun getCanvasPaddingInDevicePixels(
        scenario: ActivityScenario<PdfViewer>
    ) = EdgeInsets(
        left = evaluateCanvasPaddingInDevicePixels(scenario, "paddingLeft"),
        top = evaluateCanvasPaddingInDevicePixels(scenario, "paddingTop"),
        right = evaluateCanvasPaddingInDevicePixels(scenario, "paddingRight"),
        bottom = evaluateCanvasPaddingInDevicePixels(scenario, "paddingBottom")
    )

    private fun evaluateCanvasPaddingInDevicePixels(
        scenario: ActivityScenario<PdfViewer>,
        propertyName: String
    ): Float {
        return PdfViewerTestUtils.evaluateJs(
            scenario,
            """
                (function() {
                    const canvas = document.getElementById('content');
                    const value = parseFloat(getComputedStyle(canvas)['$propertyName']) || 0;
                    return value * globalThis.devicePixelRatio;
                })()
            """.trimIndent()
        ).toFloat()
    }

    private fun assertInsetsMatch(
        label: String,
        expected: EdgeInsets,
        actual: EdgeInsets
    ) {
        assertTrue(
            "$label should match expected=$expected actual=$actual",
            insetsMatch(expected, actual)
        )
    }

    private fun insetsMatch(expected: EdgeInsets, actual: EdgeInsets): Boolean {
        return abs(expected.left - actual.left) <= INSET_TOLERANCE_PX &&
                abs(expected.top - actual.top) <= INSET_TOLERANCE_PX &&
                abs(expected.right - actual.right) <= INSET_TOLERANCE_PX &&
                abs(expected.bottom - actual.bottom) <= INSET_TOLERANCE_PX
    }

    private companion object {
        const val INSET_TOLERANCE_PX = 1.5f
    }
}
