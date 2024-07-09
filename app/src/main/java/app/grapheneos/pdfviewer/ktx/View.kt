package app.grapheneos.pdfviewer.ktx

import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val systemBars = WindowInsetsCompat.Type.statusBars()

fun View.hideSystemUi(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController
        controller?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (window.decorView.rootWindowInsets.displayCutout == null) {
            controller?.hide(systemBars)
        } else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller?.hide(systemBars)
        }
    } else {
        val controller = WindowCompat.getInsetsController(window, this)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) && (window.decorView.rootWindowInsets.displayCutout == null)) {
            controller.hide(systemBars)
        } else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.hide(systemBars)
        }
    }
}

fun View.showSystemUi(window: Window) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController
        controller?.show(systemBars)
    } else {
        val controller = WindowCompat.getInsetsController(window, this)
        controller.show(systemBars)
    }
}
