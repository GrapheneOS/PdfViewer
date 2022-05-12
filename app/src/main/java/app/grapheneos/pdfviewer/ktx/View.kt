package app.grapheneos.pdfviewer.ktx

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val systemBars = WindowInsetsCompat.Type.statusBars()

fun View.hideSystemUi() {
    val controller = ViewCompat.getWindowInsetsController(this) ?: return
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(systemBars)
}

fun View.showSystemUi() {
    ViewCompat.getWindowInsetsController(this)?.show(systemBars)
}
