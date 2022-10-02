package app.grapheneos.pdfviewer.ktx

import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val systemBars = WindowInsetsCompat.Type.statusBars()

fun View.hideSystemUi(window: Window) {
    val controller = WindowCompat.getInsetsController(window, this)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(systemBars)
}

fun View.showSystemUi(window: Window) {
    WindowCompat.getInsetsController(window, this).show(systemBars)
}
