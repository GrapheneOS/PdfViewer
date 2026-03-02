package app.grapheneos.pdfviewer

import android.content.ContentResolver
import android.net.Uri
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.grapheneos.pdfviewer.preferences.PdfPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

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

suspend fun calculateHash(
    uri: Uri,
    contentResolver: ContentResolver
): String? = withContext(Dispatchers.IO) {
    try {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    } catch (e: SecurityException) {
        e.printStackTrace()
        null
    }
}

suspend fun calculateFileHashAsync(
    uri: Uri,
    contentResolver: ContentResolver,
    onHashCalculated: (String?) -> Unit
) {
    val hash = calculateHash(uri, contentResolver)
    onHashCalculated(hash)
}

suspend fun waitForHashAndCheckSavedPage(
    hashJob: kotlinx.coroutines.Job,
    fileHash: String?,
    repository: PdfPreferencesRepository,
    onPageFound: (Int?) -> Unit
) {
    hashJob.join()

    if (fileHash != null) {
        val savedPage = repository.getPageForFile(fileHash)
        onPageFound(savedPage)
    }
}