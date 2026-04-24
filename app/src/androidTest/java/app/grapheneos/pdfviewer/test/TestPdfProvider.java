package app.grapheneos.pdfviewer.test;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Minimal ContentProvider that serves test PDF assets as content:// URIs.
 */
public class TestPdfProvider extends ContentProvider {

    public static final String AUTHORITY = "app.grapheneos.pdfviewer.test.provider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, @NonNull String mode) throws FileNotFoundException {
        String filename = uri.getLastPathSegment();
        if (filename == null) {
            throw new FileNotFoundException("URI must include a filename");
        }

        File file = new File(getContext().getCacheDir(), filename);
        if (!file.exists()) {
            try {
                InputStream is = getContext().getAssets().open(filename);
                FileOutputStream os = new FileOutputStream(file);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.close();
                is.close();
            } catch (IOException e) {
                throw new FileNotFoundException(
                        "Failed to copy asset '" + filename + "': " + e.getMessage());
            }
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return "application/pdf";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String assetName = uri.getLastPathSegment();
        if (assetName == null) return null;

        if (projection == null) {
            projection = new String[]{
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
            };
        }

        MatrixCursor cursor = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                row[i] = assetName;
            } else if (OpenableColumns.SIZE.equals(projection[i])) {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) { return null; }
    @Override
    public int delete(@NonNull Uri uri, String s, String[] a) { return 0; }
    @Override
    public int update(@NonNull Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
