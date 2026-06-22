package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.RetryableComposeRule
import app.grapheneos.pdfviewer.crashed
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.documentProperties
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
 * Options menu visibility and enabled states
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerMenuStateTest {

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
    fun preLoadState_navigationItemsNotShown() {
        PdfViewerLauncher.launchDefault().use {
            robot.assertNavigationNotShown()
        }
    }

    @Test
    fun postLoadState_firstPage_previousDisabledNextEnabled() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            robot.assertNavigationState(previousEnabled = false, nextEnabled = true)
        }
    }

    @Test
    fun postLoadState_lastPage_previousEnabledNextDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.currentPage = 4
            }
            composeRule.waitForIdle()

            robot.assertNavigationState(previousEnabled = true, nextEnabled = false)
        }
    }

    @Test
    fun postLoadState_middlePage_bothNavigationEnabled() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.currentPage = 2
            }
            composeRule.waitForIdle()

            robot.assertNavigationState(previousEnabled = true, nextEnabled = true)
        }
    }

    @Test
    fun postLoadState_singlePage_bothNavigationDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            robot.assertNavigationState(previousEnabled = false, nextEnabled = false)
        }
    }

    // Crash state

    @Test
    fun postLoadState_crashed_allActionsDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.crashed = true
            }
            composeRule.waitForIdle()

            robot.assertMenuItemEnabled(PdfViewerRobot.AppMenuItem.Open, expected = false)
            robot.assertMenuItemEnabled(PdfViewerRobot.AppMenuItem.Next, expected = false)
            robot.assertMenuItemEnabled(PdfViewerRobot.AppMenuItem.Previous, expected = false)
            robot.assertMenuItemEnabled(PdfViewerRobot.AppMenuItem.Share, expected = false)
            robot.assertMenuItemEnabled(PdfViewerRobot.AppMenuItem.SaveAs, expected = false)
            robot.assertMenuItemEnabled(PdfViewerRobot.AppMenuItem.JumpToPage, expected = false)
        }
    }

    // Outline visibility

    @Test
    fun postLoadState_noOutline_outlineItemNotVisible() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            robot.assertMenuItemVisible(PdfViewerRobot.AppMenuItem.Outline, expected = false)
        }
    }

    @Test
    fun postLoadState_hasOutline_outlineItemVisible() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)

            robot.assertMenuItemVisible(PdfViewerRobot.AppMenuItem.Outline, expected = true)
        }
    }

    // Document properties

    @Test
    fun postLoadState_propertiesNull_propertiesItemDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.documentProperties = null
            }
            composeRule.waitForIdle()

            robot.assertMenuItemEnabled(
                PdfViewerRobot.AppMenuItem.ViewDocumentProperties, expected = false
            )
        }
    }

    @Test
    fun postLoadState_propertiesSet_propertiesItemEnabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            robot.assertMenuItemEnabled(
                PdfViewerRobot.AppMenuItem.ViewDocumentProperties, expected = true
            )
        }
    }
}
