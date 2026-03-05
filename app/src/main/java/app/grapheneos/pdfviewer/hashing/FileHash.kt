package app.grapheneos.pdfviewer.hashing

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

object FileHash {

    private const val HASH_BUFFER_SIZE = 8192
    private const val MAX_HASH_BYTES = 16 * 1024  // 16KB

    /**
     * Calculates SHA-256 hash of file content (first 16KB only)
     * @return hex string hash, or null if calculation fails
     */
    suspend fun calculateFileHash(
        uri: Uri,
        contentResolver: ContentResolver
    ): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                var totalBytesRead = 0
                var bytesRead = 0

                while (totalBytesRead < MAX_HASH_BYTES &&
                    inputStream.read(buffer).also { bytesRead = it } != -1) {

                    val bytesToHash = minOf(bytesRead, MAX_HASH_BYTES - totalBytesRead)

                    digest.update(buffer, 0, bytesToHash)
                    totalBytesRead += bytesToHash
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
}