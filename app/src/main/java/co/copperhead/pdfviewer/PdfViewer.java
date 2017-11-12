package co.copperhead.pdfviewer;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import co.copperhead.pdfviewer.fragment.DocumentPropertiesFragment;
import co.copperhead.pdfviewer.fragment.JumpToPageFragment;
import co.copperhead.pdfviewer.loader.DocumentPropertiesLoader;

public class PdfViewer extends Activity implements LoaderManager.LoaderCallbacks<List<CharSequence>> {
    public static final String TAG = "PdfViewer";

    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_LEVEL = "zoomLevel";
    private static final String STATE_PROPERTIES = "properties";
    private static final String STATE_JSON_PROPERTIES = "json_properties";

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 4;
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final int STATE_LOADED = 1;
    private static final int STATE_END = 2;
    private static final int PADDING = 10;

    private WebView mWebView;
    private Uri mUri;
    public int mPage;
    public int mNumPages;
    private int mZoomLevel = 2;
    private int mDocumentState;
    private Channel mChannel;
    private List<CharSequence> mDocumentProperties;
    private InputStream mInputStream;
    private TextView mTextView;
    private Toast mToast;

    private class Channel {
        @JavascriptInterface
        public int getPage() {
            return mPage;
        }

        @JavascriptInterface
        public int getZoomLevel() {
            return mZoomLevel;
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            mNumPages = numPages;
        }

        @JavascriptInterface
        public void setDocumentProperties(final String properties) {
            if (mDocumentProperties != null) {
                throw new SecurityException("mDocumentProperties not null");
            }

            final Bundle args = new Bundle();
            args.putString(STATE_JSON_PROPERTIES, properties);
            getLoaderManager().restartLoader(DocumentPropertiesLoader.ID, args, PdfViewer.this);
        }
    }

    // Can be removed once minSdkVersion >= 26
    @SuppressWarnings("deprecation")
    private void disableSaveFormData(final WebSettings settings) {
        settings.setSaveFormData(false);
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview);

        mWebView = findViewById(R.id.webview);
        final WebSettings settings = mWebView.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        disableSaveFormData(settings);

        CookieManager.getInstance().setAcceptCookie(false);

