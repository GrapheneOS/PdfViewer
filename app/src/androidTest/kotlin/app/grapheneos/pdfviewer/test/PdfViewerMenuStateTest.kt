package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.crashed
import app.grapheneos.pdfviewer.currentPage
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
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.currentPage = 1
                it.totalPages = 5
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = false, nextEnabled = true)
        }
    }

    @Test
    fun postLoadState_lastPage_previousEnabledNextDisabled() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.currentPage = 5
                it.totalPages = 5
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = true, nextEnabled = false)
        }
    }

    @Test
    fun postLoadState_middlePage_bothNavigationEnabled() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.currentPage = 3
                it.totalPages = 5
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = true, nextEnabled = true)
        }
    }

    @Test
    fun postLoadState_singlePage_bothNavigationDisabled() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.currentPage = 1
                it.totalPages = 1
                it.refreshMenuSync()
            }

            robot.assertNavigationState(previousEnabled = false, nextEnabled = false)
        }
    }

    // Crash state

    @Test
    fun postLoadState_crashed_allActionsDisabled() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.crashed = true
                it.refreshMenuSync()
            }

            robot.assertMenuItemEnabled(scenario, R.id.action_open, expected = false)
            robot.assertMenuItemEnabled(scenario, R.id.action_next, expected = false)
            robot.assertMenuItemEnabled(scenario, R.id.action_previous, expected = false)
            robot.assertMenuItemEnabled(scenario, R.id.action_share, expected = false)
            robot.assertMenuItemEnabled(scenario, R.id.action_save_as, expected = false)
            robot.assertMenuItemEnabled(scenario, R.id.action_jump_to_page, expected = false)
        }
    }

    // Outline visibility

    @Test
    fun postLoadState_noOutline_outlineItemNotVisible() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()
            robot.assertMenuItemVisible(scenario, R.id.action_outline, expected = false)
        }
    }

    @Test
    fun postLoadState_hasOutline_outlineItemVisible() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.outlineStatus = PdfViewModel.OutlineStatus.Available
                it.refreshMenuSync()
            }

            robot.assertMenuItemVisible(scenario, R.id.action_outline, expected = true)
        }
    }

    // Document properties

    @Test
    fun postLoadState_propertiesNull_propertiesItemDisabled() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            robot.assertMenuItemEnabled(
                scenario, R.id.action_view_document_properties, expected = false
            )
        }
    }

    @Test
    fun postLoadState_propertiesSet_propertiesItemEnabled() {
        PdfViewerLauncher.launchWithFakeUri().use { scenario ->
            PdfViewerTestUtils.waitForDocumentLoaded()

            scenario.onActivity {
                it.documentProperties = listOf("File name:test.pdf", "Title:-Test Document")
                it.refreshMenuSync()
            }

            robot.assertMenuItemEnabled(
                scenario, R.id.action_view_document_properties, expected = true
            )
        }
    }
}