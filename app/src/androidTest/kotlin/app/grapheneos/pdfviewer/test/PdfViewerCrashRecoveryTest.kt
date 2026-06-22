package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.crashed
import app.grapheneos.pdfviewer.testrules.OrientationRules
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Whole WebView crash recovery flow.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerCrashRecoveryTest {

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
    fun reloadButton_dismissesCrashUiAndRecovers() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            scenario.onActivity { it.crashed = true }
            scenario.recreate()

            robot.assertCrashUiVisible()
            robot.clickReload()

            PdfViewerTestUtils.waitForCrashUiDismissed(scenario)

            robot.assertCrashUiHidden()
            robot.assertWebViewVisible()
        }
    }
}