        mChannel = new Channel();
        mWebView.addJavascriptInterface(mChannel, "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("GET".equals(request.getMethod()) && "https://localhost/placeholder.pdf".equals(request.getUrl().toString())) {
                    return new WebResourceResponse("application/pdf", null, mInputStream);
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mDocumentState = STATE_LOADED;
                invalidateOptionsMenu();
            }
        });

        mTextView = new TextView(this);
        mTextView.setBackgroundColor(Color.DKGRAY);
        mTextView.setTextColor(ColorStateList.valueOf(Color.WHITE));
        mTextView.setTextSize(18);
        mTextView.setPadding(PADDING, 0, PADDING, 0);

        final Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (!"application/pdf".equals(intent.getType())) {
                Log.e(TAG, "invalid mime type");
                finish();
                return;
            }
            mUri = intent.getData();
            mPage = 1;
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(STATE_URI);
            mPage = savedInstanceState.getInt(STATE_PAGE);
            mZoomLevel = savedInstanceState.getInt(STATE_ZOOM_LEVEL);
            mDocumentProperties = savedInstanceState.getCharSequenceArrayList(STATE_PROPERTIES);
        }

        if (mUri != null) {
            loadPdf();
        }
    }

    @Override
    public Loader<List<CharSequence>> onCreateLoader(int id, Bundle args) {
        return new DocumentPropertiesLoader(this, args.getString(STATE_JSON_PROPERTIES), mNumPages, mUri);
    }

    @Override
    public void onLoadFinished(Loader<List<CharSequence>> loader, List<CharSequence> data) {
        mDocumentProperties = data;
        getLoaderManager().destroyLoader(DocumentPropertiesLoader.ID);
    }

    @Override
    public void onLoaderReset(Loader<List<CharSequence>> loader) {
        mDocumentProperties = null;
    }

    private void loadPdf() {
        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
            mInputStream = getContentResolver().openInputStream(mUri);
        } catch (IOException e) {
            return;
        }
        mWebView.loadUrl("file:///android_asset/viewer.html");
    }

    private void renderPage(final boolean lazy) {
        mWebView.evaluateJavascript(lazy ? "onRenderPage(true)" : "onRenderPage(false)", null);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    private static void enableDisableMenuItem(MenuItem item, boolean enable) {
        if (enable) {
            if (!item.isEnabled()) {
                item.setEnabled(true);
                item.getIcon().setAlpha(ALPHA_HIGH);
            }
        } else if (item.isEnabled()) {
            item.setEnabled(false);
            item.getIcon().setAlpha(ALPHA_LOW);
        }
    }

    public void positiveButtonRenderPage(int selected_page) {
        if (selected_page >= 1 && selected_page <= mNumPages) {
            mPage = selected_page;
            renderPage(false);
            showPageNumber();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mPage);
        savedInstanceState.putInt(STATE_ZOOM_LEVEL, mZoomLevel);
        savedInstanceState.putCharSequenceArrayList(STATE_PROPERTIES,
                (ArrayList<CharSequence>) mDocumentProperties);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                mUri = resultData.getData();
                mPage = 1;
                mDocumentProperties = null;
                loadPdf();
                invalidateOptionsMenu();
            }
        }
    }

    private void showPageNumber() {
        if (mToast != null) {
            mToast.cancel();
        }
        mTextView.setText(String.format("%s/%s", mPage, mNumPages));
        mToast = new Toast(getApplicationContext());
        mToast.setGravity(Gravity.BOTTOM | Gravity.END, PADDING, PADDING);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.setView(mTextView);
        mToast.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final int ids[] = { R.id.action_zoom_in, R.id.action_zoom_out, R.id.action_jump_to_page,
                R.id.action_next, R.id.action_previous, R.id.action_view_document_properties };
        if (mDocumentState == 0) {
            for (final int id : ids) {
                final MenuItem item = menu.findItem(id);
                if (item.isVisible()) {
                    item.setVisible(false);
                }
            }
        } else if (mDocumentState == STATE_LOADED) {
            for (final int id : ids) {
                final MenuItem item = menu.findItem(id);
                if (!item.isVisible()) {
                    item.setVisible(true);
                }
            }
            mDocumentState = STATE_END;
        }

        switch (mZoomLevel) {
            case MAX_ZOOM_LEVEL:
                enableDisableMenuItem(menu.findItem(R.id.action_zoom_in), false);
                return true;
            case MIN_ZOOM_LEVEL:
                enableDisableMenuItem(menu.findItem(R.id.action_zoom_out), false);
                return true;
            default:
                enableDisableMenuItem(menu.findItem(R.id.action_zoom_in), true);
                enableDisableMenuItem(menu.findItem(R.id.action_zoom_out), true);
                return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                if (mPage > 1) {
                    mPage--;
                    renderPage(false);
                    showPageNumber();
                }
                return true;

            case R.id.action_next:
                if (mPage < mNumPages) {
                    mPage++;
                    renderPage(false);
                    showPageNumber();
                }
                return true;

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_out:
                if (mZoomLevel > 0) {
                    mZoomLevel--;
                    renderPage(true);
                    invalidateOptionsMenu();
                }
                return true;

            case R.id.action_zoom_in:
                if (mZoomLevel < MAX_ZOOM_LEVEL) {
                    mZoomLevel++;
                    renderPage(true);
                    invalidateOptionsMenu();
                }
                return true;

            case R.id.action_view_document_properties:
                DocumentPropertiesFragment
                        .getInstance((ArrayList<CharSequence>) mDocumentProperties)
                        .show(getFragmentManager(), null);
                return true;

            case R.id.action_jump_to_page:
                new JumpToPageFragment().show(getFragmentManager(), null);
                return true;

            default:
                return true;
        }
    }
}
