package app.grapheneos.pdfviewer

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun applySystemBarMargins(view: View, applyBottom: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val mlp = v.layoutParams as MarginLayoutParams
        mlp.leftMargin = insets.left
        mlp.rightMargin = insets.right
        if (applyBottom) {
            mlp.bottomMargin = insets.bottom
        }
        v.layoutParams = mlp
        windowInsets
    }
}
