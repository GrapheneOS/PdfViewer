package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.crashed
import app.grapheneos.pdfviewer.currentPage
import app.grapheneos.pdfviewer.documentName
import app.grapheneos.pdfviewer.documentProperties
import app.grapheneos.pdfviewer.outlineStatus
import app.grapheneos.pdfviewer.refreshMenuSync
import app.grapheneos.pdfviewer.totalPages
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import app.grapheneos.pdfviewer.util.PdfViewerTestUtils
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Options menu visibility and enabled states
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerMenuStateTest {

    private val robot = PdfViewerRobot()

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

            scenario.onActivity {
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = false, nextEnabled = true)
        }
    }

    @Test
    fun postLoadState_lastPage_previousEnabledNextDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.currentPage = 4
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = true, nextEnabled = false)
        }
    }

    @Test
    fun postLoadState_middlePage_bothNavigationEnabled() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.currentPage = 2
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = true, nextEnabled = true)
        }
    }

    @Test
    fun postLoadState_singlePage_bothNavigationDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.refreshMenuSync()
            }

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
                it.refreshMenuSync()
            }

            robot.assertMenuItemEnabled(scenario, PdfViewerRobot.AppMenuItem.Open, expected = false)
            robot.assertMenuItemEnabled(scenario, PdfViewerRobot.AppMenuItem.Next, expected = false)
            robot.assertMenuItemEnabled(scenario, PdfViewerRobot.AppMenuItem.Previous, expected = false)
            robot.assertMenuItemEnabled(scenario, PdfViewerRobot.AppMenuItem.Share, expected = false)
            robot.assertMenuItemEnabled(scenario, PdfViewerRobot.AppMenuItem.SaveAs, expected = false)
            robot.assertMenuItemEnabled(scenario, PdfViewerRobot.AppMenuItem.JumpToPage, expected = false)
        }
    }

    // Outline visibility

    @Test
    fun postLoadState_noOutline_outlineItemNotVisible() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.refreshMenuSync()
            }

            robot.assertMenuItemVisible(scenario, PdfViewerRobot.AppMenuItem.Outline, expected = false)
        }
    }

    @Test
    fun postLoadState_hasOutline_outlineItemVisible() {
        PdfViewerLauncher.launchWithTestAsset("test-multipage.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)
            PdfViewerTestUtils.waitForOutlineAvailable(scenario)

            scenario.onActivity {
                it.refreshMenuSync()
            }

            robot.assertMenuItemVisible(scenario, PdfViewerRobot.AppMenuItem.Outline, expected = true)
        }
    }

    // Document properties

    @Test
    fun postLoadState_propertiesNull_propertiesItemDisabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.documentProperties = null
                it.refreshMenuSync()
            }

            robot.assertMenuItemEnabled(
                scenario, PdfViewerRobot.AppMenuItem.ViewDocumentProperties, expected = false
            )
        }
    }

    @Test
    fun postLoadState_propertiesSet_propertiesItemEnabled() {
        PdfViewerLauncher.launchWithTestAsset("test-simple.pdf").use { scenario ->
            PdfViewerTestUtils.waitForDocumentFullyLoaded(scenario)

            scenario.onActivity {
                it.refreshMenuSync()
            }

            robot.assertMenuItemEnabled(
                scenario, PdfViewerRobot.AppMenuItem.ViewDocumentProperties, expected = true
            )
        }
    }
}
