package app.grapheneos.pdfviewer

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Throws(
    FileNotFoundException::class,
    IOException::class,
    IllegalArgumentException::class,
    OutOfMemoryError::class
)
fun saveAs(context: Context, existingUri: Uri, saveAs: Uri) {
    context.asInputStream(existingUri)?.use { inputStream ->
        context.asOutputStream(saveAs)?.use { outputStream ->
            outputStream.write(inputStream.readBytes())
        }
    }

}

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

@Throws(FileNotFoundException::class)
private fun Context.asInputStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)

@Throws(FileNotFoundException::class)
private fun Context.asOutputStream(uri: Uri): OutputStream? = contentResolver.openOutputStream(uri)
