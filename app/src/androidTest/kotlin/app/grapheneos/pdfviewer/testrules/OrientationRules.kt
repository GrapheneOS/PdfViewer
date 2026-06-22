package app.grapheneos.pdfviewer.testrules

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class OrientationRules : TestWatcher() {
    override fun starting(description: Description) {
        resetToPortrait()
    }

    override fun finished(description: Description) {
        resetToPortrait()
    }

    private fun resetToPortrait() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.setOrientationNatural()
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        device.unfreezeRotation()
    }
}
