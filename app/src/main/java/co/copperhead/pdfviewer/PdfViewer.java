package co.copperhead.pdfviewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

public class PdfViewer extends Activity {
    private static final int MAX_ZOOM_LEVEL = 4;
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_LEVEL = "zoomLevel";

    private WebView mWebView;
    private Uri mUri;
    private Channel mChannel;

    private class Channel {
        public String mUrl;
        public int mPage;
        public int mNumPages;
        public int mZoomLevel;

        Channel() {
            mZoomLevel = 2;
        }

        @JavascriptInterface
        public String getUrl() {
            return mUrl;
        }

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

        mWebView = (WebView) findViewById(R.id.webView1);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);

        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);

        mChannel = new Channel();
        mWebView.addJavascriptInterface(mChannel, "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (mUri != null) {
                    loadPdf();
                }
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action)) {
            if (!type.equals("application/pdf")) {
                throw new RuntimeException();
            }
            mUri = (Uri) intent.getData();
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(STATE_URI);
            mChannel.mPage = savedInstanceState.getInt(STATE_PAGE);
            mChannel.mZoomLevel = savedInstanceState.getInt(STATE_ZOOM_LEVEL);
        }

        mWebView.loadUrl("file:///android_asset/viewer.html");
    }

    private void loadPdf() {
        mChannel.mPage = 1;
        mChannel.mUrl = mUri.toString();
        mWebView.evaluateJavascript("onGetDocument()", null);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mChannel.mPage);
        savedInstanceState.putInt(STATE_ZOOM_LEVEL, mChannel.mZoomLevel);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                mUri = resultData.getData();
                loadPdf();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                if (mChannel.mPage > 1) {
                    mChannel.mPage--;
                    mWebView.evaluateJavascript("onRenderPage()", null);
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_next:
                if (mChannel.mPage < mChannel.mNumPages) {
                    mChannel.mPage++;
                    mWebView.evaluateJavascript("onRenderPage()", null);
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_out:
                if (mChannel.mZoomLevel > 0) {
                    mChannel.mZoomLevel--;
                    mWebView.evaluateJavascript("onRenderPage()", null);
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_in:
                if (mChannel.mZoomLevel < MAX_ZOOM_LEVEL) {
                    mChannel.mZoomLevel++;
                    mWebView.evaluateJavascript("onRenderPage()", null);
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_jump_to_page: {
                final NumberPicker picker = new NumberPicker(this);
                picker.setMinValue(1);
                picker.setMaxValue(mChannel.mNumPages);
                picker.setValue(mChannel.mPage);

                final FrameLayout layout = new FrameLayout(this);
                layout.addView(picker, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

                new AlertDialog.Builder(this)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            int page = picker.getValue();
                            if (page >= 1 && page <= mChannel.mNumPages) {
                                mChannel.mPage = page;
                                mWebView.evaluateJavascript("onRenderPage()", null);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();

                return super.onOptionsItemSelected(item);
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
