package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.grapheneos.pdfviewer.util.PdfViewerLauncher
import app.grapheneos.pdfviewer.util.PdfViewerRobot
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Password dialog behavior without JS callbacks.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerPasswordDialogTest {

    private val robot = PdfViewerRobot()

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
                it.viewModel.invalid()
            }

            robot.assertPasswordError("invalid password")
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
