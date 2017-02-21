package co.copperhead.pdfviewer;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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

public class PdfViewer extends Activity {
    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 4;
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final int STATE_DEFAULT = 0;
    private static final int STATE_LOADED = 1;
    private static final int STATE_END = 2;
    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_LEVEL = "zoomLevel";
    private static final int PADDING = 10;

    private WebView mWebView;
    private Uri mUri;
    int mPage;
    int mNumPages;
    private int mZoomLevel = 2;
    private int mDocumentState;
    private Channel mChannel;
    private boolean mZoomInState = true;
    private boolean mZoomOutState = true;
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
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview);

        mWebView = (WebView) findViewById(R.id.webview);
        WebSettings settings = mWebView.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(settings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);

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
                throw new RuntimeException();
            }
            mUri = (Uri) intent.getData();
            mPage = 1;
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(STATE_URI);
            mPage = savedInstanceState.getInt(STATE_PAGE);
            mZoomLevel = savedInstanceState.getInt(STATE_ZOOM_LEVEL);
        }

        if (mUri != null) {
            loadPdf();
        }
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

    private void renderPage() {
        mWebView.evaluateJavascript("onRenderPage()", null);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    private void saveMenuItemState(MenuItem item, boolean state) {
        if (item.getItemId() == R.id.action_zoom_in) {
            mZoomInState = state;
        } else {
            mZoomOutState = state;
        }
    }

    private void restoreMenuItemState(Menu menu) {
        if (menu.findItem(R.id.action_zoom_in).isEnabled() != mZoomInState) {
            menu.findItem(R.id.action_zoom_in).setEnabled(mZoomInState);
        } else if (menu.findItem(R.id.action_zoom_out).isEnabled() != mZoomOutState) {
            menu.findItem(R.id.action_zoom_out).setEnabled(mZoomOutState);
        }
    }

    private void disableItem(MenuItem item) {
        item.setEnabled(false);
        item.getIcon().setAlpha(ALPHA_LOW);
    }

    private void enableItem(MenuItem item) {
        item.setEnabled(true);
        item.getIcon().setAlpha(ALPHA_HIGH);
    }

    private void checkDisableMenuItem(MenuItem item) {
        if (item.isEnabled()) {
            disableItem(item);
            saveMenuItemState(item, false);
        }
    }

    private void checkEnableMenuItem(MenuItem item) {
        if (!item.isEnabled()) {
            enableItem(item);
            saveMenuItemState(item, true);
        }
    }

    private void enableDisableItems(Menu menu, boolean disable) {
        final int ids[] = { R.id.action_zoom_in, R.id.action_zoom_out, R.id.action_jump_to_page,
            R.id.action_next, R.id.action_previous };
        for (final int id : ids) {
            if (disable) {
                disableItem(menu.findItem(id));
            } else {
                enableItem(menu.findItem(id));
            }
        }
    }

    void positiveButtonRenderPage(int selected_page) {
        if (selected_page >= 1 && selected_page <= mNumPages) {
            mPage = selected_page;
            renderPage();
            showPageNumber();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mPage);
        savedInstanceState.putInt(STATE_ZOOM_LEVEL, mZoomLevel);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                mUri = resultData.getData();
                mPage = 1;
                loadPdf();
                mDocumentState = STATE_DEFAULT;
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
        restoreMenuItemState(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        switch (mDocumentState) {
            case STATE_DEFAULT:
                enableDisableItems(menu, true);
                return super.onPrepareOptionsMenu(menu);
            case STATE_LOADED:
                enableDisableItems(menu, false);
                mDocumentState = STATE_END;
                return super.onPrepareOptionsMenu(menu);
            default:
                break;
        }
        switch (mZoomLevel) {
            case MAX_ZOOM_LEVEL:
                checkDisableMenuItem(menu.findItem(R.id.action_zoom_in));
                return super.onPrepareOptionsMenu(menu);
            case MIN_ZOOM_LEVEL:
                checkDisableMenuItem(menu.findItem(R.id.action_zoom_out));
                return super.onPrepareOptionsMenu(menu);
            default:
                checkEnableMenuItem(menu.findItem(R.id.action_zoom_in));
                checkEnableMenuItem(menu.findItem(R.id.action_zoom_out));
                return super.onPrepareOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                if (mPage > 1) {
                    mPage--;
                    renderPage();
                    showPageNumber();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_next:
                if (mPage < mNumPages) {
                    mPage++;
                    renderPage();
                    showPageNumber();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_out:
                if (mZoomLevel > 0) {
                    mZoomLevel--;
                    renderPage();
                    invalidateOptionsMenu();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_in:
                if (mZoomLevel < MAX_ZOOM_LEVEL) {
                    mZoomLevel++;
                    renderPage();
                    invalidateOptionsMenu();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_jump_to_page:
                new JumpToPageFragment().show(getFragmentManager(), null);
                return super.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
