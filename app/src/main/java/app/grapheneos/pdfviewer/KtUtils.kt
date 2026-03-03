package app.grapheneos.pdfviewer

import android.content.ContentResolver
import android.net.Uri
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.grapheneos.pdfviewer.preferences.PdfPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

suspend fun calculateFileHash(
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

fun calculateFileHashAsync(
    lifecycleOwner: LifecycleOwner,
    uri: Uri,
    contentResolver: ContentResolver,
    onHashCalculated: (String?) -> Unit
): Job {
    return lifecycleOwner.lifecycleScope.launch {
        val hash = calculateFileHash(uri, contentResolver)
        onHashCalculated(hash)
    }
}

fun waitForHashAndCheckSavedPageAsync(
    lifecycleOwner: LifecycleOwner,
    hashJob: Job,
    fileHash: String?,
    repository: PdfPreferencesRepository,
    onPageFound: (Int?) -> Unit
) {
    lifecycleOwner.lifecycleScope.launch {
        hashJob.join()

        if (fileHash != null) {
            val savedPage = repository.getPageForFile(fileHash)
            onPageFound(savedPage)
        }
    }
}

fun getPageForFileBlocking(
    repository: PdfPreferencesRepository,
    fileHash: String
): Int? {
    return runBlocking {
        repository.getPageForFile(fileHash)
    }
}

fun loadPdfStateBlocking(
    repository: PdfPreferencesRepository
): PdfPreferencesRepository.PdfState {
    return runBlocking {
        repository.pdfStateFlow.first()
    }
}

fun savePdfStateBlocking(
    repository: PdfPreferencesRepository,
    uri: String,
    page: Int,
    fileHash: String?,
    includeHashMapping: Boolean
) {
    runBlocking {
        repository.saveLastOpened(uri, page)

        if (includeHashMapping && fileHash != null) {
            repository.updatePagePosition(fileHash, page)
        }
    }
}

fun savePdfStateAsync(
    lifecycleOwner: LifecycleOwner,
    repository: PdfPreferencesRepository,
    uri: String,
    page: Int,
    fileHash: String?,
    includeHashMapping: Boolean
) {
    lifecycleOwner.lifecycleScope.launch {
        repository.saveLastOpened(uri, page)

        if (includeHashMapping && fileHash != null) {
            repository.updatePagePosition(fileHash, page)
        }
    }
}

fun clearLastOpenedBlocking(
    repository: PdfPreferencesRepository
) {
    runBlocking {
        repository.clearLastOpened()
    }
}