package app.grapheneos.pdfviewer.loader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.loader.content.AsyncTaskLoader;

import java.util.List;

public class DocumentPropertiesAsyncTaskLoader extends AsyncTaskLoader<List<CharSequence>> {

    public static final String TAG = "DocumentPropertiesLoader";

    public static final int ID = 1;

    private final String mProperties;
    private final int mNumPages;
    private final Uri mUri;

    public DocumentPropertiesAsyncTaskLoader(Context context, String properties, int numPages, Uri uri) {
        super(context);

        mProperties = properties;
        mNumPages = numPages;
        mUri = uri;
    }


    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Nullable
    @Override
    public List<CharSequence> loadInBackground() {

        DocumentPropertiesLoader loader = new DocumentPropertiesLoader(
                getContext(),
                mProperties,
                mNumPages,
                mUri
        );

        return loader.loadAsList();
    }
}
