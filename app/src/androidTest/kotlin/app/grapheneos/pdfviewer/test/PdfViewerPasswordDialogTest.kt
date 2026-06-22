package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.grapheneos.pdfviewer.testrules.RetryRules
import app.grapheneos.pdfviewer.RetryableComposeRule
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
 * Password dialog behavior without JS callbacks.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerPasswordDialogTest {

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
    fun passwordDialog_positiveButtonStartsDisabled() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            robot.showPasswordDialog(scenario)
            robot.waitForPasswordDialog()
            robot.assertPasswordPositiveButtonEnabled(enabled = false)
        }
    }

    @Test
    fun passwordDialog_typingEnablesPositiveButton() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            robot.showPasswordDialog(scenario)
            robot.waitForPasswordDialog()

            robot.typePassword("password")
            robot.assertPasswordPositiveButtonEnabled(enabled = true)
        }
    }

    @Test
    fun passwordDialog_clearingTextDisablesPositiveButton() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            robot.showPasswordDialog(scenario)
            robot.waitForPasswordDialog()

            robot.typePassword("password")
            robot.assertPasswordPositiveButtonEnabled(enabled = true)

            robot.clearPasswordField()
            robot.assertPasswordPositiveButtonEnabled(enabled = false)
        }
    }

    @Test
    fun passwordDialog_invalidPasswordShowsError() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            robot.showPasswordDialog(scenario)
            robot.waitForPasswordDialog()
            robot.typePassword("wrongpassword")

            scenario.onActivity {
                it.viewModel.invalidPassword()
            }

            robot.waitForPasswordError()
            robot.assertPasswordError()
            robot.assertPasswordPositiveButtonEnabled(enabled = false)
        }
    }

    @Test
    fun passwordDialog_notDismissibleByBackPress() {
        PdfViewerLauncher.launchDefault().use { scenario ->
            robot.showPasswordDialog(scenario)
            robot.waitForPasswordDialog()

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressBack()

            robot.assertPasswordDialogShown()
        }
    }
}
